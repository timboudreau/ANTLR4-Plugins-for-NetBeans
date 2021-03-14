/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.spi.language.highlighting;

import com.mastfrog.range.DataIntRange;
import com.mastfrog.range.DataRange;
import com.mastfrog.range.Range;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.swing.text.AttributeSet;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.SemanticRegions.SemanticRegionsBuilder;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.support.AbstractHighlightsContainer;

/**
 * A SemanticRegions-based implementation of HighlightsContainer. OffsetsBag is
 * nice, but its coalescing algorithm is noticably slow, and SemanticRegions is
 * the ideal data-structure for what highlights containers do. So this allows
 * things to be faster, and simpler; it also avoids a bug in OffsetsBag that,
 * post-insertion, results in highlights that were offscreen when a character is
 * inserted retain their pre-insert offsets.
 * <p>
 * The principal difference with OffsetsBag in usage is that an entire set of
 * highlights is applied, and then commit() is called to actually update the live
 * data; there are no incremental updates.
 * </p>
 *
 * @author Tim Boudreau
 */
final class AlternateBag extends AbstractHighlightsContainer implements HighlightConsumer {

    private final List<RangeEntry> entries = new ArrayList<>( 128 );
    private short ix = Short.MIN_VALUE;
    volatile int rev;
    int lastSize = 64;
    SemanticRegions<AttributeSet> current;
    private final Object ident;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final WriteLock writeLock = lock.writeLock();
    private final ReadLock readLock = lock.readLock();

    AlternateBag( Object ident ) {
        this.ident = ident;
    }

    public String toString() {
        SemanticRegions<AttributeSet> regs = current;
        return "bag(" + ident + " " + ( regs == null ? "null" : regs.size() ) + " pending " + entries.size() + ")";
    }

    public void addHighlight( int startOffset, int endOffset, AttributeSet attributes ) {
        synchronized ( this ) {
            if ( !entries.isEmpty() ) {
                RangeEntry last = entries.get( entries.size() - 1 );
                if ( last.start() == startOffset && last.size() == endOffset - startOffset ) {
                    AttributeSet nue = AttributesUtilities.createImmutable( last.attrs, attributes );
//                    AttributeSet nue = AttributesUtilities.createComposite( last.attrs, attributes );
                    last.attrs = nue;
                    return;
                }
            }
            entries.add( new RangeEntry( startOffset, endOffset - startOffset, attributes, ix++ ) );
        }
    }

    public void update( BooleanSupplier run ) {
        boolean needCommit;
        synchronized ( this ) {
            needCommit = run.getAsBoolean();
        }
        if ( needCommit ) {
            commit();
        } else {
            clear();
        };
    }

    @Override
    public HighlightsContainer getHighlightsContainer() {
        return this;
    }

    @Override
    public void clear() {
        int localRev = this.rev;
        synchronized ( this ) {
            entries.clear();
            ix = Short.MIN_VALUE;
        }
        if ( this.rev == localRev + 1 ) {
            setCurrent( null );
        }
    }

    private void setCurrent( SemanticRegions<AttributeSet> regions ) {
        writeLock.lock();
        try {
            current = regions;
            rev++;
        } finally {
            writeLock.unlock();
        }
    }

    SemanticRegions<AttributeSet> current() {
        readLock.lock();
        try {
            return current;
        } finally {
            readLock.unlock();
        }
    }

    void onDeletion( int chars, int at ) {
        int was = rev;
        SemanticRegions<AttributeSet> old = current();
        if ( old == null || old.isEmpty() ) {
            return;
        }
        SemanticRegions<AttributeSet> nue = old.withDeletion( chars, at );
        boolean changed = false;

        if ( rev == was ) {
            setCurrent( nue );
            changed = true;
        }
        if ( changed ) {
            fireHighlights( old, nue );
        }
    }

    void onInsertion( int chars, int at ) {
        int was = rev;
        SemanticRegions<AttributeSet> old = current();
        if ( old == null || old.isEmpty() ) {
            return;
        }
        SemanticRegions<AttributeSet> nue = old.withInsertion( chars, at );
        boolean changed = false;
        if ( rev == was ) {
            setCurrent( nue );
            changed = true;
        }
        if ( changed ) {
            fireHighlights( old, nue );
        }
    }

    SemanticRegions<AttributeSet> build() {
        if ( entries.isEmpty() ) {
            return SemanticRegions.empty();
        }
        List<RangeEntry> copy = new ArrayList<>( entries.size() );
        int sz;
        synchronized ( this ) {
            copy.addAll( entries );
            entries.clear();
            lastSize = sz = copy.size();
            ix = Short.MIN_VALUE;
        }
        SemanticRegionsBuilder<AttributeSet> bldr = SemanticRegions.builder( AttributeSet.class, sz );
        Collections.sort( copy );
        for ( RangeEntry re : copy ) {
            bldr.add( re.attrs, re.start(), re.end() );
        }
        copy.clear();

        return bldr.build().flatten( new AttributesCoalescer() );
    }

    static class AttributesCoalescer implements Function<List<? extends AttributeSet>, AttributeSet> {
        private final Map<List<AttributeSet>, AttributeSet> cache = new HashMap<>();

        @Override
        public AttributeSet apply( List<? extends AttributeSet> attrs ) {
            AttributeSet result = cache.get( attrs );
            if ( result != null ) {
                return result;
            }
            result = AttributesUtilities.createComposite( attrs.toArray( new AttributeSet[ attrs.size() ] ) );
            cache.put( new ArrayList<>( attrs ), result );
            return result;
        }

    }

    void commit() {
        SemanticRegions<AttributeSet> old = current();
        SemanticRegions<AttributeSet> nue = build();
        boolean simpleChange = ( old == null || old.isEmpty() ) != ( nue == null || nue.isEmpty() );
        boolean anyChange = simpleChange || ( old != null && nue != null && !old.equalTo( nue ) );
        if ( anyChange ) {
            setCurrent( nue );
            fireHighlights( old, nue );
        }
    }

    private static boolean async = !Boolean.getBoolean( "unit.test" );

    private void fireHighlights( SemanticRegions<AttributeSet> old, SemanticRegions<AttributeSet> nue ) {
        int was = rev;
        if ( nue.isEmpty() && ( old == null || old.isEmpty() ) ) {
            return;
        }
        if ( old == null ) {
            fireHighlightsChange( 0, nue.forIndex( nue.size() - 1 ).end() );
            return;
        }
        if ( nue.isEmpty() ) {
            fireHighlightsChange( old.forIndex( 0 ).start(), old.forIndex( old.size() - 1 ).end() );
            return;
        }
        int changeStart = 0;
        int changeEnd = 0;
        int target = -1;
        for ( int i = 0; i < Math.min( nue.size(), old.size() ); i++ ) {
            SemanticRegion<AttributeSet> a = old.forIndex( i );
            SemanticRegion<AttributeSet> b = nue.forIndex( i );
            if ( a.start() == b.start() && a.end() == b.end() ) {
                if ( !a.key().equals( b.key() ) ) {
                    changeStart = a.start();
                    target = i;
                    break;
                }
            } else {
                changeStart = Math.min( a.start(), b.start() );
                target = i;
                break;
            }
        }
        if ( target == -1 ) {
            return;
        }
        for ( int endA = old.size() - 1, endB = nue.size() - 1; endA >= 0 && endB >= 0; endA--, endB-- ) {
            SemanticRegion<AttributeSet> a = old.forIndex( endA );
            SemanticRegion<AttributeSet> b = nue.forIndex( endB );
            if ( a.start() == b.start() && a.end() == b.end() ) {
                if ( !a.key().equals( b.key() ) ) {
                    changeEnd = a.end();
                    break;
                }
            } else {
                changeEnd = Math.max( a.end(), b.end() );
                break;
            }
        }
        if ( changeStart != changeEnd ) {
            fireHighlightsChange( changeStart, changeEnd );
            ChangeFirer f = new ChangeFirer( changeStart, changeEnd, this, was );
            if ( async ) {
                EventQueue.invokeLater( f );
            } else {
                f.run();
            }
        }
    }

    static final class ChangeFirer implements Runnable {
        private final int start;
        private final int end;
        private final AlternateBag bag;
        private final int rev;

        public ChangeFirer( int start, int end, AlternateBag bag, int rev ) {
            this.start = start;
            this.end = end;
            this.bag = bag;
            this.rev = rev;
        }

        @Override
        public void run() {
            if ( bag.rev == rev ) {
                bag.fireHighlightsChange( start, end );
            }
        }

    }

    @Override
    public HighlightsSequence getHighlights( int startOffset, int endOffset ) {
        SemanticRegions<AttributeSet> regions = current();
        if ( regions == null || regions.isEmpty() ) {
            return HighlightsSequence.EMPTY;
        }
        if ( endOffset - startOffset == 1 ) {
            SemanticRegion<AttributeSet> item = regions.at( startOffset );
            if ( item == null ) {
                return HighlightsSequence.EMPTY;
            }
            return new SingleSeq( startOffset, item.key() );
        }
        Seq result = new Seq( regions, startOffset, endOffset );
        return result;
    }

    @Override
    public void setHighlights( HighlightsContainer other ) {
        if ( other instanceof AlternateBag ) {
            AlternateBag alt = ( AlternateBag ) other;
            boolean needBuild;
            synchronized ( this ) {
                this.entries.clear();
                synchronized ( other ) {
                    this.entries.addAll( alt.entries );
                }
                needBuild = !this.entries.isEmpty();
            }
            if ( needBuild ) {
                commit();
            } else {
                SemanticRegions<AttributeSet> otherCurrent = alt.current();
                if ( otherCurrent != null ) {
                    synchronized ( this ) {
                        setCurrent( otherCurrent );
                    }
                }
            }
        } else {
            HighlightsSequence seq = other.getHighlights( 0, Integer.MAX_VALUE );
            if ( seq == null ) {
                clear();
            }
            boolean any = false;
            int rev;
            synchronized ( this ) {
                rev = this.rev;
                while ( seq.moveNext() ) {
                    any = true;
                    addHighlight( seq.getStartOffset(), seq.getEndOffset(), seq.getAttributes() );
                }
            }
            if ( rev == this.rev ) {
                if ( any ) {
                    build();
                } else {
                    clear();
                }
            }
        }
    }

    /**
     * Many requests are for the character at the caret, so provide these more
     * efficiently.
     */
    static class SingleSeq implements HighlightsSequence {
        private final int start;
        private final AttributeSet attrs;
        private boolean used;

        public SingleSeq( int start, AttributeSet attrs ) {
            this.start = start;
            this.attrs = attrs;
        }

        @Override
        public boolean moveNext() {
            if ( used ) {
                return false;
            }
            used = true;
            return true;
        }

        @Override
        public int getStartOffset() {
            if ( !used ) {
                throw new NoSuchElementException();
            }
            return start;
        }

        @Override
        public int getEndOffset() {
            return getStartOffset() + 1;
        }

        @Override
        public AttributeSet getAttributes() {
            if ( !used ) {
                throw new NoSuchElementException();
            }
            return attrs;
        }
    }

    static class Seq implements HighlightsSequence {

        private final SemanticRegions<AttributeSet> regions;
        private SemanticRegion<AttributeSet> curr;
        int cursor = -1;
        private final int startOffset;
        private final int endOffset;

        public Seq( SemanticRegions<AttributeSet> regions, int startOffset, int endOffset ) {
            this.regions = regions;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            SemanticRegion<AttributeSet> first = regions.nearestTo( startOffset );
            if ( first == null ) {
                cursor = regions.size();
            } else {
                if ( first.end() <= startOffset ) {
                    if ( first.index() < regions.size() - 1 ) {
                        first = regions.forIndex( first.index() + 1 );
                    } else {
                        cursor = regions.size();
                    }
                }
                if ( cursor == -1 ) {
                    cursor = first.index() - 1;
                }
            }
        }

        @Override
        public String toString() {
            if ( curr == null ) {
                return "Seq(" + curr + " with " + regions.size() + " at " + cursor
                       + " covering " + startOffset + ":" + endOffset
                       + " - finished )";
            }
            SemanticRegion<AttributeSet> next = curr.index() == regions.size() - 1 ? null : regions.forIndex( curr
                    .index() + 1 );

            SemanticRegion<AttributeSet> prev = curr.index() == 0 ? null : regions.forIndex( curr.index() - 1 );
            return "Seq(" + curr + " with " + regions.size() + " at " + cursor
                   + " covering " + startOffset + ":" + endOffset
                   + " prev " + prev + " next " + next + ")";
        }

        @Override
        public boolean moveNext() {
            cursor++;
            boolean result = cursor < regions.size();
            if ( result ) {
                curr = regions.forIndex( cursor );
                if ( curr.start() >= endOffset ) {
                    cursor = regions.size();
                    result = false;
                }
            }
            return result;
        }

        @Override
        public int getStartOffset() {
            if ( curr == null ) {
                throw new NoSuchElementException();
            }
            if ( curr.start() < startOffset ) {
                return startOffset;
            }
            return Math.max( startOffset, curr.start() );
        }

        @Override
        public int getEndOffset() {
            if ( curr == null ) {
                throw new NoSuchElementException();
            }
            return Math.min( curr.end(), endOffset );
        }

        @Override
        public AttributeSet getAttributes() {
            if ( curr == null ) {
                throw new NoSuchElementException();
            }
            return curr.key();
        }
    }

    static class RangeEntry implements DataIntRange<AttributeSet, RangeEntry> {

        private AttributeSet attrs;
        private final short index;
        private final long value;

        public RangeEntry( int start, int size, AttributeSet attrs, short index ) {
//            this.start = start;
//            this.size = ( short ) Math.min( size, Short.MAX_VALUE );
            this.attrs = attrs;
            this.index = index;
            // We can wind up with thousands of these in memory.  It's worth it.
            value = ( ( ( long ) size ) << 32 ) | ( start & 0xffffffffL );
        }

        @Override
        public int compareTo( Range<?> o ) {
            int result = DataIntRange.super.compareTo( o );
            if ( result == 0 && o.getClass() == RangeEntry.class ) {
                RangeEntry re = ( RangeEntry ) o;
                result = Short.compare( index, re.index );
            }
            return result;
        }

        @Override
        public DataRange<AttributeSet, RangeEntry> newRange( int start, int size, AttributeSet newObject ) {
            return new RangeEntry( start, size, newObject, index );
        }

        @Override
        public DataRange<AttributeSet, RangeEntry> newRange( long start, long size, AttributeSet newObject ) {
            return new RangeEntry( ( int ) start, ( int ) size, newObject, index );
        }

        @Override
        public int start() {
            return ( int ) value;
        }

        @Override
        public int size() {
            return ( int ) ( value >> 32 );
        }

        @Override
        public RangeEntry newRange( int start, int size ) {
            return new RangeEntry( start, size, attrs, index );
        }

        @Override
        public RangeEntry newRange( long start, long size ) {
            return newRange( ( int ) start, ( int ) size );
        }

        @Override
        public AttributeSet get() {
            return attrs;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + ( ( int ) value );
            hash = 97 * hash + ( ( int ) ( value >> 32 ) );
            hash = 97 * hash + Objects.hashCode( this.attrs );
            hash = 97 * hash + this.index;
            return hash;
        }

        @Override
        public boolean equals( Object obj ) {
            if ( this == obj ) {
                return true;
            } else if ( obj == null || getClass() != obj.getClass() ) {
                return false;
            }
            final RangeEntry other = ( RangeEntry ) obj;
            return this.index == other.index && this.value == other.value
                   && Objects.equals( this.attrs, other.attrs );
        }
    }
}

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
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
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
 *
 * @author Tim Boudreau
 */
public final class AlternateBag extends AbstractHighlightsContainer implements HighlightConsumer {

    private final List<RangeEntry> entries = new ArrayList<>();
    private short ix = 0;
    volatile int rev;
    SemanticRegions<AttributeSet> current;
    private final Object ident;

    AlternateBag( Object ident ) {
        this.ident = ident;
    }

    public String toString() {
        SemanticRegions<AttributeSet> regs = current;
        return "bag(" + ident + " " + ( regs == null ? "null" : regs.size() ) + " pending " + entries.size() + ")";
    }

    public void addHighlight( int startOffset, int endOffset, AttributeSet attributes ) {
        if ( !entries.isEmpty() ) {
            RangeEntry last = entries.get( entries.size() - 1 );
            if ( last.start == startOffset && last.size == endOffset - startOffset ) {
                AttributeSet nue = AttributesUtilities.createComposite( last.attrs, attributes );
                last.attrs = nue;
                return;
            }
        }
        entries.add( new RangeEntry( startOffset, endOffset - startOffset, attributes, ix++ ) );
    }

    public void clear() {
        new Exception( "Clear bag " + this ).printStackTrace();
        entries.clear();
        ix = 0;
        synchronized ( this ) {
            current = null;
            rev++;
        }
    }

    void onDeletion( int chars, int at ) {
        int was = rev;
//        System.out.println( "onDeletion " + chars + " at " + at + " in " + this );
        SemanticRegions<AttributeSet> old = current;
        if ( old == null || old.isEmpty() ) {
            return;
        }
        SemanticRegions<AttributeSet> nue = old.withDeletion( chars, at );
        boolean changed = false;
        synchronized ( this ) {
            if ( rev == was ) {
                current = nue;
                rev++;
                changed = true;
            }
        }
        if ( changed ) {
            fireHighlights( old, nue );
        }
    }

    void onInsertion( int chars, int at ) {
        int was = rev;
//        System.out.println( "onInsertion " + chars + " at " + at + " in " + this );
        SemanticRegions<AttributeSet> old = current;
        if ( old == null || old.isEmpty() ) {
            return;
        }
        SemanticRegions<AttributeSet> nue = old.withInsertion( chars, at );
        boolean changed = false;
        synchronized ( this ) {
            if ( rev == was ) {
                current = nue;
                rev++;
                changed = true;
            }
        }
        if ( changed ) {
            fireHighlights( old, nue );
        }
    }

    SemanticRegions<AttributeSet> build() {
        if ( entries.isEmpty() ) {
//            System.out.println( "  build but have empty regions" );
            return SemanticRegions.empty();
        }
        SemanticRegionsBuilder<AttributeSet> bldr = SemanticRegions.builder( AttributeSet.class );
        Collections.sort( entries );
        try {
            for ( RangeEntry re : entries ) {
                bldr.add( re.attrs, re.start, re.end() );
            }
        } finally {
            entries.clear();
        }
        ix = 0;
        return bldr.build().flatten( attrs -> {
            return AttributesUtilities.createComposite( attrs.toArray( new AttributeSet[attrs.size()]));
        });
    }

    void commit() {
        SemanticRegions<AttributeSet> nue = build();
        SemanticRegions<AttributeSet> old = current;
        synchronized ( this ) {
            rev++;
            current = nue;
            if ( nue != null && nue.size() > 0 ) {
                System.out.println( "commit " + nue.size() + " for " + this );
            }
        }
        fireHighlights( old, nue );
    }

    private void fireHighlights( SemanticRegions<AttributeSet> old, SemanticRegions<AttributeSet> nue ) {
//        if ( true ) {
//            fireHighlightsChange( 0, Integer.MAX_VALUE );
//        }
        int was = rev;
        EventQueue.invokeLater( () -> {
            if ( rev != was ) {
                System.out.println( "skip fire" );
                return;
            }
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
            fireHighlightsChange( changeStart, changeEnd );
        } );
    }

    @Override
    public HighlightsSequence getHighlights( int startOffset, int endOffset ) {

        SemanticRegions<AttributeSet> regions;
        synchronized ( this ) {
            regions = current;
        }
        if ( current == null || current.isEmpty() ) {
//            System.out.println( "current null, return empty for " + this );
            return HighlightsSequence.EMPTY;
        }
        if ( false && endOffset - startOffset == 1 ) {
            SemanticRegion<AttributeSet> item = regions.at( startOffset );
            if ( item == null ) {
                return HighlightsSequence.EMPTY;
            }
            SemanticRegion<AttributeSet> par = item.parent();
            AttributeSet attrs = item.key();
            if ( par != null ) {
                LinkedList<AttributeSet> l = new LinkedList<>();
                l.add( attrs );
                while ( par != null ) {
                    l.add( 0, par.key() );
                    par = par.parent();
                }
                for ( SemanticRegion<AttributeSet> ch : item.allChildren() ) {
                    if ( ch.start() == item.start() ) {
                        l.add( ch.key() );
                    } else {
                        break;
                    }
                }
                attrs = AttributesUtilities.createComposite( l.toArray( new AttributeSet[ l.size() ] ) );
            }
//            System.out.println( "sng highlights " + startOffset + ":" + endOffset + " for " + this );
            return new SingleSeq( startOffset, attrs );
        }
//        System.out.println( "mult highlights " + startOffset + ":" + endOffset + " for " + this );
        return new Seq( current, startOffset, endOffset );
    }

    @Override
    public void setHighlights( HighlightsContainer other ) {
        if ( other instanceof AlternateBag ) {
            AlternateBag alt = ( AlternateBag ) other;
            this.entries.clear();
            this.entries.addAll( alt.entries );
            if ( ( ( AlternateBag ) other ).current != null ) {
                synchronized ( this ) {
                    this.current = ( ( AlternateBag ) other ).current;
                    rev++;
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

    class Seq implements HighlightsSequence {

        private final SemanticRegions<AttributeSet> regions;
        private SemanticRegion<AttributeSet> curr;
        private List<AttributeSet> activeScratch = new ArrayList<>( 12 );
        int cursor = -1;
        private final int startOffset;
        private final int endOffset;

        public Seq( SemanticRegions<AttributeSet> regions, int startOffset, int endOffset ) {
            this.regions = regions;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            if ( startOffset > 0 ) {
                SemanticRegion reg = regions.at( startOffset );
                if ( reg == null ) {
//                    System.out.println( "SCAN FOR " + startOffset );
                    for ( int i = 0; i < regions.size(); i++ ) {
                        SemanticRegion<AttributeSet> test = regions.forIndex( i );
                        if ( test.contains( startOffset ) ) {
//                            System.out.println( "settle at " + test + " for " + startOffset + ":" + endOffset );
                            cursor = i - 1;
                            break;
                        }
                    }
                } else {
//                    System.out.println(
//                            "  AT GETS " + startOffset + " -> " + reg + " for " + startOffset + ":" + endOffset );
                    cursor = reg.index() - 1;
                }
            } else {
                for ( int i = 0; i < regions.size(); i++ ) {
                    if ( regions.forIndex( i ).start() != 0 ) {
                        break;
                    }
                    cursor = i - 1;
                }
            }
        }

        @Override
        public String toString() {
            SemanticRegion<AttributeSet> next = curr.index() == regions.size() - 1 ? null : regions.forIndex( curr
                    .index() + 1 );

            SemanticRegion<AttributeSet> prev = curr.index() == 0 ? null : regions.forIndex( curr.index() - 1 );
            return "Seq(" + curr + " with " + regions.size() + " at " + cursor
                   + " covering " + startOffset + ":" + endOffset + " for " + AlternateBag.this
                   + " prev " + prev + " next " + next + ")";
        }

        @Override
        public boolean moveNext() {
            cursor++;
            boolean result = cursor < regions.size();
            if ( result ) {
                curr = regions.forIndex( cursor );
                if ( curr.start() >= endOffset ) {
                    result = false;
                    cursor = regions.size();
                } else {
                    // Find the *innermost* entry at the given position -
                    // there may be
                    for ( int i = curr.index() + 1; i < regions.size(); i++ ) {
                        SemanticRegion<AttributeSet> nested = regions.forIndex( i );
                        if ( nested.start() == curr.start() && nested.end() > endOffset ) {
                            cursor = nested.index();
//                            System.out.println( " ff to " + nested + " from " + curr + " for start " + curr.start()
//                                                + " and " + startOffset + ":" + endOffset );
                            curr = nested;
                        } else {
                            break;
                        }
                    }
                    cursor = curr.index();
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
            int end = curr.end();
            if ( cursor < regions.size() - 1 ) {
                SemanticRegion<AttributeSet> next = regions.forIndex( cursor + 1 );
                if ( curr.contains( next.start() ) && next.start() != curr.start() ) {
                    if ( next.start() == curr.start() ) {
                        end = next.end();
                    } else {
//                        System.out.println(
//                                "  ff end to " + next.start() + ":" + next.end() + " for " + startOffset + ":" + endOffset );
                        if ( next.start() > curr.start() && next.start() > startOffset ) {
                            end = next.start();
//                            System.out.println( "WWEIRD: " + curr + " / " + next );
                        } else {
//                            System.out.println( "WEIRD: " + curr + " / " + next );
                        }
                    }
                }
            }
            int result = Math.min( end, endOffset );
//            System.out.println( "    ret " + end + " for " + end + " / " + endOffset );
            return result;
        }

        @Override
        public AttributeSet getAttributes() {
            if ( curr == null ) {
                throw new NoSuchElementException();
            }
            SemanticRegion<AttributeSet> par = curr.parent();
            while ( par != null ) {
                activeScratch.add( 0, par.key() );
                par = par.parent();
            }
            for ( SemanticRegion<AttributeSet> ch : curr.allChildren() ) {
                if ( ch.start() == curr.start() ) {
                    // XXX out of order with curr's
                    activeScratch.add( ch.key() );
                } else {
                    break;
                }
            }
            AttributeSet attrs = curr.key();
            if ( !activeScratch.isEmpty() ) {
                activeScratch.add( attrs );
                attrs = AttributesUtilities.createComposite( activeScratch.toArray( new AttributeSet[ activeScratch
                        .size() ] ) );
                activeScratch.clear();
            }
//            System.out
//                    .println( "ATTRS " + attrs + " for " + curr.start() + ":" + curr.end() + " size " + curr.size()
//                              + " on " + this );
            return attrs;
        }
    }

    static class RangeEntry implements DataIntRange<AttributeSet, RangeEntry> {

        private final int start;
        private final int size;
        private AttributeSet attrs;
        private final short index;

        public RangeEntry( int start, int size, AttributeSet attrs, short index ) {
            this.start = start;
            this.size = size;
            this.attrs = attrs;
            this.index = index;
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
            return start;
        }

        @Override
        public int size() {
            return size;
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
            hash = 97 * hash + this.start;
            hash = 97 * hash + this.size;
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
            if ( this.start != other.start ) {
                return false;
            }
            if ( this.size != other.size ) {
                return false;
            }
            if ( this.index != other.index ) {
                return false;
            }
            return this.start == other.start && this.size == other.size
                   && Objects.equals( this.attrs, other.attrs );
        }
    }
}

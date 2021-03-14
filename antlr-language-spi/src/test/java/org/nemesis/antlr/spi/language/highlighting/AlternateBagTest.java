/*
 * Copyright 2020 Mastfrog Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.spi.language.highlighting;

import com.mastfrog.function.throwing.ThrowingQuadConsumer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.spi.language.highlighting.AlternateBag.SingleSeq;
import org.netbeans.spi.editor.highlighting.HighlightsChangeEvent;
import org.netbeans.spi.editor.highlighting.HighlightsChangeListener;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Tim Boudreau
 */
public class AlternateBagTest {
    private int index;

    @Test
    public void testNonOverlapping() {
        AlternateBag ab = new AlternateBag( "x" );
        for ( int i = 0, offset = 0; offset < 100; offset += 10, i++ ) {
            ab.addHighlight( offset, offset + 5, attr() );
        }
        ab.commit();
        HighlightsSequence seq = ab.getHighlights( 0, 100 );
        assertNotSame( HighlightsSequence.EMPTY, seq, "Empty sequence returned" );
        int ix = 0;
        while ( seq.moveNext() ) {
            int st = seq.getStartOffset();
            int en = seq.getEndOffset();
            AttributeSet attrs = seq.getAttributes();
            int expectedStart = ix * 10;
            int expectedEnd = expectedStart + 5;

            assertEquals( st, expectedStart, "Wrong start for attr " + ix );
            assertEquals( en, expectedEnd, "Wrong end for attr " + ix );
            assertAttributes( attrs, ix );
            ix++;
        }
    }

    @Test
    public void testMultipleOverlapping() {
        AlternateBag ab = new AlternateBag( "z" );
        int max = 1000;
        List<Set<String>> sets = new ArrayList<>( max );
        for ( int i = 0; i < max; i++ ) {
            sets.add( new HashSet<>( 4 ) );
        }
        for ( int i = 0; i < 10; i++ ) {
            Attri attr = attr();
            String key1 = Integer.toString( attr.ix );
            int base = i * 100;
            for ( int j = base; j < base + 100; j++ ) {
                sets.get( j ).add( key1 );
            }
            ab.addHighlight( base, base + 100, attr );

            for ( int j = 0; j < 9; j++ ) {
                int off = base + ( j * 10 );
                Attri mid = attr();
                ab.addHighlight( off, off + 5, mid );
                for ( int k = off; k < off + 5; k++ ) {
                    sets.get( k ).add( Integer.toString( mid.ix ) );
                }
                Attri out = attr();
                ab.addHighlight( off + 6, off + 10, out );
                sets.get( off + 6 ).add( Integer.toString( out.ix ) );
                sets.get( off + 7 ).add( Integer.toString( out.ix ) );
                sets.get( off + 8 ).add( Integer.toString( out.ix ) );
                Attri in = attr();
                ab.addHighlight( off + 7, off + 8, in );
                sets.get( off + 7 ).add( Integer.toString( in.ix ) );

                Attri oth = attr();
                ab.addHighlight( off, off + 1, oth );
                sets.get( off ).add( Integer.toString( oth.ix ) );
            }
        }
        ab.commit();
        for ( int i = 0; i < 990; i++ ) {
            for ( int j = 0; j < 5; j++ ) {
                HighlightsSequence sq = ab.getHighlights( i, i + j + 1 );
                if ( sq.moveNext() ) {
                    int st = sq.getStartOffset();
                    assertTrue( st >= i );
                    assertTrue( st < i + j + 1 );
                    int en = sq.getEndOffset();
                    assertTrue( en <= i + j + 1 );
                    assertTrue( en > st, " end should be > start " + st + ":" + en + " in " + sq );
                }
            }
        }
        HighlightsSequence seq = ab.getHighlights( 0, 1000 );
        assertTrue( seq.moveNext() );
        for ( int i = 0; i < sets.size(); i++ ) {
//            System.out.println( i + ". " + Strings.join( ", ", sets.get( i ) ) );
            int start = seq.getStartOffset();
            int end = seq.getEndOffset();
            if ( i < start ) {
                i = start;
            }
            if ( end == i ) {
                seq.moveNext();
                start = seq.getStartOffset();
                end = seq.getEndOffset();
                if ( i < start ) {
                    i = start;
                }
            }
            AttributeSet attrs = seq.getAttributes();
            Set<String> names = names( attrs );
            for ( int j = start; j < end; j++ ) {
                Set<String> expect = sets.get( j );
//                System.out.println( "EXP " + Strings.join( ", ", expect ) + " GOT " + Strings.join( ", ", names ) );
                assertTrue( names.containsAll( expect ), "Mismatch at " + j + " expect " + expect + " got " + names
                                                         + " for seq " + seq + " at " + i );
            }
            for ( int j = 1; j < 11; j++ ) {
                HighlightsSequence sq = ab.getHighlights( i, i + j );
                sq.moveNext();
                assertEquals( names, names( sq.getAttributes() ),
                              "Attribute mismatch sequence " + start + ":" + end + " for " + i
                              + " vs. attributes returned by sequence for " + i + ":" + ( i + j ) + " - " + sq + " names " + names
                              + " vs " + names( sq.getAttributes() ) + " at " + j );
                assertEquals( i, sq.getStartOffset() );
//                assertEquals( Math.min( end, i + j ), sq.getEndOffset() );

            }
        }
    }

    @Test
    public void testOverlapping() {
        AlternateBag ab = new AlternateBag( "y" );
        for ( int i = 0; i < 5; i++ ) {
            int start = i * 10;
            int end = 100 - start;
            Attri attr = attr();
            ab.addHighlight( start, end, attr );
        }
        ab.commit();
        HighlightsSequence seq = ab.getHighlights( 0, 1000 );
        assertNotSame( HighlightsSequence.EMPTY, seq, "Empty sequence returned" );
        for ( int i = 0; i < 5; i++ ) {
            int offset = i * 10;
            assertTrue( seq.moveNext(), "MoveNext failed at " + i );
            int st = seq.getStartOffset();
            int en = seq.getEndOffset();
            AttributeSet attrs = seq.getAttributes();
            Set<String> names = names( attrs );
//            System.out.println( i + ". " + st + ":" + en + " " + names );
            for ( int j = 0; j <= i; j++ ) {
                String k = Integer.toString( j );
                assertTrue( names.contains( k ) );
            }
        }
        for ( int i = 0; i < 99; i++ ) {
            int ix;
            if ( i < 50 ) {
                ix = i / 10;
            } else if ( i == 50 ) {
                ix = 4;
            } else {
                int iv = 100 - i;
                ix = iv / 10;
            }
            HighlightsSequence sing = ab.getHighlights( i, i + 1 );
            assertTrue( sing instanceof SingleSeq );
            assertTrue( sing.moveNext() );
            AttributeSet attrs = sing.getAttributes();
//            System.out
//                    .println( i + ". " + ix + " ix " + sing.getStartOffset() + ":" + sing.getEndOffset() + " " + attrs );
            Set<String> names = names( attrs );
            for ( int j = 0; j < ix; j++ ) {
                String k = Integer.toString( j );
                assertTrue( names.contains( k ), "Should contain " + k + ": " + names );
            }
            assertFalse( sing.moveNext() );
            for ( int k = 2; k <= 10; k++ ) {
                HighlightsSequence mult = ab.getHighlights( i, i + k );
                assertFalse( mult instanceof SingleSeq );
                assertTrue( mult.moveNext() );
                attrs = mult.getAttributes();
//                System.out
//                        .println(
//                                i + ". " + ix + " ix " + sing.getStartOffset() + ":" + sing.getEndOffset() + " " + attrs );
                names = names( attrs );
                for ( int j = 0; j < ix; j++ ) {
                    String key = Integer.toString( j );
                    assertTrue( names.contains( key ),
                                "Should contain " + key + ": " + names + " for " + i + ":" + i + 1 );
                }

            }
        }
    }

    private static Set<String> names( AttributeSet set ) {
        Set<String> result = new HashSet<>( set.getAttributeCount() );
        Enumeration<?> en = set.getAttributeNames();
        while ( en.hasMoreElements() ) {
            result.add( en.nextElement().toString() );
        }
        return result;
    }

    private void assertAttributes( AttributeSet at, int... all ) {
        for ( int val : all ) {
            Integer value = ( Integer ) at.getAttribute( Integer.toString( val ) );
            assertEquals( Integer.valueOf( val ), value, "Not present: " + val + " in " + at );
        }
    }

    private Attri attr() {
        return new Attri( index++ );
    }

    static final class Attri extends SimpleAttributeSet {

        private int ix;

        Attri( int ix ) {
            this.ix = ix;
            super.addAttribute( "key", ix );
            super.addAttribute( Integer.toString( ix ), ix );
        }

        public boolean equals( Object o ) {
            return o instanceof Attri && ( ( Attri ) o ).ix == ix;
        }

        public int hashCode() {
            return 71 * ( ix + 1 );
        }

        public String toString() {
            return "Attri(" + ix + ")";
        }
    }

    private final String text = "In this directory are general-purpose tooling for generating most of\n"
                                + "a language plugin for NetBeans off of an Antlr Grammar and a few annotations.\n"
                                + "In the spirit of eating one's own dog food, under this project are modules which\n"
                                + "use that tooling to implement support for editing Antlr grammars themselves.\n"
                                + "\n"
                                + "Those that are working reliably are depended on by the module `antlr-editing-kit`.\n";

    @Test
    public void testCompareWithOffsetsBag() throws Exception {
        document( text, AlternateBagTest::decorateText, ( doc, oBag, aBag, hcl ) -> {
              HighlightsSequence oSeq = oBag.getHighlights( 0, text.length() );
              HighlightsSequence aSeq = aBag.getHighlights( 0, text.length() );
              int origEnd = assertSequencesSame( "Full text", oSeq, aSeq );
//              System.out.println( "sequences match" );
              for ( int i = 0; i < text.length() - 1; i++ ) {
                  for ( int j = text.length() - i; j >= 1; j-- ) {
                      oSeq = oBag.getHighlights( i, j );
                      aSeq = aBag.getHighlights( i, j );
                      assertSequencesSame( i + " to " + j + " in " + aBag + " vs " + oBag, oSeq, aSeq );
//                      System.out.println( i + ":" + j + " ok." );
                  }
              }
//              System.out.println( "insert string now" );
              doc.insertString( 29, "-generally-useful-hoober-goober", null );
//              System.out.println( "new text " + doc.getText( 0, doc.getLength() ) );
              HighlightsChangeEvent evt = hcl.assertAtLeastOneEvent();
//              System.out.println( "EVENT: " + evt.getStartOffset() + ":" + evt.getEndOffset() );

              oSeq = oBag.getHighlights( 0, text.length() );
              aSeq = aBag.getHighlights( 0, text.length() );
              assertTrue( oSeq.moveNext() );
              assertTrue( aSeq.moveNext() );
              int newEnd = assertSequencesSame( "Full text", oSeq, aSeq );
              assertNotEquals( origEnd, newEnd );
          } );
    }

    static int assertSequencesSame( String msg, HighlightsSequence a, HighlightsSequence b ) {
        int result = -1;
        for ( int i = 0;; i++ ) {
            boolean aNext = a.moveNext();
            boolean bNext = b.moveNext();
            assertEquals( aNext, bNext );
            if ( !aNext ) {
                break;
            }
            int aStart = a.getStartOffset();
            int bStart = b.getStartOffset();

            int aEnd = a.getEndOffset();
            int bEnd = b.getEndOffset();
            result = aEnd;

            AttributeSet aAttrs = a.getAttributes();
            AttributeSet bAttrs = b.getAttributes();

            int ix = i;
            assertEquals( aStart, bStart, () -> {
                      return msg + ": Start offsets differ for seq " + ix + ": " + aStart + " vs " + bStart
                             + " expected " + aStart + ":" + aEnd + " " + aAttrs + " but got "
                             + bStart + ":" + bEnd + " " + bAttrs + " in " + b + " types " + a.getClass()
                                      .getSimpleName()
                             + " and " + b.getClass().getSimpleName();
                  } );

            assertEquals( aEnd, bEnd, () -> {
                      return msg + ": End offsets differ for seq " + ix + ": " + aStart + " vs " + bStart
                             + " expected " + aStart + ":" + aEnd + " " + aAttrs + " but got "
                             + bStart + ":" + bEnd + " " + bAttrs + " in " + b;

                  } );

            assertEquals( aAttrs, bAttrs, msg + ": Attributes differ for " + ix + " at " + aStart + ":" + aEnd + ": "
                                          + aAttrs + " vs " + bAttrs + " expected " + aStart + ":" + aEnd + " " + aAttrs + " but got "
                                          + bStart + ":" + bEnd + " " + bAttrs + " in " + b );
        }
        return result;
    }

    static final Map<String, Integer> wordOffsets = new HashMap<>();

    static void decorateText( String text, HighlightConsumer con ) {
        StringBuilder currentWord = new StringBuilder();
        int lastWordStart = -1;
        int sentenceStart = 0;
        int sentenceCount = 1;
        for ( int i = 0; i < text.length(); i++ ) {
            char c = text.charAt( i );
            if ( Character.isWhitespace( c ) || c == '.' ) {
                if ( lastWordStart != -1 ) {
                    SimpleAttributeSet mu = new SimpleAttributeSet();
                    String word = currentWord.toString();
                    char first = word.charAt( 0 );
                    mu.addAttribute( "word", word );
                    wordOffsets.put( word, lastWordStart );
                    con.addHighlight( lastWordStart, i, mu );
                    if ( Character.isUpperCase( c ) ) {
                        SimpleAttributeSet caps = new SimpleAttributeSet();
                        caps.addAttribute( "cap", c );
                        con.addHighlight( lastWordStart, lastWordStart + 1, caps );
                    }
                    currentWord.setLength( 0 );
                    lastWordStart = -1;
                }
            } else {
                if ( lastWordStart == -1 ) {
                    lastWordStart = i;
                }
                currentWord.append( c );
                if ( c == '.' ) {
                    SimpleAttributeSet sen = new SimpleAttributeSet();
                    sen.addAttribute( "sentence", sentenceCount++ );
                    con.addHighlight( sentenceStart, i - 1, sen );
                    sentenceStart = i + 1;
                }
            }
        }
    }

    static void document( String text, BiConsumer<String, HighlightConsumer> c,
            ThrowingQuadConsumer<Document, OffsetsBag, AlternateBag, HCL> c1 ) throws Exception {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        AlternateBag ourBag = new AlternateBag( "a" );
        OffsetsBag addTo = new OffsetsBag( doc, true );
        doc.insertString( 0, text, null );
        c.accept( text, ourBag );
        c.accept( text, HighlightConsumer.wrap( addTo, doc ) );
        OffsetsBag realBag = new OffsetsBag( doc, true );

        HCL hcl = new HCL();
        realBag.addHighlightsChangeListener( hcl );
        ourBag.addHighlightsChangeListener( hcl );

        realBag.setHighlights( addTo );
        ourBag.commit();
        addTo.discard();

        hcl.assertEventsSame();

        doc.addDocumentListener( new DocumentListener() {
            @Override
            public void insertUpdate( DocumentEvent e ) {
//                System.out.println( "got insert" );
                ourBag.onInsertion( e.getLength(), e.getOffset() );
            }

            @Override
            public void removeUpdate( DocumentEvent e ) {
//                System.out.println( "got delete" );
                ourBag.onDeletion( e.getLength(), e.getOffset() );
            }

            @Override
            public void changedUpdate( DocumentEvent e ) {
                // do nothing
            }

        } );
        c1.accept( doc, realBag, ourBag, hcl );
    }

    static final class WrapWrap implements HighlightConsumer {
        private final HighlightConsumer delegate;

        public WrapWrap( HighlightConsumer delegate ) {
            this.delegate = delegate;
        }

        @Override
        public void addHighlight( int startOffset, int endOffset, AttributeSet attributes ) {
            // Sanity check test bugs
            if ( startOffset == endOffset ) {
                throw new IllegalArgumentException(
                        "Start == end " + startOffset + ":" + endOffset + " with " + attributes );
            }
            delegate.addHighlight( startOffset, endOffset, attributes );
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public void setHighlights( HighlightsContainer other ) {
            delegate.setHighlights( other );
        }

        @Override
        public void update( BooleanSupplier run ) {
            delegate.update( run );
        }

        @Override
        public HighlightsContainer getHighlightsContainer() {
            return delegate.getHighlightsContainer();
        }
    }

    static class HCL implements HighlightsChangeListener {
        List<HighlightsChangeEvent> events = new ArrayList<>();

        @Override
        public void highlightChanged( HighlightsChangeEvent event ) {
            events.add( event );
        }

        HighlightsChangeEvent assertAtLeastOneEvent() {
            assertTrue( events.size() > 0 );
            HighlightsChangeEvent evt = events.get( 0 );
            events.clear();
            return evt;
        }

        void assertEventsSame() {
            assertEquals( 2, events.size(), "Did not receive 2 events: " + events );
            List<HighlightsChangeEvent> l = new ArrayList<>( events );
            events.clear();
            HighlightsChangeEvent ev1 = l.get( 0 );
            HighlightsChangeEvent ev2 = l.get( 1 );

            Object src1 = ev1.getSource();
            Object src2 = ev2.getSource();

            assertEquals( ev1.getStartOffset(), ev2.getStartOffset(), () -> {
                      return "Event start offsets differ " + ev1.getStartOffset() + " from " + src1
                             + " and " + ev2.getStartOffset() + " from " + src2;
                  } );
            assertEquals( ev1.getEndOffset(), ev2.getEndOffset(), () -> {
                      return "Event end offsets differ " + ev1.getEndOffset() + " from " + src1
                             + " and " + ev2.getEndOffset() + " from " + src2;
                  } );
        }
    }
}

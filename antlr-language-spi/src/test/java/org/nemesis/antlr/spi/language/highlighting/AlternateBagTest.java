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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.spi.language.highlighting.AlternateBag.SingleSeq;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

//            System.out.println( ix + ". " + st + ":" + en + " " + attrs );
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
                    + " for seq " + seq + " at " + i);
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

}

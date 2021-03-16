/*
 * Copyright 2021 Mastfrog Technologies.
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

import com.mastfrog.function.throwing.ThrowingIntConsumer;
import com.mastfrog.function.throwing.ThrowingQuadConsumer;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.spi.language.highlighting.AlternateBagTest.HCL;
import org.netbeans.spi.editor.highlighting.HighlightsChangeEvent;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.nemesis.antlr.spi.language.highlighting.AlternateBagTest.document;

public class DebugAlternateBagTest {

    private final String text = "Hello this world.";

    @Test
    public void testInsertionAndDeletionWithNestedHighlights() throws Exception {
        String tx = someText();
        System.out.println( "Text: '" + tx + "'" );
        System.out.println( "Len: " + tx.length() );
        iter( 1, tx.length() - 1, start -> {
          iter( start + 1, tx.length(), end -> {
            simpleDocument( tx, ( doc, oBag, aBag, hcl ) -> {
                        HighlightsSequence oSeq = oBag.getHighlights( 0, tx.length() );
                        HighlightsSequence aSeq = aBag.getHighlights( 0, tx.length() );
                        List<En> orig = entries( oSeq );
                        assertEquals( orig, entries( aSeq ) );

                        doc.insertString( 30, "xxxxx", null );

                        oSeq = oBag.getHighlights( 0, tx.length() );
                        aSeq = aBag.getHighlights( 0, tx.length() );

                        System.out.println( "WAS: " + Strings.join( "\t", orig ) );
                        assertEntriesEqual( "After insert, entries wrong", entries( oSeq ), entries( aSeq ) );

//                    hcl.assertEventsSame();
                        System.out.println( "DOC SIZE " + doc.getLength() );
                        doc.remove( start, end - start );

                        System.out.println( "doc now '"
                                            + doc.getText( 0, doc.getLength() ) + " with length " + doc.getLength() );

                        oSeq = oBag.getHighlights( 0, tx.length() );
                        aSeq = aBag.getHighlights( 0, tx.length() );

                        List<En> oE1 = entries( oSeq );
                        List<En> aE1 = entries( aSeq );

                        String txt = doc.getText( 0, doc.getLength() );

                        assertEntriesEqual( "After deleting " + start + ":"
                                            + end + " (" + ( end - start )
                                            + " chars) to get '" + txt + "'\n"
                                            + "exp has " + oE1.size() + "\n"
                                            + "got has " + aE1.size(), oE1, aE1 );
                    } );
        } );
      } );
    }

    static void iter( int start, int end, ThrowingIntConsumer c ) throws Exception {
        for ( int i = start; i < end; i++ ) {
            c.accept( i );
        }
    }

    void assertEntriesEqual( String msg, List<En> expect, List<En> got ) {
        if ( expect.equals( got ) ) {
            return;
        }
        List<En> exp = new ArrayList<>( expect );
        List<En> gt = new ArrayList<>( expect );
        exp.removeAll( got );
        gt.removeAll( expect );
        int divergingStart = -1;
        int divergingStartEnd = -1;
        int divergingStartIndex = -1;
        int divergingEnd = -1;
        int divergingEndStart = -1;
        int divergingEndIndex = -1;
        for ( int i = 0; i < Math.min( expect.size(), got.size() ); i++ ) {
            if ( divergingStart < 0 && expect.get( i ).start != got.get( i ).start ) {
                divergingStartIndex = i;
                divergingStart = Math.min( expect.get( i ).start, got.get( i ).start );
            }
            if ( divergingStartEnd < 0 && expect.get( i ).end != got.get( i ).end ) {
                divergingStartEnd = Math.min( expect.get( i ).end, got.get( i ).end );
            }
            int eix = ( exp.size() - 1 ) - i;
            int gix = ( gt.size() - 1 ) - i;
            if ( eix >= 0 && gix >= 0 ) {
                En e = expect.get( i );
                En g = got.get( i );
                if ( divergingEnd < 0 && e.end != g.end ) {
                    divergingEnd = Math.max( e.end, g.end );
                    divergingEndIndex = i;
                }
                if ( divergingEndStart < 0 && e.start != g.start ) {
                    divergingEndStart = Math.max( e.start, g.start );
                }
            }
            if ( divergingStartEnd >= 0 && divergingEnd >= 0 && divergingStartEnd >= 0 && divergingEndStart >= 0 ) {
                break;
            }
        }
        StringBuilder sb = new StringBuilder( msg ).append( "\nContents differ starting at index " ).append(
                divergingStartIndex )
                .append( " thru " ).append( divergingEndIndex )
                .append( " with " ).append( divergingStart )
                .append( ":" ).append( divergingEnd ).append( ".  In expected:\n" )
                .append( Strings.join( "\t", exp ) ).append( " - in got:\n" ).append( Strings.join( "\t", gt ) )
                .append( " full contents - expected:\n" ).append( Strings.join( "\t", expect ) )
                .append( " but got\n" ).append( Strings.join( "\t", got ) );

        fail( sb.toString() );
    }

    static void simpleDocument( String text,
            ThrowingQuadConsumer<Document, OffsetsBag, AlternateBag, AlternateBagTest.HCL> c1 ) throws Exception {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        AlternateBag aBag = new AlternateBag( "a" );
        OffsetsBag oBag = new OffsetsBag( doc, true );
        doc.insertString( 0, text, null );
        aBag.update( () -> {;
            int ct = 0;
            int ct2 = 0;
            for ( int i = 0; i < text.length(); i++ ) {
                if ( i % 10 == 0 ) {
                    SimpleAttributeSet a = new SimpleAttributeSet();
                    a.addAttribute( "ss", "s-" + ct++ );
                    aBag.addHighlight( i, text.length(), a );
                    oBag.addHighlight( i, text.length(), a );
                }
                if ( i % 5 == 0 ) {
                    SimpleAttributeSet a = new SimpleAttributeSet();
                    a.addAttribute( "ww", "w-" + ct2++ );
                    oBag.addHighlight( i, i + 5, a );
                    aBag.addHighlight( i, i + 5, a );
                }
            }
            return true;
        } );
        HCL hcl = new HCL();
        oBag.addHighlightsChangeListener( hcl );
        aBag.addHighlightsChangeListener( hcl );
        doc.addDocumentListener( new DocumentListener() {
            @Override
            public void insertUpdate( DocumentEvent e ) {
                System.out.println( "got insert " + e.getLength() + " at " + e.getOffset() );
                aBag.onInsertion( e.getLength(), e.getOffset() );
            }

            @Override
            public void removeUpdate( DocumentEvent e ) {
                System.out.println( "got delete " + e.getLength() + " at " + e.getOffset() );
//                System.out.println( "got delete" );
                aBag.onDeletion( e.getLength(), e.getOffset() );
            }

            @Override
            public void changedUpdate( DocumentEvent e ) {
                // do nothing
            }
        } );
        c1.accept( doc, oBag, aBag, hcl );
    }

    private static String someText() {
        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < 6; i++ ) {
            for ( int j = 0; j < 7; j++ ) {
                sb.append( i );
            }
        }
        return sb.toString();
    }

//    @Test
    public void testInsertionAndDeletion() throws Exception {
        document( text, AlternateBagTest::decorateText, ( doc, oBag, aBag, hcl ) -> {
              HighlightsSequence oSeq = oBag.getHighlights( 0, text.length() );
              HighlightsSequence aSeq = aBag.getHighlights( 0, text.length() );

              List<En> oEntriesOrig = entries( oSeq );
              List<En> aEntriesOrig = entries( aSeq );

              for ( En e : aEntriesOrig ) {
                  System.out.println( " - " + e );
              }
              assertEquals( oEntriesOrig, aEntriesOrig );

              doc.insertString( 5, " ", null );
              SimpleAttributeSet a = new SimpleAttributeSet();
              a.addAttribute( "word", "stuff" );
              doc.insertString( 6, "stuff", null );

              oBag.addHighlight( 6, 11, a );
//              aBag.addHighlight( 6, 11, a );
//              aBag.build();
              aBag.setHighlights( oBag );

              oSeq = oBag.getHighlights( 0, text.length() );
              aSeq = aBag.getHighlights( 0, text.length() );

              List<En> oEntriesMod1 = entries( oSeq );
              List<En> aEntriesMod1 = entries( aSeq );

              System.out.println( "modEns o " + oEntriesMod1 );
              System.out.println( "modEns a " + aEntriesMod1 );

              for ( En e : oEntriesMod1 ) {
                  System.out.println( " o " + e );
              }

              for ( En e : aEntriesMod1 ) {
                  System.out.println( " a " + e );
              }
              assertEquals( oEntriesMod1, aEntriesMod1 );

              oBag.addHighlightsChangeListener( hc -> {
                  System.out.println( "O HL CHANGE " + hc.getStartOffset() + ":" + hc.getEndOffset() );
              } );
              aBag.addHighlightsChangeListener( hc -> {
                  System.out.println( "A HL CHANGE " + hc.getStartOffset() + ":" + hc.getEndOffset() );
              } );

              SimpleAttributeSet a1 = new SimpleAttributeSet();
              doc.insertString( 3, " xxxx ", null );

              oSeq = oBag.getHighlights( 0, text.length() );
              aSeq = aBag.getHighlights( 0, text.length() );

              List<En> oEntriesMod2 = entries( oSeq );
              List<En> aEntriesMod2 = entries( aSeq );

              oEntriesMod2.forEach( System.out::println );

              assertEquals( oEntriesMod2, aEntriesMod2 );

              System.out.println( "DOC T NOW " + doc.getText( 0, doc.getLength() ) );

              doc.remove( 2, 2 );

              System.out.println( "REM 2 NOW " + doc.getText( 0, doc.getLength() ) );

              oSeq = oBag.getHighlights( 0, text.length() );
              aSeq = aBag.getHighlights( 0, text.length() );

              List<En> oEntriesMod3 = entries( oSeq );
              List<En> aEntriesMod3 = entries( aSeq );

              System.out.println( "\nPOST DEL O\n" );
              oEntriesMod3.forEach( System.out::println );
              System.out.println( "\nPOST DEL A\n" );
              aEntriesMod3.forEach( System.out::println );
              System.out.println( "\n-----------" );

              assertEquals( oEntriesMod3, aEntriesMod3 );
          } );
    }

    private List<En> entries( HighlightsSequence seq ) {
        List<En> result = new ArrayList<>();
        while ( seq.moveNext() ) {
            int start = seq.getStartOffset();
            int end = seq.getEndOffset();
            AttributeSet attrs = seq.getAttributes();
            // OffsetsBag will occasionally produce an empty range that
            // we elide - ignore it as it has no effect on anything
            if ( start != end ) {
                En e = new En( start, end, attrs );
                result.add( e );
            }
        }
        return result;
    }

    static final class En {
        final int start;
        final int end;
        final AttributeSet attrs;

        public En( int start, int end, AttributeSet attrs ) {
            this.start = start;
            this.end = end;
            this.attrs = attrs;
        }

        @Override
        public boolean equals( Object o ) {
            if ( o == this ) {
                return true;
            } else if ( o == null || o.getClass() != En.class ) {
                return false;
            }
            En other = ( En ) o;
            if ( other.start != start || other.end != end ) {
                return false;
            }
            AttributeSet a = other.attrs;
            if ( ( a == null ) != ( attrs == null ) ) {
                return false;
            }
            return attrs == null ? true : isEqual( attrs, other.attrs );
        }

        static boolean isEqual( AttributeSet a, AttributeSet b ) {
            return toMap( a ).equals( toMap( b ) );
        }

        private static Map<String, String> toMap( AttributeSet a ) {
            if ( a == null ) {
                return Collections.emptyMap();
            }
            Map<String, String> m = new TreeMap<>();
            for ( Object key : CollectionUtils.toIterable( a.getAttributeNames() ) ) {
                Object att = a.getAttribute( key );
                m.put( Objects.toString( key ), Objects.toString( att ) );
            }
            return m;
        }

        @Override
        public int hashCode() {
            return ( 11 * start ) + ( 271 * end ) + toMap( attrs ).hashCode();
        }

        public String toString() {
            return start + ":" + end + " " + toMap( attrs );
        }
    }

//    @Test
    public void testDefaultBehavior() throws Exception {
        document( text, AlternateBagTest::decorateText, ( doc, oBag, aBag, hcl ) -> {
              HighlightsSequence oSeq = oBag.getHighlights( 1, text.length() );
              HighlightsSequence aSeq = aBag.getHighlights( 1, text.length() );
              for ( int i = 0;; i++ ) {
                  boolean oNext = oSeq.moveNext();
                  boolean aNext = aSeq.moveNext();

                  if ( oNext != aNext ) {
                  }

                  if ( !oNext && !aNext ) {
                      break;
                  }

                  int aStart = oSeq.getStartOffset();
                  int bStart = aSeq.getStartOffset();

                  int aEnd = oSeq.getEndOffset();
                  int bEnd = aSeq.getEndOffset();

                  assertEquals( oNext, aNext );
                  assertEquals( aStart, bStart );
                  assertEquals( aEnd, bEnd );
              }

              int st = 5;
              int en = 12;
              HighlightsSequence oSeqX = oBag.getHighlights( st, en );
              HighlightsSequence aSeqX = aBag.getHighlights( st, en );
              String sub = text.substring( st, en );
              for ( int i = 0;; i++ ) {
                  boolean oNext = oSeqX.moveNext();
                  boolean aNext = aSeqX.moveNext();

                  if ( oNext != aNext ) {
//                      System.out.println( "  DIFFERENCE AT " + i + " offsetsBag " + oNext + " altBag " + aNext
//                                          + " in text of length " + text.length() + "\nOB: " + oSeqX + "\nAB:" + aSeqX );
                  }

                  AttributeSet oAttrs = oNext ? oSeqX.getAttributes() : null;
                  AttributeSet aAttrs = aNext ? aSeqX.getAttributes() : null;

                  if ( !oNext && !aNext ) {
                      break;
                  }

                  int aStart = oNext ? oSeqX.getStartOffset() : -1;
                  int bStart = aNext ? aSeqX.getStartOffset() : -1;

                  int aEnd = oNext ? oSeqX.getEndOffset() : -1;
                  int bEnd = aNext ? aSeqX.getEndOffset() : -1;

                  assertEquals( aStart, bStart );
                  assertEquals( aEnd, bEnd );
              }

          }
        );
    }

    @Test
    public void testIt() throws Exception {
        document( text, AlternateBagTest::decorateText, ( doc, oBag, aBag, hcl ) -> {
              HighlightsSequence oSeq = oBag.getHighlights( 0, text.length() );
              HighlightsSequence aSeq = aBag.getHighlights( 0, text.length() );
              int origEnd = assertSequencesSame( "Full text", oSeq, aSeq, text );
              for ( int i = 0; i < text.length() - 1; i++ ) {
                  for ( int j = text.length() - i; j >= 1; j-- ) {
                      if ( i >= j ) {
                          // OffsetsBag WILL give a nonsensical sequence for backwards
                          // coordinates
                          break;
                      }
                      oSeq = oBag.getHighlights( i, j );
                      aSeq = aBag.getHighlights( i, j );
                      assertSequencesSame( i + " to " + j + " in " + aBag, oSeq, aSeq, text );
//                      System.out.println( i + ":" + j + " ok." );
                  }
              }
              doc.insertString( text.length() - 1, "-generally-useful-hoober-goober", null );
              System.out.println( "new text " + doc.getText( 0, doc.getLength() ) );
              HighlightsChangeEvent evt = hcl.assertAtLeastOneEvent();
              oSeq = oBag.getHighlights( 0, text.length() );
              aSeq = aBag.getHighlights( 0, text.length() );
              assertTrue( oSeq.moveNext() );
              assertTrue( aSeq.moveNext() );
              int newEnd = assertSequencesSame( "Full text", oSeq, aSeq, text );
              assertNotEquals( origEnd, newEnd );
          } );
    }

    static int assertSequencesSame( String msg, HighlightsSequence a, HighlightsSequence b, String text ) {
        int result = -1;
        for ( int i = 0;; i++ ) {
            boolean aNext = a.moveNext();
            boolean bNext = b.moveNext();
            assertEquals( aNext, bNext,
                          "MoveNext differs at " + i + " a " + aNext + " b "
                          + bNext + " for\n" + a + " ... and " + b + "\n" + msg );
            if ( !aNext ) {
                break;
            }
            int aStart = a.getStartOffset();
            int bStart = b.getStartOffset();

            int aEnd = a.getEndOffset();
            int bEnd = b.getEndOffset();
            result = aEnd;

            assertTrue( aEnd >= aStart, "Got backwards offsets from A: " + a + " " + a.getClass().getSimpleName()
                                        + " for " + msg );
            assertTrue( bEnd >= bStart, "Got backwards offsets from B: " + b + " " + a.getClass().getSimpleName()
                                        + " for " + msg );

            AttributeSet aAttrs = a.getAttributes();
            AttributeSet bAttrs = b.getAttributes();

            String seqB = text.substring( bStart, bEnd );
            String seqA = text.substring( aStart, aEnd );

            String sequences = "'" + seqA + "' vs '" + seqB + "'";

            int ix = i;
            assertEquals( aStart, bStart, () -> {
                      return sequences + "\n" + msg
                             + "\nStart offsets differ for seq " + ix + ": " + aStart + " vs " + bStart
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

            assertEquals( aAttrs, bAttrs, msg + ": Attributes differ for " + ix + " at "
                                          + aStart + ":" + aEnd + ": "
                                          + aAttrs + " vs " + bAttrs + " expected "
                                          + aStart + ":" + aEnd + " " + aAttrs + " but got "
                                          + bStart + ":" + bEnd + " " + bAttrs + " in " + b );
        }
        return result;
    }

}

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

import javax.swing.text.AttributeSet;
import org.junit.jupiter.api.Test;
import org.netbeans.spi.editor.highlighting.HighlightsChangeEvent;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nemesis.antlr.spi.language.highlighting.AlternateBagTest.document;

public class DebugAlternateBagTest {

    private final String text = "Hello this world.";

    @Test
    public void testDefaultBehavior() throws Exception {
        document( text, AlternateBagTest::decorateText, ( doc, oBag, aBag, hcl ) -> {
              HighlightsSequence oSeq = oBag.getHighlights( 1, text.length() );
              HighlightsSequence aSeq = aBag.getHighlights( 1, text.length() );
              for ( int i = 0;; i++ ) {
                  boolean oNext = oSeq.moveNext();
                  boolean aNext = aSeq.moveNext();

                  if ( oNext != aNext ) {
//                      System.out.println( "  DIFFERENCE AT " + i + " offsetsBag " + oNext + " altBag " + aNext );
                  }

                  if ( !oNext && !aNext ) {
//                      System.out.println( "Done at " + i );
                      break;
                  }

                  int aStart = oSeq.getStartOffset();
                  int bStart = aSeq.getStartOffset();

                  int aEnd = oSeq.getEndOffset();
                  int bEnd = aSeq.getEndOffset();

//                  System.out.println( i + ". " + aStart + ":" + aEnd + "\t" + bStart + ":" + bEnd );
                  assertEquals( oNext, aNext );
                  assertEquals( aStart, bStart );
                  assertEquals( aEnd, bEnd );
              }

              int st = 5;
              int en = 12;
              HighlightsSequence oSeqX = oBag.getHighlights( st, en );
              HighlightsSequence aSeqX = aBag.getHighlights( st, en );
              String sub = text.substring( st, en );
              System.out.println( "SUBSTRING '" + sub + "'" );
              for ( int i = 0;; i++ ) {
                  boolean oNext = oSeqX.moveNext();
                  boolean aNext = aSeqX.moveNext();

                  if ( oNext != aNext ) {
                      System.out.println( "  DIFFERENCE AT " + i + " offsetsBag " + oNext + " altBag " + aNext
                                          + " in text of length " + text.length() + "\nOB: " + oSeqX + "\nAB:" + aSeqX );
                  }

                  AttributeSet oAttrs = oNext ? oSeqX.getAttributes() : null;
                  AttributeSet aAttrs = aNext ? aSeqX.getAttributes() : null;

                  System.out.println( "OATT " + oAttrs + " AATT " + aAttrs );

                  if ( !oNext && !aNext ) {
                      System.out.println( "Done at " + i );
                      break;
                  }

                  int aStart = oNext ? oSeqX.getStartOffset() : -1;
                  int bStart = aNext ? aSeqX.getStartOffset() : -1;

                  int aEnd = oNext ? oSeqX.getEndOffset() : -1;
                  int bEnd = aNext ? aSeqX.getEndOffset() : -1;

                  System.out.println( i + ". " + aStart + ":" + aEnd + "\t" + bStart + ":" + bEnd );
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
              System.out.println( "sequences match" );
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
              System.out.println( "insert string now" );
              doc.insertString( text.length() - 1, "-generally-useful-hoober-goober", null );
              System.out.println( "new text " + doc.getText( 0, doc.getLength() ) );
              HighlightsChangeEvent evt = hcl.assertAtLeastOneEvent();
              System.out.println( "EVENT: " + evt.getStartOffset() + ":" + evt.getEndOffset() );

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

package org.nemesis.antlrformatting.api;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.misc.Interval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.nemesis.simple.SampleFiles;
import org.nemesis.simple.language.SimpleLanguageLexer;

/**
 *
 * @author Tim Boudreau
 */
public class FastStreamRewriterTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testModalTokenNewlinePositionsAreCorrect() throws Exception {
        SimpleLanguageLexer lex = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES.lexer();
        EverythingTokenStream str = new EverythingTokenStream(lex, SimpleLanguageLexer.modeNames);
        for (int i = 0; i < str.size() - 1; i++) {
            ModalToken tok = str.get(i);
            int[] nlp = tok.newlinePositions();
            char[] c = tok.getText().toCharArray();
            List<Integer> found = new ArrayList<>(nlp.length);
            for (int j = 0; j < c.length; j++) {
                if (c[j] == '\n') {
                    found.add(j);
                }
            }
            List<Integer> real = (List<Integer>) CollectionUtils.toList(nlp);
            assertEquals(found, real, "Newline positions wrong for '" + Strings.escape(tok.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE) + "' in " + tok);
        }
    }

    @Test
    public void testNewlinePositionsComputedCorrectly() throws IOException {
        SimpleLanguageLexer lex = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES.lexer();
        EverythingTokenStream str = new EverythingTokenStream(lex, SimpleLanguageLexer.modeNames);
        FastStreamRewriter rew = new FastStreamRewriter(str);
        int charIndex = 0;
        IntList foundNewlinePositions = IntList.create(str.size());
        IntList starts = IntList.create(str.size());
        for (int i = 0; i < str.size(); i++) {
            ModalToken tok = str.get(i);
            String txt = tok.getText();
            starts.add(charIndex);
            char[] c = txt.toCharArray();
            int currStart = charIndex;
            for (int j = 0; j < c.length; j++, charIndex++) {
                if (c[j] == '\n') {
//                    System.out.println("nl at " + (currStart + j) + " for token "
//                            + i + " character " + j + " in "
//                            + Strings.escape(txt,
//                                    Escaper.NEWLINES_AND_OTHER_WHITESPACE));
                    foundNewlinePositions.add(currStart + j);
                }
            }
        }

        assertEquals(starts, rew.startPositions);
        assertEquals(foundNewlinePositions, rew.newlinePositions);
    }

    @Test
    public void testPositionReportedCorrectlyWithNoRewrites() throws IOException {
        SimpleLanguageLexer lex = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES.lexer();
        EverythingTokenStream str = new EverythingTokenStream(lex, SimpleLanguageLexer.modeNames);
        FastStreamRewriter rew = new FastStreamRewriter(str);
        int charIndex = 0;
        int lastNewline = -1;
        for (int i = 0; i < str.size() - 1; i++) {
            ModalToken tok = str.get(i);
            String txt = tok.getText();
            if (lastNewline != -1) {
                int start = tok.getStartIndex();
                int expected = (start - lastNewline) - 1;
                int found = rew.lastNewlineDistance(i);
                assertEquals(expected, found, "With no rewrites, "
                        + "rewriter misreports newline position for " + i + " " + tok);
            }
            int currStart = charIndex;
            char[] c = txt.toCharArray();
            for (int j = 0; j < c.length; j++) {
                if (c[j] == '\n') {
                    lastNewline = currStart + j;
                }
                charIndex++;
            }
        }
    }

    @Test
    public void testPositionReportedCorrectlyWithReplacementsNotContainingNewlines() throws IOException {
        SimpleLanguageLexer lex = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES.lexer();
        EverythingTokenStream str = new EverythingTokenStream(lex, SimpleLanguageLexer.modeNames);
        FastStreamRewriter rew = new FastStreamRewriter(str);
        IntList[] items = startsAndNewlinePositions(str);
        IntList computedStarts = items[0];
        IntList computedNewlinePositions = items[1];
        int removedLength = str.get(3).getText().length();

        int start = computedStarts.getAsInt(3);
        System.out.println("Delete " + removedLength + " characters by removing token " + 3 + " starting at " + start);
        assertEquals(start, rew.startPositions.getAsInt(3));
        assertFalse(rew.newlinePositions.contains(start));
        System.out.println("BEFORE STARTS: " + rew.startPositions);

        String newText = "WookieWookieWookie";
        int lengthChange = newText.length() - removedLength;

        rew.replace(3, newText);;
        System.out.println(" AFTER STARTS: " + rew.startPositions);

        System.out.println("NEW TEXT: " + rew.getText(new Interval(3, 3)));
        assertEquals(newText, rew.getText(new Interval(3, 3)));

        assertNotEquals(computedNewlinePositions, rew.newlinePositions,
                "Newline positions should have changed on text insert via "
                + "replacement.");
        assertNotEquals(computedStarts, rew.startPositions,
                "Start positions should have changed on text insert via "
                + "replacement.");

        assertEquals(start + newText.length(), rew.startPositions.getAsInt(4),
                "start position of token 4 should have shifted upwards by "
                + lengthChange + " on replacing the preceding token "
                + "with that much more text");

        assertTrue(rew.newlinePositions.contains(start + 1 + lengthChange),
                "After deleting ; to shift a double newline backward, "
                + "position " + (start + 1)
                + "should be recorded as a newline "
                + (start + 1) + ". Contents: " + rew.newlinePositions);

        for (int i = 4; i < str.size() - 1; i++) {
            int expectedNewStart = computedStarts.getAsInt(i) + lengthChange;
            assertEquals(expectedNewStart, rew.startPositions.getAsInt(i),
                    "Start position of token " + i
                    + " should have been moved forward by " + removedLength
                    + " from " + computedStarts.getAsInt(i) + " to " + expectedNewStart
                    + " after replacement grew token 3 by " + lengthChange
                    + " characters.");
        }
        assertFalse(rew.newlinePositions.contains(start + 1), "Replacing '"
                + str.get(3).getText()
                + " - token 3  - at " + start + " with '" + newText + "'"
                + " should result the subsequent two newlines shifting fowards "
                + ". Contents: " + rew.newlinePositions);

    }

    @Test
    public void testPositionReportedCorrectlyWithDeletes() throws IOException {
        SimpleLanguageLexer lex = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES.lexer();
        EverythingTokenStream str = new EverythingTokenStream(lex, SimpleLanguageLexer.modeNames);
        FastStreamRewriter rew = new FastStreamRewriter(str);
        IntList[] items = startsAndNewlinePositions(str);
        IntList computedStarts = items[0];
        IntList computedNewlinePositions = items[1];
//        assertEquals(computedStarts, rew.startPositions);
//        assertEquals(computedNewlinePositions, rew.newlinePositions);
        int removedLength = str.get(3).getText().length();
        int start = computedStarts.getAsInt(3);
        System.out.println("Delete " + removedLength + " characters by removing token " + 3 + " starting at " + start);
        assertEquals(start, rew.startPositions.getAsInt(3));
        assertFalse(rew.newlinePositions.contains(start));
        System.out.println("BEFORE: " + rew.newlinePositions);

        rew.delete(3);
        System.out.println(" AFTER: " + rew.newlinePositions);
        System.out.println("BEFORE STARTS: " + computedStarts);
        System.out.println(" AFTER STARTS: " + rew.startPositions);

        assertEquals(start, rew.startPositions.getAsInt(4));

        assertTrue(rew.newlinePositions.contains(start + 1),
                "After deleting ; to shift a double newline backward, "
                + "position " + (start + 1)
                + "should be recorded as a newline "
                + (start + 1) + ". Contents: " + rew.newlinePositions);

        for (int i = 4; i < str.size() - 1; i++) {
            int expectedNewStart = computedStarts.getAsInt(i) - removedLength;
            assertEquals(expectedNewStart, rew.startPositions.getAsInt(i),
                    "Start position of token " + i
                    + " should have been moved bacward by " + removedLength
                    + " from " + computedStarts.get(i) + " to " + expectedNewStart);
        }
        assertTrue(rew.newlinePositions.contains(start), "Deleting '"
                + str.get(3).getText()
                + " - token 3  - at " + start
                + " should result the subsequent two newlines shifting backwards"
                + ". Contents: " + rew.newlinePositions);
    }

    @Test
    public void testPositionReportedCorrectlyWithInsertBefores() throws IOException {
        SimpleLanguageLexer lex = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES.lexer();
        EverythingTokenStream str = new EverythingTokenStream(lex, SimpleLanguageLexer.modeNames);
        FastStreamRewriter rew = new FastStreamRewriter(str);
        IntList[] items = startsAndNewlinePositions(str);
        IntList computedStarts = items[0];
        IntList computedNewlinePositions = items[1];
        System.out.println("INSERT TWO NEWLINES BEFORE " + str.get(3));
        int start = computedStarts.getAsInt(3);
        assertEquals(start, rew.startPositions.getAsInt(3));
        assertFalse(rew.newlinePositions.contains(start));
        System.out.println("BEFORE: " + rew.newlinePositions);
        rew.insertBefore(3, "\n\n");
        System.out.println(" AFTER: " + rew.newlinePositions);
        assertTrue(rew.newlinePositions.contains(start), "After inserting two "
                + "newlines before start of token 3 at " + start
                + ", the rewriter's newlinePositions should show a newline at "
                + start + ". Contents: " + rew.newlinePositions);
        assertTrue(rew.newlinePositions.contains(start + 1),
                "After inserting two "
                + "newlines before start of token 3 at " + start
                + ", the rewriter's newlinePositions should show a newline at "
                + (start + 1) + ". Contents: " + rew.newlinePositions);

        for (int i = 4; i < str.size() - 1; i++) {
            int expectedNewStart = computedStarts.getAsInt(i) + 2;
            assertEquals(expectedNewStart, rew.startPositions.getAsInt(i), "Start position of token " + i + " should have been changed");
        }
    }

    @Test
    public void testPositionReportedCorrectlyWithInsertAfters() throws IOException {
        SimpleLanguageLexer lex = SampleFiles.MUCH_NESTING_WITH_EXTRA_NEWLINES.lexer();
        EverythingTokenStream str = new EverythingTokenStream(lex, SimpleLanguageLexer.modeNames);
        FastStreamRewriter rew = new FastStreamRewriter(str);
        IntList[] items = startsAndNewlinePositions(str);
        IntList computedStarts = items[0];
        IntList computedNewlinePositions = items[1];
        System.out.println("INSERT TWO NEWLINES BEFORE " + str.get(3));
        int start = computedStarts.getAsInt(3);
        int start4 = computedStarts.getAsInt(4);
        System.out.println("OLD TOK 4 " + Strings.escape(str.get(4).getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE));
        assertEquals(start, rew.startPositions.getAsInt(3));
        assertFalse(rew.newlinePositions.contains(start));

        assertTrue(rew.newlinePositions.contains(start4));
        assertTrue(rew.newlinePositions.contains(start4 + 1));
        assertFalse(rew.newlinePositions.contains(start4 + 2));
        assertFalse(rew.newlinePositions.contains(start4 + 3));

        System.out.println("BEFORE: " + rew.newlinePositions);
        rew.insertAfter(3, "\n\n");
        System.out.println(" AFTER: " + rew.newlinePositions);
        assertTrue(rew.newlinePositions.contains(start4 + 2), "After inserting two "
                + "newlines before start of token 3 at " + (start4 + 2)
                + ", the rewriter's newlinePositions should show a newline at "
                + (start4 + 2) + ". Contents: " + rew.newlinePositions);
        assertTrue(rew.newlinePositions.contains(start4 + 3),
                "After inserting two "
                + "newlines before start of token 3 at " + (start4 + 3)
                + ", the rewriter's newlinePositions should show a newline at "
                + (start4 + 3) + ". Contents: " + rew.newlinePositions);

        for (int i = 5; i < str.size() - 1; i++) {
            int expectedNewStart = computedStarts.getAsInt(i) + 2;
            assertEquals(expectedNewStart, rew.startPositions.getAsInt(i), "Start position of token " + i + " should have been changed");
        }
    }

    private IntList[] startsAndNewlinePositions(EverythingTokenStream str) {
        int charIndex = 0;
        IntList computedStarts = IntList.create(str.size());
        IntList computedNewlinePositions = IntList.create(str.size());
        for (int i = 0; i < str.size() - 1; i++) {
            ModalToken tok = str.get(i);
            String txt = tok.getText();
            int currStart = charIndex;
            computedStarts.add(currStart);
            char[] c = txt.toCharArray();
            for (int j = 0; j < c.length; j++) {
                if (c[j] == '\n') {
                    computedNewlinePositions.add(currStart + j);
                }
                charIndex++;
            }
        }
        return new IntList[]{computedStarts, computedNewlinePositions};
    }
}

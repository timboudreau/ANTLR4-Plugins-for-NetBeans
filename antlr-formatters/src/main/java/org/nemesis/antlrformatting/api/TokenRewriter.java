package org.nemesis.antlrformatting.api;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Can rewrite the text of a token.
 *
 * @see org.nemesis.antlrformatting.api.FormattingAction
 * @see org.nemesis.antlrformatting.api.FormattingRule
 */
public interface TokenRewriter {

    /**
     * Rewrite the text of a token, possibly reflowing comments, etc.
     *
     * @param charsPerIndent The number of characters each indent takes up in
     * the current formatting context
     * @param text The token text
     * @param currLinePosition The current position in the current line,
     * possibly including whitespace prepended by a preceding rules append
     * whitespace call
     * @param state The lexing state
     * @return A rewritten string, or null to do nothing
     */
    public String rewrite(int charsPerIndent, String text, int currLinePosition, LexingState state);

    /**
     * Performs simple collation of text, using a the passed predicate to handle
     * whitespace.
     *
     * @param text Some text
     * @param wordBreakTest An IntPredicate which is passed each character and
     * should return true if it is a line break.
     *
     * @return A list of words, as determined by the predicate
     */
    public static List<String> collate(String text, IntPredicate wordBreakTest) {
        return new SimpleCollator(text, wordBreakTest).toList();
    }

    /**
     * Performs simple collation of text, splitting on whitespace.
     *
     * @param text Some text
     * @return A list of words, split on whitespace
     */
    static List<String> collate(String text) {
        return new SimpleCollator(text).toList();
    }

    /**
     * Create a very simple token text rewriter which collates on whitespace and
     * simply splits text onto a new line if it goes above a line length limit.
     *
     * @param lineLimit The maximum line length
     * @return A token rewriter
     */
    public static TokenRewriter simpleReflow(int lineLimit) {
        return simpleReflow(lineLimit, null);
    }

    /**
     * Create a very simple token text rewriter which collates on whitespace and
     * simply splits text onto a new line if it goes above a line length limit.
     *
     * @param lineLimit The maximum line length
     * @param stateKey If non-null, the line position to align to will be taken
     * from this state key's value instead of the current line position
     * @return A token rewriter
     */
    public static <T extends Enum<T>> TokenRewriter simpleReflow(int lineLimit, T stateKey) {
        assert lineLimit > 0 : "Absurd line limit " + lineLimit;
        return (int charsPerIndent, String text, int currLinePosition, LexingState state) -> {
            if (stateKey != null) {
                int val = state.get(stateKey);
                if (val > 0) {
                    currLinePosition = val;
                }
            }
            StringBuilder sb = new StringBuilder(text.length());
            int pos = currLinePosition;
            for (Iterator<String> it = TokenRewriter.collate(text).iterator(); it.hasNext();) {
                String word = it.next();
                if (word == null) {
                    continue; // XXX ???
                }
                int projectedPosition = pos == currLinePosition ? pos + word.length()
                        : (pos - 1) + word.length();
                if (projectedPosition > lineLimit) {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                        sb.setLength(sb.length() - 1);
                    }
                    sb.append('\n');
                    char[] indent = new char[currLinePosition];
                    Arrays.fill(indent, ' ');
                    sb.append(indent);
                    pos = currLinePosition;
                }
                sb.append(word);
                if (it.hasNext()) {
                    sb.append(' ');
                }
                pos += word.length() + 1;
            }
            return sb.toString();
        };
    }
}

package org.nemesis.antlr.completion;

import java.util.List;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Token;

/**
 * Miscellaneous utilities for working with lists of tokens.
 *
 * @author Tim Boudreau
 */
public final class TokenUtils {

    private TokenUtils() {
        throw new AssertionError();
    }

    public static boolean contains(Token tok, int pos) {
        boolean result = pos >= tok.getStartIndex() && pos <= tok.getStopIndex();
        return result;
    }

    public static int findCaretToken(int caret, List<Token> stream, int start, int stop) {
        // binary search
        Token first = stream.get(start);
        if (contains(first, caret)) {
            return first.getTokenIndex();
        }
        if (start >= stop) {
            return -1;
        }
        Token last = stream.get(stop);
        if (contains(last, caret)) {
            return last.getTokenIndex();
        }
        int middle = start + ((stop - start) / 2);
        Token mid = stream.get(middle);
        if (contains(mid, caret)) {
            return mid.getTokenIndex();
        }
        start++;
        stop--;
        if (stop < start || start < 0) {
            return -1;
        }
        if (caret < mid.getStartIndex()) {
            middle--;
            if (middle < 0) {
                return -1;
            }
            return findCaretToken(caret, stream, start, middle);
        } else {
            middle++;
            if (middle > stream.size()) {
                return -1;
            }
            return findCaretToken(caret, stream, middle, stop);
        }
    }

    private static boolean checkBefore(int[] before, List<Token> in, int tokenIndex, IntPredicate ignore) {
        for (int i = tokenIndex - 1, j = before.length - 1; i >= 0 && j >= 0; i--) {
            int type = in.get(i).getType();
            if (ignore.test(type)) {
                continue;
            }
            int expect = before[j];
            if (type != expect) {
                return false;
            }
            j--;
        }
        return true;
    }

    private static boolean checkAfter(int[] after, List<Token> in, int tokenIndex, IntPredicate ignore) {
        for (int i = tokenIndex + 1, j = 0; i < in.size() && j < after.length; i++) {
            int type = in.get(i).getType();
            if (ignore.test(type)) {
                continue;
            }
            int expect = after[j];
            if (type != expect) {
                return false;
            }
            j++;
        }
        return true;
    }

    /**
     * Match a collection of arrays of before and after tokens, a predicate for
     * tokens to skip, and predicates to restrict matching to when the target
     * token is of a certain type, and a list of names for the matches, the one
     * of which will be returned that is of the index of the first matching
     * pattern. All arrays must have the exact same size.
     *
     * @param names An array of names, one of which may be returned
     * @param befores An array of token types which must precede the target
     * token (any tokens that match the before tokens will not be counted).
     * @param afters An array of token types which must follow the target token
     * @param ignore A predicate which, if it matches a token, that token will
     * not count towards previous or subsequent tokens and will be skipped when
     * computing matches.
     * @param tokenMatches An array of predicates which, if non-null, will need
     * to match the target token for the rest of the pattern to be considered
     * @param in The list of tokens the target token occurs in
     * @param target The target token within the list
     * @return A string from the names array if a pattern is matched, otherwise
     * null
     */
    public static String isTokenPatternMatched(String[] names, int[][] befores, int[][] afters, IntPredicate ignore,
            IntPredicate[] tokenMatches,
            List<Token> in,
            Token target) {
        assert befores.length == afters.length;
        int ix = target.getTokenIndex();
        int type = target.getType();
        for (int i = 0; i < befores.length; i++) {
            if (tokenMatches[i] == null || tokenMatches[i].test(type)) {
                if (checkBefore(befores[i], in, ix, ignore) && checkAfter(afters[i], in, ix, ignore)) {
                    return names[i];
                }
            }
        }
        return null;
    }
}

/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.completion;

import com.mastfrog.antlr.code.completion.spi.CaretToken;
import com.mastfrog.util.search.Bias;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;
import javax.swing.text.StyledDocument;
import org.antlr.v4.runtime.Token;
import org.nemesis.editor.ops.DocumentOperator;

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

    public static List<? extends Token> tokensOf(CaretToken tk) {
        if (tk instanceof CaretTokenInfo) {
            return ((CaretTokenInfo) tk).tokens();
        }
        throw new IllegalArgumentException("Wrong type: " + tk.getClass().getName());
    }

    public static CaretToken strippingLeadingText(CaretToken orig) {
        CaretTokenInfo cti = (CaretTokenInfo) orig;
        return cti.strippingLeadingText();
    }

    public static CaretToken withInsertedCharacters(boolean newToken, int origTokenPosition, CaretToken orig, JTextComponent withUpdatedCursor, BiConsumer<CaretToken, CharSequence> c) throws BadLocationException {
        CaretTokenInfo cti = (CaretTokenInfo) orig;
        int oldPos = origTokenPosition;
        int newPos = withUpdatedCursor.getCaret().getDot();
        if (oldPos < newPos) {
            StyledDocument doc = (StyledDocument) withUpdatedCursor.getDocument();
            Segment addedChars = DocumentOperator.render(doc, () -> {
                int start = Math.min(oldPos, newPos);
                int end = Math.max(oldPos, newPos);
                Segment seg = new Segment();
                doc.getText(start, end - start, seg);
                return seg;
            });
            CaretTokenInfo result = cti.withAppendedText(newPos + addedChars.length(), addedChars.toString(), newToken);
            c.accept(result, addedChars);
            return result;
        } else {
            return cti.forCaretPosition(Math.max(0, newPos), Bias.NONE);
        }
    }

    /**
     * Binary search over the token stream for the token containing or abutting
     * the caret, and return a CaretTokenInfo for it.
     *
     * @param caret The caret position
     * @param stream The tokens
     * @return A CaretTokenInfo
     */
    public static CaretToken caretTokenInfo(int caret, List<? extends Token> stream) {
        return caretTokenInfo(caret, stream, Bias.NONE);
    }

    /**
     * Binary search over the token stream for the token containing or abutting
     * the caret, and return a CaretTokenInfo for it.
     *
     * @param caret The caret position
     * @param stream The tokens
     * @param bias If FORWARD and the caret position is the end of the token
     * @return A CaretTokenInfo
     */
    public static CaretToken caretTokenInfo(int caret, List<? extends Token> stream, Bias bias) {
        if (stream.isEmpty()) {
            return CaretTokenInfo.EMPTY;
        }
        if (stream.size() == 1) {
            Token t = stream.get(0);
            int tokenIndex = caret >= t.getStartIndex() && caret <= t.getStopIndex()
                    ? t.getTokenIndex() : -1;
            if (tokenIndex >= 0) {
                CaretTokenInfo result = new CaretTokenInfo(t, caret, stream);
                return result.biasedBy(bias);
            }
            return CaretTokenInfo.EMPTY;
        }
        Token first = stream.get(0);
        Token last = stream.get(stream.size() - 1);
        if (caret >= first.getStartIndex() && caret <= last.getStopIndex()) {
            return caretTokenInfo(caret, stream, first.getTokenIndex(), last.getTokenIndex())
                    .biasedBy(bias);
        }
        return CaretTokenInfo.EMPTY;
    }

    /**
     * Binary search over the token stream for the token containing or abutting
     * the caret; note that this method assumes a 1:1 correspondence between
     * stream.indexOf(someToken) and someToken.getTokenIndex(), and may give
     * unpredictable results if the list was constructed from a
     * CommonTokenStream which hides tokens on some channels. Get your tokens
     * directly from a lexer, not from a TokenStream to be sure that is not
     * happening.
     *
     * @param caret The caret position
     * @param stream The tokens
     * @return The token id
     */
    public static int findCaretToken(int caret, List<? extends Token> stream) {
        if (stream.isEmpty()) {
            return -1;
        }
        if (stream.size() == 1) {
            Token t = stream.get(0);
            return caret >= t.getStartIndex() && caret <= t.getStopIndex()
                    ? t.getTokenIndex() : -1;
        }
        Token first = stream.get(0);
        Token last = stream.get(stream.size() - 1);
        if (caret >= first.getStartIndex() && caret <= last.getStopIndex()) {
            return findCaretToken(caret, stream, first.getTokenIndex(), last.getTokenIndex());
        }
        return -1;
    }

    /**
     * Binary search over the token stream for the token containing or abutting
     * the caret.
     *
     * @param caret The caret position
     * @param stream The tokens
     * @param start Search start
     * @param stop Search end
     * @return The token id
     */
    public static int findCaretToken(int caret, List<? extends Token> stream, int start, int stop) {
        // We must add 1, or for single char tokens, we will offer completions that
        // could come *after* the token the caret is before
        return _findCaretToken(caret, stream, Math.max(0, start), Math.min(stream.size() - 1, stop));
    }

    private static CaretTokenInfo caretTokenInfo(int caret, List<? extends Token> stream, int start, int stop) {
        if (stream.isEmpty()) {
            return CaretTokenInfo.EMPTY;
        }
        int tok = findCaretToken(caret, stream, start, stop);
        if (tok < 0) {
            return CaretTokenInfo.EMPTY;
        }
        if (tok == stream.size() - 1 && stream.size() > 1) {
            Token t = stream.get(tok);
            if (Token.EOF == t.getType()) {
                t = stream.get(tok - 1);
                return new CaretTokenInfo(t, caret, stream).after();
            }
        }
        return new CaretTokenInfo(stream.get(tok), caret, stream);
    }

    private static int _findCaretToken(int caret, List<? extends Token> stream, int start, int stop) {
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
            return _findCaretToken(caret, stream, start, middle);
        } else {
            middle++;
            if (middle > stream.size()) {
                return -1;
            }
            return _findCaretToken(caret, stream, middle, stop);
        }
    }

    private static boolean checkBefore(int[] before, List<Token> in, int tokenIndex, IntPredicate ignore) {
        for (int i = tokenIndex - 1, j = before.length - 1; i >= 0 && j >= 0; i--) {
            int type = in.get(i).getType();
            if (ignore.test(type)) {
                continue;
            }
            int expect = before[j];
            if (type != expect && expect != 0) {
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
            if (type != expect && expect != 0) {
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

        int result = findTokenPatternMatch(befores, afters, ignore, tokenMatches, in, target);
        return result < 0 ? null : names[result];
    }

    public static int findTokenPatternMatch(int[][] befores, int[][] afters, IntPredicate ignore,
            IntPredicate[] tokenMatches,
            List<Token> in,
            Token target) {
        assert befores.length == afters.length;
        int ix = target.getTokenIndex();
        int type = target.getType();
        for (int i = 0; i < befores.length; i++) {
            if (tokenMatches[i] == null || tokenMatches[i].test(type)) {
                if (checkBefore(befores[i], in, ix, ignore) && checkAfter(afters[i], in, ix, ignore)) {
                    return i;
                }
            }
        }
        return -1;
    }
}

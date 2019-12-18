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

import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.search.Bias;
import com.mastfrog.util.strings.Strings;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/**
 * Encapsulates information about the relationship between a position in a
 * document and a token.
 *
 * @author Tim Boudreau
 */
public class CaretTokenInfo {

    public static final CaretTokenInfo EMPTY;

    static {
        CommonToken tok = new CommonToken(Token.EOF);
        tok.setText("");
        EMPTY = new CaretTokenInfo(tok, 0, Collections.singletonList(tok));
    }
    private final Token token;
    private final int caretPositionInDocument;
    private final List<? extends Token> toks;

    CaretTokenInfo(Token token, int caretPositionInDocument, List<? extends Token> toks) {
        this.token = token;
        this.caretPositionInDocument = caretPositionInDocument;
        this.toks = toks;
    }

    public boolean isWhitespace() {
        String txt = tokenText();
        for (int i = 0; i < txt.length(); i++) {
            if (!Character.isWhitespace(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "<empty>";
        }
        return "{" + caretPositionInDocument + " ("
                + token.getTokenIndex() + "/" + toks.size() + ") '"
                + Strings.escapeControlCharactersAndQuotes(tokenText())
                + "' type " + token.getType() + " rel "
                + caretRelation() + " pfx '"
                + Strings.escapeControlCharactersAndQuotes(leadingTokenText())
                + "' sfx '"
                + Strings.escapeControlCharactersAndQuotes(trailingTokenText())
                + "' }";
    }

    public int tokenType() {
        return token.getType();
    }

    public int tokenStart() {
        if (!isUserToken()) {
            return -1;
        }
        return token.getStartIndex();
    }

    public int tokenStop() {
        if (!isUserToken()) {
            return -1;
        }
        return token.getStopIndex();
    }

    public int tokenEnd() {
        if (!isUserToken()) {
            return -1;
        }
        return token.getStopIndex() + 1;
    }

    public int tokenLength() {
        return Math.max(0, tokenEnd() - tokenStart());
    }

    /**
     * Get a caret token info affected by the passed Bias - if the caret is at
     * the first character of the token and the bias is BACKWARD, return a
     * CaretTokenInfo for the preceding token; if the caret is at the last
     * character of the token, return a CaretTokenInfo for the next token; in
     * the case that this object represents the first or last token and the
     * query would result in, respectively, returning a non-existent preceding
     * or next token, the empty instance is returned.
     * <p>
     * The empty instance will always return itself, as will instances
     * representing an EOF or other non-user token.
     * <p>
     * Note that CaretTokenInfo instances returned for FORWARD or BACKWARD
     * biases will have a <code>caretTokenPosition()</code> matching the start
     * index or stop index+1 of their token, which may not be the same value as
     * returned by the original instance.
     *
     * @param bias The bias - FORWARD and BACKWARD are relevant
     * @return A caret token info, never null
     */
    public CaretTokenInfo biasedBy(Bias bias) {
        if (!isUserToken()) {
            return this;
        }
        switch (notNull("bias", bias)) {
            case FORWARD:
                if (caretPositionInDocument >= token.getStopIndex()) {
                    if (token.getTokenIndex() < toks.size() - 1) {
                        Token next = toks.get(token.getTokenIndex() + 1);
                        return new CaretTokenInfo(next, next.getStartIndex(), toks);
                    }
                    return EMPTY;
                }
                return this;
            case BACKWARD:
                switch (caretRelation()) {
                    case AT_TOKEN_START:
                        if (token.getTokenIndex() > 0) {
                            Token prev = toks.get(token.getTokenIndex() - 1);
                            return new CaretTokenInfo(prev, prev.getStopIndex() + 1, toks);
                        }
                        return EMPTY;
                    case AT_TOKEN_END:
                    case UNRELATED:
                    case WITHIN_TOKEN:
                        break; // and fallthrough
                    default:
                        throw new AssertionError(caretRelation());
                }
            case NEAREST:
            case NONE:
                return this;
            default:
                throw new AssertionError(bias);
        }
    }

    /**
     * If the token ends with whitespace,
     *
     * @return
     */
    public String trailingNewlinesAndWhitespace() {
        String body = token.getText();
        int newlinePos = -1;
        for (int pos = body.length() - 1; pos >= 0; pos--) {
            char c = body.charAt(pos);
            if (c == '\n') {
                newlinePos = pos;
            } else if (!Character.isWhitespace(c)) {
                break;
            }
        }
        if (newlinePos == 0) {
            return body;
        } else if (newlinePos > 0) {
            return body.substring(newlinePos);
        } else {
            return "";
        }
    }

    public enum CaretTokenRelation {
        AT_TOKEN_START,
        WITHIN_TOKEN,
        AT_TOKEN_END,
        UNRELATED;
    }

    /**
     * Return a CaretTokenInfo for this instance's token which reports the caret
     * position as the first position <i>after</i>
     * the stop position of the token.
     *
     * @return A CaretTokenInfo
     */
    public CaretTokenInfo after() {
        return new CaretTokenInfo(token, token.getStopIndex() + 1, toks);
    }

    /**
     * Returns a CaretTokenInfo for the token <i>preceding</i> this one, with
     * the caret position set to the stop index of that token + 1. Returns the
     * empty instance if there is no such token.
     *
     * @return A CaretTokenInfo
     */
    public CaretTokenInfo before() {
        if (token.getTokenIndex() > 0) {
            Token prev = toks.get(token.getTokenIndex() - 1);
            return new CaretTokenInfo(prev, prev.getStopIndex() + 1, toks);
        }
        return EMPTY;
    }

    /**
     * Determine if the token consists only of characters which are neither
     * letters, nor digits, nor whitespace, nor ISO control characters.
     *
     * @return
     */
    public boolean isPunctuation() {
        boolean result = isUserToken();
        if (result) {
            String name = token.getText();
            result = name.length() > 0;
            if (result) {
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (Character.isLetter(c) || Character.isDigit(c) || Character.isWhitespace(c) || Character.isISOControl(c)) {
                        result = false;
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Get the relationship of the caret position to the token. Note that
     * instances returned by TokenUtils directly will <i>never</i>
     * return CaretTokenRelation.AT_TOKEN_END from this method, because that
     * indicates the caret is positioned <i>after</i> the last token, in which
     * case TokenUtils will have returned the <i>next</i> token. Use the methods
     * on this class or TokenUtils that take a Bias to get one such.
     *
     * @return The caret token relation
     */
    public CaretTokenRelation caretRelation() {
        int start = token.getStartIndex();
        int stop = token.getStopIndex();
        if (caretPositionInDocument < start || caretPositionInDocument > stop + 1) {
            return CaretTokenRelation.UNRELATED;
        } else if (caretPositionInDocument == start) {
            return CaretTokenRelation.AT_TOKEN_START;
        } else if (caretPositionInDocument == stop + 1) {
            return CaretTokenRelation.AT_TOKEN_END;
        } else {
            return CaretTokenRelation.WITHIN_TOKEN;
        }
    }

    /**
     * Get the caret position within the document this instance was created
     * with.
     *
     * @return The caret position
     */
    public int caretPositionInDocument() {
        return caretPositionInDocument;
    }

    /**
     * Get the token matched.
     *
     * @return A token
     */
    public Token token() {
        return token;
    }

    public int tokenIndex() {
        return token.getTokenIndex();
    }

    /**
     * Get the matched token's text
     *
     * @return The token text
     */
    public String tokenText() {
        if (!isUserToken()) {
            return "";
        }
        return token.getText();
    }

    /**
     * Determine if this is a document token or one of those used internally by
     * Antlr, such as EOF.
     *
     * @return Whether or not this is a user token
     */
    public boolean isUserToken() {
        return token.getType() >= Token.MIN_USER_TOKEN_TYPE && token.getStartIndex() <= token.getStopIndex();
    }

    /**
     * Get the text occurring at or after the caret in this token.
     *
     * @return The trailing text
     */
    public String trailingTokenText() {
        if (!isUserToken()) {
            return "";
        }
        int relPos = Math.max(0, caretPositionInDocument - token.getStartIndex());
        return token.getText().substring(relPos);
    }

    public boolean containsNewline() {
        return !isUserToken() ? false : token.getText().contains("\n");
    }

    /**
     * Get the text occurring up to, but not including, the caret position.
     *
     * @return
     */
    public String leadingTokenText() {
        if (!isUserToken()) {
            return "";
        }
        String txt = token.getText();
        int relPos = Math.min(txt.length(), Math.max(0, caretPositionInDocument - token.getStartIndex()));
        return txt.substring(0, relPos);
    }

    /**
     * Fetch the matched token, incorporating the passed bias such that
     * <ul>
     * <li>If the caret position is before or at the first character of the
     * token and BACKWARD is passed, the preceding token (if any) is
     * returned</li>
     * <li>If the caret position is after the last character of the token and
     * FORWARD is passed, the next token is returned</li>
     * <li>NEAREST and NONE biases return the same result as calling
     * <code>token()</code></li>
     * </ul>
     * <b>Boundary conditions:</b> If the next or previous token would be
     * returned and there is no such token, the current token is used.
     *
     * @param bias The bias
     * @return
     */
    public Token token(Bias bias) {
        if (!isUserToken()) {
            return token;
        }
        switch (bias) {
            case BACKWARD:
                if (caretPositionInDocument <= token.getStartIndex()) {
                    int ix = token.getTokenIndex();
                    if (ix > 0) {
                        return toks.get(ix - 1);
                    }
                }
                return token;
            case FORWARD:
                if (caretPositionInDocument >= token.getStopIndex()) {
                    int ix = token.getTokenIndex();
                    if (ix < toks.size() - 1) {
                        return toks.get(ix + 1);
                    }
                }
            // fallthrough
            case NONE:
            case NEAREST:
                return token;
            default:
                throw new AssertionError(bias);
        }
    }

    /**
     * Get the number of characters preceding the caret position to the token
     * start position. Note that for instances created by biasedBy() the result
     * may be greater than the length of the token.
     *
     * @return The relative position
     */
    public int caretDistanceBackwardsToTokenStart() {
        return caretPositionInDocument - token.getStartIndex();
    }

    public int caretDistanceForwardsToTokenEnd() {
        return Math.max(0, token.getStopIndex() - caretPositionInDocument);
    }

}

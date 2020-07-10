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
package com.mastfrog.antlr.code.completion.spi;

import com.mastfrog.util.search.Bias;
import com.mastfrog.util.strings.Strings;

/**
 *
 * @author Tim Boudreau
 */
public interface CaretToken {

    /**
     * Return a CaretTokenInfo for this instance's token which reports the caret
     * position as the first position <i>after</i>
     * the stop position of the token.
     *
     * @return A CaretTokenInfo
     */
    CaretToken after();

    /**
     * Returns a CaretTokenInfo for the token <i>preceding</i> this one, with
     * the caret position set to the stop index of that token + 1. Returns the
     * empty instance if there is no such token.
     *
     * @return A CaretTokenInfo
     */
    CaretToken before();

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
    CaretToken biasedBy(Bias bias);

    /**
     * Get the number of characters preceding the caret position to the token
     * start position. Note that for instances created by biasedBy() the result
     * may be greater than the length of the token.
     *
     * @return The relative position
     */
    default int caretDistanceBackwardsToTokenStart() {
        return caretPositionInDocument() - tokenStart();
    }

    /**
     * Get the number of characters following the caret position to the token
     * end position. Note that for instances created by biasedBy() the result
     * may be greater than the length of the token.
     *
     * @return The relative position
     */
    default int caretDistanceForwardsToTokenEnd() {
        return Math.max(0, tokenStop() - caretPositionInDocument());
    }

    /**
     * Get the start offset of this token in the document.
     *
     * @return The start offset
     */
    int tokenStart();

    /**
     * Get the offset of the final character in this token in the document.
     *
     * @return The stop position (not to be confused with the end which is this
     * value + 1)
     */
    int tokenStop();

    /**
     * Get the matched token's text
     *
     * @return The token text
     */
    String tokenText();

    /**
     * Get the Antlr lexer token type of this token.
     *
     * @return The token type
     */
    int tokenType();

    /**
     * Determine if this is a document token or one of those used internally by
     * Antlr, such as EOF.
     *
     * @return Whether or not this is a user token
     */
    boolean isUserToken();

    /**
     * Get the caret position within the document this instance was created
     * with.
     *
     * @return The caret position
     */
    int caretPositionInDocument();

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
    default CaretTokenRelation caretRelation() {
        int start = tokenStart();
        int stop = tokenStop();
        int caretPositionInDocument = caretPositionInDocument();
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

    default boolean containsNewline() {
        return !isUserToken() ? false : tokenText().indexOf('\n') >= 0;
    }

    /**
     * Determine if the token consists only of characters which are neither
     * letters, nor digits, nor whitespace, nor ISO control characters.
     *
     * @return
     */
    default boolean isPunctuation() {
        boolean result = isUserToken();
        if (result) {
            String name = tokenText();
            result = name.length() > 0;
            if (result) {
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (Character.isLetter(c) || Character.isDigit(c)
                            || Character.isWhitespace(c) || Character.isISOControl(c)) {
                        result = false;
                        break;
                    }
                }
            }
        }
        return result;
    }

    default boolean isWhitespace() {
        return isUserToken() && Strings.isBlank(tokenText());
    }

    /**
     * Get the text occurring up to, but not including, the caret position.
     *
     * @return
     */
    default String leadingTokenText() {
        if (!isUserToken()) {
            return "";
        }
        String txt = tokenText();
        int relPos = Math.min(txt.length(), Math.max(0, caretPositionInDocument() - tokenStart()));
        return txt.substring(0, relPos);
    }

    default int tokenEnd() {
        return isUserToken() ? tokenStop() + 1 : -1;
    }

    int tokenIndex();

    default int tokenLength() {
        return Math.max(0, tokenEnd() - tokenStart());
    }

    /**
     * If the token ends with whitespace,
     *
     * @return
     */
    default String trailingNewlinesAndWhitespace() {
        if (!isUserToken()) {
            return "";
        }
        String body = tokenText();
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

    /**
     * Get the text occurring at or after the caret in this token.
     *
     * @return The trailing text
     */
    default String trailingTokenText() {
        if (!isUserToken()) {
            return "";
        }
        String txt = tokenText();
        int relPos = Math.max(0, caretPositionInDocument() - tokenStart());
        if (relPos >= txt.length()) {
            return "";
        }
        return txt.substring(relPos);
    }

}

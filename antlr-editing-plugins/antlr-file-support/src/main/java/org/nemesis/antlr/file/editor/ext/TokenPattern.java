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
package org.nemesis.antlr.file.editor.ext;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Matches a pattern of tokens if a stop token is not encountered before or
 * while processing it, iterating forward or backward from the passed
 * caret position.
 *
 * @author Tim Boudreau
 */
final class TokenPattern {

    private final boolean forward;
    private final IntPredicate[] pattern;
    private final IntPredicate stop;
    private final boolean startOrEndOk;
    private final IntPredicate ignore;

    TokenPattern(boolean forward, IntPredicate[] pattern, IntPredicate stop, boolean startOrEndOk, IntPredicate ignore) {
        this.forward = forward;
        this.pattern = pattern;
        this.stop = stop;
        this.startOrEndOk = startOrEndOk;
        this.ignore = ignore;
        if (pattern.length == 0) {
            throw new IllegalArgumentException("0-length token pattern");
        }
    }

    /*

    Use a microformat something like this:
     > or < forward or backwards
    | allow end of doc
    LIST OF TOKENS IN PATTERN
    ! LIST OF STOP TOKENS
    ? LIST OF IGNORE TOKENS

    >| RPAREN {TOK_A, TOK_B} * LPAREN SEMI ! STOP_TOK_1 STOP_TOK_2 ? WHITESPACE BLOCK_COMMENT

    public static final TokenPattern parse(String val, ToIntFunction<String> converter) {
        val = val.trim();
        if (val.length() < 4) {
            throw new IllegalArgumentException("Pattern string too short");
        }
        if (val.charAt(0) == '>' || val.charAt(0) == '<') {
            boolean forward = val.charAt(0) == '>';
            boolean endOk = val.charAt(1) == '|';
            val = val.substring(endOk ? 2 : 1).trim();
            int bangIx = val.indexOf('!');
            if (bangIx < 0) {
                throw new IllegalArgumentException("! not found - stop tokens not specified");
            }
            String patternTokenList = val.substring(0, bangIx).trim();
            Set<String> patternTokens = new HashSet<>(5);
            for (String s : patternTokenList.split("\\s+")) {
                patternTokens.add(s);
            }
            if (patternTokens.isEmpty()) {
                throw new IllegalArgumentException("Pattern token sequence '"
                        + patternTokenList + " is impty in '" + val + "'");
            }
            String remainder = val.substring(bangIx+1, val.length());
            String stopTokenList;

            int qix = remainder.indexOf('?');
            if (qix < 0) {
                stopTokenList = remainder;
                remainder = "";
            } else {
                stopTokenList = remainder.substring(0, qix);
                remainder = remainder.substring(qix+1, remainder.length());
            }
            Set<String> stopTokens = new HashSet<>(5);
            for (String stop : stopTokenList.split("\\s+")) {
                stopTokens.add(stop);
            }
            Set<String> ignoreTokens = new HashSet<>(5);
            if (!remainder.isEmpty()) {
                for (String ign : remainder.split("\\s+")) {
                    ignoreTokens.add(ign);
                }
            }

            Function<Set<String>, int[]> intsForString = set -> {
                int[] result = new int[set.size()];
                int ix = 0;
                for (String s : set) {
                    int tokenId = converter.applyAsInt(s);
                    if (tokenId < -1) {
                        throw new IllegalArgumentException("Could not convert '"
                                + s + "' to a token id - got " + tokenId);
                    }
                }
                return result;
            };


        } else {
            throw new IllegalArgumentException("Pattern must start with > or "
                    + "< to indicate forward or backward search");
        }
        return null;
    }
    */

    @Override
    public String toString() {
        return Arrays.toString(pattern) + (forward ? " after " : " before ")
                + (startOrEndOk ? "or line start/end " : "but not line start/end ")
                + " prior to encountering " + stop + " ignoring " + ignore;
    }

    <T> TokenPatternBuilder<T> builder(Function<TokenPatternBuilder<T>, T> convert, boolean fwd, int[] pattern) {
        return new TokenPatternBuilder<>(convert, fwd, pattern);
    }

    <T> TokenPatternBuilder<T> builder(Function<TokenPatternBuilder<T>, T> convert, boolean fwd, IntPredicate[] pattern) {
        return new TokenPatternBuilder<>(convert, fwd, pattern);
    }

    public boolean test(EditorFeatureUtils utils, ContextWrapper wrapper, int position) {
        return utils.withTokenSequence(wrapper.document(), position, !forward, (ts) -> {
            if (forward) {
                boolean moveSuccess = true;
                for (int type = ts.token().id().ordinal(), patternPos = 0;
                        !stop.test(type);
                        moveSuccess = ts.moveNext(), type = moveSuccess
                        ? ts.token().id().ordinal()
                        : -1) {
                    if (!ignore.test(type)) {
                        if (pattern[patternPos].test(type)) {
                            patternPos++;
                            if (patternPos == pattern.length) {
                                return true;
                            }
                        } else if (stop.test(type)) {
                            return false;
                        }
                    }
                }
                return !moveSuccess ? startOrEndOk : false;
            } else {
                boolean moveSuccess = true;
                for (int type = ts.token().id().ordinal(), patternPos = pattern.length - 1;
                        !stop.test(type);
                        moveSuccess = ts.movePrevious(), type = moveSuccess
                        ? ts.token().id().ordinal()
                        : -1) {
                    if (!ignore.test(type)) {
                        if (pattern[patternPos].test(type)) {
                            patternPos--;
                            if (patternPos == -1) {
                                return true;
                            }
                        } else if (stop.test(type)) {
                            return false;
                        }
                    }
                }
                return !moveSuccess ? startOrEndOk : false;
            }
        });
    }
}

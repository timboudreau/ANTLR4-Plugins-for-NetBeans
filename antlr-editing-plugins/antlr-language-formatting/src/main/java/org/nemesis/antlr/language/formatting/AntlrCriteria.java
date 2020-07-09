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
package org.nemesis.antlr.language.formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import com.mastfrog.antlr.utils.Criterion;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrCriteria {

    public static final int[] ALL_WHITESPACE = new int[]{
        WS,
        CHN_WS,
        FRAGDEC_WS,
        HDR_IMPRT_WS,
        TYPE_WS,
        HEADER_P_WS,
        HEADER_WS,
        ID_WS,
        IMPORT_WS,
        LEXCOM_WS,
        OPT_WS,
        PARDEC_OPT_WS,
        PARDEC_WS,
        TOKDEC_WS,
        TOK_WS,};
    public static final int[] ALL_BLOCK_COMMENTS = new int[]{
        BLOCK_COMMENT, CHN_BLOCK_COMMENT, DOC_COMMENT,
        HEADER_BLOCK_COMMENT, ID_BLOCK_COMMENT,
        HEADER_P_BLOCK_COMMENT,
        LEXCOM_BLOCK_COMMENT, IMPORT_BLOCK_COMMENT,
        OPT_BLOCK_COMMENT, PARDEC_BLOCK_COMMENT,
        PARDEC_OPT_BLOCK_COMMENT, TOK_BLOCK_COMMENT
    };

    static Criterion lineComments() {
        return LINE_COMMENTS;
    }

    private static final Criterion LINE_COMMENTS = new LineCommentCriterion();

    static final class LineCommentCriterion implements Criterion {

        @Override
        public boolean test(int value) {
            return isLineComment(value);
        }

        public String toString() {
            return "<all-line-comments>";
        }
    }

    public static IntPredicate mode(String... names) {
        return modeNames(false, names);
    }

    public static IntPredicate notMode(String... names) {
        return modeNames(true, names);
    }

    private static IntPredicate modeNames(boolean not, String... names) {
        Set<String> all = new TreeSet<>(Arrays.asList(names));
        List<Integer> ints = new ArrayList<>(all.size());
        List<String> allModeNames = ANTLRv4Lexer.modeNames == null
                ? Collections.emptyList() : Arrays.asList(ANTLRv4Lexer.modeNames);

        for (String s : all) {
            int ix = allModeNames.indexOf(s);
            if (ix >= 0) {
                ints.add(ix);
            } else if ("DEFAULT_MODE".equals(s) || "default".equals(s)) {
                ints.add(0);
            }
        }
        final int[] vals = new int[ints.size()];
        for (int i = 0; i < ints.size(); i++) {
            vals[i] = ints.get(i);
        }
        Arrays.sort(vals);
        return new IntPredicate() {
            @Override
            public boolean test(int value) {
                int ix = Arrays.binarySearch(vals, value);
                if (not) {
                    return ix < 0;
                } else {
                    return ix >= 0;
                }
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder(not ? "notMode(" : "mode(");
                int initialLength = sb.length();
                for (String s : all) {
                    if (sb.length() != initialLength) {
                        sb.append(", ");
                    }
                    sb.append(s);
                }
                sb.append(" = [");
                for (int i = 0; i < vals.length; i++) {
                    sb.append(vals[i]);
                    if (i != vals.length - 1) {
                        sb.append(",");
                    }
                }
                sb.append("]");
                return sb.append(")").toString();
            }
        };
    }

    static boolean isLineComment(int type) {
        switch (type) {
            case LINE_COMMENT:
            case CHN_LINE_COMMENT:
            case FRAGDEC_LINE_COMMENT:
            case HDR_IMPRT_LINE_COMMENT:
            case HEADER_P_LINE_COMMENT:
            case ID_LINE_COMMENT:
            case HEADER_LINE_COMMENT:
            case HDR_PCKG_LINE_COMMENT:
            case IMPORT_LINE_COMMENT:
            case LEXCOM_LINE_COMMENT:
            case OPT_LINE_COMMENT:
            case PARDEC_LINE_COMMENT:
            case PARDEC_OPT_LINE_COMMENT:
            case TOK_LINE_COMMENT:
            case TYPE_LINE_COMMENT:
                return true;
            default:
                return false;
        }
    }

    static boolean isBlockComment(int type) {
        switch (type) {
            case CHN_BLOCK_COMMENT:
            case HEADER_BLOCK_COMMENT:
            case HEADER_P_BLOCK_COMMENT:
            case ID_BLOCK_COMMENT:
            case IMPORT_BLOCK_COMMENT:
            case OPT_BLOCK_COMMENT:
            case LEXCOM_BLOCK_COMMENT:
            case PARDEC_BLOCK_COMMENT:
            case PARDEC_OPT_BLOCK_COMMENT:
            case TOK_BLOCK_COMMENT:
            case BLOCK_COMMENT:
                return true;
            default:
                return false;
        }
    }

    static boolean notWhitespace(int tokenType) {
        return !isWhitespace(tokenType);
    }

    static Criterion whitespace() {
        return WHITESPACE;
    }

    private static final Criterion WHITESPACE = new WhitespaceCriterion();
    private static final Criterion NOT_WHITESPACE = new NotWhitespaceCriterion();

    private static final class WhitespaceCriterion implements Criterion {

        @Override
        public boolean test(int value) {
            return isWhitespace(value);
        }

        public String toString() {
            return "<whitespace>";
        }

        @Override
        public Criterion negate() {
            return NOT_WHITESPACE;
        }
    }

    private static final class NotWhitespaceCriterion implements Criterion {

        @Override
        public boolean test(int value) {
            return !isWhitespace(value);
        }

        public String toString() {
            return "<not-whitespace>";
        }

        public Criterion negate() {
            return WHITESPACE;
        }
    }

    static boolean isWhitespace(int tokenType) {
        switch (tokenType) {
            case WS:
            case CHN_WS:
            case FRAGDEC_WS:
            case HDR_IMPRT_WS:
            case TYPE_WS:
            case HEADER_P_WS:
            case HEADER_WS:
            case ID_WS:
            case IMPORT_WS:
            case LEXCOM_WS:
            case OPT_WS:
            case PARDEC_OPT_WS:
            case PARDEC_WS:
            case TOKDEC_WS:
            case TOK_WS:
                return true;
            default:
                return false;
        }
    }

    private AntlrCriteria() {
        throw new AssertionError();
    }
}

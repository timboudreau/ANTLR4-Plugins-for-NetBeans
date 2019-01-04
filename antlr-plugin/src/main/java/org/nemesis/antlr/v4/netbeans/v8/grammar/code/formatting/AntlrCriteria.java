package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrCriteria {

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
            case ANTLRv4Lexer.LINE_COMMENT:
            case ANTLRv4Lexer.CHN_LINE_COMMENT:
            case ANTLRv4Lexer.FRAGDEC_LINE_COMMENT:
            case ANTLRv4Lexer.HDR_IMPRT_LINE_COMMENT:
            case ANTLRv4Lexer.HEADER_P_LINE_COMMENT:
            case ANTLRv4Lexer.ID_LINE_COMMENT:
            case ANTLRv4Lexer.HEADER_LINE_COMMENT:
            case ANTLRv4Lexer.HDR_PCKG_LINE_COMMENT:
            case ANTLRv4Lexer.IMPORT_LINE_COMMENT:
            case ANTLRv4Lexer.LEXCOM_LINE_COMMENT:
            case ANTLRv4Lexer.OPT_LINE_COMMENT:
            case ANTLRv4Lexer.PARDEC_LINE_COMMENT:
            case ANTLRv4Lexer.PARDEC_OPT_LINE_COMMENT:
            case ANTLRv4Lexer.TOK_LINE_COMMENT:
            case ANTLRv4Lexer.TYPE_LINE_COMMENT:
                return true;
            default:
                return false;
        }
    }

    static boolean isBlockComment(int type) {
        switch (type) {
            case ANTLRv4Lexer.CHN_BLOCK_COMMENT:
            case ANTLRv4Lexer.HEADER_BLOCK_COMMENT:
            case ANTLRv4Lexer.HEADER_P_BLOCK_COMMENT:
            case ANTLRv4Lexer.ID_BLOCK_COMMENT:
            case ANTLRv4Lexer.IMPORT_BLOCK_COMMENT:
            case ANTLRv4Lexer.OPT_BLOCK_COMMENT:
            case ANTLRv4Lexer.LEXCOM_BLOCK_COMMENT:
            case ANTLRv4Lexer.PARDEC_BLOCK_COMMENT:
            case ANTLRv4Lexer.PARDEC_OPT_BLOCK_COMMENT:
            case ANTLRv4Lexer.TOK_BLOCK_COMMENT:
            case ANTLRv4Lexer.BLOCK_COMMENT:
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
            case ANTLRv4Lexer.WS:
            case ANTLRv4Lexer.CHN_WS:
            case ANTLRv4Lexer.FRAGDEC_WS:
            case ANTLRv4Lexer.HDR_IMPRT_WS:
            case ANTLRv4Lexer.TYPE_WS:
            case ANTLRv4Lexer.HEADER_P_WS:
            case ANTLRv4Lexer.HEADER_WS:
            case ANTLRv4Lexer.ID_WS:
            case ANTLRv4Lexer.IMPORT_WS:
            case ANTLRv4Lexer.LEXCOM_WS:
            case ANTLRv4Lexer.OPT_WS:
            case ANTLRv4Lexer.PARDEC_OPT_WS:
            case ANTLRv4Lexer.PARDEC_WS:
            case ANTLRv4Lexer.TOKDEC_WS:
            case ANTLRv4Lexer.TOK_WS:
                return true;
            default:
                return false;
        }
    }

    private AntlrCriteria() {
        throw new AssertionError();
    }
}

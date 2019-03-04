package org.nemesis.extraction;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 *
 * @author Tim Boudreau
 */
enum RegionExtractType implements Function<Object, int[]> {
    TOKEN, TERMINAL_NODE, INT_ARRAY, PARSER_RULE_CONTEXT, TERMINAL_NODE_LIST;

    @SuppressWarnings("unchecked")
    public <K, TType> BiConsumer<K, TType> wrap(BiConsumer<K, int[]> c) {
        return (K t, TType u) -> {
            if (RegionExtractType.this == TERMINAL_NODE_LIST && u instanceof List<?>) {
                List<TerminalNode> tns = (List<TerminalNode>) u;
                for (TerminalNode n : tns) {
                    c.accept(t, TERMINAL_NODE.apply(u));
                }
            } else {
                c.accept(t, apply(u));
            }
        };
    }

    @Override
    public int[] apply(Object t) {
        if (t == null) {
            return null;
        }
        switch (this) {
            case TOKEN:
                Token tok = (Token) t;
                System.out.println("APPLY TOKEN " + tok.getText() + " " + tok.getStartIndex() + ":" + tok.getStopIndex());
                if (tok.getStartIndex() > tok.getStopIndex()) {
                    System.out.println("ILLEGAL TOKEN " + tok.getType() + " text '" + tok.getText() + "'");
                    return null;
                }
                return new int[]{tok.getStartIndex(), tok.getStopIndex() + 1};
            case TERMINAL_NODE:
                return TOKEN.apply(((TerminalNode) t).getSymbol());
            case PARSER_RULE_CONTEXT:
                ParserRuleContext rule = (ParserRuleContext) t;
                Token start = rule.getStart();
                Token end = rule.getStop();
                if (start != null && end != null) {
                    System.out.println("ap tok a " + start + " " + end);
                    int maxStart = Math.min(start.getStartIndex(), end.getStartIndex());
                    int maxEnd = Math.max(end.getStopIndex(), start.getStopIndex());
                    if (end.getStartIndex() < start.getStartIndex()) {
                        System.out.println("PERVERSE RULE TOKENS START " + start + " END " + end + " for "
                            + rule.getText() + " type " + rule.getClass().getName());
                        return null;
                    }
                    return new int[]{Math.min(maxStart, maxEnd), Math.max(maxStart, maxEnd) + 1};
//                    return new int[] { start.getStartIndex(), end.getStopIndex()+1};
                } else if (start == null && end != null) {
                    System.out.println("ap tok b " + start + " " + end);
                    return new int[]{end.getStartIndex(), end.getStopIndex() + 1};
                } else if (start != null && end == null) {
                    System.out.println("ap tok c " + start + " " + end);
                    return new int[]{start.getStartIndex(), start.getStopIndex() + 1};
                } else if (start == null && end == null) {
                    System.out.println("NULL START AND END TOKENS FOR " + rule + " ival " + rule.getSourceInterval());
                    return null;
                }

            case INT_ARRAY:
                int[] val = (int[]) t;
                if (val.length != 2) {
                    throw new IllegalArgumentException("Array must have two elements: " + Arrays.toString(val));
                }
                return val;
            default:
                throw new AssertionError(this);
        }
    }

}

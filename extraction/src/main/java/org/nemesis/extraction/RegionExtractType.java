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
                return new int[]{tok.getStartIndex(), tok.getStopIndex() + 1};
            case TERMINAL_NODE:
                return TOKEN.apply(((TerminalNode) t).getSymbol());
            case PARSER_RULE_CONTEXT:
                ParserRuleContext rule = (ParserRuleContext) t;
                return new int[]{rule.getStart().getStartIndex(), rule.getStop().getStopIndex() + 1};
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

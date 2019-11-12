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
package org.nemesis.extraction;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 *
 * @author Tim Boudreau
 */
enum RegionExtractType implements Function<Object, int[]> {
    TOKEN, TERMINAL_NODE, INT_ARRAY, PARSER_RULE_CONTEXT, TERMINAL_NODE_LIST;

    private static final Logger LOG = Logger.getLogger(RegionExtractType.class.getName());
    private static final Level PERVERSITIES_LOG_LEVEL = Level.FINER;

    @SuppressWarnings("unchecked")
    public <K, TType> BiPredicate<K, TType> wrap(BiPredicate<K, int[]> c) {
        return (K t, TType u) -> {
            boolean result = false;
            if (RegionExtractType.this == TERMINAL_NODE_LIST && u instanceof List<?>) {
                List<TerminalNode> tns = (List<TerminalNode>) u;
                for (TerminalNode n : tns) {
                    result |= c.test(t, TERMINAL_NODE.apply(n));
                }
            } else {
                result = c.test(t, apply(u));
            }
            return result;
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
                if (tok.getStartIndex() > tok.getStopIndex()) {
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
                    // WTF??
                    int maxStart = Math.min(start.getStartIndex(), end.getStartIndex());
                    int maxEnd = Math.max(end.getStopIndex(), start.getStopIndex());
                    if (end.getStartIndex() < start.getStartIndex()) {
                        if (LOG.isLoggable(PERVERSITIES_LOG_LEVEL)) {
                            LOG.log(PERVERSITIES_LOG_LEVEL,
                                    "Parser rule {0} has nonsensical coordinates: "
                                    + "ending token start {1} is before than starting token start {2}. "
                                    + "Start token type {3}, end token type {4}, "
                                    + "start token text ''{5}'', end token text ''{6}'', "
                                    + "rule text ''{7}''.  Rule index {8} at {9}. "
                                    + "Start token {10}:{11}, end token {12}:{13}. "
                                    + "Ignoring.",
                                    new Object[]{rule.getClass().getSimpleName(),
                                        end.getStartIndex(), start.getStartIndex(),
                                        start.getType(), end.getType(),
                                        start.getText(), end.getText(),
                                        rule.getText(),
                                        rule.getRuleIndex(), rule.getSourceInterval(),
                                        start.getStartIndex(), start.getStopIndex() + 1,
                                        end.getStartIndex(), end.getStopIndex() + 1
                                    });
                        }
                        return null;
                    }
                    return new int[]{Math.min(maxStart, maxEnd), Math.max(maxStart, maxEnd) + 1};
//                    return new int[] { start.getStartIndex(), end.getStopIndex()+1};
                } else if (start == null && end != null) {
//                    System.out.println("ap tok b " + start + " " + end);
                    return new int[]{end.getStartIndex(), end.getStopIndex() + 1};
                } else if (start != null && end == null) {
//                    System.out.println("ap tok c " + start + " " + end);
                    return new int[]{start.getStartIndex(), start.getStopIndex() + 1};
                } else if (start == null && end == null) {
//                    System.out.println("NULL START AND END TOKENS FOR " + rule + " ival " + rule.getSourceInterval());
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

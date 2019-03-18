package org.nemesis.antlrformatting.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
class ParserRulePredicates {

    private final String[] ruleNames;

    public ParserRulePredicates(String[] ruleNames) {
        this.ruleNames = ruleNames;
    }

    Predicate<Set<Integer>> inRule(int rule) {
        return new AbstractLoggablePredicate() {

            @Override
            public boolean test(Set<Integer> t) {
                return t.contains(rule);
            }

            @Override
            public String toString() {
                return "in-parser-rule-" + list(rule);
            }
        };
    }

    Predicate<Set<Integer>> inAnyOf(int rule, int... moreRules) {
        if (moreRules.length == 0) {
            return inRule(rule);
        }
        return new AbstractLoggablePredicate() {

            @Override
            public boolean test(Set<Integer> t) {
                boolean result = t.contains(rule);
                if (!result) {
                    for (int r : moreRules) {
                        result = t.contains(r);
                        if (result) {
                            break;
                        }
                    }
                }
                return result;
            }

            @Override
            public String toString() {
                return "in--any-parser-rules:(" + list(rule, moreRules) + ")";
            }
        };
    }

    Predicate<Set<Integer>> notInRule(int rule) {
        return new AbstractLoggablePredicate() {

            @Override
            public boolean test(Set<Integer> t) {
                return !t.contains(rule);
            }

            @Override
            public String toString() {
                return "not-in-parser-rule-" + list(rule);
            }
        };
    }

    Predicate<Set<Integer>> notInAnyOf(int rule, int... moreRules) {
        return new AbstractLoggablePredicate() {

            @Override
            public boolean test(Set<Integer> t) {
                boolean result = !t.contains(rule);
                if (result) {
                    for (int r : moreRules) {
                        result = !t.contains(r);
                        if (!result) {
                            break;
                        }
                    }
                }
                return result;
            }

            @Override
            public String toString() {
                return "notInAnyOfParserRules("
                        + list(rule, moreRules) + ")";
            }
        };
    }

    String list(int rule, int... moreRules) {
        StringBuilder sb = new StringBuilder(ruleName(rule));
        for (int i = 0; i < moreRules.length; i++) {
            sb.append(',').append(ruleName(moreRules[i]));
        }
        return sb.toString();
    }

    String names(Set<Integer> names) {
        List<String> l = new ArrayList<>();
        for (Integer i : names) {
            l.add(ruleName(i));
        }
        Collections.sort(l);
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private String ruleName(int ix) {
        return ruleNames != null && ix >= 0
                && ix < ruleNames.length
                        ? ruleNames[ix]
                        : Integer.toString(ix);
    }

    static abstract class AbstractLoggablePredicate implements Predicate<Set<Integer>> {

        @Override
        public Predicate<Set<Integer>> and(Predicate<? super Set<Integer>> other) {
            return new AbstractLoggablePredicate() {
                @Override
                public boolean test(Set<Integer> t) {
                    return AbstractLoggablePredicate.this.test(t) && other.test(t);
                }

                @Override
                public String toString() {
                    return "(" + AbstractLoggablePredicate.this.toString() + " && " + other + ")";
                }
            };
        }

        @Override
        public Predicate<Set<Integer>> negate() {
            return new AbstractLoggablePredicate() {
                @Override
                public boolean test(Set<Integer> t) {
                    return !AbstractLoggablePredicate.this.test(t);
                }

                @Override
                public String toString() {
                    return "not(" + AbstractLoggablePredicate.this + ")";
                }

                @Override
                public Predicate<Set<Integer>> negate() {
                    return AbstractLoggablePredicate.this;
                }
            };
        }

        @Override
        public Predicate<Set<Integer>> or(Predicate<? super Set<Integer>> other) {
            return new AbstractLoggablePredicate() {
                @Override
                public boolean test(Set<Integer> t) {
                    return AbstractLoggablePredicate.this.test(t) || other.test(t);
                }

                @Override
                public String toString() {
                    return "(" + AbstractLoggablePredicate.this + " || " + other + ")";
                }
            };
        }
    }
}

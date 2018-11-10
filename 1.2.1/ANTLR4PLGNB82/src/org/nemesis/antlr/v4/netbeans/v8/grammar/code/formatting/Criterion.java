package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Vocabulary;

/**
 * One test a rule can use to determine if it matches the current tokens.
 *
 * @author Tim Boudreau
 */
interface Criterion extends IntPredicate {

    // Lambdas would be nice, but loggability demands implementing
    // toString(), as
    // FormattingRules$FormattingAction$$Lambda$1/1007603019@5383967b
    // doesn't say much
    static Criterion matching(Vocabulary vocab, int val) {
        return new Criterion() {
            private boolean logged;

            @Override
            public boolean test(int value) {
                return value == val;
            }

            public String toString() {
                try {
                    return "match(" + vocab.getSymbolicName(val) + ")";
                } catch (Exception ex) {
                    if (!logged) {
                        Logger.getLogger(Criterion.class.getName()).log(
                                Level.WARNING,
                                "Vocabulary does not contain a symbolic name for "
                                + val, ex);
                        logged = true;
                    }
                    return "match(" + val + ")";
                }
            }
        };
    }

    static Criterion notMatching(Vocabulary vocab, int val) {
        return new Criterion() {
            private boolean logged;

            @Override
            public boolean test(int value) {
                return value != val;
            }

            public String toString() {
                try {
                    return "not(" + vocab.getSymbolicName(val) + ")";
                } catch (Exception ex) {
                    if (!logged) {
                        Logger.getLogger(Criterion.class.getName()).log(
                                Level.WARNING,
                                "Vocabulary does not contain a symbolic name for "
                                + val, ex);
                        logged = true;
                    }
                    return "not(" + val + ")";
                }
            }
        };
    }

    static Criterion anyOf(Vocabulary vocab, int... ints) {
        Arrays.sort(ints);
        return new Criterion() {
            private boolean logged;

            @Override
            public boolean test(int value) {
                return Arrays.binarySearch(ints, value) >= 0;
            }

            public String toString() {
                StringBuilder sb = new StringBuilder("any(");
                for (int i = 0; i < ints.length; i++) {
                    if (sb.length() > 4) {
                        sb.append(',');
                    }
                    try {
                        sb.append(vocab.getSymbolicName(ints[i]));
                    } catch (Exception ex) {
                        if (!logged) {
                            Logger.getLogger(Criterion.class.getName()).log(
                                    Level.WARNING,
                                    "Vocabulary does not contain a symbolic name for "
                                    + ints[i], ex);
                            logged = true;
                        }
                        sb.append(ints[i]);
                    }
                }
                return sb.append(')').toString();
            }
        };
    }

    default Criterion or(Criterion other) {
        return new Criterion() {
            @Override
            public boolean test(int value) {
                return Criterion.this.test(value) || other.test(value);
            }

            @Override
            public String toString() {
                return Criterion.this.toString() + " | " + other;
            }
        };
    }

    default Criterion and(Criterion other) {
        return new Criterion() {
            @Override
            public boolean test(int value) {
                return Criterion.this.test(value) && other.test(value);
            }

            @Override
            public String toString() {
                return Criterion.this.toString() + " & " + other;
            }
        };
    }


    static Criterion noneOf(Vocabulary vocab, int... ints) {
//        return anyOf(ints).negate();
        Arrays.sort(ints);
        return new Criterion() {
            private boolean logged;

            @Override
            public boolean test(int value) {
                return Arrays.binarySearch(ints, value) < 0;
            }

            public String toString() {
                StringBuilder sb = new StringBuilder("none(");
                for (int i = 0; i < ints.length; i++) {
                    if (sb.length() > 5) {
                        sb.append(',');
                    }
                    try {
                        sb.append(vocab.getSymbolicName(ints[i]));
                    } catch (Exception ex) {
                        if (!logged) {
                            Logger.getLogger(Criterion.class.getName()).log(
                                    Level.WARNING,
                                    "Vocabulary does not contain a symbolic name for "
                                    + ints[i], ex);
                            sb.append(ints[i]);
                            logged = true;
                        }
                    }
                }
                return sb.append(')').toString();
            }
        };
    }

    default Criterion negate() {
        return new Criterion() {
            public boolean test(int val) {
                return !Criterion.this.test(val);
            }

            public String toString() {
                return "not(" + Criterion.this.toString() + ")";
            }
        };
    }

    static Criterion ALWAYS = new Criterion() {
        @Override
        public boolean test(int val) {
            return true;
        }

        @Override
        public String toString() {
            return "<always>";
        }
    };
}

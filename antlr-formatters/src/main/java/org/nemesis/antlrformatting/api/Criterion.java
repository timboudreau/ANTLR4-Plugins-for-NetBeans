package org.nemesis.antlrformatting.api;

import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.nemesis.antlrformatting.api.Criteria.ArrayCriterion;
import org.nemesis.antlrformatting.api.Criteria.FixedCriterion;
import org.nemesis.antlrformatting.api.Criteria.LogicalCriterion;
import org.nemesis.antlrformatting.api.Criteria.NegatingCriterion;
import org.nemesis.antlrformatting.api.Criteria.SingleCriterion;

/**
 * One test a rule can use to determine if it matches one or more token types.
 * This class does not add anything to IntPredicate, but ensures that instances
 * created via logical operations are meaningfully loggable, which lambdas do
 * not.  Implementations for matching, non-matching individual values and
 * arrays also implement their equality and hashCode contract correctly,
 * allowing them to be used as map keys.
 *
 * @author Tim Boudreau
 */
public interface Criterion extends IntPredicate {

    // Lambdas would be nice, but loggability demands implementing
    // toString(), as
    // FormattingRules$FormattingAction$$Lambda$1/1007603019@5383967b
    // doesn't say much
    static Criterion matching(Vocabulary vocab, int val) {
        return new SingleCriterion(val, vocab, false);
    }

    static Criterion notMatching(Vocabulary vocab, int val) {
        return new SingleCriterion(val, vocab, true);
    }

    public static Criterion NEVER = new FixedCriterion(false);

    static Criterion anyOf(Vocabulary vocab, int... ints) {
        if (ints.length == 0) {
            return NEVER;
        }
        if (ints.length == 1) {
            return Criterion.matching(vocab, ints[0]);
        }
        Arrays.sort(ints);
        return new ArrayCriterion(ints, vocab, false);
    }

    default Criterion or(Criterion other) {
        return new LogicalCriterion(this, other, true);
    }

    default Criterion and(Criterion other) {
        return new LogicalCriterion(this, other, false);
    }

    default <R> Predicate<R> convertedBy(ToIntFunction<R> func) {
        return new Predicate<R>() {
            public boolean test(R val) {
                return Criterion.this.test(func.applyAsInt(val));
            }

            @Override
            public String toString() {
                return "convert(" + Criterion.this + " <- " + func + ")";
            }
        };
    }

    default Criterion firstNmatches(int max) {
        return new Criterion() {
            private int count;

            @Override
            public boolean test(int value) {
                boolean result = Criterion.this.test(value);
                if (result && count++ > max) {
                    result = false;
                }
                return result;
            }

            @Override
            public String toString() {
                return "first-" + max + "-matches";
            }
        };
    }

    public static Criterion noneOf(Vocabulary vocab, int... ints) {
        if (ints.length == 0) {
            return ALWAYS;
        } else if (ints.length == 1) {
            return new SingleCriterion(ints[0], vocab, true);
        }
        Arrays.sort(ints);
        return new ArrayCriterion(ints, vocab, true);
    }

    @Override
    default Criterion negate() {
        return new NegatingCriterion(this);
    }

    public static final Criterion ALWAYS = new FixedCriterion(true);

    public static Criterion greaterThan(int expected) {
        return new Criterion() {
            @Override
            public boolean test(int value) {
                return value > expected;
            }

            @Override
            public String toString() {
                return ">" + expected;
            }
        };
    }

    public static Criterion lessThan(int expected) {
        return new Criterion() {
            @Override
            public boolean test(int value) {
                return value < expected;
            }

            @Override
            public String toString() {
                return "<" + expected;
            }
        };
    }

    static Criterion equalTo(int expected) {
        return new SingleCriterion(expected, null, false);
    }

    @Override
    default Criterion or(IntPredicate other) {
        return new LogicalCriterion(this, other, true);
    }

    @Override
    default Criterion and(IntPredicate other) {
        if (other == this) {
            return this;
        }
        return new LogicalCriterion(this, other, false);
    }

    default Criterion firstNMatches(int n) {
        return new Criterion() {
            int counter = 0;

            @Override
            public boolean test(int value) {
                return counter++ < n && Criterion.this.test(value);
            }

            @Override
            public String toString() {
                return Criterion.this.toString() + " (first " + n + " matches)";
            }

        };
    }

    /**
     * Return a wrapper for this criterion which will only return true when the
     * immediately preceding call was with the value <code>n</code>.
     *
     * @param n Another value
     * @return
     */
    default Criterion precededby(int n) {
        return new Criterion() {
            int prev = -1;

            @Override
            public boolean test(int value) {
                boolean result = prev == n && Criterion.this.test(value);
                prev = value;
                return result;
            }

            @Override
            public String toString() {
                return Criterion.this.toString() + " (preceded by " + n + ")";
            }
        };
    }

    /**
     * Creates a wrapper for this criterion which will match on the token type,
     * but will ignore subsequent matches until it has been called with some
     * other value.
     *
     * @return A criterion
     */
    default Criterion firstInSeries() {
        return new Criterion() {
            int ct = -1;

            @Override
            public boolean test(int value) {
                boolean result = Criterion.this.test(value);
                if (result) {
                    ct++;
                } else {
                    ct = -1;
                }
                return result && ct == 0;
            }

            @Override
            public String toString() {
                return Criterion.this.toString() + " (first-in-series)";
            }
        };
    }

    default Criterion andSubsequent(int n) {
        return new Criterion() {
            int ct = -1;

            @Override
            public boolean test(int value) {
                if (ct > 0) {
                    if (ct++ > n) {
                        ct = -1;
                        return false;
                    }
                    return true;
                }
                boolean result = Criterion.this.test(value);
                if (result) {
                    ct = 0;
                }
                return result;
            }

            @Override
            public String toString() {
                return Criterion.this.toString() + " (and subsequent" + n + " values of any type)";
            }
        };
    }

    default Predicate<Token> toTokenPredicate() {
        return new Predicate<Token>() {
            @Override
            public boolean test(Token t) {
                return Criterion.this.test(t.getType());
            }

            @Override
            public String toString() {
                return "Token<" + Criterion.this.toString() + ">";
            }
        };
    }
}

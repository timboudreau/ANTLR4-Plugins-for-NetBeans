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
package com.mastfrog.antlr.utils;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Vocabulary;

/**
 * Factory for Criterion instances which uses a shared instance of Vocabulary
 * for the convenience of not needing to pass it to every invocation of static
 * methods on Criterion; these are effectively just loggable IntPredicates which
 * show the token names rather than token-type numbers, so that calling
 * <code>toString()</code> on even a complex, aggregated Criterion instance will
 * actually tell you what it is trying to match on and when, rather than
 * something like "Foo$$Lambda1".
 *
 * @author Tim Boudreau
 */
public final class Criteria {

    private final Vocabulary vocab;

    private Criteria(Vocabulary vocab) {
        this.vocab = vocab;
    }

    /**
     * Create a Criteria instance for a particular Antlr vocabulary.
     *
     * @param vocab The vocabulary
     * @return A Criteria instance
     */
    public static Criteria forVocabulary(Vocabulary vocab) {
        assert vocab != null : "vocab null";
        return new Criteria(vocab);
    }

    /**
     * Get a criterion that matches one specific token type.
     *
     * @param val The token type
     * @return A criterion
     */
    public Criterion matching(int val) {
        return Criterion.matching(vocab, val);
    }

    /**
     * Get a criterion that matches all types <i>except</i> the passed token
     * type.
     *
     * @param val The token type
     * @return A criterion
     */
    public Criterion notMatching(int val) {
        return Criterion.notMatching(vocab, val);
    }

    /**
     * Get a criterion that matches any of a set of token types (the passed
     * array should not contain duplicates, and this will eventually be
     * enforced).
     *
     * @param tokenTypes The token types
     * @return A criterion
     */
    public Criterion anyOf(int... tokenTypes) {
        return Criterion.anyOf(vocab, tokenTypes);
    }

    /**
     * Get a criterion that matches any token types <i>except</i> the set of
     * token types (the passed array should not contain duplicates, and this
     * will eventually be enforced).
     *
     * @param tokenTypes The token types
     * @return A criterion
     */
    public Criterion noneOf(int... ints) {
        return Criterion.noneOf(vocab, ints);
    }

    public static IntPredicate rulesMatchPredicate(String[] ruleNames, int... rules) {
        return new RulesMatch(ruleNames, rules);
    }

    private static final class RulesMatch implements IntPredicate {

        private final BitSet bits;
        private final String[] ruleNames;

        RulesMatch(String[] ruleNames, int... rules) {
            this.ruleNames = ruleNames;
            bits = new BitSet(ruleNames.length);
            for (int i = 0; i < rules.length; i++) {
                bits.set(rules[i]);
            }
        }

        @Override
        public boolean test(int value) {
            return bits.get(value);
        }

        @Override
        public int hashCode() {
            return bits.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj ? true : obj == null ? false : !(obj instanceof RulesMatch) ? false
                    : ((RulesMatch) obj).bits.equals(bits);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Rules(");
            for (int bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1)) {
                String name = null;
                if (sb.length() > 6) {
                    sb.append(", ");
                }
                if (bit < ruleNames.length) {
                    name = ruleNames[bit];
                    if (name != null) {
                        sb.append(name).append('[').append(bit).append(']');
                    } else {
                        sb.append(bit);
                    }
                } else {
                    sb.append(bit);
                }
            }
            return sb.append(')').toString();
        }
    }

    // Criterion implementation types placed here so they don't become
    // part of the public API of Criterion.  Use of Criterion instances
    // in TokenCache makes it useful to have these all be real classes
    // which correctly implement equals and hashCode
    static final class SingleCriterion implements Criterion {

        private final int value;
        private final Vocabulary vocab;
        private final boolean negated;
        private boolean logged;

        SingleCriterion(int value, Vocabulary vocab, boolean negated) {
            this.value = value;
            this.vocab = vocab;
            this.negated = negated;
        }

        @Override
        public Criterion negate() {
            return new SingleCriterion(value, vocab, !negated);
        }

        @Override
        public boolean test(int value) {
            return negated ? value != this.value : value == this.value;
        }

        @Override
        public int hashCode() {
            // Use the same algorithm as Arrays.hashCode(), so
            // equivalent items have consistent hash codes
            int result = 1;
            result = 31 * result + value;
            result = (vocab == null ? 0 : vocab.getClass().hashCode()) + 53 * result;
            return negated ? -result : result;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof SingleCriterion) {
                SingleCriterion other = (SingleCriterion) o;
                return value == other.value && negated == other.negated
                        && ((vocab == null ? null : vocab.getClass())
                        == (other.vocab == null ? null : other.vocab.getClass()));
            }
            return false;
        }

        @Override
        public String toString() {
            if (vocab == null) {
                return negated ? "!= " + value : "==value";
            }
            String base = negated ? "non-match(" : "match(";
            try {
                String s = vocab.getSymbolicName(value);
                if (s == null) {
                    s = vocab.getLiteralName(value);
                }
                if (s == null) {
                    s = vocab.getDisplayName(value);
                }
                if (s == null) {
                    s = Integer.toString(value);
                }
                return base + s + ")";
            } catch (Exception ex) {
                if (!logged) {
                    Logger.getLogger(Criterion.class.getName()).log(
                            Level.WARNING,
                            "Vocabulary does not contain a symbolic name for "
                            + value, ex);
                    logged = true;
                }
                return base + value + ")";
            }
        }
    }

    static int[] sortAndDedup(int[] arr) {
        if (arr.length > 1) {
            Arrays.sort(arr);
            int dupCount = 0;
            for (int i = 1; i < arr.length; i++) {
                int prev = arr[i - 1];
                int curr = arr[i];
                if (prev == curr) {
                    dupCount++;
                }
            }
            if (dupCount == 0) {
                return arr;
            }
            int[] finalResult = new int[arr.length - dupCount];
            int prev;
            int cursor = 1;
            finalResult[0] = prev = arr[0];
            for (int i = 1; i < arr.length; i++) {
                if (prev != arr[i]) {
                    finalResult[cursor++] = prev = arr[i];
                }
            }
            return finalResult;
        }
        return arr;
    }

    static final class ArrayCriterion implements Criterion {

        private final int[] ints;
        private final Vocabulary vocab;
        private boolean logged;
        private final boolean negated;

        ArrayCriterion(int[] ints, Vocabulary vocab, boolean negated) {
            // XXX we should be policing this somewhere else, but
            // some piece of code is causing Arrays.binarySearch to
            // go into an endless loop with an unsorted array here.

            // Should be fixed eventually to throw an AssertionError
            // instead of fixing it, but not when in the stabilization
            // phase for a release
            this.ints = sortAndDedup(ints);
            this.vocab = vocab;
            this.negated = negated;
        }

        @Override
        public Criterion negate() {
            return new ArrayCriterion(ints, vocab, !negated);
        }

        @Override
        public boolean test(int value) {
            return negated ? Arrays.binarySearch(ints, value) < 0
                    : Arrays.binarySearch(ints, value) >= 0;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof ArrayCriterion) {
                ArrayCriterion other = (ArrayCriterion) o;
                return other.negated == negated && other.vocab.getClass() == vocab.getClass()
                        && Arrays.equals(ints, other.ints);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = vocab.getClass().hashCode() + 53 * Arrays.hashCode(ints);
            return negated ? -result : result;
        }

        @Override
        public String toString() {
            String base = negated ? "none(" : "any(";
            StringBuilder sb = new StringBuilder(base);
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
    }

    static class NegatingCriterion implements Criterion {

        private final Criterion orig;

        NegatingCriterion(Criterion orig) {
            this.orig = orig;
        }

        @Override
        public String toString() {
            return "not(" + orig + ")";
        }

        @Override
        public Criterion negate() {
            return orig;
        }

        @Override
        public int hashCode() {
            return -orig.hashCode();
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof NegatingCriterion) {
                return ((NegatingCriterion) o).orig.equals(orig);
            }
            return false;
        }

        @Override
        public boolean test(int value) {
            return !orig.test(value);
        }
    }

    static final class FixedCriterion implements Criterion {

        private final boolean val;

        FixedCriterion(boolean val) {
            this.val = val;
        }

        @Override
        public boolean test(int value) {
            return val;
        }

        @Override
        public String toString() {
            return val ? "<always>" : "<never>";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof FixedCriterion) {
                return ((FixedCriterion) o).val == val;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return val ? 1096 : 1;
        }
    }

    static class LogicalCriterion implements Criterion {

        private final Criterion a;
        private final IntPredicate b;
        private final boolean or;

        LogicalCriterion(Criterion a, IntPredicate b, boolean or) {
            this.a = a;
            this.b = b;
            this.or = or;
        }

        @Override
        public boolean test(int value) {
            return or ? a.test(value) || b.test(value)
                    : a.test(value) && b.test(value);
        }

        @Override
        public String toString() {
            return a + (or ? " | " : " & ") + b;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof LogicalCriterion) {
                LogicalCriterion other = (LogicalCriterion) o;
                return other.or == or && (a.equals(other.a) && b.equals(other.b))
                        || (a.equals(other.b) && b.equals(other.a));
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = a.hashCode() + 53 * b.hashCode();
            return or ? result * 79 : result;
        }
    }
}

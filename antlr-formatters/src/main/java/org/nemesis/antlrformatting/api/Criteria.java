/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlrformatting.api;

import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Vocabulary;

/**
 * Factory for Criterion instances which uses a shared instance of Vocabulary
 * for the convenience of not needing to pass it to every invocation of static
 * methods on Criterion.
 *
 * @author Tim Boudreau
 */
public final class Criteria {

    private final Vocabulary vocab;

    private Criteria(Vocabulary vocab) {
        this.vocab = vocab;
    }

    public static Criteria forVocabulary(Vocabulary vocab) {
        assert vocab != null : "vocab null";
        return new Criteria(vocab);
    }

    public Criterion matching(int val) {
        return Criterion.matching(vocab, val);
    }

    public Criterion notMatching(int val) {
        return Criterion.notMatching(vocab, val);
    }

    public Criterion anyOf(int... ints) {
        return Criterion.anyOf(vocab, ints);
    }

    public Criterion noneOf(int... ints) {
        return Criterion.noneOf(vocab, ints);
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

    static final class ArrayCriterion implements Criterion {

        private final int[] ints;
        private final Vocabulary vocab;
        private boolean logged;
        private final boolean negated;

        ArrayCriterion(int[] ints, Vocabulary vocab, boolean negated) {
            this.ints = ints;
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

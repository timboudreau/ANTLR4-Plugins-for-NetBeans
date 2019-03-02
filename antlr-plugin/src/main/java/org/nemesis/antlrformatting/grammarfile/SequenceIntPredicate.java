package org.nemesis.antlrformatting.grammarfile;

import org.nemesis.antlrformatting.api.util.Predicates;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Allows a series of predicates to be concatenated, such that you get a single
 * predicate which only returns true if preceding calls to test() have matched
 * earlier values. Makes it possible to construct a predicate that only matches
 * if called with, say, 2, 4, 6 in that order with no intervening numbers passed
 * to it.
 * <p>
 * So, for example, if you created a predicate:
 * <pre>
 * SequenceIntPredicate.matchingAnyOf(2).then(4).then(6);
 * </pre> and called
 * <pre>
 * test(0); // returns false
 * test(2); // returns false
 * test(4); // returns false
 * test(6); // returns true
 * </pre> The call that passes 6 would return true, <i>if and only if</i> the
 * preceding two calls had passed 4, and prior to that, 2, with no other numbers
 * in between. A subsequent identical sequence matchingAnyOf calls would also
 * return true once 6 was reached, no matter what numbers had been passed in
 * calls to test() in between the two matching sequences.
 * </p>
 *
 * @author Tim Boudreau
 */
final class SequenceIntPredicate implements ResettableCopyableIntPredicate {

    private final SequenceIntPredicate parent;
    private final IntPredicate test;
    private boolean lastResult;

    SequenceIntPredicate(int val) {
        this(Predicates.predicate(val));
    }

    SequenceIntPredicate(IntPredicate test) {
        this(null, test);
    }

    SequenceIntPredicate(SequenceIntPredicate parent, int val) {
        this(parent, Predicates.predicate(val));
    }

    SequenceIntPredicate(SequenceIntPredicate parent, IntPredicate test) {
        this.parent = parent;
        this.test = test;
    }

    /**
     * Create a duplicate matchingAnyOf this SequenceIntPredicate and its
     * parents, which does not share matching state with them.
     *
     * @return A new SequenceIntPredicate
     */
    public SequenceIntPredicate copy() {
        SequenceIntPredicate nue = parent == null ? new SequenceIntPredicate(test)
                : new SequenceIntPredicate(parent.copy(), test);
        return nue;
    }

    /**
     * Returns a predicate which will match the passed array if test() is called
     * sequentially with the same values as appear in the array in the same
     * order.
     *
     * @param sequence A sequence of values to match
     * @return A combined SequenceBytePredicate which matches all of the passed
     * values
     * @throws IllegalArgumentException if the array is 0-length
     */
    public static SequenceIntPredicate toMatchSequence(int... sequence) {
        if (sequence.length == 0) {
            throw new IllegalArgumentException("0-length array");
        }
        SequenceIntPredicate result = null;
        for (int i = 0; i < sequence.length; i++) {
            if (result == null) {
                result = new SequenceIntPredicate(sequence[i]);
            } else {
                result = result.then(sequence[i]);
            }
        }
        return result;
    }

    /**
     * Start a new predicate with one which matches the passed value (call
     * then() to match additional subsequent values).
     *
     * @param val The initial value to match
     * @return a predicate
     */
    public static SequenceIntPredicate matching(int val) {
        return new SequenceIntPredicate(val);
    }

    /**
     * Returns a starting predicate whose first element will match <i>any</i>
     * matching the passed values.
     *
     * @param val The first value
     * @param moreVals Additional values
     * @return A predicate
     */
    public static SequenceIntPredicate matchingAnyOf(int val, int... moreVals) {
        if (moreVals.length == 0) {
            return matching(val);
        }
        return new SequenceIntPredicate(Predicates.predicate(val, moreVals));
    }

    /**
     * Wrap a plain predicate as a SequenceIntPredicate.
     *
     * @param pred The original predicate
     * @return A new sequential predicate
     */
    public static SequenceIntPredicate of(IntPredicate pred) {
        return new SequenceIntPredicate(pred);
    }

    /**
     * Define the next step in this sequence.
     *
     * @param nextTest The test which must pass, along with the test in this
     * object for the preceding call to test(), for the returned predicate to
     * return true.
     *
     * @param nextTest - A predicate to match against, if the preceding call to
     * test matched this object's test
     * @return A new predicate
     */
    public SequenceIntPredicate then(IntPredicate nextTest) {
        return new SequenceIntPredicate(this, nextTest);
    }

    /**
     * Define the next step in this sequence.
     *
     * @param nextTest The test which must pass, along with the test in this
     * object for the preceding call to test(), for the returned predicate to
     * return true.
     *
     * @return A new predicate
     */
    public SequenceIntPredicate then(int val) {
        return new SequenceIntPredicate(this, Predicates.predicate(val));
    }

    /**
     * Define the next step in this sequence.
     *
     * @param nextTest The test which must pass, along with the test in this
     * object for the preceding call to test(), for the returned predicate to
     * return true.
     *
     * @return A new predicate
     */
    public SequenceIntPredicate then(int val, int... moreVals) {
        return new SequenceIntPredicate(this, Predicates.predicate(val, moreVals));
    }

    @Override
    public String toString() {
        if (parent == null) {
            return test.toString();
        }
        return "Seq{" + test.toString() + " <- " + parent.toString() + "}";
    }

    private boolean reallyTest(int value) {
        lastResult = test.test(value);
        return lastResult;
    }

    @Override
    public boolean test(int value) {
        if (parent == null) {
            boolean result = reallyTest(value);
            return result;
        }
        if (parent != null && parent.lastResult) {
            boolean res = reallyTest(value);
            clear();
            return res;
        }
        SequenceIntPredicate toTest = parent;
        while (toTest != null) {
            boolean testThisOne
                    = (toTest.parent != null && toTest.parent.lastResult)
                    || (toTest.parent == null);

            if (testThisOne) {
                boolean res = toTest.reallyTest(value);
                if (!res) {
                    // We have a repetition of a partial value
                    boolean shouldResetState = toTest.parent != null && toTest.parent.lastResult;
                    clear();
                    if (shouldResetState) {
                        toTest.parent.test(value);
                    }
                }
                return false;
            }
            toTest = toTest.parent;
        }
        clear();
        return false;
    }

    /**
     * Reset any state in this predicate, before using it against a new sequence
     * matchingAnyOf calls to test().
     */
    public SequenceIntPredicate reset() {
        clear();
        return this;
    }

    /**
     * Determine if this predicate is in the state of having a partial match.
     *
     * @return true if a partial match has been found
     */
    public boolean isPartiallyMatched() {
        if (parent != null) {
            return lastResult || parent.isPartiallyMatched();
        } else {
            return lastResult;
        }
    }

    private void clear() {
        lastResult = false;
        if (parent != null) {
            parent.clear();
        }
    }

    // Sigh - so we get meaninfgfully loggable objects
    /**
     * Overridden to return a predicate with a reasonable implementation
     * matchingAnyOf toString().
     *
     * @param other Another predicate
     * @return a new predicate
     */
    @Override
    public ResettableCopyableIntPredicate and(IntPredicate other) {
        return new LogicalPredicateImpl(copy(), other, true);
    }

    /**
     * Overridden to return a predicate with a reasonable implementation
     * matchingAnyOf toString().
     *
     * @return A negated version matchingAnyOf this predicate
     */
    @Override
    public ResettableCopyableIntPredicate negate() {
        return new NegatingImpl(this);
    }

    /**
     * Get the number of items that need to be matched for this predicate to
     * match.
     *
     * @return The number of items (the number of parents + 1)
     */
    public int size() {
        return parent == null ? 1 : 1 + parent.size();
    }

    /**
     * Overridden to return a predicate with a reasonable implementation
     * matchingAnyOf toString().
     *
     * @param other Another predicate
     * @return a new predicate
     */
    @Override
    public ResettableCopyableIntPredicate or(IntPredicate other) {
        return new LogicalPredicateImpl(copy(), other, false);
    }

    public static SequenceIntPredicateBuilder<SequenceIntPredicate> builder() {
        return builder(t -> {
            return t;
        });
    }

    public static <T> SequenceIntPredicateBuilder<T> builder(
            Function<SequenceIntPredicate, T> converter) {
        return new SequenceIntPredicateBuilder<>(converter);
    }

    public static final class SequenceIntPredicateBuilder<T> {

        private final Function<SequenceIntPredicate, T> converter;

        public SequenceIntPredicateBuilder(
                Function<SequenceIntPredicate, T> converter) {
            this.converter = converter;
        }

        public FinishableSequenceIntPredicateBuilder<T> startingWith(int val) {
            return new FinishableSequenceIntPredicateBuilder<>(converter,
                    SequenceIntPredicate.matching(val));
        }

        public static final class FinishableSequenceIntPredicateBuilder<T> {

            private final Function<SequenceIntPredicate, T> converter;
            private SequenceIntPredicate predicate;

            FinishableSequenceIntPredicateBuilder(
                    Function<SequenceIntPredicate, T> converter,
                    SequenceIntPredicate predicate) {
                this.converter = converter;
                this.predicate = predicate;
            }

            public FinishableSequenceIntPredicateBuilder<T> thenAnyOf(int val,
                    int... more) {
                predicate = predicate.then(val, more);
                return this;
            }

            public FinishableSequenceIntPredicateBuilder<T> then(int val) {
                predicate = predicate.then(val);
                return this;
            }

            public FinishableSequenceIntPredicateBuilder<T> then(
                    IntPredicate pred) {
                predicate = predicate.then(pred);
                return this;
            }

            public T build() {
                return converter.apply(predicate);
            }
        }
    }

    private static final class NegatingImpl implements ResettableCopyableIntPredicate {

        private final ResettableCopyableIntPredicate delegate;

        public NegatingImpl(ResettableCopyableIntPredicate delegate) {
            this.delegate = delegate;
        }

        @Override
        public ResettableCopyableIntPredicate copy() {
            return new NegatingImpl(delegate.copy());
        }

        @Override
        public ResettableCopyableIntPredicate reset() {
            delegate.reset();
            return this;
        }

        @Override
        public boolean test(int value) {
            return !delegate.test(value);
        }

        @Override
        public ResettableCopyableIntPredicate and(IntPredicate other) {
            return new LogicalPredicateImpl(copy(), other, true);
        }

        @Override
        public ResettableCopyableIntPredicate negate() {
            return delegate.copy();
        }

        @Override
        public ResettableCopyableIntPredicate or(IntPredicate other) {
            return new LogicalPredicateImpl(copy(), other, false);
        }

        @Override
        public String toString() {
            return "not(" + delegate + ")";
        }
    }

    private static final class LogicalPredicateImpl implements ResettableCopyableIntPredicate {

        private final ResettableCopyableIntPredicate delegate;
        private final IntPredicate other;
        private final boolean and;

        public LogicalPredicateImpl(ResettableCopyableIntPredicate copy, IntPredicate other, boolean and) {
            this.delegate = copy;
            this.other = other;
            this.and = and;
        }

        public ResettableCopyableIntPredicate or(IntPredicate predicate) {
            return new LogicalPredicateImpl(this, predicate, false);
        }

        public ResettableCopyableIntPredicate and(IntPredicate predicate) {
            return new LogicalPredicateImpl(this, predicate, true);
        }

        @Override
        public boolean test(int value) {
            if (and) {
                return delegate.test(value) && other.test(value);
            } else {
                return delegate.test(value) || other.test(value);
            }
        }

        @Override
        public String toString() {
            return "(" + delegate + (and ? " & " : " | ") + other + ")";
        }

        @Override
        public ResettableCopyableIntPredicate copy() {
            ResettableCopyableIntPredicate delegateCopy = delegate.copy();
            IntPredicate otherCopy = other;
            if (otherCopy instanceof ResettableCopyableIntPredicate) {
                otherCopy = ((ResettableCopyableIntPredicate) otherCopy).copy();
            }
            return new LogicalPredicateImpl(delegateCopy, otherCopy, and);
        }

        @Override
        public ResettableCopyableIntPredicate reset() {
            delegate.reset();
            if (other instanceof ResettableCopyableIntPredicate) {
                ((ResettableCopyableIntPredicate) other).reset();
            }
            return this;
        }

        @Override
        public ResettableCopyableIntPredicate negate() {
            return new NegatingImpl(copy());
        }
    }
}

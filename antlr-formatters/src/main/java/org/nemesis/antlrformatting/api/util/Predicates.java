package org.nemesis.antlrformatting.api.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Utility methods for constructing predicates which implement toString()
 * meaningfully for logging purposes.
 *
 * @author Tim Boudreau
 */
public final class Predicates {

    private Predicates() {
        throw new AssertionError();
    }

    public static final class Fixed<T> implements Predicate<T> {

        private final boolean val;

        public Fixed(boolean val) {
            this.val = val;
        }

        @Override
        public boolean test(T t) {
            return val;
        }

        @Override
        public String toString() {
            return val ? "everything" : "nothing";
        }
    }

    public static <T> Predicate<T> nothing() {
        return new Fixed(false);
    }

    public static <T> Predicate<T> everything() {
        return new Fixed(true);
    }

    /**
     * Combine a value and an array and assert that there are no duplicates.
     *
     * @param first The first item
     * @param more More items
     * @return An array containing the first and later items
     */
    public static String[] combine(String first, String... more) {
        String[] result = new String[more.length + 1];
        result[0] = first;
        System.arraycopy(more, 0, result, 1, more.length);
        assert noDuplicates(result) : "Duplicate values in " + Arrays.toString(result);
        return result;
    }

    private static String stringify(int[] array) {
        int[] copy = Arrays.copyOf(array, array.length);
        Arrays.sort(copy);
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < copy.length; i++) {
            sb.append(copy[i]);
            if (i != copy.length-1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Combine a value and an array and assert that there are no duplicates.
     *
     * @param first The first item
     * @param more More items
     * @return An array containing the first and later items
     */
    public static int[] combine(int prepend, int... more) {
        int[] vals = new int[more.length + 1];
        vals[0] = prepend;
        System.arraycopy(more, 0, vals, 1, more.length);
        assert noDuplicates(vals) : "Duplicate values in " + stringify(vals);
        return vals;
    }

    private static Set<Integer> setOf(int[] vals) {
        Set<Integer> set = new HashSet<>();
        for (int val : vals) {
            set.add(val);
        }
        return set;
    }

    private static boolean noDuplicates(int[] vals) {
        return setOf(vals).size() == vals.length;
    }

    private static boolean noDuplicates(String[] vals) {
        return new HashSet<>(Arrays.asList(vals)).size() == vals.length;
    }

    /**
     * Create a predicate which matches any of the passed integers.
     *
     * @param first The first integer
     * @param more Additional integers
     * @throws AssertionError if duplicates are present and assertions are
     * enabled
     * @return A predicate
     */
    public static IntPredicate predicate(int first, int... more) {
        if (more.length == 0) {
            return new SinglePredicate(false, first);
        }
        int[] vals = Predicates.combine(first, more);
        Arrays.sort(vals);
        return new ArrayPredicate(vals, false);
    }

    /**
     * Create a predicate which matches any of the passed strings.
     *
     * @param first The first string
     * @param more Some more strings
     * @throws AssertionError if duplicates are present and assertions are
     * enabled
     * @return A predicate
     */
    public static Predicate<String> predicate(String first, String... more) {
        if (more.length == 0) {
            return new SingleStringPredicate(false, first);
        }
        String[] vals = Predicates.combine(first, more);
        Arrays.sort(vals);
        return new StringArrayPredicate(false, vals);
    }

    private static final class SingleStringPredicate implements Predicate<String> {

        private final boolean negated;
        private final String string;

        SingleStringPredicate(boolean negated, String string) {
            this.negated = negated;
            this.string = string;
        }

        @Override
        public boolean test(String t) {
            boolean result = string.equals(t);
            return negated ? !result : result;
        }

        public String toString() {
            return (negated ? "!match(" : "match(") + string + ")";
        }
    }

    private static final class StringArrayPredicate implements Predicate<String> {

        private final boolean negated;
        private final String[] vals;

        StringArrayPredicate(boolean negated, String[] vals) {
            this.negated = negated;
            this.vals = vals;
        }

        @Override
        public boolean test(String t) {
            boolean result = Arrays.binarySearch(vals, t) >= 0;
            return negated ? !result : result;
        }

        @Override
        public Predicate<String> negate() {
            return new StringArrayPredicate(!negated, vals);
        }

        @Override
        public String toString() {
            String pfx = negated ? "!match(" : "match(";
            return pfx + (vals.length == 1 ? vals[0] : Arrays.toString(vals)) + ")";
        }
    }

    private static class SinglePredicate implements IntPredicate {

        private final boolean negated;
        private final int val;

        SinglePredicate(boolean negated, int val) {
            this.negated = negated;
            this.val = val;
        }

        @Override
        public boolean test(int value) {
            return negated ? value != val : value == val;
        }

        @Override
        public IntPredicate negate() {
            return new SinglePredicate(!negated, val);
        }

        @Override
        public String toString() {
            String pfx = negated ? "!match(" : "match";
            return pfx + val + ")";
        }
    }

    private static class ArrayPredicate implements IntPredicate {

        private final int[] vals;
        private final boolean negated;

        ArrayPredicate(int[] vals, boolean negated) {
            this.vals = vals;
            this.negated = negated;
        }

        @Override
        public boolean test(int val) {
            boolean result;
            if (vals.length == 1) {
                result = vals[0] == val;
            } else {
                result = Arrays.binarySearch(vals, val) >= 0;
            }
            return negated ? !result : result;
        }

        @Override
        public String toString() {
            if (vals.length == 1) {
                return (negated ? "!" : "") + "match(" + vals[0] + ")";
            } else {
                return (negated ? "!" : "") + "match("
                        + Arrays.toString(vals) + ")";
            }
        }

        @Override
        public IntPredicate negate() {
            return new ArrayPredicate(vals, !negated);
        }
    }
}

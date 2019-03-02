package org.nemesis.antlrformatting.grammarfile;

import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 *
 * @author Tim Boudreau
 */
interface ResettableCopyableIntPredicate extends IntPredicate {

    ResettableCopyableIntPredicate copy();

    ResettableCopyableIntPredicate reset();

    /**
     * Convert this into an Object-taking predicate which uses the passed
     * function to resolve an integer.
     *
     * @param <R> The type the returned predicate takes
     * @param func A conversion function
     * @return A predicate
     */
    default <R> Predicate<R> convertedBy(ToIntFunction<R> func) {
        return new Predicate<R>() {
            public boolean test(R val) {
                return ResettableCopyableIntPredicate.this.test(func.applyAsInt(val));
            }

            public String toString() {
                return "convert(" + ResettableCopyableIntPredicate.this + " <- " + func + ")";
            }
        };
    }
}

package org.nemesis.misc.utils.function;

import java.util.function.Function;

/**
 * Analogue of java.util.function.Function which can throw.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ThrowingFunction<In, Out> {

    Out apply(In in) throws Exception;

    default <NextOut> ThrowingFunction<In, NextOut> andThen(ThrowingFunction<Out, NextOut> f) {
        return in -> {
            return f.apply(ThrowingFunction.this.apply(in));
        };
    }

    default <NextOut> ThrowingFunction<In, NextOut> andThen(Function<Out, NextOut> f) {
        return in -> {
            return f.apply(ThrowingFunction.this.apply(in));
        };
    }

    default Function<In, Out> toFunction() {
        return in -> {
            try {
                return ThrowingFunction.this.apply(in);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }
}

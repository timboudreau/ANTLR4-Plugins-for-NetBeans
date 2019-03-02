package org.nemesis.registration.utils.function;

import java.util.function.BiConsumer;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ThrowingBiConsumer<T, R> {

    void accept(T a, R b) throws Exception;

    default ThrowingBiConsumer<T, R> andThen(ThrowingBiConsumer<T, R> tb) {
        return (a, b) -> {
            ThrowingBiConsumer.this.accept(a, b);
            tb.accept(a, b);
        };
    }

    default ThrowingBiConsumer<T, R> andThen(BiConsumer<T, R> tb) {
        return (a, b) -> {
            ThrowingBiConsumer.this.accept(a, b);
            tb.accept(a, b);
        };
    }

    default ThrowingBiConsumer<T, R> andThen(IOBiConsumer<T, R> tb) {
        return (a, b) -> {
            ThrowingBiConsumer.this.accept(a, b);
            tb.accept(a, b);
        };
    }
}

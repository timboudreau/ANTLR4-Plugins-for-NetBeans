package org.nemesis.misc.utils.function;

import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ThrowingConsumer<T> {

    void accept(T t) throws Exception;

    default ThrowingConsumer<T> and (ThrowingConsumer<T> other) {
        return t -> {
            this.accept(t);
            other.accept(t);
        };
    }

    default ThrowingConsumer<T> and(Consumer<T> other) {
        return t -> {
            this.accept(t);
            other.accept(t);
        };
    }
}

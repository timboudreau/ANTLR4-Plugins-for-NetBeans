package org.nemesis.misc.utils.function;

import java.io.IOException;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface IOConsumer<T> {

    void accept(T arg) throws IOException;

    default IOConsumer<T> andThen(IOConsumer<T> c) {
        return arg -> {
            IOConsumer.this.accept(arg);
            c.accept(arg);
        };
    }

    default IOConsumer<T> andThen(Consumer<T> c) {
        return arg -> {
            IOConsumer.this.accept(arg);
            c.accept(arg);
        };
    }
}

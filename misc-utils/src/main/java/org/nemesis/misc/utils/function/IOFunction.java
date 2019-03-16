package org.nemesis.misc.utils.function;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface IOFunction<In, Out> {

    Out apply(In a) throws IOException;

    default <V> IOFunction<In, V> andThen(Function<? super Out, ? extends V> after) {
        Objects.requireNonNull(after);
        return (In t) -> after.apply(apply(t));
    }

}

package org.nemesis.misc.utils.function;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface IOTriFunction<In1, In2, In3, Out> {

    Out apply(In1 a, In2 b, In3 c) throws IOException;

    default <V> IOTriFunction<In1, In2, In3, V> andThen(Function<? super Out, ? extends V> after) {
        Objects.requireNonNull(after);
        return (In1 t, In2 u, In3 v) -> after.apply(apply(t, u, v));
    }
}

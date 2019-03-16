package org.nemesis.misc.utils.function;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface IOBiFunction<In1, In2, Out> {

    Out apply(In1 a, In2 b) throws IOException;

    default <V> IOBiFunction<In1, In2, V> andThen(Function<? super Out, ? extends V> after) {
        Objects.requireNonNull(after);
        return (In1 t, In2 u) -> after.apply(apply(t, u));
    }

}

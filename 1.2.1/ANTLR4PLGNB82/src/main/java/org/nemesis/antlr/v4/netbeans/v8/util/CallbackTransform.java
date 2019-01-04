package org.nemesis.antlr.v4.netbeans.v8.util;

import java.util.Optional;
import org.openide.util.Parameters;

/**
 * Transforms the result of a callback into a different kind.
 *
 * @author Tim Boudreau
 */
public interface CallbackTransform<In, Out> {

    <Ret> Ret xform(In in, Callback<Out, Ret> receiver);

    default <Ret> Callback<In, Ret> transform(Callback<Out, Ret> cb) {
        Parameters.notNull("cb", cb);
        return (Optional<String> failure, In arg) -> cb.ifNotFailed(failure, () -> {
            return xform(arg, cb);
        });
    }

    default <T> CallbackTransform<In, T> transformCallback(CallbackTransform<Out, T> next) {
        return new CallbackTransform<In, T>() {
            @Override
            public <Ret> Ret xform(In in, Callback<T, Ret> receiver) {
                return CallbackTransform.this.xform(in, (Optional<String> err, Out intermediate) -> {
                    return receiver.ifNotFailed(err, () -> {
                        Ret result = next.xform(intermediate, receiver);
                        return result;
                    });
                });
            }
        };
    }
}

/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.misc.utils.function;

import java.util.Optional;

/**
 * Transforms the result of a callback into a different kind.
 *
 * @author Tim Boudreau
 */
public interface CallbackTransform<In, Out> {

    <Ret> Ret xform(In in, Callback<Out, Ret> receiver);

    default <Ret> Callback<In, Ret> transform(Callback<Out, Ret> cb) {
        assert cb != null : "callback is null";
        return (Optional<String> failure, In arg) -> cb.ifNotFailed(failure, () -> {
            return xform(arg, cb);
        });
    }

    @SuppressWarnings("Convert2Lambda")
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

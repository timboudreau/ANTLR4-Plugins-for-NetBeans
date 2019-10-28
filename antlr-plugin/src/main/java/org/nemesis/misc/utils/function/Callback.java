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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A little functional optional special sauce.
 *
 * @author Tim Boudreau
 */
public interface Callback<T, R> extends BiFunction<Optional<String>, T, R> {

    @Override
    public default R apply(Optional<String> t, T u) {
        return call(t, u);
    }


    R call(Optional<String> failure, T arg);

    static <T> Callback<T,T> returnOrThrow() {
        return (Optional<String> failure, T arg) -> {
            if (failure.isPresent()) {
                throw new IllegalStateException(failure.get());
            }
            return arg;
        };
    }

    static <T> Callback<T,T> nullable() {
        return (Optional<String> failure, T arg) -> {
            return arg;
        };
    }

    static <T> Callback<T,Optional<T>> optional() {
        return (Optional<String> failure, T arg) -> {
            if (failure.isPresent()) {
                Logger.getLogger(Callback.class.getName()).log(Level.INFO, failure.get());
            }
            return Optional.ofNullable(arg);
        };
    }

    default <S> Callback<S, R> from(T in, CallbackTransform<S, T> xform) {
        return new Callback<S, R>() {
            @Override
            public R call(Optional<String> failure, S arg) {

                return xform.xform(arg, Callback.this);
            }
        };
    }

    default <Y> Callback<R, Y> then(T arg, String ifNullMessage, Callback<R, Y> cb) {
        return (Optional<String> failure, R arg2) -> {
            if (failure.isPresent()) {
                return cb.call(failure, null);
            }
            R val = Callback.this.call(Optional.empty(), arg);
            return cb.failIfNull(ifNullMessage, val);
        };
    }

    default R fail(String s) {
        return call(Optional.of(s), null);
    }

    default R succeed(T arg) {
        assert arg != null : "arg is null";
        return call(Optional.empty(), arg);
    }

    default R ifNotFailed(Optional<String> err, Supplier<R> run) {
        assert run != null : "run is null";
        if (err.isPresent()) {
            return call(err, null);
        }
        return run.get();
    }

    default R ifTrue(Optional<String> err, String messageIfFalse, boolean val, T arg) {
        if (err.isPresent()) {
            return call(err, null);
        } else {
            if (val) {
                return call(Optional.empty(), arg);
            } else {
                return call(Optional.of(messageIfFalse), arg);
            }
        }
    }

    default R ifNoFailure(Optional<String> err, String ifSupplierReturnsNull, java.util.function.Supplier<T> supp) {
        if (err.isPresent()) {
            return call(err, null);
        } else {
            return failIfNull(ifSupplierReturnsNull, supp.get());
        }
    }

    default R failIfNull(String err, Object val, Supplier<R> cont) {
        if (val == null) {
            return call(Optional.of(err), null);
        } else {
            return cont.get();
        }
    }

    default R ifFileExists(Path path, Supplier<R> cont) {
        if (path == null || !Files.exists(path)) {
            return call(Optional.of("Does not exist: " + path), null);
        } else {
            return cont.get();
        }
    }


    default R failIfNull(String err, T val) {
        if (val == null) {
            return call(Optional.of(err), null);
        } else {
            return call(Optional.empty(), val);
        }
    }

    default R callOrDefault(T val, T def) {
        if (val != null) {
            return call(Optional.empty(), val);
        } else {
            return call(Optional.empty(), val);
        }
    }
}

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
package com.mastfrog.antlr.project.helpers.ant;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public class LambdaUtils {

    public static <T, R> R ifNotNull(Supplier<T> supp, Function<T, R> func) {
        return ifNotNull(null, supp, func);
    }

    public static <T, R> R ifNotNull(String logString, Supplier<T> supp, Function<T, R> func) {
        T obj = supp.get();
        if (obj != null) {
            return func.apply(obj);
        } else {
            System.out.println(logString);
        }
        return null;
    }

    public static <R> R ifNull(String logString, Supplier<?> supp, Supplier<R> res) {
        Object obj = supp.get();
        if (obj == null) {
            return res.get();
        } else {
            System.out.println(logString);
        }
        return null;
    }

    public static <R> R ifNull(Supplier<?> supp, Supplier<R> res) {
        Object obj = supp.get();
        if (obj == null) {
            return res.get();
        }
        return null;
    }

    public static <T, R> R ifPresent(String msg, Optional<T> opt, Function<T, R> func) {
        if (opt.isPresent()) {
            return func.apply(opt.get());
        } else {
            System.out.println(msg);
        }
        return null;
    }

    public static <T, R> R ifPresent(Optional<T> opt, Function<T, R> func) {
        if (opt.isPresent()) {
            return func.apply(opt.get());
        }
        return null;
    }

    public static <R> R ifNotPresent(String msg, Optional<?> opt, Supplier<R> supp) {
        if (opt.isPresent()) {
            return null;
        } else {
            System.out.println(msg);
        }
        return supp.get();
    }

    public static <R> R ifNotPresent(Optional<?> opt, Supplier<R> supp) {
        if (opt.isPresent()) {
            return null;
        }
        return supp.get();
    }

    public static <T, R> R ifNotPresentOr(Optional<T> opt, Predicate<? super T> test, Supplier<R> supp) {
        if (opt.isPresent() && !test.test(opt.get())) {
            return null;
        }
        return supp.get();
    }

    public static <T, R> R ifNotPresentOr(String msg, Optional<T> opt, Predicate<? super T> test, Supplier<R> supp) {
        if (opt.isPresent() && !test.test(opt.get())) {
            return null;
        }
        return supp.get();
    }

    public static <T> Result<T> with(Supplier<T> obj) {
        return Result.of(obj.get());
    }

    public static <R> Result<R> ifPresent(Optional<R> opt) {
        if (opt.isPresent()) {
            return Result.of(opt.get());
        } else {
            return Result.empty();
        }
    }

    public static class Result<T> implements Supplier<T> {

        private final T obj;

        public Result(T obj) {
            this.obj = obj;
        }

        static <T> Result<T> of(T obj) {
            return obj == null ? empty() : new Result<>(obj);
        }

        static final Result<Object> EMPTY = new Result(null);

        @SuppressWarnings("unchecked")
        public static <T> Result<T> empty() {
            return (Result<T>) EMPTY;
        }

        public Result<T> or(Supplier<T> alternateSupplier) {
            if (obj != null) {
                return this;
            }
            return of(alternateSupplier.get());
        }

        public <R> Result<R> ifNotNull(Function<T, R> func) {
            if (obj != null) {
                return of(func.apply(obj));
            }
            return empty();
        }

        public <R> Result<R> ifNotNull(Supplier<R> supp) {
            if (obj != null) {
                return of(supp.get());
            }
            return empty();
        }

        public <R> Result<R> ifNull(Supplier<R> supp) {
            return of(supp.get());
        }

        public Result<T> ifMatches(Predicate<T> test) {
            if (obj != null && test.test(obj)) {
                return this;
            }
            return empty();
        }

        public Result<T> ifNotMatching(Predicate<T> test) {
            if (obj == null) {
                return empty();
            }
            return test.test(obj) ? empty() : this;
        }

        public <R, S> Result<S> ifPresent(Optional<R> opt, BiFunction<T, R, S> func) {
            if (obj != null && opt.isPresent()) {
                return of(func.apply(obj, opt.get()));
            }
            return empty();
        }

        @Override
        public T get() {
            return obj;
        }

        public Optional<T> optional() {
            return obj == null ? Optional.empty() : Optional.of(obj);
        }
    }
}

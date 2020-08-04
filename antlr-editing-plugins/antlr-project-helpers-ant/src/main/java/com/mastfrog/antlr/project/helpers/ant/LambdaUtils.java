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

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class LambdaUtils {

    private static final Set<Thread> logEnabled = ConcurrentHashMap.newKeySet(10);

    public static <T> T withLoggingEnabledThrowing(ThrowingSupplier<T> r) throws Exception {
        logEnabled.add(Thread.currentThread());
        try {
            return r.get();
        } finally {
            logEnabled.remove(Thread.currentThread());
        }
    }

    public static <T> T withLoggingEnabled(Supplier<T> r) {
        logEnabled.add(Thread.currentThread());
        try {
            return r.get();
        } finally {
            logEnabled.remove(Thread.currentThread());
        }
    }

    static void log(String msg) {
        if (msg != null && logEnabled.contains(Thread.currentThread())) {
            System.out.println(msg); // println ok
        }
    }

    public static <T> Supplier<T> lkp(Lookup.Provider p, Class<T> type) {
        return () -> p.getLookup().lookup(type);
    }

    public static <T, R> R ifNotNull(Supplier<T> supp, Function<T, R> func) {
        return ifNotNull(null, supp, func);
    }

    public static <T, R> R ifNotNull(String logString, Supplier<T> supp, Function<T, R> func) {
        T obj = supp.get();
        if (obj != null) {
            return func.apply(obj);
        } else {
            log(logString);
        }
        return null;
    }

    public static <R> R ifTrue(String logString, BooleanSupplier supp, Supplier<R> func) {
        if (supp.getAsBoolean()) {
            return func.get();
        } else {
            log(logString);
        }
        return null;
    }

    public static <R> R ifEquals(Object expect, Supplier<?> compareTo, Supplier<R> func) {
        return ifTrue(() -> Objects.equals(expect, compareTo.get()), func);
    }

    public static <R> R ifEquals(String msg, Object expect, Supplier<?> compareTo, Supplier<R> func) {
        return ifTrue(msg, () -> Objects.equals(expect, compareTo.get()), func);
    }

    public static <R> R ifNotEquals(Object expect, Supplier<?> compareTo, Supplier<R> func) {
        return ifUntrue(() -> Objects.equals(expect, compareTo.get()), func);
    }

    public static <R> R ifNotEquals(String msg, Object expect, Supplier<?> compareTo, Supplier<R> func) {
        return ifUntrue(msg, () -> Objects.equals(expect, compareTo.get()), func);
    }

    public static <T, R> Map<T, R> map(Iterable<? extends R> coll, Function<? super R, ? extends T> xform) {
        Map<T, R> result = new HashMap<>();
        coll.forEach((R item) -> {
            T key = xform.apply(item);
            if (key != null) {
                result.put(key, item);
            }
        });
        return result;
    }

    public static <T, R> Map<T, R> map(Iterable<? extends R> coll, Predicate<? super T> test, Function<? super R, ? extends T> xform) {
        Map<T, R> result = new HashMap<>();
        coll.forEach((R item) -> {
            T key = xform.apply(item);
            if (key != null && test.test(key)) {
                result.put(key, item);
            }
        });
        return result;
    }

    public static <R> R ifTrue(BooleanSupplier supp, Supplier<R> func) {
        if (supp.getAsBoolean()) {
            return func.get();
        }
        return null;
    }

    public static <R> R ifTrue(String logString, boolean val, Supplier<R> func) {
        if (val) {
            return func.get();
        } else {
            log(logString);
        }
        return null;
    }

    public static <R> R ifTrue(boolean val, Supplier<R> func) {
        if (val) {
            return func.get();
        }
        return null;
    }

    public static <R> R ifUntrue(String logString, BooleanSupplier supp, Supplier<R> func) {
        if (!supp.getAsBoolean()) {
            return func.get();
        } else {
            log(logString);
        }
        return null;
    }

    public static <R> R ifUntrue(BooleanSupplier supp, Supplier<R> func) {
        if (!supp.getAsBoolean()) {
            return func.get();
        }
        return null;
    }

    public static <R> R ifUntrue(String logString, boolean val, Supplier<R> func) {
        if (!val) {
            return func.get();
        } else {
            log(logString);
        }
        return null;
    }

    public static <R> R ifUntrue(boolean val, Supplier<R> func) {
        if (!val) {
            return func.get();
        }
        return null;
    }

    public static <R> R ifNull(String logString, Supplier<?> supp, Supplier<R> res) {
        Object obj = supp.get();
        if (obj == null) {
            return res.get();
        } else {
            log(logString);
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
            log(msg);
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
            log(msg);
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
            log(msg);
            return null;
        }
        return supp.get();
    }

    public static <T> Result<T> with(Supplier<T> obj) {
        return Result.of(obj.get());
    }

    public static <T> Result<T> with(String msg, Supplier<T> obj) {
        return Result.of(msg, obj.get());
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

        static <T> Result<T> of(String msg, T obj) {
            if (obj == null) {
                log(msg);
                return empty();
            }
            return new Result<>(obj);
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

        public <R> Result<R> ifNotNull(String msg, Function<T, R> func) {
            if (obj != null) {
                return of(func.apply(obj));
            }
            log(msg);
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

        public Result<T> ifMatches(String msg, Predicate<T> test) {
            if (obj != null && test.test(obj)) {
                return this;
            }
            log(msg);
            return empty();
        }

        public Result<T> ifNotMatching(String msg, Predicate<T> test) {
            if (obj == null) {
                log(msg);
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

        public <R, S> Result<S> ifPresent(String msg, Optional<R> opt, BiFunction<T, R, S> func) {
            if (obj != null && opt.isPresent()) {
                return of(func.apply(obj, opt.get()));
            }
            log(msg);
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

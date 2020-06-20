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
package org.nemesis.misc.utils;

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingToIntFunction;
import com.mastfrog.function.throwing.io.IOConsumer;
import com.mastfrog.function.throwing.io.IOFunction;
import com.mastfrog.function.throwing.io.IORunnable;
import com.mastfrog.function.throwing.io.IOToIntFunction;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * A generic threadlocal cache for repeated calls to look up the same results
 * that may be expensive to compute.
 *
 * @author Tim Boudreau
 */
public class ThreadLocalCache<T, R> {
//    private final Map<T, R> cache = new HashMap<>();

    private final ThreadLocal<Map<T, R>> cache = new ThreadLocal<>();
    private final Function<T, R> valueSupplier;

    ThreadLocalCache(Function<T, R> valueSupplier) {
        this.valueSupplier = valueSupplier;
    }

    public static <T, R> ThreadLocalCache<T, R> create(Function<T, R> valueSupplier) {
        return new ThreadLocalCache<>(valueSupplier);
    }

    Map<T, R> enter() {
        Map<T, R> map = cache.get();
        if (map == null) {
            map = new HashMap<>();
            cache.set(map);
        }
        return map;
    }

    void exit() {
        cache.remove();
    }

    public void enter(Runnable r) {
        boolean firstEntry = cache.get() == null;
        enter();
        try {
            r.run();
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public void enterThrowing(ThrowingRunnable r) throws Exception {
        boolean firstEntry = cache.get() == null;
        enter();
        try {
            r.run();
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public void enterIO(IORunnable r) throws IOException {
        boolean firstEntry = cache.get() == null;
        enter();
        try {
            r.run();
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public void enterThrowing(T key, ThrowingConsumer<R> r) throws Exception {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            r.accept(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public void enterIO(T key, IOConsumer<R> r) throws IOException {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            r.accept(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public void enter(T key, Consumer<R> r) {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            r.accept(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public <S> S get(T key, Function<R, S> r) {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.apply(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public <S> S getThrowing(T key, ThrowingFunction<R, S> r) throws Exception {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.apply(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public <S> S getIO(T key, IOFunction<R, S> r) throws IOException {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.apply(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public boolean test(T key, Predicate<R> r) {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.test(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    /*
    pending release of mastfrog 2.6.11

    public boolean testThrowing(T key, ThrowingPredicate<R> r) throws Exception {
        boolean firstEntry = cache.get() == null;
        Map<T,R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.test(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public boolean testIO(IOPredicate r) throws IOException {
        boolean firstEntry = cache.get() == null;
        Map<T,R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.test(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }
     */
    public int getInt(T key, ToIntFunction<R> r) {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.applyAsInt(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public int getIntIO(T key, IOToIntFunction<R> r) throws IOException {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.applyAsInt(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }

    public int getIntThrowing(T key, ThrowingToIntFunction<R> r) throws Exception {
        boolean firstEntry = cache.get() == null;
        Map<T, R> map = enter();
        try {
            R value = map.get(key);
            if (value == null) {
                value = valueSupplier.apply(key);
                map.put(key, value);
            }
            return r.applyAsInt(value);
        } finally {
            if (firstEntry) {
                exit();
            }
        }
    }
}

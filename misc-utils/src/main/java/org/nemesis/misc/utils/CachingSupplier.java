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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Allows for caching the result of a supplier, for example, for static default
 * instances of things, without a bunch of complicated code to synchronize and
 * use of static volatile fields, and should be better performing with atomic
 * references which only spin during a write.
 *
 * @author Tim Boudreau
 */
public final class CachingSupplier<T> implements Supplier<T> {

    private final AtomicReference<Supplier<T>> ref;
    private final Supplier<T> creator;

    CachingSupplier(Supplier<T> creator) {
        this.creator = creator;
        ref = new AtomicReference<>(new ReplacingSupplier());
    }

    public static <T> Supplier<T> of(Supplier<T> creator) {
        return new CachingSupplier<>(creator);
    }

    public void discard() {
        ref.set(creator);
    }

    @Override
    public T get() {
        return ref.get().get();
    }

    class ReplacingSupplier implements Supplier<T> {

        @Override
        public synchronized T get() {
            T result = creator.get();
            if (ref.compareAndSet(this, new SingleSupplier<>(result))) {
                return result;
            }
            return ref.get().get();
        }
    }

    static final class SingleSupplier<T> implements Supplier<T> {

        private final T obj;

        public SingleSupplier(T obj) {
            this.obj = obj;
        }

        @Override
        public T get() {
            return obj;
        }
    }
}

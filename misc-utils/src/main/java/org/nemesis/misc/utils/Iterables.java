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

import java.util.Collection;
import java.util.Map;

/**
 * Utilities for working with iterable things.
 *
 * @author Tim Boudreau
 */
public final class Iterables {

    public static <T> void iterate(Collection<? extends T> set, SimpleIterationConsumer<T> ic) {
        iterate(set, (IterationConsumer<T>) ic);
    }

    public static <T> void iterate(Collection<? extends T> set, IterationConsumer<T> ic) {
        int max = set.size();
        int pos = 0;
        for (T t : set) {
            ic.accept(pos++, t, max);
        }
    }

    public static <T, R> void iterate(Map<T, R> map, IterationBiConsumer<T, R> ibc) {
        int max = map.size();
        int pos = 0;
        for (Map.Entry<T, R> e : map.entrySet()) {
            ibc.accept(pos++, e.getKey(), e.getValue(), max);
        }
    }

    public interface IterationConsumer<T> {

        void accept(int number, T item, int of);
    }

    public interface IterationBiConsumer<T, R> {

        void accept(int number, T key, R val, int of);
    }

    public interface SimpleIterationConsumer<T> extends IterationConsumer<T> {

        @Override
        public default void accept(int number, T item, int of) {
            accept(item, number == of - 1);
        }

        void accept(T item, boolean last);
    }

    private Iterables() {
        throw new AssertionError();
    }
}

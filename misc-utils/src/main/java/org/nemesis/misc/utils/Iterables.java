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

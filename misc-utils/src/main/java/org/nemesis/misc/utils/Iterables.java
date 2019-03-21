package org.nemesis.misc.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public final class Iterables {

    public static <T, R> Map<T, R> supplierMap(Supplier<R> supplier) {
        return new SupplierMap<>(supplier);
    }

    public static <T, R> Map<T, R> supplierMap(Supplier<R> supplier, Map<T, R> delegate) {
        return new SupplierMap<>(supplier, delegate);
    }

    public static <T> Iterable<T> combine(Iterable<Iterable<T>> iterables) {
        return new MergeIterables<>(iterables);
    }

    public static <T> Iterable<T> combined(Iterable<T> a, Iterable<T> b) {
        return new MergeIterables<>(a, b);
    }

    public static <T> Iterable<T> combined(Iterable<T> a, Iterable<T> b, Iterable<T> c) {
        return new MergeIterables<>(a, b, c);
    }

    public static <T> Iterable<T> combine(Iterable<T>... iterables) {
        return new MergeIterables<>(iterables);
    }

    public static <T, F> Iterable<T> convertIterable(Iterable<F> delegate, Function<F, T> converter) {
        return new ConvertIterable<>(converter, delegate);
    }

    public static <T, F> Iterator<T> convertIterator(Iterator<F> delegate, Function<F, T> converter) {
        return new ConvertIterable.ConvertIterator<>(converter, delegate);
    }

    public static <T> Iterable<T> toIterable(Supplier<Iterator<T>> iteratorSupplier) {
        return new IteratorIterable<>(iteratorSupplier);
    }

    public static <T> Iterable<T> toOneShotIterable(Iterator<T> iterator) {
        return new IteratorIterable<>(() -> {
            return iterator;
        });
    }

    public static <T, R> List<R> transform(List<? extends T> list, Function<T, R> xform) {
        List<R> result = list instanceof LinkedList<?> ? new LinkedList<>() : new ArrayList<>(list.size());
        for (T t : list) {
            result.add(xform.apply(t));
        }
        return result;
    }

    public static <T, R> Set<R> transform(Set<? extends T> list, Function<T, R> xform) {
        Set<R> result = list instanceof LinkedHashSet<?> || list instanceof TreeSet<?>
                ? new LinkedHashSet<>() : new HashSet<>();
        for (T t : list) {
            result.add(xform.apply(t));
        }
        return result;
    }

    public static <T> void iterate(Set<? extends T> set, IterationConsumer<T> ic) {
        int max = set.size();
        int pos = 0;
        for (T t : set) {
            ic.accept(pos++, t, max);
        }
    }

    public static <T> void iterate(List<? extends T> set, IterationConsumer<T> ic) {
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

    private static final class IteratorIterable<T> implements Iterable<T> {

        private final Supplier<Iterator<T>> supp;

        IteratorIterable(Supplier<Iterator<T>> supp) {
            this.supp = supp;
        }

        @Override
        public Iterator<T> iterator() {
            return supp.get();
        }
    }

    private Iterables() {
        throw new AssertionError();
    }
}

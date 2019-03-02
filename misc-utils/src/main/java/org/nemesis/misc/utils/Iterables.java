package org.nemesis.misc.utils;

import java.util.Iterator;
import java.util.Map;
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

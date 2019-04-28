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
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Utilities for working with iterable things.
 *
 * @author Tim Boudreau
 */
public final class Iterables {

    /**
     * Returns a map which, if an item not present is requested, will use the
     * passed Supplier to add one before returning it.
     *
     * @param <T> The key type
     * @param <R> The value type
     * @param supplier A supplier of missing objects
     * @return A map
     */
    public static <T, R> Map<T, R> supplierMap(Supplier<R> supplier) {
        return new SupplierMap<>(supplier);
    }

    /**
     * Returns a map which, if an item not present is requested, will use the
     * passed Supplier to add one before returning it, wrapping the original map
     * (which will be modified if new items are requested).
     *
     * @param <T> The key type
     * @param <R> The value type
     * @param supplier A supplier of missing objects
     * @return A map
     */
    public static <T, R> Map<T, R> supplierMap(Supplier<R> supplier, Map<T, R> delegate) {
        return new SupplierMap<>(supplier, delegate);
    }

    /**
     * Create a single Iterable which concatenates multiple iterables without
     * copying them into another collection, so iteration happens only once.
     *
     * @param <T> The iterable type
     * @param iterables A collection of iterables
     * @return An iterable
     */
    public static <T> Iterable<T> combine(Iterable<Iterable<T>> iterables) {
        return new MergeIterables<>(iterables);
    }

    /**
     * Create a single Iterable which concatenates multiple iterables without
     * copying them into another collection, so iteration happens only once.
     *
     * @param <T> The iterable type
     * @param iterables A collection of iterables
     * @return An iterable
     */
    public static <T> Iterable<T> combined(Iterable<T> a, Iterable<T> b) {
        return new MergeIterables<>(a, b);
    }

    /**
     * Create a single Iterable which concatenates multiple iterables without
     * copying them into another collection, so iteration happens only once.
     *
     * @param <T> The iterable type
     * @param iterables A collection of iterables
     * @return An iterable
     */
    public static <T> Iterable<T> combined(Iterable<T> a, Iterable<T> b, Iterable<T> c) {
        return new MergeIterables<>(a, b, c);
    }

    /**
     * Create a single Iterable which concatenates multiple iterables without
     * copying them into another collection, so iteration happens only once.
     *
     * @param <T> The iterable type
     * @param iterables A collection of iterables
     * @return An iterable
     */
    public static <T> Iterable<T> combine(Iterable<T>... iterables) {
        return new MergeIterables<>(iterables);
    }

    /**
     * Create an Iterable over another using the passed function to transform
     * objects from the initial iterable to the returned one.
     *
     * @param <T> The original type
     * @param <F> The transformed type
     * @param delegate The original iterable
     * @param converter The conversion function
     * @return An iterable
     */
    public static <T, F> Iterable<T> convertIterable(Iterable<F> delegate, Function<F, T> converter) {
        return new ConvertIterable<>(converter, delegate);
    }

    /**
     * Create an Iterator over another using the passed function to transform
     * objects from the initial iterable to the returned one.
     *
     * @param <T> The original type
     * @param <F> The transformed type
     * @param delegate The original iterable
     * @param converter The conversion function
     * @return An iterable
     */
    public static <T, F> Iterator<T> convertIterator(Iterator<F> delegate, Function<F, T> converter) {
        return new ConvertIterable.ConvertIterator<>(converter, delegate);
    }

    /**
     * Returns Iterables for a supplier of Iterator.
     *
     * @param <T>
     * @param iteratorSupplier
     * @return
     */
    public static <T> Iterable<T> toIterable(Supplier<Iterator<T>> iteratorSupplier) {
        return new IteratorIterable<>(iteratorSupplier);
    }

    /**
     * For use with enhanced for loop from APIs where you are handed an
     * iterator, create a one-shot iterable.
     *
     * @param <T> The iterated type
     * @param iteratorSupplier A supplier of iterators
     * @return An iterable
     */
    public static <T> Iterable<T> toOneShotIterable(Iterator<T> iterator) {
        return new IteratorIterable<>(() -> {
            return iterator;
        });
    }

    /**
     * Transform a list one one type into a list of another type using the
     * passed function.
     *
     * @param <T> The original type
     * @param <R> The revised type
     * @param list The original list
     * @param xform The transformer
     * @return A list
     */
    public static <T, R> List<R> transform(List<? extends T> list, Function<T, R> xform) {
        List<R> result = list instanceof LinkedList<?> ? new LinkedList<>() : new ArrayList<>(list.size());
        for (T t : list) {
            result.add(xform.apply(t));
        }
        return result;
    }

    /**
     * Transform a set one one type into a list of another type using the passed
     * function.
     *
     * @param <T> The original type
     * @param <R> The revised type
     * @param set The original set
     * @param xform The transformer
     * @return A list
     */
    public static <T, R> Set<R> transform(Set<? extends T> set, Function<T, R> xform) {
        Set<R> result = set instanceof LinkedHashSet<?> || set instanceof TreeSet<?>
                ? new LinkedHashSet<>() : new HashSet<>();
        for (T t : set) {
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

    public static <T> List<T> filter(List<? extends T> list, Predicate<? super T> filter) {
        List<T> result = new ArrayList<>(list.size());
        list.stream().filter((obj) -> (filter.test(obj))).forEachOrdered(result::add);
        return result;
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

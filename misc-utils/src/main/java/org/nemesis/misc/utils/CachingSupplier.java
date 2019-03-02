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

package org.nemesis.misc.utils;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public final class ThreadLocalUtils {

    private ThreadLocalUtils() {
        throw new AssertionError();
    }

    public static Consumer<IntConsumer> entryCounter() {
        return new ThreadLocalCounter();
    }

    public static <T> ThreadLocal<T> supplierThreadLocal(Supplier<? extends T> supp) {
        return new SupplierThreadLocal<>(supp);
    }
}

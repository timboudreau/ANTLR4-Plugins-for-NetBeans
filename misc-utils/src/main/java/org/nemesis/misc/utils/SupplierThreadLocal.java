package org.nemesis.misc.utils;

import java.util.function.Supplier;

/**
 * A ThreadLocal that uses a Supplier for its initial value.
 *
 * @author Tim Boudreau
 */
final class SupplierThreadLocal<T> extends ThreadLocal<T> {

    private final Supplier<? extends T> supplier;

    SupplierThreadLocal(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    @Override
    protected T initialValue() {
        return supplier.get();
    }
}

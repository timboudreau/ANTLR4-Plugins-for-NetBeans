package org.nemesis.misc.utils;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 *
 * @author Tim Boudreau
 */
final class ThreadLocalCounter implements Consumer<IntConsumer> {

    private final ThreadLocal<Integer> value = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    int value() {
        return value.get();
    }

    int increment() {
        int val = value.get();
        value.set(++val);
        return val;
    }

    void decrement() {
        int val = value.get() - 1;
        if (val == 0) {
            value.remove();
        } else {
            value.set(val);
        }
    }

    void run(IntConsumer c) {
        try {
            c.accept(increment());
        } finally {
            decrement();
        }
    }

    @Override
    public void accept(IntConsumer t) {
        run(t);
    }
}

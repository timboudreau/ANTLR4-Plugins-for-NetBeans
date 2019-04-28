package org.nemesis.misc.utils.function;

import java.util.concurrent.Callable;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ThrowingRunnable {

    default ThrowingRunnable andThen(ThrowingRunnable run) {
        return () -> {
            ThrowingRunnable.this.run();
            run.run();
        };
    }

    default ThrowingRunnable andThen(Runnable run) {
        return () -> {
            ThrowingRunnable.this.run();
            run.run();
        };
    }

    default ThrowingRunnable andThen(Callable<Void> run) {
        return () -> {
            ThrowingRunnable.this.run();
            run.call();
        };
    }

    void run() throws Exception;
}

package org.nemesis.misc.utils.function;

import java.io.IOException;

/**
 * Runnable which throws IOException.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface IORunnable {

    public void run() throws IOException;

    default IORunnable andThen(IORunnable next) {
        return () -> {
            IORunnable.this.run();
            next.run();
        };
    }

    default IORunnable andThen(Runnable next) {
        return () -> {
            IORunnable.this.run();
            next.run();
        };
    }
}

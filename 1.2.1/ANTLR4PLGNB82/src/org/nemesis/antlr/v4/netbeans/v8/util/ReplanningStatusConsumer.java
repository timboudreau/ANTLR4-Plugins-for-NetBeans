package org.nemesis.antlr.v4.netbeans.v8.util;

import java.util.Objects;
import java.util.function.Consumer;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Mutex;

/**
 * Wraps a Consumer&lt:String&gt; to replan status notifications
 * to the Swing event thread.
 *
 * @author Tim Boudreau
 */
public final class ReplanningStatusConsumer implements Consumer<String>, Runnable {

    volatile String data;
    private final Consumer<String> orig;
    private String lastNotified;

    public ReplanningStatusConsumer(Consumer<String> orig) {
        this.orig = orig;
    }

    /**
     * Wraps the passed consumer if non-null; otherwise creates a
     * consumer which routes messages to StatusDisplayer.getDefault().
     *
     * @param orig The original passed consumer or null
     * @return A consumer which either wraps the original or forwards
     * status messages to StatusDisplayer
     */
    public static Consumer<String> wrapConsumer(Consumer<String> orig) {
        if (orig == null) {
            return (st) -> {
                StatusDisplayer.getDefault().setStatusText(st);
            };
        } else {
            return new ReplanningStatusConsumer(orig);
        }
    }

    @Override
    public void accept(String t) {
        data = t;
        Mutex.EVENT.readAccess(this);
    }

    @Override
    public void run() {
        String d = data;
        if (d != null && !Objects.equals(lastNotified, d)) {
            lastNotified = d;
            orig.accept(d);
        }
    }
}

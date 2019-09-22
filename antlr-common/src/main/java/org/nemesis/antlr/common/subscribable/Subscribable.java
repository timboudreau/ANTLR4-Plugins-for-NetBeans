package org.nemesis.antlr.common.subscribable;

import java.util.function.BiConsumer;

/**
 *
 * @author Tim Boudreau
 */
public interface Subscribable<T, R> {

    void subscribe(T key, BiConsumer<T, R> jobOutput);
}

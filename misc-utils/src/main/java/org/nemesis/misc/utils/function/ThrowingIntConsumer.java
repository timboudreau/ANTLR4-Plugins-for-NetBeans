package org.nemesis.misc.utils.function;

/**
 * Throwing version of IntConsumer.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ThrowingIntConsumer {

    void consume(int val) throws Exception;
}

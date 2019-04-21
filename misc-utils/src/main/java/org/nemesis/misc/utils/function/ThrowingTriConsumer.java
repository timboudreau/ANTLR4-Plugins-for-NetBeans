package org.nemesis.misc.utils.function;

/**
 * Three parameter variant on ThrowingBiFunction.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ThrowingTriConsumer<A, B, C> {

    void accept(A a, B b, C c) throws Exception;
}

package org.nemesis.misc.utils.function;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ThrowingBiFunction<InA, InB, Out> {

    Out apply(InA a, InB b);
}

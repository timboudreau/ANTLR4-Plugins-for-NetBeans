package org.nemesis.data.graph.bits;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface DoubleLongFunction {

    double apply(long value);
}

package org.nemesis.range;

/**
 * Coalesces two overlapping ranges.
 *
 * @author Tim Boudreau
 */
public interface Coalescer<R extends Range<? extends R>> {

    /**
     * Create a range with a new start and size which combines attributes of
     * others.
     *
     * @param a One range
     * @param b Another range
     * @param start The start location
     * @param size The size
     * @return A combination range
     */
    R combine(R a, R b, int start, int size);

    default R resized(R orig, int start, int size) {
        return orig.newRange(start, size);
    }
}

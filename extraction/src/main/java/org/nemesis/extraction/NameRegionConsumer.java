package org.nemesis.extraction;

/**
 *
 * @author Tim Boudreau
 */
public interface NameRegionConsumer<K extends Enum<K>> {

    void accept(int start, int end, String name, K kind);

    /**
     * Alternate accept method that takes a null kind,
     * used when constructing reference sets, where the
     * kind is (usually) unknown.
     *
     * @param start The start
     * @param end The end
     * @param name The name
     */
    default void accept(int start, int end, String name) {
        accept(start, end, name, null);
    }

}

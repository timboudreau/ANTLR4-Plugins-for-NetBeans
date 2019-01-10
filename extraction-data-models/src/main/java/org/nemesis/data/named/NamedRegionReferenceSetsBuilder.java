package org.nemesis.data.named;

/**
 *
 * @author Tim Boudreau
 */
public abstract class NamedRegionReferenceSetsBuilder<K extends Enum<K>> {

    NamedRegionReferenceSetsBuilder() {
    }

    public abstract void addReference(String name, int start, int end);

    public abstract NamedRegionReferenceSets<K> build();

}

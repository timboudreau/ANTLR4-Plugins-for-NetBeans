package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named;

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

package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named;

/**
 * A region which references a name defined elsewhere within the smae document and
 * is able to dereference the location of that definition.
 *
 * @author Tim Boudreau
 */
public interface NamedSemanticRegionReference<K extends Enum<K>> extends NamedSemanticRegion<K> {

    /**
     * The item being referenced.
     *
     * @return
     */
    public NamedSemanticRegion<K> referencing();

    /**
     * The index of the referenced item within its owning NamedSemanticRegions.
     *
     * @return An index
     */
    public int referencedIndex();
}

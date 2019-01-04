package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named;

/**
 *
 * @author Tim Boudreau
 */
public interface NamedSemanticRegionPositionIndex<K extends Enum<K>> extends Iterable<NamedSemanticRegion<K>> {

    public NamedSemanticRegion<K> regionAt(int ix);

    public NamedSemanticRegion<K> withStart(int start);

    public NamedSemanticRegion<K> withEnd(int end);

}

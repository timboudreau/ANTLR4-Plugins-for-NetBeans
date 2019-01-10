package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import org.nemesis.data.graph.BitSetStringGraph;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.NamedRegionKey;
import org.nemesis.data.SemanticRegions;

/**
 *
 * @author Tim Boudreau
 */
interface NameInfoStore {

    <T extends Enum<T>> void addNamedRegions(NamedRegionKey<T> key, NamedSemanticRegions<T> regions);

    <T extends Enum<T>> void addReferences(NameReferenceSetKey<T> key, NamedRegionReferenceSets<T> regions);

    <T extends Enum<T>> void addReferenceGraph(NameReferenceSetKey<T> refSetKey, BitSetStringGraph stringGraph);

    <T extends Enum<T>> void addUnknownReferences(NameReferenceSetKey<T> refSetKey, SemanticRegions<UnknownNameReference<T>> build);

    <T extends Enum<T>> void addDuplicateNamedRegions(NamedRegionKey<T> key, String name, Iterable<? extends NamedSemanticRegion<T>> duplicates);

}

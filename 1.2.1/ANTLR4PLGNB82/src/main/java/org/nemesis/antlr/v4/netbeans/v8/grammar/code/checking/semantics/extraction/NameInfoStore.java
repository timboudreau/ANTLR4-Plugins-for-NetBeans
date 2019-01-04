package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.SemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.graph.BitSetStringGraph;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedRegionReferenceSets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.NamedRegionKey;

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

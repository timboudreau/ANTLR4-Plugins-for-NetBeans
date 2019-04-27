package org.nemesis.extraction;

import org.nemesis.data.SemanticRegions;
import org.nemesis.graph.StringGraph;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.netbeans.api.annotations.common.NullAllowed;

/**
 *
 * @author Tim Boudreau
 */
interface NameInfoStore {

    <T extends Enum<T>> void addNamedRegions(@NullAllowed String scopingDelimiter, NamedRegionKey<T> key, NamedSemanticRegions<T> regions);

    <T extends Enum<T>> void addReferences(NameReferenceSetKey<T> key, NamedRegionReferenceSets<T> regions);

    <T extends Enum<T>> void addReferenceGraph(NameReferenceSetKey<T> refSetKey, StringGraph stringGraph);

    <T extends Enum<T>> void addUnknownReferences(NameReferenceSetKey<T> refSetKey, SemanticRegions<UnknownNameReference<T>> build);

    <T extends Enum<T>> void addDuplicateNamedRegions(NamedRegionKey<T> key, String name, Iterable<? extends NamedSemanticRegion<T>> duplicates);

}

/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.extraction;

import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import com.mastfrog.graph.StringGraph;
import org.nemesis.data.named.ContentsChecksums;
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

    <T extends Enum<T>> void addNameAndBoundsKeyPair(NameAndBoundsPair<T> pair);

    <T extends Enum<T>> void addChecksums(NamedRegionKey<T> key, ContentsChecksums<NamedSemanticRegion<T>> checksums);
}

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
package org.nemesis.data.named;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.nemesis.data.IndexAddressable;

/**
 * A set of sets of references to different names within a
 * {@link NamedSemanticRegions}. Essentially: A NamedSemanticRegions contains a
 * set of named regions within a file or document. The file or document may also
 * contain usages of the name of a region. This data structure represents
 * regions of the same file which are usages or pointers back to a named region.
 *
 * @author Tim Boudreau
 */
public interface NamedRegionReferenceSets<K extends Enum<K>> extends Iterable<NamedRegionReferenceSet<K>>, Serializable, IndexAddressable.NamedIndexAddressable<NamedSemanticRegionReference<K>> {

    NamedRegionReferenceSet<K> references(String name);

    NamedSemanticRegionReference<K> itemAt(int pos);

    Set<String> collectNames(Predicate<NamedSemanticRegionReference<K>> pred);

    NamedSemanticRegions<K> originals();

    static <Q extends Enum<Q>> NamedRegionReferenceSets<Q> empty() {
        return empty(NamedSemanticRegions.<Q>empty());
    }

    static <Q extends Enum<Q>> NamedRegionReferenceSets<Q> empty(NamedSemanticRegions<Q> owner) {
        return new EmptyNamedRegionReferenceSets<>(owner);
    }

    default void collectItems(List<? super NamedSemanticRegionReference<K>> into) {
        for (NamedRegionReferenceSet<K> refs : this) {
            for (NamedSemanticRegionReference<K> item : refs) {
                into.add(item);
            }
        }
    }
}

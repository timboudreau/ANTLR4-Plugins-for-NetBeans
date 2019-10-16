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

import java.util.Objects;
import org.nemesis.data.IndexAddressable;
import com.mastfrog.abstractions.Named;

/**
 * A flyweight object which encapsulates one region in a
 * NamedSemanticRegions. Comparable on its start() and end() positions.
 */
public interface NamedSemanticRegion<K extends Enum<K>> extends IndexAddressable.IndexAddressableItem, Named {

    /**
     * Get the assigned "kind" of this region.
     *
     * @return The region's kind
     */
    K kind();

    /**
     * Get the position, in the total order of named regions in the owning
     * NamedSemanticRegions, of this one, based on its start and end
     * positions (this operation may be expensive).
     *
     * @return The position of this item relative to its siblings when
     * sorted by its start position
     */
    int ordering();

    /**
     * If true, this item represents a <i>reference</i> to another item (if
     * it returns true, this item belongs to a NamedRegionsReferenceSet).
     *
     * @return
     */
    boolean isReference();

    /**
     * Determine if this item is a reference to an item in a different
     * NamedSemanticRegionsobject.
     *
     * @return
     */
    default boolean isForeign() {
        return false;
    }

    /**
     * Creates a snapshot of this region, which does not hold a reference to
     * its owner or any of its arrays, for use in ElementHandles and other
     * objects which may exist long after this semantic region is defunct.
     *
     * @return
     */
    @Override
    default NamedSemanticRegion<K> snapshot() {
        return new NamedSemanticRegionSnapshot<>(this);
    }

    /**
     * Determine if the bounds and name of another item exactly match this
     * one, without a full equality test.
     *
     * @param item Another item
     * @return true if the item represents the same name, start and end
     */
    default boolean boundsAndNameEquals(NamedSemanticRegion<?> item) {
        return start() == item.start() && end() == item.end() && Objects.equals(name(), item.name());
    }

}

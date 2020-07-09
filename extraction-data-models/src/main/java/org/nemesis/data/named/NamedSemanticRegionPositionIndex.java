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

/**
 * Position-based index into a NamedSemanticRegions, allowing lookup based on
 * start position, end position or containment of a position.
 *
 * @author Tim Boudreau
 */
public interface NamedSemanticRegionPositionIndex<K extends Enum<K>> extends Iterable<NamedSemanticRegion<K>> {

    /**
     * Get the region by sort order.
     *
     * @param ix The sort order starting from 0
     * @return A region or null
     */
    public NamedSemanticRegion<K> regionAt(int ix);

    /**
     * Get the region, if any, with the passed start position.
     *
     * @param start A starting position
     * @return A region or null
     */
    public NamedSemanticRegion<K> withStart(int start);

    /**
     * Get the region, if any, with the passed end position.
     *
     * @param start A starting position
     * @return A region or null
     */
    public NamedSemanticRegion<K> withEnd(int end);

    /**
     * Get first region by position, if any.
     *
     * @param start A starting position
     * @return A region or null
     */
    public NamedSemanticRegion<K> first();

    /**
     * Find the nearest item whose start is less than or equal to the passed
     * position, whether or not that position is contained within the returned
     * region.
     *
     * @param position A position >= 0
     * @return A region, if any such exists
     */
    public NamedSemanticRegion<K> nearestPreceding(int position);
}

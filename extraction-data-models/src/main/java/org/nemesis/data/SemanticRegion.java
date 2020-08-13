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
package org.nemesis.data;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A semantic region associated with some data, which has a start
 * (inclusive) and end (exclusive). May contain nested child regions.
 * <p>
 * The iterator returned by the iterator() method iterates all direct and
 * indirect children of this region.
 *
 * @param <T> The data type, returned from the key() method
 */
public interface SemanticRegion<T> extends IndexAddressable.IndexAddressableItem, Iterable<SemanticRegion<T>> {

    /**
     * Get the data associated with this semantic region.
     *
     * @return
     */
    T key();

    /**
     * Get the parent region, if any.
     *
     * @return A region or null
     */
    SemanticRegion<T> parent();

    /**
     * Get the outermost ancestor of this region
     *
     * @return A region or null
     */
    SemanticRegion<T> outermost();

    default List<SemanticRegion<T>> parents() {
        List<SemanticRegion<T>> result = new LinkedList<>();
        SemanticRegion<T> p = parent();
        while (p != null) {
            result.add(p);
            p = p.parent();
        }
        return result;
    }

    /**
     * Get the index of this region relative to its sibling children of its
     * immediate parent, or -1 if no parent.
     *
     * @return The index of this child in its parent's immediate children
     */
    default int childIndex() {
        // XXX this is expensive
        SemanticRegion<T> parent = parent();
        if (parent != null) {
            return parent.children().indexOf(this);
        }
        return -1;
    }

    /**
     * Determine if this region has any nested child regions.
     *
     * @return true if there are nested regions
     */
    default boolean hasChildren() {
        return iterator().hasNext();
    }

    /**
     * Get all children or childrens' children of this region.
     *
     * @return A list
     */
    default List<SemanticRegion<T>> allChildren() {
        List<SemanticRegion<T>> result = new LinkedList<>();
        for (SemanticRegion<T> r : this) {
            result.add(r);
        }
        return result;
    }

    /**
     * Get all direct children of this region.
     *
     * @return A list
     */
    default List<SemanticRegion<T>> children() {
        List<SemanticRegion<T>> result = new LinkedList<>();
        for (SemanticRegion<T> r : this) {
            if (equals(r.parent())) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * Fetch the keys for this position, in order from shallowest to
     * deepest, into the passed list.
     *
     * @param pos The position
     * @param keys A list of keys
     */
    default void keysAtPoint(int pos, Collection<? super T> keys) {
        if (contains(pos)) {
            keys.add(key());
            for (SemanticRegion<T> child : this) {
                if (child.contains(pos)) {
                    keys.add(child.key());
                    //                        child.keysAtPoint(pos, keys);
                }
            }
        }
    }

    default void regionsAtPoint(int pos, List<? super SemanticRegion<T>> regions) {
        if (contains(pos)) {
            for (SemanticRegion<T> child : this) {
                if (child.contains(pos)) {
                    regions.add(child);
                    //                        child.regionsAtPoint(pos, regions);
                }
            }
        }
    }

    @Override
    default boolean contains(int pos) {
        return pos >= start() && pos < end();
    }

    /**
     * Get the nesting depth of this region, 0 being outermost.
     *
     * @return The depth
     */
    default int nestingDepth() {
        return 0;
    }

    @Override
    default int size() {
        return end() - start();
    }

    default int last() {
        return end() - 1;
    }

}

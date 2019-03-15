/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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

    default int size() {
        return end() - start();
    }

    default int last() {
        return end() - 1;
    }

}

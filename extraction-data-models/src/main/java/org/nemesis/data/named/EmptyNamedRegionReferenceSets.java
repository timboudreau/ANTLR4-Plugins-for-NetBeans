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

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
final class EmptyNamedRegionReferenceSets<K extends Enum<K>> implements NamedRegionReferenceSets<K> {

    private final NamedSemanticRegions<K> owner;

    EmptyNamedRegionReferenceSets(NamedSemanticRegions<K> owner) {
        this.owner = owner;
    }

    @Override
    public NamedRegionReferenceSet<K> references(String name) {
        return new EmptyRS<>();
    }

    @Override
    public NamedSemanticRegionReference<K> itemAt(int pos) {
        return null;
    }

    @Override
    public Set<String> collectNames(Predicate<NamedSemanticRegionReference<K>> pred) {
        return Collections.emptySet();
    }

    @Override
    public Iterator<NamedRegionReferenceSet<K>> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<NamedSemanticRegionReference<K>> byPositionIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public NamedSemanticRegionReference<K> at(int position) {
        return null;
    }

    @Override
    public boolean isChildType(IndexAddressableItem item) {
        return false;
    }

    @Override
    public int indexOf(Object o) {
        return -1;
    }

    @Override
    public NamedSemanticRegionReference<K> forIndex(int index) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public NamedSemanticRegions<K> originals() {
        return owner;
    }

    static class EmptyRS<K extends Enum<K>> implements NamedRegionReferenceSet<K> {

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean contains(int pos) {
            return false;
        }

        @Override
        public String name() {
            return "_";
        }

        @Override
        public NamedSemanticRegionReference<K> at(int pos) {
            return null;
        }

        @Override
        public NamedSemanticRegion<K> original() {
            return null;
        }

        @Override
        public Iterator<NamedSemanticRegionReference<K>> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public Iterator<NamedSemanticRegionReference<K>> byPositionIterator() {
            return Collections.emptyIterator();
        }

        @Override
        public boolean isChildType(IndexAddressableItem item) {
            return false;
        }

        @Override
        public int indexOf(Object o) {
            return -1;
        }

        @Override
        public NamedSemanticRegionReference<K> forIndex(int index) {
            return null;
        }
    }

}

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

import org.nemesis.data.IndexAddressable;

/**
 * A region which references a name defined elsewhere within the smae document
 * and is able to dereference the location of that definition.
 *
 * @author Tim Boudreau
 */
public interface NamedSemanticRegionReference<K extends Enum<K>> extends NamedSemanticRegion<K> {

    /**
     * The item being referenced.
     *
     * @return
     */
    public NamedSemanticRegion<K> referencing();

    /**
     * The index of the referenced item within its owning NamedSemanticRegions.
     *
     * @return An index
     */
    public int referencedIndex();

    public NamedSemanticRegions<K> ownedBy();

    @Override
    default NamedSemanticRegionReference<K> withStart(int newStart) {
        return new NamedSemanticRegionReference<K>() {
            @Override
            public K kind() {
                return NamedSemanticRegionReference.this.kind();
            }

            @Override
            public int ordering() {
                return NamedSemanticRegionReference.this.ordering();
            }

            @Override
            public boolean isReference() {
                return NamedSemanticRegionReference.this.isReference();
            }

            @Override
            public int index() {
                return NamedSemanticRegionReference.this.index();
            }

            @Override
            public int start() {
                return newStart;
            }

            @Override
            public String name() {
                return NamedSemanticRegionReference.this.name();
            }

            @Override
            public int end() {
                int result = NamedSemanticRegionReference.this.end();
                if (result < newStart) {
                    result = newStart;
                }
                return result;
            }

            @Override
            public int size() {
                return Math.max(0, NamedSemanticRegionReference.this.end() - newStart);
            }

            @Override
            public NamedSemanticRegion<K> referencing() {
                return NamedSemanticRegionReference.this.referencing();
            }

            @Override
            public int referencedIndex() {
                return NamedSemanticRegionReference.this.referencedIndex();
            }

            @Override
            public NamedSemanticRegions<K> ownedBy() {
                return NamedSemanticRegionReference.this.ownedBy();
            }
        };
    }

    @Override
    default NamedSemanticRegionReference<K> withEnd(int end) {
        return new NamedSemanticRegionReference<K>() {
            @Override
            public K kind() {
                return NamedSemanticRegionReference.this.kind();
            }

            @Override
            public int ordering() {
                return NamedSemanticRegionReference.this.ordering();
            }

            @Override
            public boolean isReference() {
                return NamedSemanticRegionReference.this.isReference();
            }

            @Override
            public int index() {
                return NamedSemanticRegionReference.this.index();
            }

            @Override
            public int start() {
                int result = NamedSemanticRegionReference.this.start();
                if (result > end) {
                    return end;
                }
                return result;
            }

            @Override
            public String name() {
                return NamedSemanticRegionReference.this.name();
            }

            @Override
            public int end() {
                return end;
            }

            @Override
            public NamedSemanticRegion<K> referencing() {
                return NamedSemanticRegionReference.this.referencing();
            }

            @Override
            public int referencedIndex() {
                return NamedSemanticRegionReference.this.referencedIndex();
            }

            @Override
            public NamedSemanticRegions<K> ownedBy() {
                return NamedSemanticRegionReference.this.ownedBy();
            }
        };
    }

    @Override
    default NamedSemanticRegion<K> withStart(long start) {
        return withStart((int) start);
    }

    @Override
    default IndexAddressable.IndexAddressableItem withEnd(long end) {
        return withEnd((int) end);
    }

}

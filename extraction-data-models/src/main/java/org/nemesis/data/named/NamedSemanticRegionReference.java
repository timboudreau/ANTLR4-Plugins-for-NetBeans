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
}

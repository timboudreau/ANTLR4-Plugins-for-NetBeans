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
import org.nemesis.data.IndexAddressable;

/**
 * A set of references to a particular name in a {@link NamedSemanticRegions}.
 * This is a secondary collection which typically shares some internal state
 * with the original, and contains pairs of offsets within the file which have
 * been added as <i>references to</i> or <i>usages of</i> a particular name defined in
 * that file.  They belong to a {@link NamedRegionReferenceSets}.
 *
 * @author Tim Boudreau
 */
public interface NamedRegionReferenceSet<K extends Enum<K>> extends Iterable<NamedSemanticRegionReference<K>>, Serializable, IndexAddressable.NamedIndexAddressable<NamedSemanticRegionReference<K>> {

    boolean contains(int pos);

    String name();

    NamedSemanticRegionReference<K> at(int pos);

    NamedSemanticRegion<K> original();

    default void collectItems(List<? super NamedSemanticRegionReference<K>> into) {
        for (NamedSemanticRegionReference<K> item : this) {
            into.add(item);
        }
    }

}

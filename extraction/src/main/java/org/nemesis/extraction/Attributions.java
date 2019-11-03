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

import org.nemesis.data.IndexAddressable;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;

/**
 * Object returned when you resolve unknown references on an Extraction.
 *
 * @author Tim Boudreau
 */
public final class Attributions<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> {

    private final SemanticRegions<AttributedForeignNameReference<R, I, N, T>> resolved;
    private final SemanticRegions<UnknownNameReference> unresolved;

    Attributions(SemanticRegions<AttributedForeignNameReference<R, I, N, T>> resolved, SemanticRegions<UnknownNameReference> unresolved) {
        this.resolved = resolved == null ? SemanticRegions.empty() : resolved;
        this.unresolved = unresolved == null ? SemanticRegions.empty() : unresolved;
    }

    public boolean hasResolved() {
        return resolved != null && !resolved.isEmpty();
    }

    public boolean hasUnresolved() {
        return unresolved != null && unresolved.isEmpty();
    }

    @Override
    public String toString() {
        return "Attributions(resolved=" + resolved + ", unresolved="
                + unresolved + ")";
    }

    /**
     * Get the set of all regions which were successfully attributed (they were
     * references to names defined in other files which have been identified and
     * can be retrieved here).
     *
     * @return A set of regions containing names which did not correspond to
     * names found in the current file, but could be mapped to others using
     * whatever strategy was previded to the extraction.
     */
    public SemanticRegions<AttributedForeignNameReference<R, I, N, T>> attributed() {
        return resolved;
    }

    /**
     * Returns those unknown name references which could not be resolved by
     * whatever strategy was provided to the extraction.
     *
     * @return A set of regions which is hopefully empty
     */
    public SemanticRegions<UnknownNameReference> remainingUnattributed() {
        return unresolved;
    }
}

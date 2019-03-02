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
        this.resolved = resolved;
        this.unresolved = unresolved;
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

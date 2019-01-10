package org.nemesis.data.named;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.named.NamedRegionReferenceSets.NamedRegionReferenceSet;

/**
 *
 * @author Tim Boudreau
 */
public interface NamedRegionReferenceSets<K extends Enum<K>> extends Iterable<NamedRegionReferenceSet<K>>, Serializable, IndexAddressable.NamedIndexAddressable<NamedSemanticRegionReference<K>> {

    NamedRegionReferenceSet<K> references(String name);

    NamedSemanticRegionReference<K> itemAt(int pos);

    Set<String> collectNames(Predicate<NamedSemanticRegionReference<K>> pred);

    static <Q extends Enum<Q>> NamedRegionReferenceSets<Q> empty() {
        return new EmptyNamedRegionReferenceSets<>();
    }

    default void collectItems(List<? super NamedSemanticRegionReference<K>> into) {
        for (NamedRegionReferenceSet<K> refs : this) {
            for (NamedSemanticRegionReference<K> item : refs) {
                into.add(item);
            }
        }
    }

    public static interface NamedRegionReferenceSet<K extends Enum<K>> extends Iterable<NamedSemanticRegionReference<K>>, Serializable, NamedIndexAddressable<NamedSemanticRegionReference<K>> {

        int size();

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
}

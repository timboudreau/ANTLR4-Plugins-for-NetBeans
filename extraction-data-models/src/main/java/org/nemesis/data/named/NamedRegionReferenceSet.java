package org.nemesis.data.named;

import java.io.Serializable;
import java.util.List;
import org.nemesis.data.IndexAddressable;

/**
 * A set of references to a particular name in a {@link NamedSemanticRegions}.
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

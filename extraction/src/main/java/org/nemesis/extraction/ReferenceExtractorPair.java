package org.nemesis.extraction;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;
import org.nemesis.extraction.key.NameReferenceSetKey;

/**
 *
 * @author Tim Boudreau
 */
class ReferenceExtractorPair<T extends Enum<T>> implements Hashable {

    final NameReferenceSetKey<T> refSetKey;
    final Set<ReferenceExtractionStrategy<?, ?>> referenceExtractors;

    ReferenceExtractorPair(Set<ReferenceExtractionStrategy<?, ?>> referenceExtractors, NameReferenceSetKey<T> refSetKey) {
        this.refSetKey = refSetKey;
        this.referenceExtractors = new HashSet<>(referenceExtractors);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ReferenceExtractorPair.class.getName()).append('@').append(System.identityHashCode(this));
        sb.append(':').append(refSetKey).append('<');
        Iterator<ReferenceExtractionStrategy<?, ?>> it;
        for (it=referenceExtractors.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.append('>').toString();
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.hashObject(refSetKey);
        for (ReferenceExtractionStrategy<?, ?> e : referenceExtractors) {
            hasher.hashObject(e);
        }
    }
}

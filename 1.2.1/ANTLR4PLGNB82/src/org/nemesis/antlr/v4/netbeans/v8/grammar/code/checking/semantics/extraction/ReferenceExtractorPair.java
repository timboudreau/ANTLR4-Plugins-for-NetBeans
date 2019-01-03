package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.HashSet;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Hashable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key.NameReferenceSetKey;

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

    @Override
    public void hashInto(Hasher hasher) {
        hasher.hashObject(refSetKey);
        for (ReferenceExtractionStrategy<?, ?> e : referenceExtractors) {
            hasher.hashObject(e);
        }
    }
}

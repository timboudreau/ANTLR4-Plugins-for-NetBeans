package org.nemesis.extraction;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;

/**
 * Attributes unknown name references found a source file by (probably) looking
 * up other related source files, parsing them and finding the unknown
 * references in them, and returning an extraction for them.
 *
 * @author Tim Boudreau
 */
public interface UnknownNameReferenceResolver<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> {

    <X> X resolve(Extraction extraction, UnknownNameReference<T> ref, ResolutionConsumer<R, I, N, T, X> c) throws IOException;

    default <X> Map<UnknownNameReference<T>, X> resolveAll(Extraction extraction,
            SemanticRegions<UnknownNameReference<T>> refs,
            ResolutionConsumer<R, I, N, T, X> c) throws IOException {
        Map<UnknownNameReference<T>, X> result = new HashMap<>();
        for (SemanticRegion<UnknownNameReference<T>> unk : refs) {
            X item = resolve(extraction, unk.key(), c);
            if (item != null) {
                result.put(unk.key(), item);
            }
        }
        return result;
    }

    Class<T> type();
}

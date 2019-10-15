package org.nemesis.extraction;

import java.io.IOException;
import java.io.Serializable;
import org.nemesis.data.IndexAddressable;
import com.mastfrog.abstractions.Named;
import org.nemesis.data.named.NamedSemanticRegion;

/**
 *
 * @author Tim Boudreau
 */
public interface UnknownNameReference<T extends Enum<T>> extends Named, IndexAddressable.IndexAddressableItem, Serializable {

    T expectedKind();

    default Class<T> kindType() {
        return expectedKind().getDeclaringClass();
    }

    default <R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>> AttributedForeignNameReference<R, I, N, T>
            resolve(Extraction extraction, UnknownNameReferenceResolver<R, I, N, T> resolver) throws IOException {
        ResolutionConsumer<R, I, N, T, AttributedForeignNameReference<R, I, N, T>> cons
                = (UnknownNameReference<T> unknown, R resolutionSource, I in, N element, Extraction target)
                -> new AttributedForeignNameReference<>(unknown, resolutionSource, in, element, extraction, target);
        return resolver.resolve(extraction, this, cons);
    }
}

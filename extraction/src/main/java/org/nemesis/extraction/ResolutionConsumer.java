package org.nemesis.extraction;

import org.nemesis.data.IndexAddressable;
import org.nemesis.data.named.NamedSemanticRegion;

/**
 *
 * @author Tim Boudreau
 */
public interface ResolutionConsumer<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>, X> {

    X resolved(UnknownNameReference<T> unknown, R resolutionSource, I in, N element, Extraction target);

}

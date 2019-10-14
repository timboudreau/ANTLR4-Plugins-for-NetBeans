package org.nemesis.extraction.attribution;

import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.UnknownNameReferenceResolver;
import org.nemesis.source.api.GrammarSource;

/**
 * A resolver for unknown names which can be registered against a mime
 * type and key type, and will be used to attribute references to names
 * which do not reference names in the origin source.
 *
 * @author Tim Boudreau
 */
public interface RegisterableResolver<K extends Enum<K>> extends UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K> {

    /**
     * Should return this, cast as a resolver of the requested type,
     * if type() == the passed type; null otherwise.
     *
     * @param <E> The type queried
     * @param type The type queried
     * @return this or null
     */
    default<E extends Enum<E>> RegisterableResolver<K> ifMatches(Class<E> type) {
        if (type == type()) {
            return (RegisterableResolver<K>) this;
        }
        return null;
    }
}

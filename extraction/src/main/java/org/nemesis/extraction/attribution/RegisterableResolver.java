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
package org.nemesis.extraction.attribution;

import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.UnknownNameReferenceResolver;
import org.nemesis.source.api.GrammarSource;

/**
 * A resolver for unknown names which can be registered against a mime type and
 * key type, and will be used to attribute references to names which do not
 * reference names in the origin source.  Register them in named lookup based
 * on the mime type, e.g. <code>antlr/resolvers/text/x-foo</code>.
 *
 * @author Tim Boudreau
 */
public interface RegisterableResolver<K extends Enum<K>> extends
        UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K> {

    /**
     * Should return this, cast as a resolver of the requested type, if type()
     * == the passed type; null otherwise.
     *
     * @param <E> The type queried
     * @param type The type queried
     * @return this or null
     */
    default <E extends Enum<E>> RegisterableResolver<K> ifMatches(Class<E> type) {
        if (type == type()) {
            return (RegisterableResolver<K>) this;
        }
        return null;
    }
}

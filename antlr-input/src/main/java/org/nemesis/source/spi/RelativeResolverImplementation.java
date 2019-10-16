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
package org.nemesis.source.spi;

import java.io.Serializable;
import java.util.Optional;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.impl.RRAccessor;

/**
 * SPI for RelativeResolver. These are registered by MIME type and looked up
 * using Lookups.forPath, and allow neighbors / imports to be looked up.
 * Registration path is <code>antlr-languages/relative-resolvers</code> plus the
 * mime type, e.g. <code>antlr-languages/relative-resolvers/text/x-g4</code>,
 * using the &#064;ServiceProvider annotation with a path.
 *
 * @author Tim Boudreau
 */
public abstract class RelativeResolverImplementation<T> implements Serializable {

    protected final Class<T> type;

    protected RelativeResolverImplementation(Class<T> type) {
        this.type = type;
    }

    public final Class<T> type() {
        return type;
    }

    /**
     * Resolve an "imported" file - depending on the MIME type this may have
     * very different behavior, such as looking up a Java file on the project
     * classpath, or looking up an imported ANTLR grammar in a parent directory
     * named "imports".
     *
     * @param relativeTo The object to look up relative to
     * @param name The name of the file to look up, as it is described in the
     * original document
     * @return An optional which may contain a reference to the object in
     * question
     */
    public abstract Optional<T> resolve(T relativeTo, String name);

    public static <T> RelativeResolverImplementation<T> noop(Class<T> type) {
        return new Noop<>(type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + type.getName() + "}";
    }

    RelativeResolver<T> toAPI() {
        return RRAccessor.getDefault().newResolver(this);
    }

    private static final class Noop<T> extends RelativeResolverImplementation<T> {

        Noop(Class<T> type) {
            super(type);
        }

        @Override
        public Optional<T> resolve(T relativeTo, String name) {
            return Optional.empty();
        }
    }
}

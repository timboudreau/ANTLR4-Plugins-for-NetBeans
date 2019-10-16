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
package org.nemesis.source.impl;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.spi.RelativeResolverImplementation;

/**
 *
 * @author Tim Boudreau
 */
public abstract class RRAccessor {

    public static RRAccessor DEFAULT;

    public static RRAccessor getDefault() {
        if (DEFAULT != null) {
            return DEFAULT;
        }
        Class<?> type = RelativeResolver.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(RRAccessor.class.getName()).log(Level.SEVERE, null, ex);
        }
        assert DEFAULT != null : "The DEFAULT field must be initialized";
        return DEFAULT;
    }

    public <T> Class<T> typeOf(RelativeResolverImplementation<T> resolver) {
        return resolver.type();
    }

    public <T> Optional<T> resolve(RelativeResolverImplementation<T> resolver, T relativeTo, String name) {
        return resolver.resolve(relativeTo, name);
    }

    public abstract <T> RelativeResolverImplementation<T> implementation(RelativeResolver<T> resolver);

    public abstract <T> RelativeResolver<T> newResolver(RelativeResolverImplementation<T> impl);
}

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
package org.nemesis.source.api;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.nemesis.source.impl.RRAccessor;
import org.nemesis.source.spi.RelativeResolverImplementation;

/**
 *
 * @author Tim Boudreau
 */
public final class RelativeResolver<T> implements Serializable {

    final RelativeResolverImplementation<T> spi;

    private RelativeResolver(RelativeResolverImplementation<T> type) {
        this.spi = type;
    }

    public final Class<T> type() {
        return RRAccessor.getDefault().typeOf(spi);
    }

    public final Optional<T> resolve(T relativeTo, String name) {
        return RRAccessor.getDefault().resolve(spi, relativeTo, name);
    }

    public static <T> RelativeResolver<T> noop(Class<T> type) {
        return new RelativeResolver<>(new Noop<>(type));
    }

    RelativeResolverImplementation<T> implementation() {
        return spi;
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

    @Override
    public final int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.spi);
        return hash;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RelativeResolver<?> other = (RelativeResolver<?>) obj;
        return Objects.equals(this.spi, other.spi);
    }

    static final class RRAccessorImpl extends RRAccessor {

        @Override
        public <T> RelativeResolver<T> newResolver(RelativeResolverImplementation<T> impl) {
            return new RelativeResolver<>(impl);
        }

        @Override
        public <T> RelativeResolverImplementation<T> implementation(RelativeResolver<T> resolver) {
            return resolver.implementation();
        }
    }

    static {
        RRAccessor.DEFAULT = new RRAccessorImpl();
    }
}

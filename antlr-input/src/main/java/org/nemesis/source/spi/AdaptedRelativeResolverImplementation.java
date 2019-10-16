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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class AdaptedRelativeResolverImplementation<F, T> extends RelativeResolverImplementation<T> {

    private final RelativeResolverImplementation<F> from;
    private final Function<? super F, ? extends T> toTo;
    private final Function<? super T, ? extends F> toFrom;

    AdaptedRelativeResolverImplementation(RelativeResolverImplementation<F> from, Class<T> type, Function<? super F, ? extends T> toTo, Function<? super T, ? extends F> toFrom) {
        super(type);
        this.from = from;
        this.toTo = toTo;
        this.toFrom = toFrom;
    }

    @Override
    public String toString() {
        return from + " -> " + type().getName()
                + " using " + toFrom + " and " + toTo;
    }

    private F toFileObject(T doc) {
        return toFrom.apply(doc);
    }

    private T toDocument(F fo) {
        return toTo.apply(fo);
    }

    @Override
    public Optional<T> resolve(T relativeTo, String name) {
        F fo = toFileObject(relativeTo);
        if (fo != null) {
            Optional<F> result = from.resolve(fo, name);
            if (result.isPresent()) {
                T doc = toDocument(result.get());
                return Optional.ofNullable(doc);
            }
        }
        return Optional.empty();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.from);
        hash = 29 * hash + Objects.hashCode(this.toTo);
        hash = 29 * hash + Objects.hashCode(this.toFrom);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AdaptedRelativeResolverImplementation<?, ?> other = (AdaptedRelativeResolverImplementation<?, ?>) obj;
        if (!Objects.equals(this.from, other.from)) {
            return false;
        }
        if (!Objects.equals(this.toTo, other.toTo)) {
            return false;
        }
        return Objects.equals(this.toFrom, other.toFrom);
    }
}

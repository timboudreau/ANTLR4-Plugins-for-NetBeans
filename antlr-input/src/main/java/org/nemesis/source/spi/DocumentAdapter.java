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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;
import java.util.function.Function;

/**
 * 
 *
 * @author Tim Boudreau
 */
public class DocumentAdapter<F, T> {

    final Class<F> fromType;
    final Class<T> toType;
    final Function<F, T> convertTo;
    final Function<T, F> convertFrom;

    protected DocumentAdapter(Class<F> fromType, Class<T> toType, Function<F, T> convertTo, Function<T, F> convertFrom) {
        this.fromType = notNull("fromType", fromType);
        this.toType = notNull("toType", toType);
        this.convertTo = notNull("convertTo", convertTo);
        this.convertFrom = notNull("convertFrom", convertFrom);
    }

    Class<F> fromType() {
        return fromType;
    }

    Class<T> toType() {
        return toType;
    }

    Function<T, F> convertFrom() {
        return convertFrom;
    }

    Function<F, T> convertTo() {
        return convertTo;
    }

    @Override
    public String toString() {
        return fromType.getName() + "->" + toType.getName();
    }

    @Override
    public final int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.fromType);
        hash = 89 * hash + Objects.hashCode(this.toType);
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
        final DocumentAdapter<?, ?> other = (DocumentAdapter<?, ?>) obj;
        if (!Objects.equals(this.fromType, other.fromType)) {
            return false;
        }
        return Objects.equals(this.toType, other.toType);
    }

    static <T, N> RelativeResolverImplementation<N> adapt(RelativeResolverImplementation<T> orig,
            Class<N> to, Function<? super T, ? extends N> toTo, Function<? super N, ? extends T> toFrom) {
        return new AdaptedRelativeResolverImplementation<>(orig, to, toTo, toFrom);
    }
}

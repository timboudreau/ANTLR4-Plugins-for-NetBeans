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
package org.nemesis.extraction;

import java.io.Serializable;
import java.util.Objects;
import org.nemesis.extraction.key.NamedRegionKey;

/**
 * For cases where item bounds and names are both extracted from the same type,
 * this allows runtime callers to get the names key from the extraction if given
 * the bounds key (or vice versa).
 *
 * @author Tim Boudreau
 */
final class NameAndBoundsPair<T extends Enum<T>> implements Serializable {

    private final NamedRegionKey<T> boundsKey;
    private final NamedRegionKey<T> namesKey;

    NameAndBoundsPair(NamedRegionKey<T> boundsKey, NamedRegionKey<T> namesKey) {
        this.boundsKey = boundsKey;
        this.namesKey = namesKey;
    }

    @SuppressWarnings("unchecked")
    <X extends Enum<X>> NamedRegionKey<X> nameKeyFor(NamedRegionKey<X> key) {
        if (key == boundsKey) {
            return (NamedRegionKey<X>) namesKey;
        } else if (key == namesKey) {
            return (NamedRegionKey<X>) namesKey;
        }
        return null;
    }

    <X extends Enum<X>> NamedRegionKey<X> boundsKeyFor(NamedRegionKey<X> key) {
        if (key == namesKey) {
            return (NamedRegionKey<X>) boundsKey;
        } else if (key == boundsKey) {
            return (NamedRegionKey<X>) boundsKey;
        }
        return null;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + Objects.hashCode(this.boundsKey);
        hash = 83 * hash + Objects.hashCode(this.namesKey);
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
        final NameAndBoundsPair<?> other = (NameAndBoundsPair<?>) obj;
        if (!Objects.equals(this.boundsKey, other.boundsKey)) {
            return false;
        }
        return Objects.equals(this.namesKey, other.namesKey);
    }

    @Override
    public String toString() {
        return "{bounds: " + boundsKey + ", names: " + namesKey + "}";
    }

}

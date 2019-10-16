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
package org.nemesis.extraction.key;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.Serializable;
import java.util.Objects;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;
import static org.nemesis.extraction.key.NamedRegionKey.checkName;

/**
 * Key used to retrieve SemanticRegions&lt;T&gt; instances from an Extraction.
 *
 * @param <T>
 */
public final class RegionsKey<T> implements Serializable, Hashable, ExtractionKey<T>, RegionKey<T> {

    final Class<? super T> type;
    final String name;

    private RegionsKey(Class<? super T> type, String name) {
        this.name = checkName(notNull("name", name));;
        this.type = notNull("type", type);
    }

    public static <T> RegionsKey<T> create(Class<? super T> type, String name) {
        return new RegionsKey<>(type, name == null ? type.getSimpleName() : name);
    }

    public static <T> RegionsKey<T> create(Class<T> type) {
        return create(type, null);
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(type.getName());
        if (name != null && !Objects.equals(name, type.getSimpleName())) {
            hasher.writeString(name);
        }
    }

    @SuppressWarnings("unchecked")
    public Class<T> type() {
        return (Class<T>) type;
    }

    public String name() {
        return name;
    }

    public String toString() {
        String nm = type.getSimpleName();
        return nm.equals(name) ? nm : name + ":" + nm;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.type);
        hash = 37 * hash + Objects.hashCode(this.name);
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
        final RegionsKey<?> other = (RegionsKey<?>) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return Objects.equals(this.type, other.type);
    }
}

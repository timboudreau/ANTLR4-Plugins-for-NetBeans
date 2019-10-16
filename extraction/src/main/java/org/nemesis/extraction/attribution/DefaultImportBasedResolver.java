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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.NamedRegionKey;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultImportBasedResolver<K extends Enum<K>> implements ImportBasedResolver<K> {

    private final NamedRegionKey<K> key;
    private final NamedRegionKey<?>[] importKeys;

    DefaultImportBasedResolver(NamedRegionKey<K> key, NamedRegionKey<?>... importKeys) {
        this.key = notNull("key", key);
        this.importKeys = importKeys;
    }

    @Override
    public Set<String> importsThatCouldContain(Extraction extraction, UnknownNameReference<K> ref) {
        Set<String> imports = new HashSet<>(32);
        for (NamedRegionKey<?> k : importKeys) {
            NamedSemanticRegions<?> regions = extraction.namedRegions(k);
            if (regions != null && !regions.isEmpty()) {
                imports.addAll(Arrays.asList(regions.nameArray()));
            }
        }
        return imports;
    }

    @Override
    public NamedRegionKey<K> key() {
        return key;
    }

    @Override
    public String toString() {
        return "DefaultImportBasedResolver{" + "key=" + key + ", importKeys="
                + Arrays.toString(importKeys) + '}';
    }

    @Override
    public Class<K> type() {
        return key.type();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + Objects.hashCode(this.key);
        hash = 71 * hash + Arrays.deepHashCode(this.importKeys);
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
        final DefaultImportBasedResolver<?> other
                = (DefaultImportBasedResolver<?>) obj;
        if (!Objects.equals(this.key, other.key)) {
            return false;
        }
        return Arrays.deepEquals(this.importKeys, other.importKeys);
    }
}

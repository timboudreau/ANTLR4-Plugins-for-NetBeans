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

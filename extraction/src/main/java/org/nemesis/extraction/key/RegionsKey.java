package org.nemesis.extraction.key;

import java.io.Serializable;
import java.util.Objects;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 * Key used to retrieve SemanticRegions&lt;T&gt; instances from an Extraction.
 *
 * @param <T>
 */
public final class RegionsKey<T> implements Serializable, Hashable, ExtractionKey<T>, RegionKey<T> {

    final Class<? super T> type;
    final String name;

    private RegionsKey(Class<? super T> type, String name) {
        this.name = name;
        this.type = type;
    }

    private RegionsKey(Class<? super T> type) {
        this.type = type;
        this.name = null;
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

package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key;

import java.io.Serializable;
import java.util.Objects;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 * Key type for named regions, used in <code>Extraction.namedRegions()</code>.
 *
 * @author Tim Boudreau
 */
public final class NamedRegionKey<T extends Enum<T>> implements Serializable, Hashable, NamedExtractionKey<T> {

    private final String name;
    final Class<T> type;

    private NamedRegionKey(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(name);
        hasher.writeString(type.getName());
    }

    public static <T extends Enum<T>> NamedRegionKey<T> create(Class<T> type) {
        return new NamedRegionKey<>(type.getSimpleName(), type);
    }

    public static <T extends Enum<T>> NamedRegionKey<T> create(String name, Class<T> type) {
        return new NamedRegionKey<>(name, type);
    }

    public String toString() {
        return name + ":" + type.getSimpleName();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.name);
        hash = 37 * hash + Objects.hashCode(this.type);
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
        final NamedRegionKey<?> other = (NamedRegionKey<?>) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        return true;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

}

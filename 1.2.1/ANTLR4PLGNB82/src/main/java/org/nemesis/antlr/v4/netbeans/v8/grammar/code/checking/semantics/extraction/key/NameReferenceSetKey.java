package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key;

import java.io.Serializable;
import java.util.Objects;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Hashable;

/**
 * Key for reference sets.
 *
 * @author Tim Boudreau
 */
public final class NameReferenceSetKey<T extends Enum<T>> implements Serializable, Hashable, NamedExtractionKey<T> {

    final String name;
    final Class<T> type;

    private NameReferenceSetKey(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public void hashInto(Hashable.Hasher hasher) {
        hasher.writeString(name);
        hasher.writeString(type.getName());
    }

    public static final <T extends Enum<T>> NameReferenceSetKey<T> create(Class<T> type) {
        return new NameReferenceSetKey(type.getSimpleName(), type);
    }

    public static final <T extends Enum<T>> NameReferenceSetKey<T> create(String name, Class<T> type) {
        return new NameReferenceSetKey(name, type);
    }

    public String toString() {
        return name + "(" + type.getName() + ")";
    }

    @Override
    public int hashCode() {
        int hash = 3;
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
        final NameReferenceSetKey<?> other = (NameReferenceSetKey<?>) obj;
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
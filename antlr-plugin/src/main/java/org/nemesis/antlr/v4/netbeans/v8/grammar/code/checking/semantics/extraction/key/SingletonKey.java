package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key;

import java.io.Serializable;
import java.util.Objects;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 * Key for looking up objects constructed from a parse, which should be
 * found <i>exactly once</i> in the document. The resulting
 * SingletonEncounters class you can fetch with such a key allows for the
 * possibility that in a malformed source, such a "singleton" can be
 * encountered multiple times, so that these can be marked as errors.
 *
 * @param <T>
 */
public final class SingletonKey<T> implements Serializable, Hashable, ExtractionKey<T> {

    private final Class<? super T> type;
    private final String name;

    SingletonKey(Class<? super T> type, String name) {
        this.name = name;
        this.type = type;
    }

    SingletonKey(Class<? super T> type) {
        this.type = type;
        this.name = null;
    }

    public static <T> SingletonKey<T> create(Class<? super T> type, String name) {
        return new SingletonKey<>(type, name == null ? type.getSimpleName() : name);
    }

    public static <T> SingletonKey<T> create(Class<T> type) {
        return create(type, null);
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(type.getName());
        if (name != type.getSimpleName()) {
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
        hash = 43 * hash + Objects.hashCode(this.type);
        hash = 43 * hash + Objects.hashCode(this.name);
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
        final SingletonKey<?> other = (SingletonKey<?>) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        return true;
    }
}

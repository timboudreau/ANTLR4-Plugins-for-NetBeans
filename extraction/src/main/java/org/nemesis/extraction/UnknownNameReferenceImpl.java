package org.nemesis.extraction;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
final class UnknownNameReferenceImpl<T extends Enum<T>> implements UnknownNameReference<T> {

    private final Class<T> type;
    private final T expectedKind;
    private final int start;
    private final int end;
    private final String name;
    private final int index;

    UnknownNameReferenceImpl(Class<T> type, int start, int end, String name, int index) {
        this.type = type;
        this.expectedKind = null;
        this.start = start;
        this.end = end;
        this.name = name;
        this.index = index;
    }

    UnknownNameReferenceImpl(T expectedKind, int start, int end, String name, int index) {
        this.expectedKind = expectedKind;
        this.start = start;
        this.end = end;
        this.name = name;
        this.index = index;
        this.type = notNull("expectedKind", expectedKind).getDeclaringClass();
    }

    @Override
    public Class<T> kindType() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public int end() {
        return end;
    }

    public boolean isReference() {
        return true;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public String toString() {
        return name + "@" + start + ":" + end;
    }

    @Override
    public int hashCode() {
        return start + (end * 73) + 7 * Objects.hashCode(name);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof UnknownNameReference<?>) {
            UnknownNameReference<?> u = (UnknownNameReference<?>) o;
            return u.start() == start() && u.end() == end && Objects.equals(name(), u.name());
        }
        return false;
    }

    @Override
    public T expectedKind() {
        return expectedKind;
    }

}

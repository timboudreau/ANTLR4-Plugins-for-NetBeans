package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
final class UnknownNameReferenceImpl<T extends Enum<T>> implements UnknownNameReference<T> {

    private final T expectedKind;
    private final int start;
    private final int end;
    private final String name;
    private final int index;

    UnknownNameReferenceImpl(T expectedKind, int start, int end, String name, int index) {
        this.expectedKind = expectedKind;
        this.start = start;
        this.end = end;
        this.name = name;
        this.index = index;
    }

    public String name() {
        return name;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public boolean isReference() {
        return true;
    }

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

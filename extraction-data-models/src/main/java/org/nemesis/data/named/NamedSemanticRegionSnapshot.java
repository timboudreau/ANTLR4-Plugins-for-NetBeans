package org.nemesis.data.named;

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
final class NamedSemanticRegionSnapshot<T extends Enum<T>> implements NamedSemanticRegion<T> {

    private final T kind;
    private final int ordering;
    private final int start;
    private final int end;
    private final boolean ref;
    private final String name;
    private final int index;

    NamedSemanticRegionSnapshot(NamedSemanticRegion<T> orig) {
        name = orig.name();
        kind = orig.kind();
        ordering = orig.ordering();
        start = orig.start();
        end = orig.end();
        ref = orig.isReference();
        index = orig.index();
    }

    @Override
    public T kind() {
        return kind;
    }

    @Override
    public int ordering() {
        return ordering;
    }

    @Override
    public boolean isReference() {
        return ref;
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

    @Override
    public int index() {
        return index;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof NamedSemanticRegion<?>) {
            NamedSemanticRegion<?> other = (NamedSemanticRegion<?>) o;
            return start == other.start() && end == other.end() && name.equals(other.name()) && Objects.equals(kind, other.kind());
        }
        return false;
    }

    public int hashCode() {
        return name.hashCode() + (7 * start) + (1029 * end);
    }

    public String toString() {
        return "snapshot:" + name + "@" + start + ":" + end;
    }

}

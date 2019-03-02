package org.nemesis.extraction;

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public class NamedRegionData<T extends Enum<T>> extends NameAndOffsets {

    final T kind;

    NamedRegionData(String string, T kind, int start, int end) {
        super(string, start, end);
        this.kind = kind;
    }

    public static <T extends Enum<T>> NamedRegionData<T> create(String name, T kind, int start, int end) {
        return new NamedRegionData<>(name, kind, start, end);
    }

    @Override
    public String toString() {
        return name + ":" + kind + ":" + start + ":" + end;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.name);
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
        final NamedRegionData<?> other = (NamedRegionData<?>) obj;
        return Objects.equals(this.name, other.name);
    }

}

package org.nemesis.extraction;

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public class NameAndOffsets {

    final String name;
    final int start;
    final int end;

    NameAndOffsets(String name, int start, int end) {
        assert name != null;
        assert start >= 0;
        assert end >= 0;
        this.name = name;
        this.start = start;
        this.end = end;
    }

    public static NameAndOffsets create(String name, int start, int end) {
        return new NameAndOffsets(name, start, end);
    }

    public String toString() {
        return name + "@" + start + ":" + end;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(this.name);
        hash = 41 * hash + this.start;
        hash = 41 * hash + this.end;
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
        final NameAndOffsets other = (NameAndOffsets) obj;
        if (this.start != other.start) {
            return false;
        }
        if (this.end != other.end) {
            return false;
        }
        return Objects.equals(this.name, other.name);
    }

}

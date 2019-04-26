package org.nemesis.range;

/**
 * Relationship between a range with a start and an end, and a coordinate which
 * might be in or outside that range.
 *
 * @author Tim Boudreau
 */
public enum RangePositionRelation implements BoundaryRelation {
    BEFORE, IN, AFTER;

    @Override
    public boolean isOverlap() {
        return this == IN;
    }

    public static RangePositionRelation get(int start, int end, int test) {
        if (test < start) {
            return BEFORE;
        } else if (test >= end) {
            return AFTER;
        } else {
            return IN;
        }
    }

    public static RangePositionRelation get(long start, long end, long test) {
        if (test < start) {
            return BEFORE;
        } else if (test >= end) {
            return AFTER;
        } else {
            return IN;
        }
    }

    public static RangePositionRelation get(short start, short end, short test) {
        if (test < start) {
            return BEFORE;
        } else if (test >= end) {
            return AFTER;
        } else {
            return IN;
        }
    }

}

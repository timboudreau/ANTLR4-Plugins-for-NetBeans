package org.nemesis.range;

/**
 * Relationship between two positions.
 *
 * @author Tim Boudreau
 */
public enum PositionRelation implements BoundaryRelation {
    LESS, GREATER, EQUAL;

    public static PositionRelation relation(int a, int b) {
        return a < b ? LESS : a == b ? EQUAL : GREATER;
    }

    public static PositionRelation relation(long a, long b) {
        return a < b ? LESS : a == b ? EQUAL : GREATER;
    }

    public static PositionRelation relation(short a, short b) {
        return a < b ? LESS : a == b ? EQUAL : GREATER;
    }

    @Override
    public boolean isOverlap() {
        return this == EQUAL;
    }

}

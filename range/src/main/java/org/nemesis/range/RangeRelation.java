package org.nemesis.range;

/**
 * Relationship between two ranges which may not, may partially or may
 * completely overlap each other.
 *
 * @author Tim Boudreau
 */
public enum RangeRelation implements BoundaryRelation {
    /**
     * The range doing the testing does not overlap and occurs entirely before
     * the bounds of the second range.
     */
    BEFORE,
    /**
     * The range doing the testing contains the start point but not the end
     * point of the second range.
     */
    STRADDLES_START,
    /**
     * The range doing the testing entirely contains, but is not equal to, the
     * range being tested.
     */
    CONTAINS,
    /**
     * Two ranges have exactly the same start and size.
     */
    EQUAL,
    /**
     * The range doing the testing is entirely contained by, but is not equal
     * to, the range being tested.
     */
    CONTAINED,
    /**
     * The range doing the testing contains the end point but not the start
     * point of the second range.
     */
    STRADDLES_END,
    /**
     * The range doing the testing does not overlap and occurs entirely after
     * the bounds of the second range.
     */
    AFTER;

    @Override
    public boolean isOverlap() {
        return this != BEFORE && this != AFTER;
    }

    public boolean isContainerContainedRelationship() {
        return this == CONTAINS || this == CONTAINED;
    }

    public boolean isStraddle() {
        return this == STRADDLES_END || this == STRADDLES_START;
    }

    /**
     * Value used in comparing two ranges.
     *
     * @return The bias, -1, 0 or 1
     */
    public int bias() {
        switch (this) {
            case STRADDLES_START:
            case BEFORE:
            case CONTAINS:
                return -1;
            case STRADDLES_END:
            case AFTER:
            case CONTAINED:
                return 1;
            case EQUAL:
                return 0;
            default:
                throw new AssertionError(this);
        }
    }

    public static RangeRelation get(int aStart, int aEnd, int bStart, int bEnd) {
        if (aStart == bStart && aEnd == bEnd) {
            return EQUAL;
        } else if (aEnd <= bStart) {
            return BEFORE;
        } else if (aStart >= bEnd) {
            return AFTER;
        } else if (aStart <= bStart && aEnd >= bEnd) {
            return CONTAINS;
        } else if (bStart <= aStart && bEnd >= aEnd) {
            return CONTAINED;
        } else if (aStart < bStart && aEnd > bStart && aEnd < bEnd) {
            return STRADDLES_START;
        } else if (aStart > bStart && aStart < bEnd && aEnd > bEnd) {
            return STRADDLES_END;
        }
        throw new AssertionError(aStart + "," + aEnd + "," + bStart + "," + bEnd);
    }

    public static RangeRelation get(long aStart, long aEnd, long bStart, long bEnd) {
        if (aStart == bStart && aEnd == bEnd) {
            return EQUAL;
        } else if (aEnd <= bStart) {
            return BEFORE;
        } else if (aStart >= bEnd) {
            return AFTER;
        } else if (aStart <= bStart && aEnd >= bEnd) {
            return CONTAINS;
        } else if (bStart <= aStart && bEnd >= aEnd) {
            return CONTAINED;
        } else if (aStart < bStart && aEnd > bStart && aEnd < bEnd) {
            return STRADDLES_START;
        } else if (aStart > bStart && aStart < bEnd && aEnd > bEnd) {
            return STRADDLES_END;
        }
        throw new AssertionError(aStart + "," + aEnd + "," + bStart + "," + bEnd);
    }

}

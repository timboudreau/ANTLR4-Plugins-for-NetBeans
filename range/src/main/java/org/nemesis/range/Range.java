package org.nemesis.range;

/**
 *
 * @author Tim Boudreau
 */
public interface Range<R extends Range> extends Comparable<Range> {

    public Number startValue();

    public Number sizeValue();

    default RangeRelation relation(Range other) {
        long myStart = startValue().longValue();
        long otherStart = other.startValue().longValue();
        long end = myStart + sizeValue().longValue();
        long otherEnd = otherStart + other.sizeValue().longValue();
        return RangeRelation.get(myStart, end, otherStart, otherEnd);
    }

    default RangePositionRelation relation(long position) {
        return RangePositionRelation.get(startValue().longValue(),
                startValue().longValue() + sizeValue().longValue(),
                position);
    }

    default RangePositionRelation relation(int position) {
        return RangePositionRelation.get(startValue().intValue(),
                startValue().longValue() + sizeValue().intValue(),
                position);
    }

    @Override
    public default int compareTo(Range o) {
        RangeRelation rel = relation(o);
        switch(rel) {
            case BEFORE :
            case CONTAINS :
            case STRADDLES_START :
                return -1;
            case EQUAL :
                return 0;
            case STRADDLES_END :
            case AFTER :
            case CONTAINED :
                return 1;
            default :
                throw new AssertionError(rel);
        }
    }

    public interface Relation {

        boolean isOverlap();
    }

    public enum PositionRelation implements Relation {
        LESS,
        GREATER,
        EQUAL;

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

    public enum RangePositionRelation implements Relation {
        BEFORE,
        CONTAINED,
        AFTER;

        @Override
        public boolean isOverlap() {
            return this == CONTAINED;
        }

        public static RangePositionRelation get(int start, int end, int test) {
            if (test < start) {
                return BEFORE;
            } else if (test >= end) {
                return AFTER;
            } else {
                return CONTAINED;
            }
        }

        public static RangePositionRelation get(long start, long end, long test) {
            if (test < start) {
                return BEFORE;
            } else if (test >= end) {
                return AFTER;
            } else {
                return CONTAINED;
            }
        }

        public static RangePositionRelation get(short start, short end, short test) {
            if (test < start) {
                return BEFORE;
            } else if (test >= end) {
                return AFTER;
            } else {
                return CONTAINED;
            }
        }
    }

    public enum RangeRelation {
        BEFORE,
        STRADDLES_START,
        CONTAINS,
        EQUAL,
        CONTAINED,
        STRADDLES_END,
        AFTER;

        public boolean isOverlap() {
            return this != BEFORE && this != AFTER;
        }

        public static RangeRelation get(int aStart, int aEnd, int bStart, int bEnd) {
            if (aEnd < bStart) {
                return BEFORE;
            } else if (aStart > bEnd) {
                return AFTER;
            } else if (aStart < bStart && aEnd > bEnd) {
                return CONTAINS;
            } else if (bStart < aStart && bEnd > aEnd) {
                return CONTAINED;
            } else if (aStart == bStart && aEnd == bEnd) {
                return EQUAL;
            } else if (aStart < bStart && aEnd > bStart && aEnd < bEnd) {
                return STRADDLES_START;
            } else if (aStart > bStart && aStart < bEnd && aEnd > bEnd) {
                return STRADDLES_END;
            }
            throw new AssertionError(aStart + "," + aEnd + "," + bStart + ","
                    + bEnd);
        }
        public static RangeRelation get(long aStart, long aEnd, long bStart, long bEnd) {
            if (aEnd < bStart) {
                return BEFORE;
            } else if (aStart > bEnd) {
                return AFTER;
            } else if (aStart < bStart && aEnd > bEnd) {
                return CONTAINS;
            } else if (bStart < aStart && bEnd > aEnd) {
                return CONTAINED;
            } else if (aStart == bStart && aEnd == bEnd) {
                return EQUAL;
            } else if (aStart < bStart && aEnd > bStart && aEnd < bEnd) {
                return STRADDLES_START;
            } else if (aStart > bStart && aStart < bEnd && aEnd > bEnd) {
                return STRADDLES_END;
            }
            throw new AssertionError(aStart + "," + aEnd + "," + bStart + ","
                    + bEnd);
        }
    }

    public interface OfInt extends Range<OfInt> {

        int start();

        int size();

        default int stop() {
            return start() + size() - 1;
        }

        default int end() {
            return start() + size();
        }

        @Override
        public default Number startValue() {
            return start();
        }

        @Override
        public default Number sizeValue() {
            return size();
        }

        @Override
        public default RangeRelation relation(Range other) {
            if (other == this) {
                return RangeRelation.EQUAL;
            }
            if (other instanceof OfInt) {
                OfInt oi = (OfInt) other;
                return RangeRelation.get(start(), end(), oi.start(), oi.end());
            }
            return Range.super.relation(other);
        }

        @Override
        public default RangePositionRelation relation(long position) {
            if (position < Integer.MAX_VALUE) {
                return RangePositionRelation.get(start(), end(), (int) position);
            }
            return Range.super.relation(position);
        }

        @Override
        public default RangePositionRelation relation(int position) {
            if (position < Integer.MAX_VALUE) {
                return RangePositionRelation.get(start(), end(), (int) position);
            }
            return Range.super.relation(position);
        }

        default OfInt snapshot() {
            int start = start();
            int sz = size();
            return new OfInt() {
                @Override
                public int start() {
                    return start;
                }

                @Override
                public int size() {
                    return sz;
                }
            };
        }
    }

    public static Range.OfInt fixed(int start, int length) {
        return new FixedIntRange(start, length);
    }

    static final class FixedIntRange implements OfInt {

        private final int start;
        private final int size;

        public FixedIntRange(int start, int size) {
            this.start = start;
            this.size = size;
        }

        @Override
        public int start() {
            return start;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public String toString() {
            return start() + ":" + end() + "(" + size() + ")";
        }
    }
}

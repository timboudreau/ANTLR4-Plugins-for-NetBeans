package org.nemesis.range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * Integer subtype of Range with many convenience methods, allowing integer
 * ranges to be compared without boxing.
 *
 * @author Tim Boudreau
 */
public interface IntRange<OI extends IntRange<OI>> extends Range<OI> {

    /**
     * The start position.
     *
     * @return The start
     */
    int start();

    /**
     * The size, which may be zero or greater.
     *
     * @return The size
     */
    int size();

    @Override
    default OI forEachPosition(IntConsumer consumer) {
        int end = end();
        int start = start();
        for (int i = start; i < end; i++) {
            consumer.accept(i);
        }
        return cast();
    }

    @Override
    default OI forEachPosition(LongConsumer consumer) {
        int end = end();
        int start = start();
        for (int i = start; i < end; i++) {
            consumer.accept(i);
        }
        return cast();
    }

    @Override
    default OI shiftedBy(int amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(start() + amount, size());
    }

    @Override
    default OI grownBy(int amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(start(), size() + amount);
    }

    @Override
    default OI shrunkBy(int amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(start(), size() - amount);
    }

    @Override
    default boolean isEmpty() {
        return size() == 0;
    }

    @Override
    default OI getOverlap(Range<?> other) {
        RangeRelation rel = relationTo(other);
        if (other instanceof IntRange<?>) {
            IntRange<?> oi = (IntRange<?>) other;
            switch (rel) {
                case EQUAL:
                case CONTAINED:
                    return (OI) this;
                case CONTAINS:
                    return (OI) oi;
                case STRADDLES_START:
                    return newRange(oi.start(), end() - oi.start());
                case STRADDLES_END:
                    return newRange(start(), oi.end() - start());
                case AFTER:
                case BEFORE:
                    return newRange(start(), 0);
                default:
                    throw new AssertionError(rel);
            }
        }
        return Range.super.getOverlap(other);
    }

    /**
     * Get the <i>stop position</i> of this range - the last position which is
     * contained in this range - <code>start() + size() - 1</code>.
     *
     * @return The stop point
     */
    default int stop() {
        return start() + size() - 1;
    }

    /**
     * Get the <i>end position</i> of this range - the last position which is
     * contained in this range - <code>start() + size()</code>.
     *
     * @return The stop point
     */
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

    default boolean abuts(Range<?> r) {
        if (r instanceof IntRange<?>) {
            IntRange<?> oi = (IntRange<?>) r;
            int myStart = start();
            int myEnd = myStart + size();
            int otherStart = oi.start();
            int otherEnd = oi.start() + oi.size();
            return otherEnd == myStart || myEnd == otherStart;
        }
        return Range.super.abuts(r);
    }

    @Override
    public default RangeRelation relationTo(Range<?> other) {
        if (other == this) {
            return RangeRelation.EQUAL;
        }
        if (other instanceof IntRange<?>) {
            IntRange<?> oi = (IntRange<?>) other;
            return RangeRelation.get(start(), end(), oi.start(), oi.end());
        }
        return Range.super.relationTo(other);
    }

    @Override
    public default RangePositionRelation relationTo(long position) {
        if (position < Integer.MAX_VALUE) {
            return RangePositionRelation.get(start(), end(), (int) position);
        }
        return Range.super.relationTo(position);
    }

    @Override
    public default RangePositionRelation relationTo(int position) {
        return RangePositionRelation.get(start(), end(), (int) position);
    }

    /**
     * Take an immutable snapshot of the current size and start point, for use
     * by mutable subtypes, but defined here so code that uses them can work
     * with any instance.
     *
     * @return A copy of the current state of this range
     */
    default IntRange<? extends IntRange<?>> snapshot() {
        return Range.of(start(), size());
    }

    default PositionRelation relationToStart(int pos) {
        return PositionRelation.relation(pos, start());
    }

    default PositionRelation relationToEnd(int pos) {
        return PositionRelation.relation(pos, end());
    }

    @Override
    default boolean containsBoundary(Range<?> range) {
        if (matches(range)) {
            return false;
        }
        if (range instanceof IntRange<?>) {
            IntRange<?> other = (IntRange<?>) range;
            return contains(other.start()) || contains(other.end());
        }
        return Range.super.containsBoundary(range);
    }

    @Override
    default List<OI> nonOverlap(Range<?> other) {
        if (other == this) {
            return Collections.emptyList();
        }
        if (!(other instanceof IntRange<?>)) {
            long otherStart = other.startValue().longValue();
            long otherSize = other.sizeValue().longValue();
            if (otherStart + otherSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Range too large for "
                        + "Integer.MAX_VALUE: " + other);
            }
            other = Range.of((int) otherStart, (int) otherSize);
        }
        List<OI> result = new ArrayList<>(4);
        int[] startStops = nonOverlappingCoordinates((IntRange<?>) other);
        for (int i = 0; i < startStops.length; i += 2) {
            int start = startStops[i];
            int stop = startStops[i + 1];
            result.add(newRange(start, stop - start + 1));
        }
        return result;
    }

    /**
     * Get the start/end positions for sub-ranges contained in only one of this
     * or the passed range.
     *
     * @param other The other range
     * @return An array of start/end pairs.
     */
    default int[] nonOverlappingCoordinates(IntRange<? extends IntRange<?>> other) {
        if (!matches(other)) {
            if (contains(other)) {
                if (start() == other.start()) {
                    return new int[]{other.end(), stop()};
                } else if (stop() == other.stop()) {
                    return new int[]{start(), other.start() - 1};
                } else {
                    return new int[]{start(), other.start() - 1, other.end(), stop()};
                }
            } else if (other.contains(this)) {
                if (start() == other.start()) {
                    return new int[]{end(), other.stop()};
                } else if (stop() == other.stop()) {
                    return new int[]{other.start(), start() - 1};
                } else {
                    return new int[]{other.start(), start() - 1, end(), other.stop()};
                }
            } else if (containsBoundary(other)) {
                if (contains(other.stop())) {
                    if (other.stop() == stop()) {
                        return new int[]{other.start(), start() - 1};
                    } else {
                        return new int[]{other.start(), start() - 1, other.stop() + 1, stop()};
                    }
                } else if (contains(other.start())) {
                    if (start() == other.start()) {
                        return new int[]{start(), other.start() - 1};
                    } else {
                        return new int[]{start(), other.start() - 1, end(), other.stop()};
                    }
                }
            }
        }
        return new int[0];
    }
}

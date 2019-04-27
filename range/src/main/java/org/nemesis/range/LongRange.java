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
public interface LongRange<OI extends LongRange<OI>> extends Range<OI> {

    /**
     * The start position.
     *
     * @return The start
     */
    long start();

    /**
     * The size, which may be zero or greater.
     *
     * @return The size
     */
    long size();

    /**
     * Determine if this item occurs after (start() &gt;= item.end()) another
     * item.
     *
     * @param item Another item
     * @return
     */
    default boolean isAfter(Range<?> range) {
        if (range instanceof IntRange<?>) {
            IntRange<?> item = (IntRange<?>) range;
            return start() >= item.end();
        } else if (range instanceof LongRange<?>) {
            LongRange<?> item = (LongRange<?>) range;
            return start() >= item.end();
        }
        return Range.super.isAfter(range);
    }

    /**
     * Determine if this item occurs before (end() &lt;= item.start()) another
     * item.
     *
     * @param item
     * @return
     */
    default boolean isBefore(Range<?> range) {
        if (range instanceof IntRange<?>) {
            IntRange<?> item = (IntRange<?>) range;
            return end() <= item.start();
        } else if (range instanceof LongRange<?>) {
            LongRange<?> item = (LongRange<?>) range;
            return end() <= item.start();
        }
        return Range.super.isBefore(range);
    }

    @Override
    default OI forEachPosition(IntConsumer consumer) {
        long end = end();
        long start = start();
        for (long i = start; i < Math.min(Integer.MAX_VALUE, end); i++) {
            consumer.accept((int) i);
        }
        return cast();
    }

    @Override
    default OI forEachPosition(LongConsumer consumer) {
        long end = end();
        long start = start();
        for (long i = start; i < end; i++) {
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
    default OI shiftedBy(long amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(start() + amount, size());
    }

    @Override
    default OI grownBy(long amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(start(), size() + amount);
    }

    @Override
    default OI shrunkBy(long amount) {
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

        if (other instanceof LongRange<?>) {
            LongRange<?> oi = (LongRange<?>) other;
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
    default long stop() {
        return end() - 1;
    }

    /**
     * Get the <i>end position</i> of this range - the last position which is
     * contained in this range - <code>start() + size()</code>.
     *
     * @return The stop point
     */
    default long end() {
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
    default boolean abuts(Range<?> r) {
        if (r instanceof LongRange<?>) {
            LongRange<?> oi = (LongRange<?>) r;
            long myStart = start();
            long myEnd = myStart + size();
            long otherStart = oi.start();
            long otherEnd = oi.start() + oi.size();
            return otherEnd == myStart || myEnd == otherStart;
        }
        return Range.super.abuts(r);
    }

    @Override
    public default RangeRelation relationTo(Range<?> other) {
        if (other == this) {
            return RangeRelation.EQUAL;
        }
        if (other instanceof LongRange<?>) {
            LongRange<?> oi = (LongRange<?>) other;
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
        return RangePositionRelation.get(start(), end(), position);
    }

    /**
     * Take an immutable snapshot of the current size and start point, for use
     * by mutable subtypes, but defined here so code that uses them can work
     * with any instance.
     *
     * @return A copy of the current state of this range
     */
    default LongRange<? extends LongRange<?>> snapshot() {
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
        if (range instanceof LongRange<?>) {
            LongRange<?> other = (LongRange<?>) range;
            return contains(other.start()) || contains(other.end());
        }
        return Range.super.containsBoundary(range);
    }

    @Override
    default List<OI> nonOverlap(Range<?> other) {
        if (other == this) {
            return Collections.emptyList();
        }
        if (!(other instanceof LongRange<?>)) {
            other = Range.of(other.startValue().longValue(), other.sizeValue().longValue());
        }
        List<OI> result = new ArrayList<>(4);
        long[] startStops = nonOverlappingCoordinates((LongRange<?>) other);
        for (int i = 0; i < startStops.length; i += 2) {
            long start = startStops[i];
            long stop = startStops[i + 1];
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
    default long[] nonOverlappingCoordinates(LongRange<? extends LongRange<?>> other) {
        if (!matches(other)) {
            if (contains(other)) {
                if (start() == other.start()) {
                    return new long[]{other.end(), stop()};
                } else if (stop() == other.stop()) {
                    return new long[]{start(), other.start() - 1};
                } else {
                    return new long[]{start(), other.start() - 1, other.end(), stop()};
                }
            } else if (other.contains(this)) {
                if (start() == other.start()) {
                    return new long[]{end(), other.stop()};
                } else if (stop() == other.stop()) {
                    return new long[]{other.start(), start() - 1};
                } else {
                    return new long[]{other.start(), start() - 1, end(), other.stop()};
                }
            } else if (containsBoundary(other)) {
                if (contains(other.stop())) {
                    if (other.stop() == stop()) {
                        return new long[]{other.start(), start() - 1};
                    } else {
                        return new long[]{other.start(), start() - 1, other.stop() + 1, stop()};
                    }
                } else if (contains(other.start())) {
                    if (start() == other.start()) {
                        return new long[]{start(), other.start() - 1};
                    } else {
                        return new long[]{start(), other.start() - 1, end(), other.stop()};
                    }
                }
            }
        }
        return new long[0];
    }
}

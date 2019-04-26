package org.nemesis.range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * A range having a start and size; type specific subclasses for ints and longs
 * contain convenience methods. Manipulating ranges within files, memory or text
 * is a task common to memory managers, syntax highlighting and many other
 * domains, and is the root of myriad off-by-one errors. This library is an
 * attempt to get that logic right in a reusable fashion, after having had to
 * implement it from scratch several times over the last few decades.
 * <h3>Implementation Notes</h3>
 * The factory methods on this class should satisfy most needs. If you do
 * implement Range, the following points are important for compatibility:
 * <ul>
 * <li>The sort order of ranges which overlap is as follows: If a range wholly
 * contains another range and starts at the same position, they are sorted from
 * largest to smallest.
 * </li>
 * <li>A range which is empty is equal to any other empty range</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public interface Range<R extends Range<R>> extends Comparable<Range<?>> {

    /**
     * Get the starting position. <i>This method exists to allow Range objects
     * of heterogenous types to be compared. Integer and Long sub-interfaces
     * have type-specific <code>int</code> or <code>long</code> methods which
     * should be used instead.</i>
     *
     * @return The start position
     */
    Number startValue();

    /**
     * Get the size. <i>This method exists to allow Range objects of
     * heterogenous types to be compared. Integer and Long sub-interfaces have
     * type-specific <code>int</code> or <code>long</code> methods which should
     * be used instead.</i>
     *
     * @return The size, non-negative
     */
    Number sizeValue();

    /**
     * Create a new range of the same type as this one. This is used for
     * creating compatible temporary ranges to reply to queries for overlaps.
     *
     * @param start The start
     * @param size The size
     * @return A new range
     */
    R newRange(int start, int size);

    /**
     * Create a new range of the same type as this one. If the implementation
     * class does not support values as large as those passed, it should throw
     * an exception. This is used for creating compatible temporary ranges to
     * reply to queries for overlaps.
     *
     * @param start The start
     * @param size The size
     * @throws IllegalArgumentException if the size or start is negative, or if
     * the resulting range (start + size) would exceed the maximum value of
     * whatever type coordinates are stored as
     * @return A new range
     */
    R newRange(long start, long size);

    /**
     * Determine if this range and another are exactly adjacent but not
     * overlapping.
     *
     * @param r Another range
     * @return True if <code>r.end() == start()<code> or
     * <code>end() == r.start()</code>.
     */
    default boolean abuts(Range<?> r) {
        long myStart = startValue().longValue();
        long myEnd = myStart + sizeValue().longValue();
        long otherStart = r.startValue().longValue();
        long otherEnd = r.startValue().longValue() + r.sizeValue().longValue();
        return (otherEnd == myStart || myEnd == otherStart);
    }

    /**
     * Determine if this range is empty.
     *
     * @return True if the size is zero
     */
    default boolean isEmpty() {
        return sizeValue().intValue() == 0;
    }

    /**
     * Get the relation between this range and another, describing its relative
     * position and if and how it overlaps.
     *
     * @param other Another range
     * @return A relation
     */
    default RangeRelation relationTo(Range<?> other) {
        long myStart = startValue().longValue();
        long otherStart = other.startValue().longValue();
        long end = myStart + sizeValue().longValue();
        long otherEnd = otherStart + other.sizeValue().longValue();
        return RangeRelation.get(myStart, end, otherStart, otherEnd);
    }

    /**
     * Get the relation of the passed position to this range.
     *
     * @param position A position
     * @return A relation
     */
    default RangePositionRelation relationTo(long position) {
        return RangePositionRelation.get(startValue().longValue(),
                startValue().longValue() + sizeValue().longValue(),
                position);
    }

    /**
     * Get the relation of the passed position to this range.
     *
     * @param position A position
     * @return A relation
     */
    default RangePositionRelation relationTo(int position) {
        return RangePositionRelation.get(startValue().intValue(),
                startValue().longValue() + sizeValue().intValue(),
                position);
    }

    /**
     * Determine if this range contains the start or end of another.
     *
     * @param range A range
     * @return True if one of the passed range's boundaries is contained within
     * this one (is &gt;= start and &lt;end).
     */
    default boolean containsBoundary(Range<?> range) {
        if (matches(range)) {
            return false;
        }
        return contains(range.startValue().longValue())
                || contains(range.startValue().longValue() + range.sizeValue().longValue());
    }

    /**
     * Determine if the passed range is wholly contained by, but not equal to,
     * this one.
     *
     * @param range A range
     * @return True if it is contained
     */
    default boolean contains(Range<?> range) {
        return Range.this.relationTo(range) == RangeRelation.CONTAINS;
    }

    /**
     * Determine if this range is wholly contained by, but not equal to, the
     * passed one.
     *
     * @param range A range
     * @return True if this is contained by the passed range
     */
    default boolean isContainedBy(Range<?> range) {
        return Range.this.relationTo(range) == RangeRelation.CONTAINED;
    }

    /**
     * Determine if this range contains the passed position.
     *
     * @param position A position
     * @return True if the position is &gt;= start and &lt; end.
     */
    default boolean contains(int position) {
        return relationTo(position) == RangePositionRelation.IN;
    }

    /**
     * Determine if this range contains the passed position.
     *
     * @param position A position
     * @return True if the position is &gt;= start and &lt; end.
     */
    default boolean contains(long position) {
        return Range.this.relationTo(position) == RangePositionRelation.IN;
    }

    /**
     * Determine if this range and another overlap. Ranges in which the end is
     * equal to the start of another do <i>not</i> overlap.
     *
     * @param other
     * @return
     */
    default boolean overlaps(Range<?> other) {
        RangeRelation rel = Range.this.relationTo(other);
        switch (rel) {
            case AFTER:
            case BEFORE:
                return false;
            default:
                return true;
        }
    }

    /**
     * Get a sub-range representing the overlap between this and another range.
     * If there is no overlap, an empty range will be returned.
     *
     * @param other Another range
     * @return A range
     */
    default R getOverlap(Range<?> other) {
        if (other == this) {
            return cast();
        }
        RangeRelation rel = relationTo(other);
        long oStart = other.startValue().longValue();
        long oEnd = oStart + other.sizeValue().longValue();
        long myStart = startValue().longValue();
        long myEnd = myStart + sizeValue().longValue();

        switch (rel) {
            case EQUAL:
            case CONTAINED:
                return cast();
            case CONTAINS:
                if (other.getClass() == getClass()) {
                    return (R) other;
                }
                return newRange(oStart, oEnd - oStart);
            case STRADDLES_START:
                return newRange(oStart, myEnd - oStart);
            case STRADDLES_END:
                return newRange(myStart, oEnd - myStart);
            case AFTER:
            case BEFORE:
                return newRange(myStart, 0);
            default:
                throw new AssertionError(rel);
        }
    }

    /**
     * Cast this range as its generic type. All range implementations must be
     * able to be cast thus.
     *
     * @return This range as its generic type
     */
    @SuppressWarnings("unchecked")
    default R cast() {
        return (R) this;
    }

    /**
     * Get a list of ranges representing the <i>non-overlapping</i> regions
     * between this and another. If the ranges share a start or end position, a
     * single range will be returned; otherwise two will be returned.
     *
     * @param other Another range
     * @return A list of ranges consisting of position which are contained in
     * only one of either this or the passed range.
     */
    default List<R> nonOverlap(Range<?> other) {
        if (other == this) {
            return Collections.emptyList();
        }
        long[] startStops = new long[0];
        if (!matches(other)) {
            long myStart = startValue().longValue();
            long myEnd = myStart + sizeValue().longValue();
            long myStop = myEnd - 1;
            long otherStart = other.startValue().longValue();
            long otherEnd = otherStart + other.sizeValue().longValue();
            long otherStop = otherEnd - 1;
            if (contains(other)) {
                if (myStart == otherStart) {
                    startStops = new long[]{otherEnd, myStop};
                } else if (myStop == otherStop) {
                    startStops = new long[]{myStart, otherStart - 1};
                } else {
                    startStops = new long[]{myStart, otherStart - 1, otherEnd, myStop};
                }
            } else if (other.contains(this)) {
                if (myStart == otherStart) {
                    startStops = new long[]{myEnd, otherStop};
                } else if (myStop == otherStop) {
                    startStops = new long[]{otherStart, myStart - 1};
                } else {
                    startStops = new long[]{otherStart, myStart - 1, myEnd, otherStop};
                }
            } else if (containsBoundary(other)) {
                if (contains(otherStop)) {
                    if (otherStop == myStop) {
                        startStops = new long[]{otherStart, myStart - 1};
                    } else {
                        startStops = new long[]{otherStart, myStart - 1, otherStop + 1, myStop};
                    }
                } else if (contains(otherStart)) {
                    if (myStart == otherStart) {
                        startStops = new long[]{myStart, otherStart - 1};
                    } else {
                        startStops = new long[]{myStart, otherStart - 1, myEnd, otherStop};
                    }
                }
            }
        }
        List<R> result = new ArrayList<>(startStops.length / 2);
        for (int i = 0; i < startStops.length; i += 2) {
            long start = startStops[i];
            long stop = startStops[i + 1];
            result.add(newRange(start, stop - start + 1));
        }
        return result;
    }

    /**
     * Compare two ranges. Ranges are comparable as follows: If the ranges do
     * not overlap, they are sorted based on start position. If they share a
     * start position, the larger range is sorted first.
     *
     * @param o Another range
     * @return A comparison value
     */
    @Override
    default int compareTo(Range<?> o) {
        return relationTo(o).bias();
    }

    /**
     * Get the relation of the passed position to the start position.
     *
     * @param pos A position
     * @return The relationship - lesser, greater or equal
     */
    default PositionRelation relationToStart(int pos) {
        return PositionRelation.relation(pos, startValue().longValue());
    }

    /**
     * Get the relation of the passed position to the start position.
     *
     * @param pos A position
     * @return The relationship - lesser, greater or equal
     */
    default PositionRelation relationToEnd(int pos) {
        return PositionRelation.relation(pos, startValue().longValue() + sizeValue().longValue());
    }

    /**
     * Get the relation of the passed position to the start position.
     *
     * @param pos A position
     * @return The relationship - lesser, greater or equal
     */
    default PositionRelation relationToStart(long pos) {
        return PositionRelation.relation(pos, startValue().longValue());
    }

    /**
     * Get the relation of the passed position to the start position.
     *
     * @param pos A position
     * @return The relationship - lesser, greater or equal
     */
    default PositionRelation relationToEnd(long pos) {
        return PositionRelation.relation(pos, startValue().longValue() + sizeValue().longValue());
    }

    /**
     * Determine if the past range's start and end are <i>exactly the same</i>
     * as this one.
     *
     * @param r Another range
     * @return True if they represent the same range
     */
    default boolean matches(Range<?> r) {
        return r == this ? true : relationTo(r) == RangeRelation.EQUAL;
    }

    /**
     * Coalesce a list of ranges which may overlap, using the passed
     * {@link Coalescer} to create smaller ranges or combine ranges. This
     * presumes that the Range subtype contains some data payload which can
     * somehow be merged. For example, say you have range A from 0 to 10, and
     * range B from 5 to 15. The result will be a range A from 0-5, range AB
     * from 5 to 10 and range B from 10 to 15.
     *
     * @param <R> The range subtype
     * @param items The list of ranges to coalesce
     * @param c A coalescer which can combine ranges
     * @return A new list of coalesced ranges which may be larger than the
     * original, but which will contain no overlapping ranges.
     */
    public static <R extends Range<R>> List<R> coalesce(List<R> items, Coalescer<R> c) {
        if (items.size() <= 1) {
            return items;
        }
        items = new ArrayList<>(items);
        RangeHolder holder = new RangeHolder(items.get(0));
        for (int i = 1; i < items.size(); i++) {
            holder = holder.coalesce(items.get(i), c);
        }
        return holder.toList();
    }

    /**
     * Get a range which represents the span of the passed list of ranges.
     *
     * @param items The ranges
     * @return A long range
     */
    public static LongRange<? extends LongRange> span(Iterable<? extends Range<?>> items) {
        long start = Long.MAX_VALUE;
        long end = Long.MIN_VALUE;
        for (Range<?> r : items) {
            start = Math.min(start, r.startValue().longValue());
            end = Math.max(end, r.startValue().longValue() + r.sizeValue().longValue());
        }
        if (start == Long.MAX_VALUE) {
            return of(0L, 0L);
        }
        return of(start, end - start);
    }

    /**
     * Visit each position within this range as an int.
     *
     * @param consumer A consumer
     * @return this
     */
    default R forEachPosition(IntConsumer consumer) {
        int end = startValue().intValue() + sizeValue().intValue();
        int start = startValue().intValue();
        for (int i = start; i < end; i++) {
            consumer.accept(i);
        }
        return cast();
    }

    /**
     * Visit each position within this range as an int.
     *
     * @param consumer A consumer
     * @return this
     */
    default R forEachPosition(LongConsumer consumer) {
        long end = startValue().longValue() + sizeValue().longValue();
        long start = startValue().longValue();
        for (long i = start; i < end; i++) {
            consumer.accept(i);
        }
        return cast();
    }

    /**
     * Coalesce this range and another using the passed coalescer.
     *
     * @param other Another range
     * @param by A coalescer which can combine ranges
     * @return A list of ranges which coalesces this and another and may contain
     * more than two ranges
     */
    default List<R> coalesce(R other, Coalescer<R> by) {
        if (isEmpty()) {
            return other.isEmpty() ? Collections.emptyList() : Arrays.asList(other);
        } else if (other.isEmpty()) {
            return isEmpty() ? Collections.emptyList() : Arrays.asList(cast());
        }
        RangeRelation rel = relationTo(other);
        switch (rel) {
            case AFTER:
                return Arrays.asList(other, cast());
            case BEFORE:
                return Arrays.asList(cast(), other);
            case EQUAL:
                return Arrays.asList(by.combine(cast(), other, startValue().intValue(), sizeValue().intValue()));
            default:
                List<R> result = new ArrayList<>();
                R over = getOverlap(other);
                if (!over.isEmpty()) {
                    result.add(by.combine(cast(), other, over.startValue().intValue(), over.sizeValue().intValue()));
                }
                List<R> non = nonOverlap(other);
                for (R r : non) {
                    if (contains(r)) {
                        result.add(by.resized(cast(), r.startValue().intValue(), r.sizeValue().intValue()));
                    } else {
                        result.add(by.resized(other, r.startValue().intValue(), r.sizeValue().intValue()));
                    }
                }
                Collections.sort(result);
                return result;
        }
    }

    /**
     * Create a range with the same size, with the start position this one's
     * start position plus the passed amount.
     *
     * @param amount The amount to shift by
     * @return This if the amount is zero, otherwise a new range
     */
    default R shiftedBy(int amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(startValue().longValue() + amount, sizeValue().longValue());
    }

    /**
     * Create a range with the same start position and this one, with the size
     * altered by adding the passed amount. May throw an exception if the
     * resulting size is negative.
     *
     * @param amount An amount
     * @return A new range unless the amount is zero
     */
    default R grownBy(int amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(startValue().longValue(), sizeValue().longValue() + amount);
    }

    /**
     * Create a range with the same start position and this one, with the size
     * altered by subtracting the passed amount. May throw an exception if the
     * resulting size is negative.
     *
     * @param amount An amount
     * @return A new range unless the amount is zero
     */
    default R shrunkBy(int amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(startValue().longValue(), sizeValue().longValue() - amount);
    }

    /**
     * Create a range with the same size, with the start position this one's
     * start position plus the passed amount.
     *
     * @param amount The amount to shift by
     * @return This if the amount is zero, otherwise a new range
     */
    default R shiftedBy(long amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(startValue().longValue() + amount, sizeValue().longValue());
    }

    /**
     * Create a range with the same start position and this one, with the size
     * altered by adding the passed amount. May throw an exception if the
     * resulting size is negative.
     *
     * @param amount An amount
     * @return A new range unless the amount is zero
     */
    default R grownBy(long amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(startValue().longValue(), sizeValue().longValue() + amount);
    }

    /**
     * Create a range with the same start position and this one, with the size
     * altered by subtracting the passed amount. May throw an exception if the
     * resulting size is negative.
     *
     * @param amount An amount
     * @return A new range unless the amount is zero
     */
    default R shrunkBy(long amount) {
        if (amount == 0) {
            return cast();
        }
        return newRange(startValue().longValue(), sizeValue().longValue() - amount);
    }

    /**
     * Create an integer range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return An integer range
     */
    public static IntRange<? extends IntRange<?>> of(int start, int length) {
        return new FixedIntRange(start, length);
    }

    /**
     * Create an integer range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return An integer range
     */
    public static <T> DataIntRange<T, ? extends DataIntRange<T, ?>> of(int start, int length, T obj) {
        return new DataFixedIntRange(start, length, obj);
    }

    /**
     * Create a long range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return An integer range
     */
    public static <T> DataLongRange<T, ? extends DataLongRange<T, ?>> of(long start, long length, T obj) {
        return new DataFixedLongRange(start, length, obj);
    }

    /**
     * Create an integer range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return An integer range
     */
    public static <T> DataIntRange<T, ? extends DataIntRange<T, ?>> ofCoordinates(int start, int end, T obj) {
        int st = Math.min(start, end);
        return new DataFixedIntRange(st, Math.max(start, end) - st, obj);
    }

    /**
     * Create a long range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return An integer range
     */
    public static <T> DataLongRange<T, ? extends DataLongRange<T, ?>> ofCoordinates(long start, long end, T obj) {
        long st = Math.min(start, end);
        return new DataFixedLongRange(start, Math.max(end, start) - st, obj);
    }

    /**
     * Create a long range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return A long range
     */
    public static LongRange<? extends LongRange<?>> of(long start, long length) {
        return new FixedLongRange(start, length);
    }

    /**
     * Create a integer range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return An integer range
     */
    public static IntRange<? extends IntRange<?>> ofCoordinates(int start, int end) {
        int st = Math.min(start, end);
        return new FixedIntRange(st, Math.max(start, end) - st);
    }

    /**
     * Create a long range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return A long range
     */
    public static LongRange<? extends LongRange<?>> ofCoordinates(long start, long end) {
        long st = Math.min(start, end);
        return new FixedLongRange(st, Math.max(start, end) - st);
    }

    /**
     * Create a mutable integer range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return An integer range
     */
    public static MutableIntRange<? extends MutableIntRange<?>> mutableOf(int start, int length) {
        return Range.mutableOf(start, length, false);
    }

    /**
     * Create a mutable long range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return A long range
     */
    public static MutableLongRange<? extends MutableLongRange<?>> mutableOf(long start, long length) {
        return mutableOf(start, length, false);
    }

    /**
     * Create a mutable integer range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param end The size, non-negative
     * @return An integer range
     */
    public static MutableIntRange<? extends MutableIntRange<?>> mutableOfCoordinates(int start, int end) {
        return mutableOfCoordinates(start, end, false);
    }

    /**
     * Create a mutable long range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return A long range
     */
    public static MutableLongRange<?> mutableOfCoordinates(long start, long end) {
        return mutableOfCoordinates(start, end, false);
    }

    /**
     * Create a mutable integer range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param end The size, non-negative
     * @return An integer range
     */
    public static MutableIntRange<? extends MutableIntRange<?>> mutableOfCoordinates(int start, int end, boolean sync) {
        int st = Math.min(start, end);
        return Range.mutableOf(st, Math.max(end, start) - st, sync);
    }

    /**
     * Create a mutable long range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @return A long range
     */
    public static MutableLongRange<?> mutableOfCoordinates(long start, long end, boolean sync) {
        long st = Math.min(start, end);
        return mutableOf(st, Math.max(end, start) - st, sync);
    }

    /**
     * Create a mutable integer range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @param sync If true, the result's mutator methods will be synchronized
     * @return An integer range
     */
    public static MutableIntRange<? extends MutableIntRange<?>> mutableOf(int start, int length,
            boolean sync) {
        return sync ? new SynchronizedMutableIntRange(start, length)
                : new MutableIntRangeImpl(start, length);
    }

    /**
     * Create a mutable long range with immutable start and size.
     *
     * @param start The start position, non-negative
     * @param length The size, non-negative
     * @param sync If true, the result's mutator methods will be synchronized
     * @return An integer range
     */
    public static MutableLongRange<? extends MutableLongRange<?>> mutableOf(long start, long length,
            boolean sync) {
        return sync ? new SynchronizedMutableLongRange(start, length)
                : new MutableLongRangeImpl(start, length);
    }
}

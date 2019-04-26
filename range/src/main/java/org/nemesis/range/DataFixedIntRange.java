package org.nemesis.range;

import java.util.function.Supplier;
import static org.nemesis.range.RangeHolder.checkStartAndSize;

/**
 *
 * @author Tim Boudreau
 */
final class DataFixedIntRange<T> implements DataIntRange<T, DataFixedIntRange<T>>, Supplier<T>, DataRange<T, DataFixedIntRange<T>> {

    private final int start;
    private final int size;
    private final T object;

    DataFixedIntRange(int start, int size, T object) {
        checkStartAndSize(start, size);
        this.start = start;
        this.size = size;
        this.object = object;
    }

    @Override
    public T get() {
        return object;
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
        return "'" + get() + "' " + start() + ":" + end() + "(" + size() + ")";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + this.start;
        hash = 37 * hash + this.size;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj instanceof Range<?>) {
            if (obj instanceof IntRange<?>) {
                IntRange<?> oi = (IntRange<?>) obj;
                return oi.start() == start() && oi.size() == size();
            } else if (obj instanceof LongRange<?>) {
                LongRange<?> li = (LongRange<?>) obj;
                return li.start() == start() && li.size() == size();
            }
            Range<?> r = (Range<?>) obj;
            return matches(r);
        }
        return false;
    }

    @Override
    public DataFixedIntRange<T> newRange(int start, int size, T newObject) {
        return new DataFixedIntRange<>(start, size, newObject);
    }

    @Override
    public DataFixedIntRange<T> newRange(long start, long size, T newObject) {
        return new DataFixedIntRange<>((int) start, (int) size, newObject);
    }

    @Override
    public DataFixedIntRange<T> newRange(int start, int size) {
        return new DataFixedIntRange<>(start, size, object);
    }

    @Override
    public DataFixedIntRange<T> newRange(long start, long size) {
        return new DataFixedIntRange<>((int) start, (int) size, object);
    }

}

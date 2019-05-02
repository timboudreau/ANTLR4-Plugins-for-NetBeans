package org.nemesis.data;

import com.mastfrog.range.Range;

/**
 *
 * @author Tim Boudreau
 */
public final class FixedRangeImpl implements IndexAddressable.IndexAddressableItem {

    private final int index;
    private final int start;
    private final int size;

    public FixedRangeImpl(int index, int start, int size) {
        this.index = index;
        this.start = start;
        this.size = size;
    }

    @Override
    public int index() {
        return index;
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
    public IndexAddressable.IndexAddressableItem newRange(int start, int size) {
        return new FixedRangeImpl(index, start, size);
    }

    @Override
    public IndexAddressable.IndexAddressableItem newRange(long start, long size) {
        return new FixedRangeImpl(index, (int) start, (int) size);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Range<?> && ((Range<?>) o).matches(this);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + this.start;
        hash = 37 * hash + this.size;
        return hash;
    }

    @Override
    public String toString() {
        return start() + ":" + end() + "(" + size() + "){" + index + "}";
    }

}

/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.data;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.nemesis.data.SemanticRegions.SemanticRegionImpl;
import org.nemesis.data.impl.ArrayUtil;
import org.nemesis.data.impl.SizedArrayValueSupplier;

/**
 * A collection of nestable semantic regions, which have some (optional) data
 * associated with them. A semantic region is typically used for something such
 * as nested <code>{}</code> blocks in a Java source file. This class
 * effeciently and compactly represents those, including their nesting
 * relationship and allows you to both iterate them, and find the block (and its
 * parents) for a given source position.
 * <p>
 * This class takes advantage of the order in which an Antlr parser scans
 * elements, and has some very specific associated constraints. In particular,
 * it creates an <i>arbitrarily nested</i> data structure, using only two arrays
 * of start and end offsets, under the hood, and using a modified binary search
 * algorithms for fast lookups within it. In particular:
 * </p>
 * <ul>
 * <li>The <code>start</code> argument of a call to add() must be greater than
 * or equal to the <code>start</code> argument to any preceding call to add</li>
 * <li>The <code>end</code> argument of a call to add() may be less than the end
 * position of a prior call to add, if the start position is greater than or
 * equal to that prior call's start position - in other words, you can add
 * bounds which are
 * <i>contained within</i> previously added bounds if no add has occurred that
 * would conflict with that - you can add 10:20 and then add 11:15 (resulting in
 * an 11:15 SemanticRegion that is a child of the 10:15 one), but not if you
 * already added, say, 12:16 - the start positions are always >= the preceding
 * start</li>
 * <li>Regions may not straddle each other - you may have two regions nested
 * within each other which have the <i>same</i> bounds, but not one which starts
 * inside one and ends after it</li>
 * </ul>
 * <p>
 * In other words, as you parse nestable semantic structures, such as {} blocks
 * in Java, add the outermost one first, then inner ones as they are
 * encountered.
 * </p><p>
 * This class can also be used for non-nested data structures efficiently, as
 * the more complex logic for dealing with nesting is only active if nesting is
 * present.
 * </p>
 * <p>
 * <b>Note:</b> The <code>SemanticRegion</code> instances emitted are
 * <i>flyweight objects</i> - they are created on demand, and while they honor
 * the <code>equals()</code> contract, you will not get the same instance
 * returned for the same input twice.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class SemanticRegions<T> implements Iterable<SemanticRegion<T>>, Serializable, IndexAddressable<SemanticRegion<T>> {

    private static final int BASE_SIZE = 3;

    private int[] starts;
    private T[] keys;
    private int[] ends;
    private int size;
    private boolean hasNesting = false;
    private int firstUnsortedEndsEntry = -1;

    /*
    Organizes a nested structure as two arrays, starts and ends.  The starts
    array is sorted, but the search algorithm is duplicate-tolerant (and not
    brute-force).  Regions are added in the order, largestWithChildren,
    nested, nested;  nested elements can have other nested elements the
    same size or smaller than themselvs - all it
    means to be nested is to have a start >= the start of the preceding
    element, and an end <= the end of the preceding element.  Out-of-order
    additions throw an exception.

    This is the same order that an antlr parse will discover the elements,
    so it is as simple as adding the offsets of each element of interest
    as you go.

    So we can end up with

    [12 [13 [13 14] [14 15] 15] 15]]
    giving us a starts array of
    [12, 13, 13, 14]
    and an ends array of
    [15, 14, 15, 15]

    The starts array will always be sorted but may contain duplicates.
    The ends array is unsorted.  Anything seeking to starts or ends
    will need to scan backwards or forwards some amount to ensure it hit
    its target.

    So we need a duplicate-tolerant variant on a ranged binary search.

    The upside is, the data structure is very small, and scans are typically
    of 1-3 elements.
     */
    @SuppressWarnings("unchecked")
    public SemanticRegions(Class<T> type) {
        starts = new int[BASE_SIZE];
        ends = new int[BASE_SIZE];
        keys = type == null || type == Void.class || type == Void.TYPE ? null : (T[]) Array.newInstance(type, BASE_SIZE);
        size = 0;
    }

    SemanticRegions(int[] starts, int[] ends, T[] keys, int size, int firstUnsortedEndsEntry, boolean hasNesting) {
        this.starts = starts;
        this.ends = ends;
        this.keys = keys;
        this.size = size;
        this.firstUnsortedEndsEntry = firstUnsortedEndsEntry;
        this.hasNesting = hasNesting;
    }

    @SuppressWarnings("unchecked")
    private static SemanticRegions<?> EMPTY = new SemanticRegions(null);

    @SuppressWarnings("unchecked")
    public static <T> SemanticRegions<T> empty() {
        return (SemanticRegions<T>) EMPTY;
    }

    SemanticRegions<T> trim() {
        if (starts.length > size) {
            starts = Arrays.copyOf(starts, size);
            ends = Arrays.copyOf(ends, size);
            if (keys != null) {
                keys = Arrays.copyOf(keys, size);
            }
        }
        return this;
    }

    SemanticRegions<T> copy() {
        int[] newStarts = Arrays.copyOf(starts, size);
        int[] newEnds = Arrays.copyOf(ends, size);
        T[] newKeys = null;
        if (keys != null) {
            newKeys = Arrays.copyOf(keys, size);
        }
        return new SemanticRegions<>(newStarts, newEnds, newKeys, size, firstUnsortedEndsEntry, hasNesting);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int indexOf(Object o) {
        if (o != null && o.getClass() == SemanticRegionImpl.class && ((SemanticRegionImpl) o).owner() == this) {
            return ((SemanticRegion<?>) o).index();
        }
        return -1;
    }

    /**
     * Iterate all regions and collect those where the key matches the passed
     * predicate.
     *
     * @param pred A predicate
     * @return A list of regions
     */
    public List<? extends SemanticRegion<T>> collect(Predicate<T> pred) {
        List<SemanticRegion<T>> result = new LinkedList<>();
        for (SemanticRegion<T> s : this) {
            if (pred.test(s.key())) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Determine if the passed item was produced by this collection.
     *
     * @param item An item
     * @return Whether or not its type and owner match this collection
     */
    @Override
    public boolean isChildType(IndexAddressableItem item) {
        return item instanceof SemanticRegion<?>;
    }

    @SuppressWarnings("unchecked")
    public static <T> SemanticRegionsBuilder<T> builder(Class<? super T> type) {
        return new SemanticRegionsBuilder<>((Class<T>) type);
    }

    public static SemanticRegionsBuilder<Void> builder() {
        return new SemanticRegionsBuilder<>(Void.class);
    }

    public SemanticRegions<T> combineWith(SemanticRegions<T> other) {
        assert keyType() == other.keyType() : "Incompatible key types " + keyType() + " and " + other.keyType();
        int firstUnsortedEndsEntry = Integer.MAX_VALUE;
        boolean nested = false;
        int sz = other.size() + size();
        int[] sts = new int[sz];
        int[] es = new int[sz];
        T[] ks = ArrayUtil.genericArray(keyType(), sz);

        int sizeA = size;
        int sizeB = other.size;
        int cursorA = 0;
        int cursorB = 0;
        for (int i = 0; i < sz; i++) {
            int startA, startB, endA, endB;
            T keyA, keyB;
            keyA = keyB = null;
            startA = startB = endA = endB = -1;
            if (cursorA < sizeA) {
                startA = starts[cursorA];
                endA = ends[cursorA];
                keyA = keys[cursorA];
            }
            if (cursorB < sizeB) {
                startB = other.starts[cursorB];
                endB = other.ends[cursorB];
                keyB = other.keys[cursorB];
            }
            if (startA == -1) {
                sts[i] = startB;
                es[i] = endB;
                ks[i] = keyB;
                cursorB++;
            } else if (startB == -1) {
                sts[i] = startA;
                es[i] = endA;
                ks[i] = keyA;
                cursorA++;
            } else {
                if (startA == startB) {
                    if (endB > endA) {
                        sts[i] = startB;
                        es[i] = endB;
                        ks[i] = keyB;
                        cursorB++;
                    } else if (endB <= endA) {
                        sts[i] = startA;
                        es[i] = endA;
                        ks[i] = keyA;
                        cursorA++;
                    }
                    firstUnsortedEndsEntry = Math.min(i, firstUnsortedEndsEntry);
                } else if (startA > startB) {
                    sts[i] = startB;
                    es[i] = endB;
                    ks[i] = keyB;
                    cursorB++;
                } else {
                    sts[i] = startA;
                    es[i] = endA;
                    ks[i] = keyA;
                    cursorA++;
                }
            }
            if (i > 0) {
                int prevStart = sts[i - 1];
                int prevEnd = es[i - 1];
                int currStart = sts[i];
                int currEnd = es[i];
                if (currStart > prevStart && currStart < prevEnd && currEnd > prevEnd) {
                    throw new IllegalStateException("Regions cannot straddle "
                            + "one another, but " + ks[i - 1] + " and "
                            + ks[i] + " will.  These region sets cannot be combined.");
                }
            }
        }
        return new SemanticRegions<>(sts, es, ks, sz, firstUnsortedEndsEntry, nested);
    }

    @SuppressWarnings("unchecked")
    public Class<T> keyType() {
        return keys == null ? (Class<T>) Void.class : (Class<T>) keys.getClass().getComponentType();
    }

    @Override
    public SemanticRegion<T> forIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Index out of bounds "
                    + index + " in SemanticRegions of size " + size);
        }
        return new SemanticRegionImpl(index, -1);
    }

    public static class SemanticRegionsBuilder<T> {

        private SemanticRegions<T> regions;

        private SemanticRegionsBuilder(Class<T> type) {
            regions = new SemanticRegions<>(type);
        }

        public int size() {
            return regions.size();
        }

        public SemanticRegionsBuilder<T> add(T key, int start, int end) {
            assert key == null || regions.keys.getClass().getComponentType().isInstance(key) :
                    "Bad key type: " + key + " (" + key.getClass() + ")";
            regions.add(key, start, end);
            return this;
        }

        public SemanticRegionsBuilder<T> add(int start, int end) {
            return add(null, start, end);
        }

        public SemanticRegions<T> build() {
            return regions.copy();
        }
    }

    @Override
    public String toString() {
        String typeName = keys == null ? "Void" : keys.getClass().getComponentType().getSimpleName();
        StringBuilder sb = new StringBuilder("SemanticRegions<").append(typeName).append(">{\n");
        for (SemanticRegion<T> reg : this) {
            int d = reg.nestingDepth();
            if (d > 0) {
                char[] c = new char[d * 2];
                Arrays.fill(c, ' ');
                sb.append(c);
            }
            sb.append(reg).append('\n');
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public int size() {
        return size;
    }

    public List<T> keysAtPoint(int pos) {
        LinkedList<T> result = new LinkedList<>();
        keysAtPoint(pos, result);
        return result;
    }

    public void keysAtPoint(int pos, Collection<? super T> into) {
        SemanticRegion<T> reg = at(pos);
        if (reg != null) {
            SemanticRegion<T> outer = reg.outermost();
            if (outer != null) {
                reg = outer;
            }
            reg.keysAtPoint(pos, into);
        }
    }

    int indexAtPoint(int pos) {
        if (!hasNesting) {
            return ArrayUtil.rangeBinarySearch(pos, starts, new MES(), size);
        }
        if (firstUnsortedEndsEntry > 0) {
            int lastSortedEnd = ends[firstUnsortedEndsEntry - 1];
            if (lastSortedEnd > pos) {
                return ArrayUtil.rangeBinarySearch(pos, 0, lastSortedEnd - 1, starts, new MES(), size);
            }
        }
        int[] id = indexAndDepthAt(pos);
        return id[0];
    }

    boolean checkInvariants() {
        for (int i = 1; i < size; i++) {
            int prevStart = starts[i - 1];
            int prevEnd = ends[i - 1];
            int start = starts[i];
            int end = ends[i];
            if (prevEnd <= start) {
                continue;
            }
            if (prevEnd > start && end > prevEnd) {
                throw new IllegalStateException("Straddle encountered at " + i
                        + " " + prevStart + ":" + prevEnd + " vs. "
                        + start + ":" + end);
            }
            if (start < prevStart) {
                throw new IllegalStateException("Starts array is not "
                        + "sorted at " + i + ": " + start + " with prev start "
                        + prevStart + " in " + Arrays.toString(starts));
            }
        }
        return true;
    }

    void add(T key, int start, int end) {
        if (start >= end) {
            throw new IllegalArgumentException("Start is >= end - "
                    + start + ":" + end);
        } else if (start < 0 || end < 0) {
            throw new IllegalArgumentException("Negative offsets " + start + ":" + end);
        }
        if (size == 0) {
            starts[0] = start;
            ends[0] = end;
            if (keys != null) {
                keys[0] = key;
            }
            size++;
            return;
        }
        int lastEnd = ends[size - 1];
        int lastStart = starts[size - 1];
        if (start < lastStart || (start == lastStart && end > lastEnd)) {
            throw new IllegalArgumentException("Add out of order - adding "
                    + start + ":" + end + " after add of " + lastStart + ":"
                    + lastEnd + " regions must be added in order, and when "
                    + "container and contained, the largest must "
                    + "be added first, the successively smaller.  Regions "
                    + "may not straddle each other. With key "
                    + (key == null
                            ? "<null>"
                            : key.getClass().getName() + ": " + key)
                    + " to " + this);
        }
        for (int i = size - 2; i >= 0; i--) {
            int st = starts[i];
            int en = ends[i];
            if (start < en) {
                if (end > en) {
                    throw new IllegalArgumentException("Add out of order - adding "
                            + start + ":" + end + " after add of " + st + ":"
                            + en + " regions must be added in order, and when "
                            + "container and contained, the largest must "
                            + "be added first, the successively smaller.  Regions "
                            + "may not straddle each other.");
                }
            }
        }
        maybeGrow(size + 1);
        starts[size] = start;
        ends[size] = end;
        if (keys != null) {
            keys[size] = key;
        }
        if (firstUnsortedEndsEntry == -1 && end <= lastEnd) {
            firstUnsortedEndsEntry = size;
        }
        size++;
        hasNesting |= start <= lastStart || end <= lastEnd;
        assert checkInvariants();
    }

    @Override
    public SemanticRegion<T> at(int pos) {
        int[] id = indexAndDepthAt(pos);
        if (id[0] == -1) {
            return null;
        }
        return new SemanticRegionImpl(id[0], id[1]);
    }

    int[] indexAndDepthAt(int pos) {
        if (!hasNesting) {
            int ix = ArrayUtil.rangeBinarySearch(pos, starts, new MES(), size);
            return new int[]{ix, ix == 0 ? 0 : -1};
        }
        return ArrayUtil.nestingBinarySearch(pos, starts, ends, size, hasNesting, firstUnsortedEndsEntry);
    }

    private void grow(int targetArrayLength) {
        starts = Arrays.copyOf(starts, targetArrayLength);
        ends = Arrays.copyOf(ends, targetArrayLength);
        if (keys != null) {
            keys = Arrays.copyOf(keys, targetArrayLength);
        }
    }

    private int arrayLength() {
        return starts.length;
    }

    private void maybeGrow(int targetSize) {
        int len = arrayLength();
        if (targetSize >= len) {
            if (len > BASE_SIZE * 3) {
                grow(Math.max(len + BASE_SIZE, len + (len / 3)));
            } else {
                grow(len + BASE_SIZE);
            }
        }
    }

    /**
     * Returns an iterator of <i>all</i> elements in this set of regions, nested
     * and non-nested, in order of occurrance.
     *
     * @return An iterator
     */
    @Override
    public Iterator<SemanticRegion<T>> iterator() {
        return new It();
    }

    /**
     * Returns an iterable of only those elements which are non-nested.
     *
     * @return An iterable
     */
    public Iterable<SemanticRegion<T>> outermostElements() {
        return new OutermostIterator();
    }

    /**
     * Return an iterable of the outermost keys.
     *
     * @return
     */
    public Iterable<T> outermostKeys() {
        return new OutermostKeysIterator();
    }

    public static <T, R> boolean differences(SemanticRegions<T> a, SemanticRegions<R> b, BiConsumer<Set<SemanticRegion<T>>, Set<SemanticRegion<R>>> consumer) {
        if (a == b || (a.size == b.size && Arrays.equals(a.starts, b.starts) && Arrays.equals(a.ends, b.ends))) {
            consumer.accept(Collections.emptySet(), Collections.emptySet());
            return false;
        }
        int start = 0;
        for (int i = 0; i < Math.min(a.size, b.size); i++) {
            if (a.starts[i] == b.starts[i] && a.ends[i] == b.ends[i]) {
                start++;
            } else {
                break;
            }
        }
        Set<SemanticRegion<T>> removed = new HashSet<>();
        Set<SemanticRegion<R>> added = new HashSet<>();
        for (int i = start; i < Math.max(a.size, b.size); i++) {
            if (i < a.size && i < b.size) {
                if (!a.containsExactBounds(b.starts[i], b.ends[i])) {
                    added.add(b.forIndex(i));
                }
                if (!b.containsExactBounds(a.starts[i], a.ends[i])) {
                    removed.add(a.forIndex(i));
                }
            } else if (i < a.size && i >= b.size) {
                if (!b.containsExactBounds(a.starts[i], a.ends[i])) {
                    removed.add(a.forIndex(i));
                }
            } else if (i >= a.size && i < b.size) {
                if (!a.containsExactBounds(b.starts[i], b.ends[i])) {
                    added.add(b.forIndex(i));
                }
            }
        }
        consumer.accept(removed, added);
        return !removed.isEmpty() || !added.isEmpty();
    }

    private boolean containsExactBounds(int start, int end) {
        for (int i = 0; i < size; i++) {
            if (starts[i] == start && ends[i] == end) {
                return true;
            }
            if (starts[i] > start) {
                return false;
            }
        }
        return false;
        // Fixme - this could be done more efficiently
//        int offset = ArrayUtil.lastOffsetLessThanOrEqualTo(start, starts, size, Bias.BACKWARD);
//        while (offset >= 0 && starts[offset] >= start) {
//            offset--;
//        }
//        if (offset < 0 || starts[offset] != start) {
//            return false;
//        }
//        int foundEnd = ends[offset];
//        if (foundEnd == end) {
//            return true;
//        }
//        while (offset < size && starts[offset] == start) {
//            if (ends[offset] == end) {
//                return true;
//            }
//            offset++;
//        }
//        return false;
    }

    /**
     * Return whether or not the contents of this SemanticRegions are equal -
     * this is not implemented in equals() because SemanticRegions instances are
     * routinely added to sets, and their identity, not value is what is useful
     * there; and this involves comparing multiple arrays which may be large.
     *
     * @param other
     * @return Whether or not the contents of another SemanticRegions instance
     * is identical to this one.
     */
    public boolean equalTo(SemanticRegions<?> other) {
        if (other == this) {
            return true;
        } else if (other == null) {
            return false;
        }
        boolean result = size == other.size
                && ((keys == null) == (other.keys == null))
                && Arrays.equals(starts, other.starts)
                && Arrays.equals(ends, other.ends);
        if (result && keys != null) {
            result &= Arrays.equals(keys, other.keys);
        }
        return result;
    }

    /**
     * Create an index which uses the passed comarator. If there are duplicate
     * keys and the region for one such is requested, some region will be
     * returned. Any elements with null keys are omitted from the index.
     * <p>
     * If the type of this SemanticRegions is Void, no index is possible and an
     * exception is thrown.
     * </p>
     *
     * @param comp A comparator
     * @return An index
     */
    public Index<T> index(Comparator<T> comp) {
        if (keys == null) {
            throw new IllegalStateException("Cannot create an index over Void");
        }
        return new IndexImpl(comp);
    }

    /**
     * Create an index over a SemanticRegions instance whose key type implements
     * Comparable.
     *
     * @param <T> The type
     * @param reg The regions
     * @return An index
     */
    public static <T extends Comparable<T>> Index<T> index(SemanticRegions<T> reg) {
        Comparator<T> comp = (a, b) -> {
            return a.compareTo(b);
        };
        return reg.index(comp);
    }

    /**
     * An index which allows semantic regions to be looked up by their key data.
     *
     * @param <T>
     */
    public interface Index<T> {

        SemanticRegion<T> get(T key);

        int size();
    }

    private class IndexImpl implements Index<T> {

        private final T[] keysSorted;
        private final int[] indices;
        private final Comparator<T> comparator;

        @SuppressWarnings("unchecked")
        IndexImpl(Comparator<T> comparator) {
            Set<ComparableStub<T>> temp = new TreeSet<>((a, b) -> {
                return comparator.compare(a.key, b.key);
            });
            for (int i = 0; i < size; i++) {
                if (keys[i] != null) {
                    temp.add(new ComparableStub<>(keys[i], i));
                }
            }
            int sz = temp.size();
//            if (sz < size) {
//                System.err.println("Duplicate keys encountered - index will omit them");
//            }
            T[] sortedKeys = (T[]) Array.newInstance(SemanticRegions.this.keys.getClass().getComponentType(), sz);
            keysSorted = sortedKeys;

//            this.keysSorted = temp.toArray(sortedKeys);
            this.comparator = comparator;
            this.indices = new int[sz];
            int ix = 0;
            for (ComparableStub<T> c : temp) {
                indices[ix++] = c.originalIndex;
                sortedKeys[ix - 1] = c.key;
            }
        }

        @Override
        public int size() {
            return keysSorted.length;
        }

        @Override
        public SemanticRegion<T> get(T key) {
            int offset = Arrays.binarySearch(keysSorted, key, comparator);
            return offset < 0 ? null : new SemanticRegionImpl(indices[offset], -1);
        }
    }

    static final class ComparableStub<T> {

        final T key;
        final int originalIndex;

        ComparableStub(T key, int originalIndex) {
            this.key = key;
            this.originalIndex = originalIndex;
        }
    }

    private class OutermostKeysIterator implements Iterator<T>, Iterable<T> {

        private final OutermostIterator om;

        OutermostKeysIterator() {
            this(new OutermostIterator());
        }

        OutermostKeysIterator(OutermostIterator it) {
            om = it;
        }

        @Override
        public boolean hasNext() {
            return om.hasNext();
        }

        @Override
        public T next() {
            return om.next().key();
        }

        @Override
        public Iterator<T> iterator() {
            OutermostIterator om2 = om.iterator();
            return om2 == om ? this : new OutermostKeysIterator(om2);
        }
    }

    private class OutermostIterator implements Iterator<SemanticRegion<T>>, Iterable<SemanticRegion<T>> {

        private int ix = 0;
        private int highestEndSeen = -1;
        private boolean consumed = size <= 0;

        OutermostIterator() {
            if (size > 0) {
                highestEndSeen = ends[0];
                consumed = false;
            }
        }

        @Override
        public boolean hasNext() {
            if (ix >= size) {
                return false;
            }
            if (consumed) {
                for (; ix < size; ix++) {
                    int end = ends[ix];
                    if (end > highestEndSeen) {
                        highestEndSeen = end;
                        consumed = false;
                        break;
                    }
                }
            }
            return ix < size;
        }

        @Override
        public SemanticRegion<T> next() {
            if (ix >= size) {
                throw new NoSuchElementException("Iterator exhausted at " + ix);
            }
            consumed = true;
            return new SemanticRegionImpl(ix++, 0);
        }

        @Override
        public OutermostIterator iterator() {
            if (ix == 0) {
                return this;
            }
            return new OutermostIterator();
        }
    }

    private class It implements Iterator<SemanticRegion<T>> {

        private int ix = -1;

        @Override
        public boolean hasNext() {
            return ix + 1 < size;
        }

        @Override
        public SemanticRegion<T> next() {
            return new SemanticRegionImpl(++ix, -1);
        }
    }

    final class SemanticRegionImpl implements SemanticRegion<T> {

        private final int index;
        private int depth;

        SemanticRegionImpl(int index, int depth) {
            this.index = index;
            this.depth = depth;
        }

        SemanticRegions<T> owner() {
            return SemanticRegions.this;
        }

        @Override
        public boolean hasChildren() {
            if (index == size - 1) {
                return false;
            }
            return ends[index + 1] <= ends[index];
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            } else if (o == this) {
                return true;
            } else if (o instanceof SemanticRegion<?>) {
                SemanticRegion<?> other = (SemanticRegion<?>) o;
//                if (other.getClass() == getClass()) {
//                    SemanticRegionImpl i = (SemanticRegionImpl) other;
//                    return owner() == i.owner() && index == i.index;
//                }
                return other.start() == start() && other.end() == end() && Objects.equals(key(), other.key());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (start() * 123_923) * end() + (7 * Objects.hashCode(key()));
        }

        @Override
        public SemanticRegionImpl parent() {
            if (depth == 0) {
                return null;
            }
            int targetIndex = -1;
            for (int i = index - 1; i >= 0; i--) {
                if (starts[i] <= start() && ends[i] >= end()) {
                    targetIndex = i;
                    break;
                }
            }
            return targetIndex == -1 ? null : new SemanticRegionImpl(targetIndex, -1);
        }

        @Override
        public SemanticRegionImpl outermost() {
            if (depth == 0) {
                return null;
            }
            int targetIndex = -1;
            for (int i = index - 1; i >= 0; i--) {
                if (starts[i] <= start() && ends[i] >= end()) {
                    targetIndex = i;
                }
            }
            return targetIndex == -1 ? null : new SemanticRegionImpl(targetIndex, -1);
        }

        @Override
        public int nestingDepth() {
            if (depth == -1) {
                if (index == 0) {
                    return 0;
                }
                int start = index - 1;
                int computedDepth = 0;
                while (start >= 0) {
                    if (ends[start] >= end()) {
                        computedDepth++;
                    }
                    start--;
                }
                return depth = computedDepth;
            }
            return depth;
        }

        @Override
        public T key() {
            return keys == null ? null : keys[index];
        }

        @Override
        public int start() {
            return starts[index];
        }

        @Override
        public int end() {
            return ends[index];
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public List<SemanticRegion<T>> children() {
            List<SemanticRegion<T>> kids = null;
            int targetStart = start() - 1;
            int nd = nestingDepth();
            for (int i = index + 1; i < size; i++) {
                int st = starts[i];
                if (st < targetStart) {
                    continue;
                }
                int en = ends[i];
                if (en <= end()) {
                    if (kids == null) {
                        kids = new LinkedList<>();
                    }
                    kids.add(new SemanticRegionImpl(i, nd + 1));
                    targetStart = en;
                } else {
                    break;
                }
            }
            return kids == null ? Collections.emptyList() : kids;
        }

        @Override
        public Iterator<SemanticRegion<T>> iterator() {
            List<SemanticRegion<T>> kids = null;
            for (int i = index + 1; i < size; i++) {
                int en = ends[i];
                if (en <= end()) {
                    if (kids == null) {
                        kids = new LinkedList<>();
                    }
                    kids.add(new SemanticRegionImpl(i, depth + 1));
                } else {
                    break;
                }
            }
            return kids == null ? Collections.emptyIterator() : kids.iterator();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            T key = key();
            if (key != null) {
                sb.append(key).append('=');
            }
            sb.append(start()).append(':').append(end()).append('@').append(index).append('^').append(nestingDepth());
            return sb.toString();
        }
    }

    private final class MES implements SizedArrayValueSupplier {

        @Override
        public int get(int index) {
            return ends[index];
        }

        @Override
        public int size() {
            return size;
        }
    }
}

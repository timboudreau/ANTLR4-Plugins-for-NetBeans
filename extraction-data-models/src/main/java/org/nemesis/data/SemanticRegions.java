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

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.range.RangePositionRelation;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.strings.Escaper;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
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
import java.util.function.Function;
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

    public SemanticRegions<T> flatten(Function<List<? extends T>, T> coalescer) {
        if (!hasNesting) {
            return this;
        }
        LinkedList<T> stack = new LinkedList<>();
//        SemanticRegionsBuilder<T> bldr = new SemanticRegionsBuilder<>(keyType());
        IntSet divisions = IntSet.create(size + size / 2);

        for (int i = 0; i < size; i++) {
            int start = starts[i];
            int end = ends[i];
            divisions.add(start);
            divisions.add(end);
        }
        IntList newStarts = IntList.create(divisions.size() + 1);
        IntList newEnds = IntList.create(divisions.size() + 1);
        List<T> all = new ArrayList<>(divisions.size() + 1);

        for (int i = 0; i < divisions.size() - 1; i++) {
            int div = divisions.valueAt(i);
            SemanticRegion<T> reg = at(div);
            if (reg != null) {
                // XXX could use the raw arrays and skip instantiating
                // region instances to do this
                SemanticRegion<T> par = reg.parent();
                boolean hasParents = par != null;
                if (hasParents) {
                    stack.clear();
                    stack.add(reg.key());
                    while (par != null) {
                        stack.add(par.key());
                        par = par.parent();
                    }
                }
                int next = divisions.valueAt(i + 1);
                T key = hasParents ? coalescer.apply(stack) : reg.key();
                newStarts.add(div);
                newEnds.add(next);
                all.add(key);
            }
        }
        Class<T> kt = keyType();
        SemanticRegions<T> result = create(kt, newStarts, newEnds, all, newStarts.size());
        return result.trim();
    }

    public SemanticRegions<T> withDeletion(int chars, int atPosition) {
        if (chars == 0 || size == 0 || atPosition > ends[size - 1]) {
            return this;
        }
        if (atPosition <= starts[0] && atPosition + chars >= ends[size - 1]) {
            return empty();
        }
        int deletionEnd = atPosition + chars;
        int newSize = size;
        int[] newStarts = new int[size];
        int[] newEnds = new int[size];
        T[] newKeys = (T[]) CollectionUtils.genericArray(keys.getClass().getComponentType(), newSize);
        int fiu = firstUnsortedEndsEntry;
        // XXX clean this up...
        for (int i = 0, cursor = 0; i < size; i++, cursor++) {
            int start = starts[i];
            int end = ends[i];
            if (atPosition <= start && deletionEnd >= end) {
                cursor--;
                newSize--;
                if (fiu > 0) {
                    fiu--;
                }
            } else if (end <= atPosition) { // before deletion - no change
                newStarts[cursor] = start;
                newEnds[cursor] = end;
                newKeys[cursor] = keys[i];
            } else if (start >= atPosition && end <= deletionEnd) { // deletion contains region - skip the region entirely
                cursor--;
                newSize--;
            } else if (start <= atPosition && end > deletionEnd) { // deletion straddles start of region
                int rem = end - deletionEnd;
                newStarts[cursor] = start;
                newEnds[cursor] = end - chars;
                newKeys[cursor] = keys[i];
            } else if (start > atPosition && end < deletionEnd) { // deletion internal to this region
                newStarts[cursor] = start;
                newEnds[cursor] = end - chars;
                newKeys[cursor] = keys[i];
            } else if (start > atPosition && start < deletionEnd && deletionEnd > end) { // deletion straddles end of region
                newStarts[cursor] = start;
                newEnds[cursor] = atPosition;
                newKeys[cursor] = keys[i];
            } else if (atPosition > start && deletionEnd < end) {
                newStarts[cursor] = start;
                newEnds[cursor] = end - chars;
                newKeys[cursor] = keys[i];
            } else if (atPosition > start && atPosition < end && deletionEnd >= end) {
                newStarts[cursor] = start;
                newEnds[cursor] = atPosition;
                newKeys[cursor] = keys[i];
            } else if (atPosition > start + 1 && atPosition < end && deletionEnd >= end) {
                if (start == atPosition - 1) {
                    cursor--;
                    newSize--;
                    if (fiu > 0) {
                        fiu--;
                    }
                } else {
                    newStarts[cursor] = start;
                    newEnds[cursor] = atPosition;
                    newKeys[cursor] = keys[i];
                }
            } else if (atPosition > start + 1 && atPosition < end && deletionEnd >= end) { // deletion from middle crossing end of region
                newStarts[cursor] = start;
                newEnds[cursor] = atPosition - 1;
                newKeys[cursor] = keys[i];
                if (newEnds[cursor] == newStarts[cursor]) {
                    if (fiu > 0) {
                        fiu--;
                    }
                    cursor--;
                    newSize--;
                }
            } else if (start >= atPosition + chars) { // after end of deletion - subtract chars from start and end
                newStarts[cursor] = start - chars;
                newEnds[cursor] = end - chars;
                newKeys[cursor] = keys[i];
            } else if (start > atPosition && deletionEnd < end) {
                newStarts[cursor] = atPosition;
                newEnds[cursor] = (end - deletionEnd) + atPosition;
                newKeys[cursor] = keys[i];
            } else {
                throw new AssertionError("Huh? " + i + "/" + cursor + " " + start + ":" + end + " deleting " + atPosition
                        + ":" + deletionEnd + " for " + keys[i] + " atPosition > start " + (atPosition > start)
                        + " atPosition < end" + (atPosition < end) + " end >= deletionEnd " + (end >= deletionEnd));
            }
        }
        SemanticRegions<T> result = new SemanticRegions<>(newStarts, newEnds, newKeys, newSize, fiu, hasNesting);
        assert result.keyType() == keys.getClass().getComponentType() : "Key type wrong: " + result.keyType();
        return result;
    }

    public SemanticRegions<T> withInsertion(int chars, int atPosition) {
        if (chars == 0 || size == 0 || atPosition > ends[size - 1]) {
            return this;
        }
        assert chars > 0;
        assert atPosition >= 0;
        if (atPosition <= starts[0]) { // just shift everything by chars
            int[] newStarts = new int[size];
            int[] newEnds = new int[size];
            for (int i = 0; i < size; i++) {
                newStarts[i] = starts[i] + chars;
                newEnds[i] = ends[i] + chars;
            }
            return new SemanticRegions<>(newStarts, newEnds, keys, size, firstUnsortedEndsEntry, hasNesting);
        } else if (atPosition >= starts[size - 1] && atPosition <= ends[size - 1]) {
            int[] newStarts = Arrays.copyOf(starts, size);
            int[] newEnds = Arrays.copyOf(ends, size);
            newEnds[size - 1] += chars;
            return new SemanticRegions<>(newStarts, newEnds, keys, size, firstUnsortedEndsEntry, hasNesting);
        } else if (atPosition > starts[0] && atPosition < ends[0]) {
            int[] newStarts = new int[size];
            int[] newEnds = new int[size];
            newStarts[0] = starts[0];
            newEnds[0] = ends[0] + chars;
            for (int i = 1; i < size; i++) {
                newStarts[i] = starts[i] + chars;
                newEnds[i] = ends[i] + chars;
            }
            return new SemanticRegions<>(newStarts, newEnds, keys, size, firstUnsortedEndsEntry, hasNesting);
        }
        SemanticRegion<T> target = at(atPosition);
        int[] newStarts = Arrays.copyOf(starts, size);
        int[] newEnds = Arrays.copyOf(ends, size);
        if (target == null) {
            for (int i = 0; i < size; i++) {
                if (newStarts[i] >= atPosition) {
                    newStarts[i] += chars;
                    newEnds[i] += chars;
                }
            }
        } else {
            int ix = target.index();
            newEnds[ix] += chars;
            for (int i = ix + 1; i < size; i++) {
                newStarts[i] += chars;
                newEnds[i] += chars;
            }
        }
        return new SemanticRegions<>(newStarts, newEnds, keys, size, firstUnsortedEndsEntry, hasNesting);
    }

    public SemanticRegion<T> nearestTo(int position) {
        if (isEmpty()) {
            return null;
        }
        SemanticRegion<T> result = at(position);
        if (result != null) {
            return result;
        }
        if (position > ends[size - 1]) {
            // we are guaranteed that the last is the smallest that
            // can possibly match
            return forIndex(size - 1);
        } else if (position <= starts[0]) {
            return forIndex(0);
        }

        int searchStart = 0;
        if (!hasNesting) {
            int ix = Arrays.binarySearch(starts, 0, size, position);
            if (ix < 0) {
                searchStart = -ix - 1;
            } else {
                searchStart = ix;
            }
        } else if (firstUnsortedEndsEntry > 0) {
            // We can't use the JDK's binary search on arrays with duplicates -
            // worked with JDK 7, fails horribly on later JDKs.  But if the
            // item is present in the array before the first unsorted entry,
            // we can search the head of the array, and may find our result
            int ix = Arrays.binarySearch(starts, 0, firstUnsortedEndsEntry, position);
            if (ix >= 0) {
                // Scan forward to the deepest entry at this position
                for (int i = ix; i < size; i++) {
                    if (starts[i] == position) {
                        ix++;
                    } else {
                        break;
                    }
                }
                return forIndex(ix);
            }
        }
        int bestOffset = Integer.MAX_VALUE;
        int bestIndex = -1;
        for (int i = searchStart; i < size; i++) {
            int start = starts[i];
            int end = ends[i];
            int diffStart = Math.abs(position - start);
            int diffEnd = Math.abs(position - end);
            if (diffStart <= diffEnd) {
                if (diffStart < bestOffset) {
                    bestOffset = diffStart;
                    bestIndex = i;
                }
            } else {
                if (diffEnd < bestOffset) {
                    bestOffset = diffEnd;
                    bestIndex = i;
                }
            }
            if (start > position && end > position) {
                break;
            }
        }
        if (bestIndex != -1) {
            return forIndex(bestIndex);
        }
        return null;
    }

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

    /**
     * Create a SemanticRegions from a type and arrays.
     *
     * @param <T> The type
     * @param keyType
     * @param starts
     * @param ends
     * @param keys
     * @param size
     * @return
     */
    public static <T> SemanticRegions<T> create(Class<T> keyType, IntList starts, IntList ends, List<T> keys, int size) {
        int sz = ends.size();
        assert starts.size() == sz;
        assert keys.size() == sz;
        assert size <= sz;
        int[] sts = starts.toIntArray();
        int[] ens = ends.toIntArray();
        T[] objs = (T[]) Array.newInstance(keyType, sz);
        objs = keys.toArray(objs);
        int firstUnsortedEndsEntry = -1;
        boolean nesting = false;
        for (int i = 1; i < size; i++) {
            int start = sts[i];
            int end = ens[i];
            int prevStart = sts[i - 1];
            int prevEnd = ens[i - 1];
            nesting |= start <= prevStart || end <= prevEnd;
            if (prevEnd >= ens[i]) {
                firstUnsortedEndsEntry = i;
                nesting = true;
                break;
            }
            if (prevEnd > start && end > prevEnd) {
                throw new IllegalStateException("Straddle encountered at " + i
                        + " " + prevStart + ":" + prevEnd + " vs. "
                        + start + ":" + end);
            }
            if (start < prevStart) {
                throw new IllegalStateException("Starts array is not "
                        + "sorted at " + i + ": " + start + " with prev start "
                        + prevStart + " in " + Arrays.toString(sts));
            }
        }
        return new SemanticRegions<>(sts, ens, objs, size, firstUnsortedEndsEntry, nesting);
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

    /**
     * Get the index of a SemanticRegion in this SemanticRegions; *if* it is one
     * created by this, simply returns its index property, and otherwise scans
     * for the first entry where the offsets and key are equal. Can also be
     * passed a key value, and will return the first index where that key equals
     * the one in this regions.
     *
     * @param o An object
     * @return An index or -1 if not present
     */
    @SuppressWarnings("unchecked")
    @Override
    public int indexOf(Object o) {
        if (o != null && o.getClass() == SemanticRegionImpl.class && ((SemanticRegionImpl) o).owner() == this) {
            return ((SemanticRegion<?>) o).index();
        } else if (o instanceof SemanticRegion<?>) {
            SemanticRegion<?> sem = (SemanticRegion<?>) o;
            for (int i = 0; i < size; i++) {
                if (starts[i] == sem.start() && ends[i] == sem.end() && Objects.equals(sem.key(), keys[i])) {
                    return i;
                }
            }
        } else if (keys != null && keyType().isInstance(o)) {
            for (int i = 0; i < size; i++) {
                if (o.equals(keys[i])) {
                    return i;
                }
            }
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
        if (keys == null) {
            return Collections.emptyList();
        }
        List<SemanticRegion<T>> result = new LinkedList<>();
        for (SemanticRegion<T> s : this) {
            if (pred.test(s.key())) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Get the outer bounds of this SemanticRegions.
     *
     * @return The bounds - if empty, will be 0:0
     */
    public IntRange<? extends IntRange<?>> bounds() {
        if (size == 0) {
            return Range.of(0, 0);
        }
        return Range.of(starts[0], ends[size - 1]);
    }

    /**
     * Look up a start position by index.
     *
     * @param ix An index
     * @return A start position
     * @throws IndexOutOfBoundsException if out of range
     */
    public int startAt(int ix) {
        if (ix < 0 || ix >= size) {
            throw new IndexOutOfBoundsException(ix + " is outside " + bounds());
        }
        return starts[ix];
    }

    /**
     * Look up an end position by index.
     *
     * @param ix An index
     * @return An end position
     * @throws IndexOutOfBoundsException if out of range
     */
    public int endAt(int ix) {
        if (ix < 0 || ix >= size) {
            throw new IndexOutOfBoundsException(ix + " is outside " + bounds());
        }
        return ends[ix];
    }

    /**
     * Look up a key by index.
     *
     * @param ix An index
     * @return A key
     * @throws IndexOutOfBoundsException if out of range
     */
    public T keyAt(int ix) {
        if (ix < 0 || ix >= size) {
            throw new IndexOutOfBoundsException(ix + " is outside " + bounds());
        }
        return keys[ix];
    }

    /**
     * Collect regions matched by the passed predicate only if they occur with
     * the specified range.
     *
     * @param start The start of the range
     * @param end The end of the range
     * @param pred A test for the keys in this range
     * @return A list or ranges; note the depth will be set to 0
     */
    public List<? extends SemanticRegion<T>> collectBetween(int start, int end, Predicate<T> pred) {
        assert end >= start;
        if (end == start || size == 0 || keys == null) {
            return Collections.emptyList();
        }
        if (true) {
            List<SemanticRegion<T>> result = null;
            for (int i = 0; i < size; i++) {
                int st = starts[i];
                int en = ends[i];
                if (st >= start && en <= end) {
                    if (pred.test(keys[i])) {
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(forIndex(i));
                    }
                }
            }
            return result == null ? Collections.emptyList() : result;
        }

        List<SemanticRegion<T>> result = null;
        int startPoint = indexAndDepthAt(start)[0];
        if (startPoint < 0) {
            IntRange<? extends IntRange> bounds = bounds();
            RangePositionRelation startRelation = bounds.relationTo(start);
            switch (startRelation) {
                case BEFORE:
                    // The start is before our start, so start at 0
                    startPoint = 0;
                    break;
                case AFTER:
                    // The start is after the last element - nothing to do
                    return Collections.emptyList();
                default:
                    if (!hasNesting) {
                        // If we do not have nesting, simple binary search
                        // will work just fine
                        int index = Arrays.binarySearch(starts, 0, size, start);
                        if (index < 0) {
                            startPoint = (-index) + 1;
                        } else {
                            startPoint = index;
                        }
                    } else {
                        // XXX once ArrayUtil.duplicatTolerantBinarySearch is
                        // solid, use that here
                        // For now, brute-force scan
                        for (int i = 0; i < size; i++) {
                            if (starts[i] >= start) {
                                startPoint = Math.max(0, i);
                                break;
                            }
                        }
                    }
                    if (startPoint < 0 || startPoint >= size) {
                        // Didn't find anything, we're done
                        return Collections.emptyList();
                    }
            }
        } else if (startPoint > 0) {
            // indexAndDepthAt gets us the deepest index,
            // so we need to back up to the first element containing the
            // start position
            while (startPoint > 0) {
                if (starts[startPoint - 1] >= start) {
                    if (ends[startPoint - 1] > end) {
                        break;
                    }
                    startPoint--;
                } else {
                    break;
                }
            }
        }
        // Scan forward from our statring point
        for (int i = startPoint; i < size; i++) {
            int st = starts[i];
            if (st >= end) {
                // The first element whose start comes after the requested start
                // position indicates we're done - the starts array is always
                // sorted
                break;
            }
            if (ends[i] > end) {
                // If an item's end is passed the requested end, ignore it,
                // but it may still contain smaller elements that are within the
                // requested range
                if (!hasNesting) {
                    // If there is no nesting, then we need look no further,
                    // because the next element will start after
                    break;
                }
                // Continue in case of other matches
                continue;
            }
            if (pred.test(keys[i])) {
                // XXX get the depth right (and change the note in the javadoc)
                SemanticRegion<T> reg = new SemanticRegionImpl(i, 0);
                if (result == null) {
                    // Only create a list if we have something to put in it
                    result = new LinkedList<>();
                }
                result.add(reg);
            }
        }
        return result == null ? Collections.emptyList() : result;
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

    @SuppressWarnings("unchecked")
    public static <T> SemanticRegionsBuilder<T> builder(Class<? super T> type, int targetCapacity) {
        return new SemanticRegionsBuilder<>((Class<T>) type, targetCapacity);
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

        // Can get rid of the mutation methods on SemanticRegions - when building
        // a large set, calling checkINvariants() once per item is way too slow.
        private final Class<T> type;
        private final IntList starts;
        private final IntList ends;
        private final List<T> objs;

        private SemanticRegionsBuilder(Class<T> type) {
            this(type, 256);
        }

        private SemanticRegionsBuilder(Class<T> type, int initialCapacity) {
            this.type = type;
            starts = IntList.create(initialCapacity);
            ends = IntList.create(initialCapacity);
            objs = new ArrayList<>(initialCapacity);
        }

        public int size() {
            return starts.size();
        }

        public SemanticRegionsBuilder<T> add(T key, int start, int end) {
            assert key == null || type.isInstance(key) :
                    "Bad key type: " + key + " (" + key.getClass() + ")";
            if (!starts.isEmpty()) {
                if (key == objs.get(objs.size() - 1) && start == starts.last() && end == starts.last()) {
                    return this;
                }
            }
            starts.add(start);
            ends.add(end);
            objs.add(key);
            assert starts.size() == ends.size();
            assert objs.size() == ends.size();
            return this;
        }

        public SemanticRegionsBuilder<T> add(int start, int end) {
            return add(null, start, end);
        }

        public SemanticRegions<T> build() {
            return create(type, starts, ends, objs, ends.size());
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
        int sz = size;
        for (int i = 1; i < sz; i++) {
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

    /**
     * Get the <i>innermost</i> (narrowest) element whose start is less than or
     * equal to the passed position, and whose end is less than the passed
     * position.
     *
     * @param pos A position in this collection's coordinate space
     * @return A region or null
     */
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

    public SemanticRegion<T> find(Predicate<T> test) {
        for (int i = 0; i < size; i++) {
            if (test.test(keys[i])) {
                return new SemanticRegionImpl(i, -1);
            }
        }
        return null;
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

    /**
     * For bug reproduction purposes and test generation, convert this
     * SemanticRegions into Java code that will recreate it; uses
     * <code>toString()</code> on keys and generates a
     * <code>SemanticRegions&lt;String&gt;</code>; use the method that takes a
     * function to generate proper constructor code for your keys.
     *
     * <code>Escaper.CONTROL_CHARACTERS.escape(CharSequence seq)</code>)
     *
     * @return Code to reconstruct some simulacrum of this regions, using
     * toString() on keys
     */
    public CharSequence toCode() {
        return toCode(new DefaultStringifier());
    }

    private static final class DefaultStringifier implements Function<Object, String> {

        @Override
        public String apply(Object key) {
            if (key == null) {
                return "null";
            } else {
                return '"' + Escaper.CONTROL_CHARACTERS.escape(key.toString()) + '"';
            }
        }
    }

    /**
     * For bug reproduction purposes and test generation, convert this
     * SemanticRegions into Java code that will recreate it identically.
     *
     * @param keyStringifier A Function which converts a key into a constructor
     * call or similar; remember to handle nulls and escape characters in
     * strings (hint: use
     * <code>Escaper.CONTROL_CHARACTERS.escape(CharSequence seq)</code>)
     * @return Code to reconstruct some simulacrum of this regions, using
     * toString() on keys
     */
    public CharSequence toCode(Function<? super T, String> keyStringifier) {
        String type = keyStringifier instanceof DefaultStringifier ? "String"
                : keyType().getSimpleName();
        StringBuilder sb = new StringBuilder(
                "        SemanticRegionsBuilder<").append(type)
                .append("> bldr = SemanticRegions.builder(")
                .append(type).append(".class, ")
                .append(size).append(");\n");
        if (size == 0) {
            sb.append(";\n        SemanticRegions<").append(type).append("> regions = bldr.build();\n");
            return sb;
        }
        sb.append("        bldr");
        for (int i = 0; i < size; i++) {
            T key = keys[i];
            if ((i + 1) % 50 == 0) {
                sb.append(";");
                if (i == 49) {
                    sb.append("\n\n        // We do not generate a single chain of calls because");
                    sb.append("\n        // with several hundred, javac will throw a StackOverflowException");
                }
                sb.append("\n        bldr");
            }
            sb.append("\n            .add(").append(keyStringifier.apply(key)).append(", ")
                    .append(starts[i]).append(", ").append(ends[i]).append(")");
            if (i == size - 1) {
                sb.append(";\n        SemanticRegions<").append(type).append("> regions = bldr.build();\n");
            }
        }
        return sb;
    }
}

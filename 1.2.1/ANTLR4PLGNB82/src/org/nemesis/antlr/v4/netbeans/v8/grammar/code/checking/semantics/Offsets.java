/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.Item;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.ReferenceSets.ReferenceSet;

/**
 * Maps pairs of start/end offsets to a set of strings. Use with care: in
 * particular, this class does not support unsorted or duplicate names, or
 * overlapping ranges. This is the right data structure for an Antlr parse, but
 * not a general-purpose thing. It specifically uses shared arrays of names,
 * start and end positions to minimize memory and disk-cache consumption.
 *
 * @author Tim Boudreau
 */
public class Offsets<K extends Enum<K>> implements Iterable<Item<K>>, Serializable {

    private final int[] starts;
    private final EndSupplier ends;
    private final String[] names;
    private final K[] kinds;
    private int size;
    private transient IndexImpl index;

    public Offsets(String[] names, int[] starts, int[] ends, K[] kinds, int size) {
        assert starts != null && ends != null && starts.length == ends.length;
        assert names != null && names.length == starts.length;
        assert kinds != null && kinds.length == names.length;
        assert kinds.getClass().getComponentType().isEnum();
        this.names = names;
        this.starts = starts;
        this.ends = new ArrayEndSupplier(ends);
        this.kinds = kinds;
        this.size = size;
    }

    public Offsets(String[] names, K[] kinds, int size) {
        assert kinds.length == names.length;
        assert new HashSet<>(Arrays.asList(names)).size() == names.length : "Names array contains duplicates: " + Arrays.toString(names);
        this.starts = new int[names.length];
        this.kinds = kinds;
        this.names = names;
        Arrays.sort(this.names);
        Arrays.fill(starts, -1);
        this.ends = new StringEndSupplier();
        this.size = size;
    }

    String[] nameArray() {
        if (names.length != size) {
            return Arrays.copyOf(names, size);
        }
        return names;
    }

    public static <K extends Enum<K>> OffsetsBuilder<K> builder(Class<K> type) {
        return new OffsetsBuilder<>(type);
    }

    interface EndSupplier extends Serializable {

        int get(int index);

        default MutableEndSupplier toMutable(int size) {
            int[] result = new int[size];
            for (int i = 0; i < size; i++) {
                result[i] = get(i);
            }
            return new ArrayEndSupplier(result);
        }
    }

    interface MutableEndSupplier extends EndSupplier {

        void setEnd(int index, int val);

        void remove(int index);
    }

    static class ArrayEndSupplier implements MutableEndSupplier {

        private final int[] ends;

        ArrayEndSupplier(int size) {
            ends = new int[size];
            Arrays.fill(ends, -1);
        }

        ArrayEndSupplier(int[] ends) {
            this.ends = ends;
        }

        @Override
        public int get(int index) {
            return ends[index];
        }

        @Override
        public void setEnd(int index, int val) {
            ends[index] = val;
        }

        @Override
        public void remove(int ix) {
            int size = ends.length;
            System.arraycopy(ends, ix + 1, ends, ix, size - (ix + 1));
        }
    }

    class StringEndSupplier implements EndSupplier {

        @Override
        public int get(int index) {
            return starts[index] == -1 ? -1 : starts[index] + names[index].length();
        }
    }

    public static final class OffsetsBuilder<K extends Enum<K>> {

        private final Class<K> type;
        private final Map<String, K> typeForName;
        private final Map<String, int[]> offsetsForName;
        private boolean needArrayBased;

        private OffsetsBuilder(Class<K> type) {
            this.type = type;
            typeForName = new TreeMap<>();
            offsetsForName = new TreeMap<>();
        }

        public OffsetsBuilder<K> arrayBased() {
            needArrayBased = true;
            return this;
        }

        public OffsetsBuilder<K> add(String name, K kind, int start, int end) {
            add(name, kind);
            offsetsForName.put(name, new int[]{start, end});
            if (end != name.length() + start) {
                needArrayBased = true;
            }
            return this;
        }

        public OffsetsBuilder<K> add(String name, K kind) {
            assert name != null;
            assert kind != null;
            K existing = typeForName.get(name);
            if (existing != null && existing.ordinal() < kind.ordinal()) {
                return this;
            }
            typeForName.put(name, kind);
            return this;
        }

        @SuppressWarnings("unchecked")
        public Offsets<K> build() {
            String[] names = typeForName.keySet().toArray(new String[typeForName.size()]);
            K[] kinds = (K[]) Array.newInstance(type, names.length);
            String last = null;
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                if (last != null) {
                    assert name.compareTo(last) >= 1 : "TreeMap keySet is unsorted";
                }
                kinds[i] = typeForName.get(name);
                assert kinds[i] != null;
                last = name;
            }
            Offsets<K> result;
            if (!needArrayBased) {
                result = new Offsets<>(names, kinds, names.length);
            } else {
                int[] starts = new int[names.length];
                int[] ends = new int[names.length];
                Arrays.fill(starts, -1);
                Arrays.fill(ends, -1);
                result = new Offsets<>(names, starts, ends, kinds, names.length);
            }
            for (Map.Entry<String, int[]> e : offsetsForName.entrySet()) {
                result.setOffsets(e.getKey(), e.getValue()[0], e.getValue()[1]);
            }
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private Class<K> kType() {
        return (Class<K>) kinds.getClass().getComponentType();
    }

    public Set<String> itemsWithNoOffsets() {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < size(); i++) {
            if (starts[i] < 0 || ends.get(i) < 0) {
                result.add(names[i]);
            }
        }
        return result;
    }

    public int size() {
        return size;
    }

    Set<String> removeItemsWithNoOffsets() {
        Set<String> result = itemsWithNoOffsets();
        for (String s : result) {
            remove(s);
        }
        return result;
    }

    int removeAll(Collection<String> c) {
        int result = 0;
        for (String s : c) {
            if (remove(s)) {
                result++;
            }
        }
        return result;
    }

    public boolean remove(String name) {
        if (size == 0) {
            return false;
        }
        int ix = indexOf(name);
        if (ix < 0) {
            return false;
        }
        int last = last();
        if (last == 0) {
            size = 0;
            return true;
        }
        System.arraycopy(names, ix + 1, names, ix, size - (ix + 1));
        System.arraycopy(starts, ix + 1, starts, ix, size - (ix + 1));
        if (ends instanceof MutableEndSupplier) {
            ((MutableEndSupplier) ends).remove(ix);
        }
        System.arraycopy(kinds, ix + 1, kinds, ix, size - (ix + 1));
        size--;
        index = null;
        return true;
    }

    public Set<String> newSet() {
        return new BitSetSet<>(Indexed.forSortedStringArray(names));
    }

    public Offsets<K> secondary() {
        String[] n = Arrays.copyOf(names, size);
        K[] k = Arrays.copyOf(kinds, size);
        int[] starts = new int[size];
        int[] ends = new int[size];
        Arrays.fill(starts, -1);
        Arrays.fill(ends, -1);
        return new Offsets<>(n, starts, ends, k, size);
    }

    public Offsets<K> sans(String... toRemove) {
        return sans(Arrays.asList(toRemove));
    }

    public int orderingOf(int ix) {
        IndexImpl index = this.index;
        if (index == null) {
            index();
            index = this.index;
        }
        return Arrays.binarySearch(index.starts, 0, size, starts[ix]);
    }

    public int orderingOf(String name) {
        return orderingOf(internalIndexOf(name));
    }

    @SuppressWarnings("unchecked")
    public Offsets<K> sans(Iterable<String> toRemove) {
        BitSet set = new BitSet(size());
        for (String s : toRemove) {
            int ix = indexOf(s);
            if (ix >= 0) {
                set.set(ix);
            }
        }
        int newCount = size() - set.cardinality();
        if (newCount == 0) {
            return this;
        }
        int[] newStarts = new int[newCount];
        int[] newEnds = new int[newCount];
        K[] newKinds = (K[]) Array.newInstance(kType(), newCount);
        String[] newNames = new String[newCount];
        int cursor = 0;
        for (int i = 0; i < size(); i++) {
            if (!set.get(i)) {
                newStarts[cursor] = starts[i];
                newEnds[cursor] = ends.get(i);
                newNames[cursor] = names[i];
                newKinds[cursor] = kinds[i];
                cursor++;
            }
        }
        return new Offsets<>(newNames, newStarts, newEnds, newKinds, newCount);
    }

    public int indexOf(String name) {
        if (name == null) {
            return -1;
        }
        int result = Arrays.binarySearch(names, 0, size, name);
        return result < 0 ? -1 : result;
    }

    private int internalIndexOf(String name) {
        int result = Arrays.binarySearch(names, 0, size, name);
        if (result < 0) {
            throw new IllegalArgumentException("Not a known name '"
                    + name + "' in " + Arrays.toString(names));
        }
        return result;
    }

    int start(String name) {
        return starts[internalIndexOf(name)];
    }

    int end(String name) {
        return ends.get(internalIndexOf(name));
    }

    void setOffsets(String name, int start, int end) {
        assert end > start;
        assert name != null;
        if (!(ends instanceof MutableEndSupplier) && end != start + name.length()) {
            throw new IllegalStateException("Ends in this Offsets are based on string length,"
                    + " but attempting to set the end for " + name + " to " + end
                    + " which is not start + name.length() = " + (start + name.length())
                    + ". Use the constructor that takes an array of starts and ends if you want that.");
        }
        int ix = internalIndexOf(name);
        starts[ix] = start;
        if (ends instanceof MutableEndSupplier) {
            ((MutableEndSupplier) ends).setEnd(ix, end);
        }
    }

    ItemImpl item(String name) {
        return new ItemImpl(internalIndexOf(name));
    }

    public K kind(int index) {
        return kinds[index];
    }

    public K kind(String name) {
        return kind(internalIndexOf(name));
    }

    public String get(int index) {
        return names[index];
    }

    void setKind(String name, K kind) {
        kinds[internalIndexOf(name)] = kind;
    }

    @Override
    public Iterator<Item<K>> iterator() {
        return new Iter();
    }

    public boolean contains(String name) {
        return indexOf(name) >= 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            sb.append(names[i]).append('@').append(starts[i]).append(':').append(ends.get(i))
                    .append('(').append(i).append(')');
            if (i != size() - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    public Iterable<Item<K>> ofKind(K kind) {
        return new ByKindIter(kind);
    }

    void collectItems(List<? super Item<K>> into) {
        for (Item<K> i : this) {
            into.add(i);
        }
    }

    private class ByKindIter implements Iterator<Item<K>>, Iterable<Item<K>> {

        private int ix = 0;
        private Item<K> nextItem;
        private final K kind;

        ByKindIter(K kind) {
            this.kind = kind;
        }

        public Iterator<Item<K>> iterator() {
            if (ix == 0) {
                return this;
            }
            return new ByKindIter(kind);
        }

        public boolean hasNext() {
            if (nextItem == null && ix < size()) {
                for (int i = ix; i < size(); i++) {
                    if (kinds[i] != kind) {
                        nextItem = new ItemImpl(i);
                        break;
                    }
                    ix++;
                }
            }
            return nextItem != null;
        }

        public Item<K> next() {
            Item<K> result = nextItem;
            if (result == null) {
                throw new IllegalStateException("next() called after end");
            }
            nextItem = null;
            ix++;
            return result;
        }
    }

    public interface ReferenceSets<K extends Enum<K>> extends Iterable<ReferenceSet<K>>, Serializable {

        public void addReference(String name, int start, int end);

        public ReferenceSet<K> references(String name);

        public Item<K> itemAt(int pos);

        default void collectItems(List<? super Item<K>> into) {
            for (ReferenceSet<K> refs : this) {
                for (Item<K> item : refs) {
                    into.add(item);
                }
            }
        }

        public interface ReferenceSet<K extends Enum<K>> extends Iterable<Item<K>>, Serializable {

            int referenceCount();

            boolean contains(int pos);

            String name();

            int visitReferences(CoordinateConsumer consumer);

            Item<K> itemAt(int pos);

            @FunctionalInterface
            interface CoordinateConsumer {

                void consume(int start, int end);
            }

            Item<K> original();

            void collectItems(List<? super Item<K>> items);
        }
    }

    final class EmptyReferenceSet implements ReferenceSet<K> {

        private final int index;

        public EmptyReferenceSet(int index) {
            this.index = index;
        }

        public void collectItems(List<? super Item<K>> items) {
            // do nothing
        }

        public Iterator<Item<K>> iterator() {
            return Collections.emptyIterator();
        }

        public Item<K> itemAt(int pos) {
            return null;
        }

        public String name() {
            return names[index];
        }

        public boolean contains(int pos) {
            return false;
        }

        @Override
        public int referenceCount() {
            return 0;
        }

        @Override
        public int visitReferences(CoordinateConsumer consumer) {
            return 0;
        }

        @Override
        public Item<K> original() {
            return new ItemImpl(index);
        }
    }

    public ReferenceSets<K> newReferenceSets() {
        return new ReferenceSetsImpl();
    }

    UsagesImpl newUsages() {
        return new UsagesImpl();
    }

    public interface Usages {

        public BitSet get(int index);

        public BitSet get(String name);

        public BitSetTree toBitSetTree();
    }

    class UsagesImpl implements Usages {

        private final BitSet[] usagesForIndex;

        UsagesImpl() {
            this.usagesForIndex = new BitSet[size()];
        }

        public BitSetTree toBitSetTree() {
            return new BitSetTree(Arrays.copyOf(usagesForIndex, usagesForIndex.length));
        }

        @Override
        public BitSet get(int index) {
            BitSet result = usagesForIndex[index];
            return result == null ? new BitSet(0) : result;
        }

        @Override
        public BitSet get(String name) {
            return get(internalIndexOf(name));
        }

        void add(int user, int uses) {
            BitSet set = usagesForIndex[user];
            if (set == null) {
                set = usagesForIndex[user] = new BitSet(size());
            }
            set.set(uses);
//            System.out.println("USAGE: " + names[user] + "(" + user + ") -> " + names[uses] + "(" + uses + "): " + set);
        }

        void add(String user, String uses) {
            add(internalIndexOf(user), internalIndexOf(uses));
        }

    }

    private class ReferenceSetsImpl implements ReferenceSets<K> {

        ReferenceSetImpl[] sets;

        @SuppressWarnings("unchecked")
        ReferenceSetsImpl() {
//            sets = new ReferenceSetImpl[size()];
            sets = (ReferenceSetImpl[]) Array.newInstance(ReferenceSetImpl.class, size());
        }

        public Item<K> itemAt(int pos) {
            for (int i = 0; i < sets.length; i++) {
                if (sets[i] != null) {
                    Item<K> result = sets[i].itemAt(pos);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }

        @Override
        public void addReference(String name, int start, int end) {
            int ix = internalIndexOf(name);
            if (sets[ix] == null) {
                sets[ix] = new ReferenceSetImpl(ix);
            }
            sets[ix].add(start, end);
        }

        @Override
        public ReferenceSet<K> references(String name) {
            int ix = internalIndexOf(name);
            return sets[ix] == null ? new EmptyReferenceSet(ix) : sets[ix];
        }

        @Override
        public Iterator<ReferenceSet<K>> iterator() {
            return new SetsIter();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sets.length; i++) {
                if (sets[i] != null) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append('[').append(sets[i]).append(']');
                }
            }
            return sb.toString();
        }

        private class SetsIter implements Iterator<ReferenceSet<K>> {

            private int ix = 0;
            private ReferenceSet<K> nextSet;

            public boolean hasNext() {
                if (nextSet == null && ix < sets.length) {
                    for (int i = ix; i < sets.length; i++) {
                        if (sets[i] != null) {
                            nextSet = sets[i];
                            break;
                        }
                        ix++;
                    }
                }
                return nextSet != null;
            }

            public ReferenceSet<K> next() {
                ReferenceSet<K> result = nextSet;
                if (result == null) {
                    throw new IllegalStateException("next() called after end");
                }
                nextSet = null;
                ix++;
                return result;
            }
        }

        private class ReferenceSetImpl implements ReferenceSet<K>, Iterable<Item<K>> {

            private int[] referenceStarts = new int[3];
//            private int[] referenceEnds = new int[3];
            private int referenceSetSize = 0;
            private final int referenceIndex;
            private final ES es = new ES();

            ReferenceSetImpl(int index) {
                this.referenceIndex = index;
            }

            class ES implements EndSupplier {

                @Override
                public int get(int index) {
                    return referenceStarts[index] + names[referenceIndex].length();
                }

            }

            public void collectItems(List<? super Item<K>> items) {
                for (Item<K> i : this) {
                    items.add(i);
                }
            }

            public Item<K> original() {
                return new ItemImpl(referenceIndex);
            }

            public String name() {
                return names[referenceIndex];
            }

            void add(int start, int end) {
                assert end > start;
                assert referenceSetSize == 0 || referenceStarts[referenceSetSize - 1] < start;
//                assert size == 0 || referenceEnds[size - 1] < end;
                assert referenceSetSize == 0 || es.get(referenceSetSize - 1) < end;
                int newSize = referenceSetSize + 1;
                if (newSize > referenceStarts.length) {
                    int len = referenceStarts.length + 3;
                    referenceStarts = Arrays.copyOf(referenceStarts, len);
//                    referenceEnds = Arrays.copyOf(referenceEnds, len);
                }
                referenceStarts[referenceSetSize] = start;
//                referenceEnds[size] = end;
                referenceSetSize++;
            }

            public boolean contains(int pos) {
                return indexFor(pos) >= 0;
            }

            public Item<K> itemAt(int pos) {
                int ix = indexFor(pos);
                return ix >= 0 ? new ReferenceItem(ix) : null;
            }

            private int indexFor(int pos) {
                return rangeBinarySearch(pos, referenceStarts, es, referenceSetSize);
            }

            @Override
            public int referenceCount() {
                return referenceSetSize;
            }

            @Override
            public int visitReferences(CoordinateConsumer consumer) {
                for (int i = 0; i < referenceSetSize; i++) {
                    consumer.consume(referenceStarts[i], es.get(i));
                }
                return referenceSetSize;
            }

            @Override
            public Iterator<Item<K>> iterator() {
                return new RefIter();
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder('{');
                for (int i = 0; i < referenceSetSize; i++) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(name()).append('@').append(referenceStarts[i])
                            .append(':').append(es.get(i))
                            .append('(').append(i).append(')');
                }
                return sb.append('}').toString();
            }

            class RefIter implements Iterator<Item<K>> {

                private int ix = -1;

                @Override
                public boolean hasNext() {
                    return ix + 1 < referenceSetSize;
                }

                @Override
                public Item<K> next() {
                    return new ReferenceItem(++ix);
                }
            }

            class ReferenceItem implements Item<K> {

                private final int boundsIndex;

                public ReferenceItem(int boundsIndex) {
                    this.boundsIndex = boundsIndex;
                }

                public boolean isReference() {
                    return true;
                }

                @Override
                public int start() {
                    return referenceStarts[boundsIndex];
                }

                public int ordering() {
                    return orderingOf(referenceIndex);
                }

                @Override
                public int end() {
//                    return referenceEnds[boundsIndex];
                    return referenceStarts[boundsIndex] + name().length();
                }

                public K kind() {
                    return kinds[referenceIndex];
                }

                @Override
                public String name() {
                    return names[referenceIndex];
                }

                @Override
                public int index() {
                    return referenceIndex;
                }

                @Override
                public String toString() {
                    StringBuilder sb = new StringBuilder("ref:")
                            .append(ordering()).append(':');
                    sb.append(name()).append('@').append(start()).append(':')
                            .append(end()).append('(').append(referenceIndex).append(')')
                            .append(':').append(kind());
                    return sb.toString();
                }
            }
        }
    }

    private final class Iter implements Iterator<Item<K>> {

        private int ix = -1;

        @Override
        public boolean hasNext() {
            return ix + 1 < size();
        }

        @Override
        public Item<K> next() {
            return new ItemImpl(++ix);
        }
    }

    /**
     * A flyweight object which encapsulates one entry in an Offsets.
     */
    public interface Item<K extends Enum<K>> extends Comparable<Item<?>> {

        int start();

        int end();

        K kind();

        int ordering();

        boolean isReference();

        default int stop() {
            return end() - 1;
        }

        String name();

        int index();

        default int length() {
            return end() - start();
        }

        default boolean containsPosition(int pos) {
            return pos >= start() && pos < end();
        }

        @Override
        public default int compareTo(Item<?> o) {
            int as = start();
            int bs = o.start();
            int result = as > bs ? 1 : as == bs ? 0 : -1;
            if (result == 0) {
                int ae = end();
                int be = end();
                result = ae > be ? 1 : ae == be ? 0 : -1;
            }
            return result;
        }

    }

    public interface Index<K extends Enum<K>> extends Iterable<Item<K>> {

        public Item<K> atOffset(int ix);

        public Item<K> withStart(int start);

        public Item<K> withEnd(int end);
    }

    public Index<K> index() {
        if (index != null) {
            return index;
        }
        List<ItemImpl> all = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            all.add(new ItemImpl(i));
        }
        Collections.sort(all);
        int[] indices = new int[size()];
        int[] sortedStarts = new int[size()];
        int[] sortedEnds = new int[size()];
        for (int i = 0; i < size(); i++) {
            ItemImpl item = all.get(i);
            indices[i] = item.index();
            if (item.start() == item.end()) {
                throw new IllegalStateException("Some indices not set: "
                        + item.name() + "@" + item.start() + ":" + item.end()
                        + "-" + item.kind());
            }
            sortedStarts[i] = item.start();
            sortedEnds[i] = item.end();
        }
        return index = new IndexImpl(sortedStarts, sortedEnds, indices);
    }

    int last() {
        return size() - 1;
    }

    private final class IndexImpl implements Index<K> {

        private final int[] starts;
        private final int[] ends;
        private final int[] indices;

        public IndexImpl(int[] starts, int[] ends, int[] indices) {
            this.starts = starts;
            this.ends = ends;
            this.indices = indices;
        }

        public Item<K> withStart(int start) {
            int offset = Arrays.binarySearch(starts, 0, size, start);
            return offset < 0 ? null : new ItemImpl(indices[offset]);
        }

        public Item<K> withEnd(int end) {
            int offset = Arrays.binarySearch(ends, 0, size, end);
            return offset < 0 ? null : new ItemImpl(indices[offset]);
        }

        private int indexFor(int pos) {
            // XXX could use the original end supplier rapped in one which
            // looks up by index, and forgo having an ends array here
            return rangeBinarySearch(pos, starts, new ArrayEndSupplier(ends), size);
        }

        @Override
        public Item<K> atOffset(int pos) {
            int ix = indexFor(pos);
            return ix < 0 ? null : new ItemImpl(ix);
        }

        @Override
        public Iterator<Item<K>> iterator() {
            return new IndexIter();
        }

        class IndexIter implements Iterator<Item<K>> {

            private int ix = -1;

            @Override
            public boolean hasNext() {
                return ix + 1 < size();
            }

            @Override
            public Item<K> next() {
                return new ItemImpl(indices[++ix]);
            }
        }
    }

    private final class ItemImpl implements Item<K> {

        private final int index;

        public ItemImpl(int index) {
            this.index = index;
        }

        @Override
        public String toString() {
            return ordering() + ":" + name() + "@" + start() + ":" + end() + "(" + index() + ")"
                    + ":" + kind();
        }

        public boolean isReference() {
            return false;
        }

        public int index() {
            return index;
        }

        public K kind() {
            return kinds[index];
        }

        public int ordering() {
            return orderingOf(index);
        }

        boolean overlaps(int pos) {
            return pos >= start() && pos < end();
        }

        @Override
        public int start() {
            return starts[index];
        }

        @Override
        public int end() {
            return ends.get(index);
        }

        @Override
        public String name() {
            return names[index];
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof Item<?>) {
                Item<?> other = (Item<?>) o;
                return other.name().equals(name());
            } else {
                return false;
            }
        }

        public int hashCode() {
            return name().hashCode();
        }
    }

    static int rangeBinarySearch(int pos, int searchFirst, int searchLast, int[] starts, EndSupplier ends, int size) {
        int firstStart = starts[searchFirst];
        int lastEnd = ends.get(searchLast);
        int firstEnd = ends.get(searchFirst);
        if (pos >= firstStart && pos < firstEnd) {
            return searchFirst;
        } else if (pos < firstStart) {
            return -1;
        } else if (pos == lastEnd - 1) {
            return searchLast;
        } else if (pos > lastEnd) {
            return -1;
        }
        int lastStart = starts[searchLast];
        if (pos >= lastStart && pos < lastEnd) {
            return searchLast;
        } else if (pos == firstEnd - 1) {
            return searchFirst;
        }
        int mid = searchFirst + ((searchLast - searchFirst) / 2);
        if (mid == searchFirst || mid == searchLast) {
            return -1;
        }
        int midStart = starts[mid];
        int midEnd = ends.get(mid);
        if (pos >= midStart && pos < midEnd) {
            return mid;
        }
        if (pos > midEnd) {
            return rangeBinarySearch(pos, mid + 1, searchLast - 1, starts, ends, size);
        } else {
            return rangeBinarySearch(pos, searchFirst + 1, mid - 1, starts, ends, size);
        }
    }

    static int rangeBinarySearch(int pos, int[] starts, EndSupplier ends, int size) {
        if (size == 0 || pos < starts[0] || pos >= ends.get(size - 1)) {
            return -1;
        }
        return rangeBinarySearch(pos, 0, size - 1, starts, ends, size);
    }
}

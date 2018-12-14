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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ArrayUtil.ArrayEndSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ArrayUtil.EndSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ArrayUtil.MutableEndSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSets.NamedRegionReferenceSet;

/**
 * Maps pairs of start/end offsets to a set of strings. Use with care: in
 * particular, this class does not support unsorted or duplicate names, or
 * overlapping ranges. This is the right data structure for an Antlr parse, but
 * not a general-purpose thing. It specifically uses shared arrays of names,
 * start and end positions to minimize memory and disk-cache consumption.
 *
 * @author Tim Boudreau
 */
public class NamedSemanticRegions<K extends Enum<K>> implements Iterable<NamedSemanticRegion<K>>, Externalizable {

    private final int[] starts;
    private final EndSupplier ends;
    private final String[] names;
    private final K[] kinds;
    private int size;
    private transient IndexImpl index;

    @SuppressWarnings("unchecked")
    private <X extends Enum<X>> void finishReadExternal(ObjectInput in, Class<X> kindType, int sz) throws IOException, ClassNotFoundException {
        X[] allKinds = kindType.getEnumConstants();
        X[] kinds = (X[]) Array.newInstance(kindType, sz);
        int[] starts = new int[sz];
        String[] names = new String[sz];
        SerializationContext ctx = SER.get();
        for (int i = 0; i < sz; i++) {
            starts[i] = in.readInt();
            if (ctx != null) {
                names[i] = ctx.stringForIndex(in.readShort());
            } else {
                names[i] = in.readUTF();
            }
            kinds[i] = allKinds[in.readByte()];
        }
        EndSupplier ends = (EndSupplier) in.readObject();
        try {
            Field f = NamedSemanticRegions.class.getDeclaredField("starts");
            f.setAccessible(true);
            f.set(this, starts);
            f = NamedSemanticRegions.class.getDeclaredField("names");
            f.setAccessible(true);
            f.set(this, names);
            f = NamedSemanticRegions.class.getDeclaredField("kinds");
            f.setAccessible(true);
            f.set(this, kinds);
            f = NamedSemanticRegions.class.getDeclaredField("size");
            f.setAccessible(true);
            f.set(this, sz);
            f = NamedSemanticRegions.class.getDeclaredField("ends");
            f.setAccessible(true);
            f.set(this, ends);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(1); // version
        out.writeInt(size);
        out.writeUTF(kinds.getClass().getComponentType().getName());
        SerializationContext ctx = SER.get();
        short[] stringTable = ctx == null ? null : ctx.toArray(names, size);
        for (int i = 0; i < size; i++) {
            out.writeInt(starts[i]);
            if (stringTable != null) {
                out.writeShort(stringTable[i]);
            } else {
                out.writeUTF(names[i]);
            }
            out.writeByte(kinds[i].ordinal());
        }
        out.writeObject(ends);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int v = in.readInt();
        if (v != 1) {
            throw new IOException("Unsupported version " + v);
        }
        int sz = in.readInt();
        assert sz >= 0;
        String type = in.readUTF();
        Class<?> enumType = Class.forName(type);
        assert enumType.isEnum();
        finishReadExternal(in, (Class) enumType, sz);
    }

    private static ThreadLocal<SerializationContext> SER = new ThreadLocal<>();

    static void withSerializationContext(SerializationContext ctx, Callable<Void> c) throws Exception {
        SerializationContext old = SER.get();
        SER.set(ctx);
        try {
            c.call();
        } finally {
            SER.set(old);
        }
    }

    static SerializationContext createSerializationContext(Iterable<NamedSemanticRegions<?>> offsets) {
        return new SerializationContext(offsets);
    }

    /**
     * Special handling to allow multiple NamedSemanticRegions instances
     * which have overlapping names arrays to be serialized while
     * serializing only a single copy of each name.
     */
    static final class SerializationContext implements Serializable {

        private String[] strings;

        SerializationContext(Iterable<NamedSemanticRegions<?>> offsets) {
            Set<String> strings = new TreeSet<>();
            for (NamedSemanticRegions<?> o : offsets) {
                strings.addAll(Arrays.asList(o.nameArray()));
            }
            this.strings = strings.toArray(new String[strings.size()]);
        }

        public String stringForIndex(int ix) {
            return strings[ix];
        }

        public short[] toArray(String[] strings, int size) {
            short[] result = new short[size];
            for (int i = 0; i < size; i++) {
                result[i] = (short) Arrays.binarySearch(this.strings, strings[i]);
                assert result[i] >= 0 : "Missing string " + strings[i] + " in " + Arrays.toString(this.strings);
            }
            return result;
        }

        public String[] toStringArray(short[] indices) {
            String[] result = new String[indices.length];
            for (int i = 0; i < indices.length; i++) {
                result[i] = strings[indices[i]];
            }
            return result;
        }
    }

    NamedSemanticRegions(String[] names, int[] starts, int[] ends, K[] kinds, int size) {
        assert starts != null && ends != null && starts.length == ends.length;
        assert names != null && names.length == starts.length;
        assert kinds != null && kinds.length == names.length;
        assert kinds.getClass().getComponentType().isEnum();
//        assert kinds.getClass().getEnumConstants().length < 65536;
        this.names = names;
        this.starts = starts;
        this.ends = ArrayUtil.createMutableArrayEndSupplier(ends);
        this.kinds = kinds;
        this.size = size;
    }

    NamedSemanticRegions(String[] names, K[] kinds, int size) {
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

    public NamedSemanticRegions() { // for deserialization only
        this.starts = null;
        this.kinds = null;
        this.ends = null;
        this.size = -1;
        this.names = null;
    }

    String[] nameArray() {
        if (names.length != size) {
            return Arrays.copyOf(names, size);
        }
        return names;
    }

    /**
     * Create a builder for a NamedSemanticRegions with the passed key
     * type.
     *
     * @param <K> The key type
     * @param type The key type
     * @return A builder
     */
    public static <K extends Enum<K>> NamedSemanticRegionsBuilder<K> builder(Class<K> type) {
        return new NamedSemanticRegionsBuilder<>(type);
    }

    class StringEndSupplier implements EndSupplier {

        @Override
        public int get(int index) {
            return starts[index] == -1 ? -1 : starts[index] + names[index].length();
        }
    }

    public static final class NamedSemanticRegionsBuilder<K extends Enum<K>> {

        private final Class<K> type;
        private final Map<String, K> typeForName;
        private final Map<String, int[]> offsetsForName;
        private boolean needArrayBased;

        private NamedSemanticRegionsBuilder(Class<K> type) {
            this.type = type;
            typeForName = new TreeMap<>();
            offsetsForName = new TreeMap<>();
        }

        public NamedSemanticRegionsBuilder<K> arrayBased() {
            needArrayBased = true;
            return this;
        }

        public NamedSemanticRegionsBuilder<K> add(String name, K kind, int start, int end) {
            add(name, kind);
            offsetsForName.put(name, new int[]{start, end});
            if (end != name.length() + start) {
                needArrayBased = true;
            }
            return this;
        }

        public NamedSemanticRegionsBuilder<K> add(String name, K kind) {
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
        public NamedSemanticRegions<K> build() {
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
            NamedSemanticRegions<K> result;
            if (!needArrayBased) {
                result = new NamedSemanticRegions<>(names, kinds, names.length);
            } else {
                int[] starts = new int[names.length];
                int[] ends = new int[names.length];
                Arrays.fill(starts, -1);
                Arrays.fill(ends, -1);
                result = new NamedSemanticRegions<>(names, starts, ends, kinds, names.length);
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

    /**
     * Get the set of regions whose offsets have not been set.
     *
     * @return A set of names
     */
    public Set<String> regionsWithUnsetOffsets() {
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

    Set<String> removeRegionsWithNoOffsets() {
        Set<String> result = regionsWithUnsetOffsets();
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

    boolean remove(String name) {
        // XXX get mutation methods out of API
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

    /**
     * Create another NamedSemanticRegions instance, sharing this ones keys
     * array, but whose offsets are independent - useful when collecting both
     * the offsets of the names of sections of a source and also the bounds of
     * that named section.
     *
     * @return An offsets with its starts and ends uninitialized.
     */
    public NamedSemanticRegions<K> secondary() {
        // XXX should return a builder
        String[] n = Arrays.copyOf(names, size);
        K[] k = Arrays.copyOf(kinds, size);
        int[] starts = new int[size];
        int[] ends = new int[size];
        Arrays.fill(starts, -1);
        Arrays.fill(ends, -1);
        return new NamedSemanticRegions<>(n, starts, ends, k, size);
    }

    /**
     * Returns a new NamedSemanticRegions which omits entries for
     * the passed set of names.
     *
     * @param toRemove The names to remove
     * @return A new region set
     */
    public NamedSemanticRegions<K> sans(String... toRemove) {
        return sans(Arrays.asList(toRemove));
    }

    int orderingOf(int ix) {
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

    /**
     * Returns a new NamedSemanticRegions which omits entries for
     * the passed set of names.
     *
     * @param toRemove The names to remove
     * @return A new region set
     */
    @SuppressWarnings("unchecked")
    public NamedSemanticRegions<K> sans(Iterable<String> toRemove) {
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
        return new NamedSemanticRegions<>(newNames, newStarts, newEnds, newKinds, newCount);
    }

    /**
     * Get the internal index of the name in question.
     *
     * @param name The name
     * @return the index, or -1 if not present
     */
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

    /**
     * Get the region with the passed name; throws an IllegalArgumentException
     * if no such name is present.
     *
     * @param name The name
     * @return A region
     * @throws IllegalArgumentException if the name is not present
     */
    public IndexNamedSemanticRegionImpl regionFor(String name) {
        return new IndexNamedSemanticRegionImpl(internalIndexOf(name));
    }

    /**
     * Get the kind of the region with this index.
     *
     * @param index The index of the region
     * @throws ArrayIndexOutOfBoundsException if out of range
     * @return
     */
    public K kind(int index) {
        return kinds[index];
    }

    /**
     * Get the kind of the region with the passed name; throws an
     * IllegalArgumentException if no such name is present.
     *
     * @param name The name
     * @return A region
     * @throws IllegalArgumentException if the name is not present
     */
    public K kind(String name) {
        return kind(internalIndexOf(name));
    }

    /**
     * Get the name at a given index.
     *
     * @param index
     * @throws ArrayIndexOutOfBoundsException if the index is &lt; 0 or &gt;
     * size().
     * @return A name
     */
    public String get(int index) {
        return names[index];
    }

    void setKind(String name, K kind) {
        kinds[internalIndexOf(name)] = kind;
    }

    @Override
    public Iterator<NamedSemanticRegion<K>> iterator() {
        return new Iter();
    }

    /**
     * Determine if this Offsets instance contains a particular name.
     *
     * @param name The name
     * @return true if it is present
     */
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

    /**
     * Get an iterable of just those regions with the kind requested.
     *
     * @param The requested kind
     * @return kind An iterable of regions
     */
    public Iterable<NamedSemanticRegion<K>> ofKind(K kind) {
        return new ByKindIter(kind);
    }

    void collectItems(List<? super NamedSemanticRegion<K>> into) {
        for (NamedSemanticRegion<K> i : this) {
            into.add(i);
        }
    }

    private class ByKindIter implements Iterator<NamedSemanticRegion<K>>, Iterable<NamedSemanticRegion<K>> {

        private int ix = 0;
        private NamedSemanticRegion<K> nextItem;
        private final K kind;

        ByKindIter(K kind) {
            this.kind = kind;
        }

        public Iterator<NamedSemanticRegion<K>> iterator() {
            if (ix == 0) {
                return this;
            }
            return new ByKindIter(kind);
        }

        public boolean hasNext() {
            if (nextItem == null && ix < size()) {
                for (int i = ix; i < size(); i++) {
                    if (kinds[i] != kind) {
                        nextItem = new IndexNamedSemanticRegionImpl(i);
                        break;
                    }
                    ix++;
                }
            }
            return nextItem != null;
        }

        public NamedSemanticRegion<K> next() {
            NamedSemanticRegion<K> result = nextItem;
            if (result == null) {
                throw new NoSuchElementException("next() called after end");
            }
            nextItem = null;
            ix++;
            return result;
        }
    }

    public interface NamedRegionReferenceSets<K extends Enum<K>> extends Iterable<NamedRegionReferenceSet<K>>, Serializable {

        public void addReference(String name, int start, int end);

        public NamedRegionReferenceSet<K> references(String name);

        public NamedSemanticRegion<K> itemAt(int pos);

        default void collectItems(List<? super NamedSemanticRegion<K>> into) {
            for (NamedRegionReferenceSet<K> refs : this) {
                for (NamedSemanticRegion<K> item : refs) {
                    into.add(item);
                }
            }
        }

        public interface NamedRegionReferenceSet<K extends Enum<K>> extends Iterable<NamedSemanticRegion<K>>, Serializable {

            int referenceCount();

            boolean contains(int pos);

            String name();

            int visitReferences(CoordinateConsumer consumer);

            NamedSemanticRegion<K> itemAt(int pos);

            @FunctionalInterface
            interface CoordinateConsumer {

                void consume(int start, int end);
            }

            NamedSemanticRegion<K> original();

            void collectItems(List<? super NamedSemanticRegion<K>> items);
        }
    }

    final class EmptyReferenceSet implements NamedRegionReferenceSet<K> {

        private final int index;

        public EmptyReferenceSet(int index) {
            this.index = index;
        }

        public void collectItems(List<? super NamedSemanticRegion<K>> items) {
            // do nothing
        }

        public Iterator<NamedSemanticRegion<K>> iterator() {
            return Collections.emptyIterator();
        }

        public NamedSemanticRegion<K> itemAt(int pos) {
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
        public NamedSemanticRegion<K> original() {
            return new IndexNamedSemanticRegionImpl(index);
        }
    }

    public NamedRegionReferenceSets<K> newReferenceSets() {
        return new ReferenceSetsImpl();
    }

    UsagesImpl newUsages() {
        return new UsagesImpl();
    }

    interface Usages {

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
        }

        void add(String user, String uses) {
            add(internalIndexOf(user), internalIndexOf(uses));
        }

    }

    private final class ReferenceSetsImpl implements NamedRegionReferenceSets<K> {

        NamedRegionReferenceSetImpl[] sets;

        @SuppressWarnings("unchecked")
        ReferenceSetsImpl() {
//            sets = new ReferenceSetImpl[size()];
            sets = (NamedRegionReferenceSetImpl[]) Array.newInstance(NamedRegionReferenceSetImpl.class, size());
        }

        public NamedSemanticRegion<K> itemAt(int pos) {
            for (int i = 0; i < sets.length; i++) {
                if (sets[i] != null) {
                    NamedSemanticRegion<K> result = sets[i].itemAt(pos);
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
                sets[ix] = new NamedRegionReferenceSetImpl(ix);
            }
            sets[ix].add(start, end);
        }

        @Override
        public NamedRegionReferenceSet<K> references(String name) {
            int ix = internalIndexOf(name);
            return sets[ix] == null ? new EmptyReferenceSet(ix) : sets[ix];
        }

        @Override
        public Iterator<NamedRegionReferenceSet<K>> iterator() {
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

        private class SetsIter implements Iterator<NamedRegionReferenceSet<K>> {

            private int ix = 0;
            private NamedRegionReferenceSet<K> nextSet;

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

            public NamedRegionReferenceSet<K> next() {
                NamedRegionReferenceSet<K> result = nextSet;
                if (result == null) {
                    throw new NoSuchElementException("next() called after end");
                }
                nextSet = null;
                ix++;
                return result;
            }
        }

        private class NamedRegionReferenceSetImpl implements NamedRegionReferenceSet<K>, Iterable<NamedSemanticRegion<K>> {

            private int[] referenceStarts = new int[3];
//            private int[] referenceEnds = new int[3];
            private int referenceSetSize = 0;
            private final int referenceIndex;
            private final ES es = new ES();

            NamedRegionReferenceSetImpl(int index) {
                this.referenceIndex = index;
            }

            class ES implements EndSupplier {

                @Override
                public int get(int index) {
                    return referenceStarts[index] + names[referenceIndex].length();
                }

            }

            public void collectItems(List<? super NamedSemanticRegion<K>> items) {
                for (NamedSemanticRegion<K> i : this) {
                    items.add(i);
                }
            }

            public NamedSemanticRegion<K> original() {
                return new IndexNamedSemanticRegionImpl(referenceIndex);
            }

            public String name() {
                return names[referenceIndex];
            }

            void add(int start, int end) {
                assert end > start;
                assert referenceSetSize == 0 || referenceStarts[referenceSetSize - 1] < start;
                assert referenceSetSize == 0 || es.get(referenceSetSize - 1) < end;
                int newSize = referenceSetSize + 1;
                if (newSize > referenceStarts.length) {
                    int len = referenceStarts.length + 3;
                    referenceStarts = Arrays.copyOf(referenceStarts, len);
                }
                referenceStarts[referenceSetSize] = start;
//                referenceEnds[size] = end;
                referenceSetSize++;
            }

            public boolean contains(int pos) {
                return indexFor(pos) >= 0;
            }

            public NamedSemanticRegion<K> itemAt(int pos) {
                int ix = indexFor(pos);
                return ix >= 0 ? new ReferenceItem(ix) : null;
            }

            private int indexFor(int pos) {
                return ArrayUtil.rangeBinarySearch(pos, referenceStarts, es, referenceSetSize);
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
            public Iterator<NamedSemanticRegion<K>> iterator() {
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

            class RefIter implements Iterator<NamedSemanticRegion<K>> {

                private int ix = -1;

                @Override
                public boolean hasNext() {
                    return ix + 1 < referenceSetSize;
                }

                @Override
                public NamedSemanticRegion<K> next() {
                    return new ReferenceItem(++ix);
                }
            }

            class ReferenceItem implements NamedSemanticRegion<K> {

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

    private final class Iter implements Iterator<NamedSemanticRegion<K>> {

        private int ix = -1;

        @Override
        public boolean hasNext() {
            return ix + 1 < size();
        }

        @Override
        public NamedSemanticRegion<K> next() {
            return new IndexNamedSemanticRegionImpl(++ix);
        }
    }

    /**
     * A flyweight object which encapsulates one region in a
     * NamedSemanticRegions. Comparable on its start() and end() positions.
     */
    public interface NamedSemanticRegion<K extends Enum<K>> extends Comparable<NamedSemanticRegion<?>> {

        /**
         * Get the start offset, inclusive.
         *
         * @return The start offset
         */
        int start();

        /**
         * Get the end offset, exclusive.
         *
         * @return 23
         */
        int end();

        /**
         * Get the assigned "kind" of this region.
         *
         * @return The region's kind
         */
        K kind();

        /**
         * Get the position, in the total order of named regions in the owning
         * NamedSemanticRegions, of this one, based on its start and end
         * positions (this operation may be expensive).
         *
         * @return The position of this item relative to its siblings when
         * sorted by its start position
         */
        int ordering();

        /**
         * If true, this item represents a <i>reference</i> to another item (if
         * it returns true, this item belongs to a NamedRegionsReferenceSet).
         *
         * @return
         */
        boolean isReference();

        /**
         * Get the final position which is within this NamedSemanticRegions -
         * equals to end()-1.
         *
         * @return
         */
        default int stop() {
            return end() - 1;
        }

        /**
         * Get the name of this region.
         *
         * @return A name
         */
        String name();

        /**
         * Get the index of this region within the NamedSemanticRegions which
         * owns it. The meaning of this number is unspecified other than that if
         * you pass this number to fetch a region from the parent by index, you
         * will get an item which equals() this one.
         *
         * @return
         */
        int index();

        /**
         * Get the length of this region - end() - start().
         *
         * @return The length
         */
        default int length() {
            return end() - start();
        }

        /**
         * Determine if this region contains a given position - equivalent to
         * <code>pos &gt;= start() && pos &lt; end()</code>.
         *
         * @param pos A position
         * @return true if it is contained
         */
        default boolean containsPosition(int pos) {
            return pos >= start() && pos < end();
        }

        /**
         * Determine if this item is a reference to an item in a different
         * NamedSemanticRegionsobject.
         *
         * @return
         */
        default boolean isForeign() {
            return false;
        }

        @Override
        public default int compareTo(NamedSemanticRegion<?> o) {
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

    public interface NamedSemanticRegionPositionIndex<K extends Enum<K>> extends Iterable<NamedSemanticRegion<K>> {

        public NamedSemanticRegion<K> regionAt(int ix);

        public NamedSemanticRegion<K> withStart(int start);

        public NamedSemanticRegion<K> withEnd(int end);
    }

    public NamedSemanticRegionPositionIndex<K> index() {
        if (index != null) {
            return index;
        }
        List<IndexNamedSemanticRegionImpl> all = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            all.add(new IndexNamedSemanticRegionImpl(i));
        }
        Collections.sort(all);
        int[] indices = new int[size()];
        int[] sortedStarts = new int[size()];
        int[] sortedEnds = new int[size()];
        for (int i = 0; i < size(); i++) {
            IndexNamedSemanticRegionImpl item = all.get(i);
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

    private final class IndexImpl implements NamedSemanticRegionPositionIndex<K> {

        private final int[] starts;
        private final int[] ends;
        private final int[] indices;

        public IndexImpl(int[] starts, int[] ends, int[] indices) {
            this.starts = starts;
            this.ends = ends;
            this.indices = indices;
        }

        public NamedSemanticRegion<K> withStart(int start) {
            int offset = Arrays.binarySearch(starts, 0, size, start);
            return offset < 0 ? null : new IndexNamedSemanticRegionImpl(indices[offset]);
        }

        public NamedSemanticRegion<K> withEnd(int end) {
            int offset = Arrays.binarySearch(ends, 0, size, end);
            return offset < 0 ? null : new IndexNamedSemanticRegionImpl(indices[offset]);
        }

        private int indexFor(int pos) {
            // XXX could use the original end supplier rapped in one which
            // looks up by index, and forgo having an ends array here
            return ArrayUtil.rangeBinarySearch(pos, starts, new ArrayEndSupplier(ends), size);
        }

        @Override
        public NamedSemanticRegion<K> regionAt(int pos) {
            int ix = indexFor(pos);
            return ix < 0 ? null : new IndexNamedSemanticRegionImpl(ix);
        }

        @Override
        public Iterator<NamedSemanticRegion<K>> iterator() {
            return new IndexIter();
        }

        class IndexIter implements Iterator<NamedSemanticRegion<K>> {

            private int ix = -1;

            @Override
            public boolean hasNext() {
                return ix + 1 < size();
            }

            @Override
            public NamedSemanticRegion<K> next() {
                return new IndexNamedSemanticRegionImpl(indices[++ix]);
            }
        }
    }

    private class IndexNamedSemanticRegionImpl implements NamedSemanticRegion<K> {

        private final int index;

        public IndexNamedSemanticRegionImpl(int index) {
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
            } else if (o instanceof NamedSemanticRegion<?>) {
                NamedSemanticRegion<?> other = (NamedSemanticRegion<?>) o;
                return other.name().equals(name());
            } else {
                return false;
            }
        }

        public int hashCode() {
            return name().hashCode();
        }
    }

    public interface ForeignNamedSemanticRegion<K extends Enum<K>, O> extends NamedSemanticRegion<K>, Serializable {

        NamedSemanticRegions<K> originOffsets();

        O origin();

        public NamedSemanticRegion<K> originalItem();
    }

    <O> ForeignNamedSemanticRegion<K, O> newForeignItem(String name, O origin, int start, int end) {
        int ix = indexOf(name);
        if (ix >= 0) {
            return new ForeignNamedSemanticRegionImpl<O>(ix, origin, start, end);
        }
        return null;
    }

    private class ForeignNamedSemanticRegionImpl<O> implements ForeignNamedSemanticRegion<K, O> {

        private final O origin;
        private final int index;
        private final int start;

        ForeignNamedSemanticRegionImpl(int index, O origin, int start, int end) {
            this.index = index;
            this.origin = origin;
            this.start = start;
        }

        public boolean isForeign() {
            return true;
        }

        public NamedSemanticRegion<K> originalItem() {
            return new IndexNamedSemanticRegionImpl(index);
        }

        public NamedSemanticRegions<K> originOffsets() {
            return NamedSemanticRegions.this;
        }

        public O origin() {
            return origin;
        }

        @Override
        public int start() {
            return start;
        }

        @Override
        public int end() {
            return start() + name().length();
        }

        @Override
        public K kind() {
            return kinds[index];
        }

        @Override
        public int ordering() {
            return orderingOf(index);
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public String name() {
            return names[index];
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + Objects.hashCode(this.origin);
            hash = 79 * hash + this.index;
            hash = 79 * hash + this.start;
            return hash;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ForeignNamedSemanticRegionImpl<?> other = (ForeignNamedSemanticRegionImpl<?>) obj;
            if (this.index != other.index) {
                return false;
            }
            if (this.start != other.start) {
                return false;
            }
            if (!Objects.equals(this.origin, other.origin)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "foreign-ref:" + name() + "@" + start + ":" + end() + "<-" + origin
                    + ":" + originalItem();
        }
    }
}

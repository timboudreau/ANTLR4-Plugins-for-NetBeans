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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ArrayUtil.ArrayEndSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ArrayUtil.EndSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ArrayUtil.MutableEndSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSets.NamedRegionReferenceSet;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegion;

/**
 * Maps pairs of start/end offsets to a set of strings. Use with care: in
 * particular, this class does not support unsorted or duplicate names, or
 * overlapping ranges. This is the right data structure for an Antlr parse, but
 * not a general-purpose thing. It specifically uses shared arrays of names,
 * start and end positions to minimize memory and disk-cache consumption.
 *
 * @author Tim Boudreau
 */
public class NamedSemanticRegions<K extends Enum<K>> implements Iterable<NamedSemanticRegion<K>>, Externalizable, IndexAddressable<NamedSemanticRegion<K>> {

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

    interface SerializationCallback {

        void run() throws IOException, ClassNotFoundException;
    }

    static SerializationContext currentSerializationContext() {
        return SER.get();
    }

    static void withSerializationContext(SerializationContext ctx, SerializationCallback c) throws IOException, ClassNotFoundException {
        SerializationContext old = SER.get();
        SER.set(ctx);
        try {
            c.run();
        } finally {
            SER.set(old);
        }
    }

    static SerializationContext createSerializationContext(Iterable<NamedSemanticRegions<?>> offsets) {
        return new SerializationContext(offsets);
    }

    /**
     * Special handling to allow multiple NamedSemanticRegions instances which
     * have overlapping names arrays to be serialized while serializing only a
     * single copy of each name.
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
            // Shorts?  Yes, shorts.  If you have a grammar with > 64k
            // rules, you have bigger problems than the serialization
            // of anything.
            short[] result = new short[size];
            for (int i = 0; i < size; i++) {
                result[i] = (short) Arrays.binarySearch(this.strings, strings[i]);
                assert result[i] >= 0 : "Missing string " + strings[i] + " in " + Arrays.toString(this.strings);
            }
            return result;
        }

        public String[] toStringArray(short[] indices) {
            // Use a cache so that where possible, deserialized instances are
            // using shared physical arrays
            ArrayCacheKey key = new ArrayCacheKey(indices);
            String[] result = null;
            if (cache != null) {
                result = cache.get(key);
            }
            if (result == null) {
                result = new String[indices.length];
                for (int i = 0; i < indices.length; i++) {
                    result[i] = strings[indices[i]];
                }
                if (cache == null) {
                    cache = new HashMap<>();
                }
                cache.put(key, result);
            }
            return result;
        }

        private transient Map<ArrayCacheKey, String[]> cache = new HashMap<>();

        private static final class ArrayCacheKey {

            private final short[] keys;

            public ArrayCacheKey(short[] keys) {
                this.keys = keys;
            }

            public boolean equals(Object o) {
                return o instanceof ArrayCacheKey && Arrays.equals(keys, ((ArrayCacheKey) o).keys);
            }

            public int hashCode() {
                return Arrays.hashCode(keys);
            }
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
     * Create a builder for a NamedSemanticRegions with the passed key type.
     *
     * @param <K> The key type
     * @param type The key type
     * @return A builder
     */
    public static <K extends Enum<K>> NamedSemanticRegionsBuilder<K> builder(Class<K> type) {
        return new NamedSemanticRegionsBuilder<>(type);
    }

    public int indexOf(Object o) {
        if (o instanceof NamedSemanticRegion<?>) {
            NamedSemanticRegion<?> n = (NamedSemanticRegion<?>) o;
            if (contains(n.name())) { // XXX check ownership instead?
                return n.index();
            }
        }
        return -1;
    }

    public boolean isChildType(IndexAddressable.IndexAddressableItem item) {
        return item instanceof NamedSemanticRegion<?>;
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

    @Override
    public NamedSemanticRegion<K> forIndex(int index) {
        assert index >= 0 && index < size;
        return new IndexNamedSemanticRegionImpl(index);
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
        // XXX forIndex mutation methods out of API
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
     * Returns a new NamedSemanticRegions which omits entries for the passed set
     * of names.
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
     * Returns a new NamedSemanticRegions which omits entries for the passed set
     * of names.
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

    public interface NamedRegionReferenceSets<K extends Enum<K>> extends Iterable<NamedRegionReferenceSet<K>>, Serializable, IndexAddressable<NamedSemanticRegionReference<K>> {

        public NamedRegionReferenceSet<K> references(String name);

        public NamedSemanticRegionReference<K> itemAt(int pos);

        default void collectItems(List<? super NamedSemanticRegionReference<K>> into) {
            for (NamedRegionReferenceSet<K> refs : this) {
                for (NamedSemanticRegionReference<K> item : refs) {
                    into.add(item);
                }
            }
        }

        public interface NamedRegionReferenceSet<K extends Enum<K>> extends Iterable<NamedSemanticRegionReference<K>>, Serializable, IndexAddressable<NamedSemanticRegionReference<K>> {

            int size();

            boolean contains(int pos);

            String name();

            NamedSemanticRegionReference<K> at(int pos);

            NamedSemanticRegion<K> original();

            default void collectItems(List<? super NamedSemanticRegionReference<K>> into) {
                for (NamedSemanticRegionReference<K> item : this) {
                    into.add(item);
                }
            }
        }
    }

    final class EmptyReferenceSet implements NamedRegionReferenceSet<K> {

        private final int index;

        public EmptyReferenceSet(int index) {
            this.index = index;
        }

        public boolean isChildType(IndexAddressableItem foo) {
            return false;
        }

        public void collectItems(List<? super NamedSemanticRegionReference<K>> items) {
            // do nothing
        }

        @Override
        public NamedSemanticRegionReference<K> forIndex(int index) {
            throw new IllegalArgumentException("Empty reference set: " + index);
        }

        public Iterator<NamedSemanticRegionReference<K>> iterator() {
            return Collections.emptyIterator();
        }

        public NamedSemanticRegionReference<K> at(int pos) {
            return null;
        }

        public String name() {
            return names[index];
        }

        public boolean contains(int pos) {
            return false;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public NamedSemanticRegion<K> original() {
            return new IndexNamedSemanticRegionImpl(index);
        }

        @Override
        public int indexOf(Object o) {
            return -1;
        }
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

    public NamedRegionReferenceSetsBuilder<K> newReferenceSetsBuilder() {
        return new ReferenceSetsBuilderImpl();
    }

    public static abstract class NamedRegionReferenceSetsBuilder<K extends Enum<K>> {

        private NamedRegionReferenceSetsBuilder() {
        }

        public abstract void addReference(String name, int start, int end);

        public abstract NamedRegionReferenceSets<K> build();
    }

    private final class ReferenceSetsBuilderImpl extends NamedRegionReferenceSetsBuilder<K> {

        SemanticRegions.SemanticRegionsBuilder<Integer> bldr = SemanticRegions.builder(Integer.class);

        public void addReference(String name, int start, int end) {
            int ix = internalIndexOf(name);
            bldr.add(ix, start, end);
        }

        public NamedRegionReferenceSets<K> build() {
            return new ReferenceSetsImpl(bldr.build());
        }
    }

    public interface NamedSemanticRegionReference<K extends Enum<K>> extends NamedSemanticRegion<K> {

        public NamedSemanticRegion<K> referencing();

        public int referencedIndex();
    }

    private final class ReferenceSetsImpl implements NamedRegionReferenceSets<K> {

        private final SemanticRegions<Integer> regions;

        ReferenceSetsImpl(SemanticRegions<Integer> regions) {
            this.regions = regions;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (SemanticRegion<Integer> reg : regions) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                RefItem i = new RefItem(reg);
                sb.append(i);
            }
            return sb.toString();
        }

        @Override
        public NamedSemanticRegionReference<K> itemAt(int pos) {
            SemanticRegion<Integer> reg = regions.at(pos);
            return reg == null ? null : new RefItem(reg);
        }

        @Override
        public int size() {
            return regions.size();
        }

        @Override
        public NamedSemanticRegionReference<K> at(int position) {
            return itemAt(position);
        }

        @Override
        public boolean isChildType(IndexAddressableItem item) {
            boolean result = item != null && item.getClass() == RefItem.class
                    && ((RefItem) item).owner() == this;
            if (!result && item != null && item.getClass() == NamedRegionReferenceSetImpl.ReferenceSetWrapper.class) {
                NamedRegionReferenceSetImpl.ReferenceSetWrapper w = (NamedRegionReferenceSetImpl.ReferenceSetWrapper) item;
                RefItem ri = w.ri;
                return isChildType(ri);
            }
            return result;
        }

        @Override
        public int indexOf(Object o) {
            if (o instanceof IndexAddressableItem && isChildType((IndexAddressableItem) o)) {
                if (o.getClass() == NamedRegionReferenceSetImpl.ReferenceSetWrapper.class) {
                    o = ((NamedRegionReferenceSetImpl.ReferenceSetWrapper) o).ri;
                }
                RefItem ri = (RefItem) o;
                return ri.index();
            }
            return -1;
        }

        @Override
        public NamedSemanticRegionReference<K> forIndex(int index) {
            return regionFor(regions.forIndex(index));
        }

        private RefItem regionFor(SemanticRegion<Integer> reg) {
            return reg == null ? null : new RefItem(reg);
        }

        @Override
        public NamedRegionReferenceSet<K> references(String name) {
            int ix = internalIndexOf(name);
            int[][] ices = this.keyIndex();
            if (ices[ix] == null) {
                return null;
            }
            return new NamedRegionReferenceSetImpl(ix, ices[ix]);
        }

        @Override
        public Iterator<NamedRegionReferenceSet<K>> iterator() {
            return new RefI();
        }

        private class RefI implements Iterator<NamedRegionReferenceSet<K>> {

            private final int[][] index = keyIndex();
            private int ix = -1;

            public boolean hasNext() {
                return ix + 1 < index.length;
            }

            public NamedRegionReferenceSet<K> next() {
                int[] nxt = index[++ix];
                if (nxt != null) {
                    return new NamedRegionReferenceSetImpl(ix, nxt);
                } else {
                    return new EmptyReferenceSet(ix);
                }
            }
        }

        private class NamedRegionReferenceSetImpl implements NamedRegionReferenceSet<K>, IndexAddressable<NamedSemanticRegionReference<K>> {

            private final int[] keys;
            private final int origIndex;

            public NamedRegionReferenceSetImpl(int origIndex, int[] keys) {
                this.keys = keys;
                this.origIndex = origIndex;
            }

            @Override
            public int size() {
                return keys.length;
            }

            public ReferenceSetWrapper forIndex(int ix) {
                if (ix < 0 || ix > keys.length) {
                    throw new IllegalArgumentException("Out of range: " + ix);
                }
                return new ReferenceSetWrapper(new RefItem(regions.forIndex(keys[ix])), ix);
            }

            @Override
            public boolean contains(int pos) {
                SemanticRegion<Integer> reg = regions.at(pos);
                if (reg != null) {
                    return reg.key() == origIndex;
                }
                return false;
            }

            @Override
            public String name() {
                return NamedSemanticRegions.this.names[origIndex];
            }

            @Override
            public NamedSemanticRegionReference<K> at(int pos) {
                SemanticRegion<Integer> i = regions.at(pos);
                if (i != null && origIndex == i.key()) {
                    int localIndex = Arrays.binarySearch(keys, i.key());
                    return localIndex < 0
                            ? null
                            : new ReferenceSetWrapper(regionFor(i), origIndex);
                }
                return null;
            }

            @Override
            public NamedSemanticRegion<K> original() {
                return NamedSemanticRegions.this.forIndex(origIndex);
            }

            @Override
            public void collectItems(List<? super NamedSemanticRegionReference<K>> items) {
                for (NamedSemanticRegionReference<K> r : this) {
                    items.add(r);
                }
            }

            @Override
            public Iterator<NamedSemanticRegionReference<K>> iterator() {
                return new SI();
            }

            @Override
            public boolean isChildType(IndexAddressableItem item) {
                if (item == null) {
                    return false;
                }
                if (item instanceof NamedSemanticRegionReference<?>) {
                    if (ReferenceSetWrapper.class == item.getClass()) {
                        ReferenceSetWrapper w = (ReferenceSetWrapper) item;
                        return w.owner() == this
                                && ReferenceSetsImpl.this.isChildType(w.ri);
                    }
                }
                return false;
            }

            @Override
            public int indexOf(Object o) {
                if (o instanceof IndexAddressableItem && isChildType((IndexAddressableItem) o)) {
                    return ((IndexAddressableItem) o).index();
                }
                return -1;
            }

            class SI implements Iterator<NamedSemanticRegionReference<K>> {

                int ix = -1;

                public boolean hasNext() {
                    return ix + 1 < keys.length;
                }

                public NamedSemanticRegionReference<K> next() {
                    if (ix >= keys.length) {
                        throw new NoSuchElementException();
                    }
                    SemanticRegion<Integer> reg = regions.forIndex(keys[++ix]);
                    return new ReferenceSetWrapper(regionFor(reg), ix);
                }
            }

            class ReferenceSetWrapper implements NamedSemanticRegionReference<K> {

                private final RefItem ri;
                private final int setIndex;

                public ReferenceSetWrapper(RefItem ri, int setIndex) {
                    this.ri = ri;
                    this.setIndex = setIndex;
                }

                NamedRegionReferenceSetImpl owner() {
                    return NamedRegionReferenceSetImpl.this;
                }

                @Override
                public NamedSemanticRegion<K> referencing() {
                    return ri.referencing();
                }

                @Override
                public int referencedIndex() {
                    return ri.referencedIndex();
                }

                @Override
                public K kind() {
                    return ri.kind();
                }

                @Override
                public int ordering() {
                    return ri.ordering();
                }

                @Override
                public boolean isReference() {
                    return true;
                }

                @Override
                public String name() {
                    return ri.name();
                }

                @Override
                public int start() {
                    return ri.start();
                }

                @Override
                public int end() {
                    return ri.end();
                }

                @Override
                public int index() {
                    return setIndex;
                }

                public int hashCode() {
                    return ri.hashCode() + (7 * setIndex);
                }

                public boolean equals(Object o) {
                    if (o == null) {
                        return false;
                    } else if (o == this) {
                        return true;
                    } else if (o instanceof NamedSemanticRegionReference<?>) {
                        NamedSemanticRegionReference<?> other = (NamedSemanticRegionReference<?>) o;
                        return start() == other.start() && end() == other.end() && name().equals(other.name())
                                && Objects.equals(kind(), other.kind());
                    }
                    return false;
                }

                public String toString() {
                    return "set:" + setIndex + ":" + ri;
                }
            }
        }

        private transient int[][] keyIndex;

        private int[][] keyIndex() {
            if (keyIndex != null) {
                return keyIndex;
            }
            Map<Integer, List<SemanticRegion<Integer>>> m = new TreeMap<>();
            for (SemanticRegion<Integer> r : regions) {
                List<SemanticRegion<Integer>> forRegion = m.get(r.key());
                if (forRegion == null) {
                    forRegion = new ArrayList<>(15);
                    m.put(r.key(), forRegion);
                }
                forRegion.add(r);
            }
            int[][] result = new int[NamedSemanticRegions.this.size()][];
            for (int i = 0; i < NamedSemanticRegions.this.size(); i++) {
                List<SemanticRegion<Integer>> l = m.get(i);
                if (l != null) {
                    int[] items = new int[l.size()];
                    result[i] = items;
                    for (int j = 0; j < items.length; j++) {
                        items[j] = l.get(j).index();
                    }
                }
            }
            return keyIndex = result;
        }

        class RefItem implements NamedSemanticRegionReference<K> {

            private final SemanticRegion<Integer> reg;
            private final int ix;

            public RefItem(SemanticRegion<Integer> reg) {
                this.reg = reg;
                ix = reg.key();
            }

            public int hashCode() {
                return ix + (7 * reg.hashCode());
            }

            public boolean equals(Object o) {
                if (o == null) {
                    return false;
                } else if (o == this) {
                    return true;
                } else if (o instanceof NamedSemanticRegionReference<?>) {
                    NamedSemanticRegionReference<?> other = (NamedSemanticRegionReference<?>) o;
                    return start() == other.start() && end() == other.end() && name().equals(other.name())
                            && Objects.equals(kind(), other.kind());
                }
                return false;
            }

            ReferenceSetsImpl owner() {
                return ReferenceSetsImpl.this;
            }

            @Override
            public K kind() {
                return kinds[ix];
            }

            @Override
            public int ordering() {
                return reg.index();
            }

            @Override
            public boolean isReference() {
                return true;
            }

            @Override
            public String name() {
                return names[ix];
            }

            @Override
            public int start() {
                return reg.start();
            }

            @Override
            public int end() {
                return reg.end();
            }

            @Override
            public int index() {
                return reg.index();
            }

            public NamedSemanticRegion<K> referencing() {
                return NamedSemanticRegions.this.forIndex(ix);
            }

            public int referencedIndex() {
                return ix;
            }

            public String toString() {
                return "ref:" + index() + ":" + name() + "@" + start() + ":" + end() + "->" + referencedIndex();
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
    public interface NamedSemanticRegion<K extends Enum<K>> extends IndexAddressable.IndexAddressableItem {

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
         * Get the name of this region.
         *
         * @return A name
         */
        String name();

        /**
         * Determine if this item is a reference to an item in a different
         * NamedSemanticRegionsobject.
         *
         * @return
         */
        default boolean isForeign() {
            return false;
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

    public String toCode() {
        String tn = this.kType().getName().replace('$', '.');
        StringBuilder sb = new StringBuilder("NamedSemanticRegionsBuilder<").append(tn).append("> bldr=NamedSemanticRegions.builder(")
                .append(tn).append(".class").append(");\n");
        for (int i = 0; i < size; i++) {
            // String name, K kind, int start, int end
            sb.append("bldr.add(\"").append(names[i]).append('"').append(", ")
                    .append(kinds == null || kinds[i] == null ? "null"
                            : tn + "." + kinds[i].name()).append(", ")
                    .append(starts[i]).append(", ").append(ends.get(i)).append(");\n");
        }
        return sb.toString();
    }

    public NamedSemanticRegion<K> at(int position) {
        return index().regionAt(position);
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

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < starts.length; i++) {
                sb.append(names[indices[i]] + "@" + starts[i] + ":" + ends[i]);
                if (i != starts.length - 1) {
                    sb.append(", ");
                }
            }
            return sb.toString();
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
            int result = ArrayUtil.rangeBinarySearch(pos, starts, new ArrayEndSupplier(ends), size);
            assert result < 0 || (pos >= starts[result] && pos < ends[result]) : "rangeBinarySearch bogus result for regionAt(" + pos + ")"
                    + new IndexNamedSemanticRegionImpl(indices[result]);
            return result;
        }

        @Override
        public NamedSemanticRegion<K> regionAt(int pos) {
            int ix = indexFor(pos);
            return ix < 0 ? null : new IndexNamedSemanticRegionImpl(indices[ix]);
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

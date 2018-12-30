package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.graph;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.Indexed;

/**
 * A set implementation which takes a larger bitset and a
 * smaller indexed, and constrains the set to the starting offset
 * bit and the length of the indexed, for use by
 * BitSetHeteroObjectGraph, which models two sets of heterogenous
 * types as a single bitset mapped to two sets of indexeds.
 *
 * @author Tim Boudreau
 */
final class BitSetSliceSet<T> extends AbstractSet<T> implements Set<T> {

    private final BitSet set;
    private final Indexed<T> data;
    private final int start;

    public BitSetSliceSet(Indexed<T> data, BitSet set, int start) {
        this.data = data;
        this.set = set;
        this.start = start;
    }

    @Override
    public int size() {
        return set.cardinality();
    }

    @Override
    public boolean isEmpty() {
        return set.nextSetBit(start) < 0;
    }

    @Override
    public boolean contains(Object o) {
        int ix = indexOf(o);
        return ix < 0 ? false : set.get(ix);
    }

    @Override
    public Iterator<T> iterator() {
        if (isEmpty()) {
            return Collections.emptyIterator();
        }
        return new Iter();
    }

    class Iter implements Iterator<T> {

        int ix = start - 1;
        int count = 0;

        @Override
        public boolean hasNext() {
            int result = set.nextSetBit(ix + 1);
            return result >= 0 && count < data.size();
        }

        @Override
        public T next() {
            int offset = set.nextSetBit(ix + 1);
            if (offset < 0) {
                throw new IllegalStateException();
            }
            ix = offset;
            T result = get(ix);
            count++;
            return result;
        }
    }

    @Override
    public boolean add(T e) {
        int ix = indexOf(e);
        if (ix < 0) {
            throw new IllegalArgumentException("Not in set: " + e);
        }
        boolean wasSet = set.get(ix);
        set.set(ix);
        return !wasSet;
    }

    @Override
    public boolean remove(Object o) {
        int ix = indexOf(o);
        if (ix < 0) {
            return false;
        }
        boolean wasSet = set.get(ix);
        set.clear(ix);
        return wasSet;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        BitSet nue = new BitSet(set.size());
        for (T obj : c) {
            int ix = indexOf(obj);
            if (ix < 0) {
                throw new IllegalArgumentException(obj + "");
            }
            nue.set(ix);
        }
        int oldCardinality = set.cardinality();
        set.or(nue);
        return set.cardinality() != oldCardinality;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        BitSet nue = new BitSet(set.size());
        if (start > 0) {
            nue.set(0, start - 1);
        }
        for (Object o : c) {
            int ix = indexOf(o);
            if (ix >= 0) {
                nue.set(ix);
            }
        }
        int oldCardinality = set.cardinality();
        set.and(nue);
        return oldCardinality != set.cardinality();
    }

    private int indexOf(Object o) {
        return data.indexOf(o) + start;
    }

    private T get(int index) {
        try {
            return data.forIndex(index - start);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Indexed size mismatch in "
                    + "BitSetSliceSet starting at " + start + " with cardinality " + set.cardinality()
                    + " firstSetBit " + firstSet() + " lastSetBit " + lastSet()
                    + " over indexed with size " + data.size(),
                    ex);
        }
    }

    private int lastSet() {
        return set.previousSetBit(Integer.MAX_VALUE);
    }

    private int firstSet() {
        return set.nextSetBit(start);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        BitSet nue = new BitSet(set.size());
        for (Object o : c) {
            int ix = indexOf(o);
            if (ix >= 0) {
                nue.set(ix);
            }
        }
        int oldCardinality = set.cardinality();
        set.andNot(nue);
        return oldCardinality != set.cardinality();
    }

    @Override
    public void clear() {
        set.clear();
    }
}

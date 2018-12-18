package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public final class BitSetSet<T> extends AbstractSet<T> implements Set<T> {

    private final BitSet set;
    private final Indexed<T> data;

    public BitSetSet(Indexed<T> data) {
        this(data, new BitSet(data.size()));
    }

    public BitSetSet(Indexed<T> data, BitSet set) {
        this.data = data;
        this.set = set;
    }

    public static BitSetSet forStringList(List<String> data) {
        return new BitSetSet<String>(Indexed.forStringList(data));
    }

    public static BitSetSet forStringSet(Set<String> data) {
        return new BitSetSet<String>(Indexed.forStringSet(data));
    }

    @Override
    public int size() {
        return set.cardinality();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        int ix = indexOf(o);
        return ix < 0 ? false : set.get(ix);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iter();
    }

    class Iter implements Iterator<T> {

        int ix = -1;

        @Override
        public boolean hasNext() {
            return set.nextSetBit(ix + 1) >= 0;
        }

        @Override
        public T next() {
            int offset = set.nextSetBit(ix + 1);
            if (offset < 0) {
                throw new IllegalStateException();
            }
            ix = offset;
            return get(ix);
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
        return data.indexOf(o);
    }

    private T get(int index) {
        return data.forIndex(index);
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

package org.nemesis.misc.utils;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.function.IntConsumer;
import jdk.internal.HotSpotIntrinsicCandidate;

/**
 * A set of primitive integers based on a BitSet, which implements
 * <code>Set&lt;Integer&gt;</code> for convenience. For performance, this is
 * considerably faster than using a standard Java set with a list maintained
 * along side it for random access <i>IF the range from lowest to highest
 * integer in the set is small</i>.
 * <p>
 * <b>Does not support negative integer values.</b>
 * </p>
 *
 *
 * @author Tim Boudreau
 */
public abstract class IntSet implements Set<Integer>, Cloneable {

    @HotSpotIntrinsicCandidate
    IntSet() {
    }

    public static IntSet create(int capacity) {
        return new IntSetImpl(capacity);
    }

    public static IntSet create() {
        return create(96);
    }

    public static IntSet create(int[] arr) {
        BitSet set = new BitSet(arr.length);
        for (int i = 0; i < arr.length; i++) {
            set.set(arr[i]);
        }
        return new IntSetImpl(set);
    }

    public static IntSet create(BitSet bits) {
        return new IntSetImpl((BitSet) bits.clone());
    }

    public static IntSet create(Collection<? extends Integer> set) {
        if (set instanceof IntSet) {
            return (IntSet) set;
        } else {
            return new IntSetImpl(set);
        }
    }

    public static IntSet merge(Iterable<IntSet> all) {
        BitSet bits = null;
        for (IntSet i : all) {
            if (bits == null) {
                bits = (BitSet) i.toBits();
            } else {
                bits.or(bits);
            }
        }
        return bits == null ? new IntSetImpl(1) : new IntSetImpl(bits);
    }

    public static IntSet intersection(Iterable<IntSet> all) {
        BitSet bits = null;
        for (IntSet i : all) {
            if (bits == null) {
                bits = (BitSet) i.toBits();
            } else {
                bits.and(bits);
            }
        }
        return bits == null ? new IntSetImpl(1) : new IntSetImpl(bits);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return copy();
    }

    public IntSet copy() {
        return create(_unsafeBits());
    }

    BitSet _unsafeBits() {
        return toBits();
    }

    public abstract IntSet or(IntSet other);

    public abstract IntSet xor(IntSet other);

    public abstract IntSet addAll(int... ints);

    /**
     * Returns a <i>copy</i> of this IntSet's internal state, modifications to
     * which shall not affect this set.
     *
     * @return A bit set
     */
    public abstract BitSet toBits();

    public final boolean add(int val) {
        if (val < 0) {
            throw new IllegalArgumentException("Negative values not allowed");
        }
        return _add(val);
    }

    abstract boolean _add(int val);

    public abstract boolean remove(int val);

    public abstract int first();

    public abstract int removeFirst();

    public abstract int removeLast();

    public abstract void forEach(IntConsumer cons);

    public abstract void forEachReversed(IntConsumer cons);

    public abstract int[] toIntArray();

    public abstract int last();

    public abstract boolean contains(int val);

    @Override
    public abstract void clear();

    public static final IntSet EMPTY = new Empty();

    private static final class Empty extends IntSet {

        @Override
        public IntSet or(IntSet other) {
            return other;
        }

        @Override
        public IntSet xor(IntSet other) {
            return new IntSetImpl().xor(other);
        }

        @Override
        public IntSet addAll(int... ints) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public BitSet toBits() {
            return new BitSet(1);
        }

        @Override
        boolean _add(int val) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean remove(int val) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public int first() {
            return -1;
        }

        @Override
        public int removeFirst() {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public int removeLast() {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public void forEach(IntConsumer cons) {
            // do nothing
        }

        @Override
        public void forEachReversed(IntConsumer cons) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public int[] toIntArray() {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public int last() {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean contains(int val) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean isEmpty() {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean contains(Object o) {
            return false;
        }

        @Override
        public Iterator<Integer> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public Object[] toArray() {
            return new Object[0];
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return (T[]) new Object[0];
        }

        @Override
        public boolean add(Integer e) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean addAll(Collection<? extends Integer> c) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("Immutable.");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException("Immutable.");
        }

    }
}

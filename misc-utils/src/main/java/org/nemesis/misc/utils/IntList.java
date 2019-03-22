package org.nemesis.misc.utils;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Just an implementation of List backed by a primitive array.
 *
 * @author Tim Boudreau
 */
public final class IntList extends AbstractList<Integer> {

    private int[] values;
    private int size;
    private final int initialCapacity;

    IntList(int initialCapacity) {
        this.values = new int[initialCapacity];
        this.initialCapacity = initialCapacity;
    }

    IntList() {
        this(96);
    }

    IntList(int[] ints) {
        this(ints, false);
    }

    IntList(int[] ints, boolean unsafe) {
        this.values = ints.length == 0 ? new int[16] : unsafe ? ints : Arrays.copyOf(ints, ints.length);
        this.size = ints.length;
        initialCapacity = Math.max(16, size);
    }

    public static IntList create() {
        return new IntList();
    }

    public static IntList create(int initialCapacity) {
        return new IntList(initialCapacity);
    }

    public static IntList create(Collection<? extends Integer> vals) {
        IntList result = new IntList(vals.size());
        result.addAll(vals);
        return result;
    }

    public static IntList createFrom(int... vals) {
        return new IntList(vals);
    }

    @Override
    public boolean addAll(Collection<? extends Integer> c) {
        if (c instanceof IntList) {
            if (!c.isEmpty()) {
                maybeGrow(size + c.size());
                IntList il = (IntList) c;
                System.arraycopy(il.values, 0, values, size, il.size);
                size += il.size;
            }
            return c.isEmpty();
        } else {
            return super.addAll(c);
        }
    }

    @Override
    public IntList subList(int fromIndex, int toIndex) {
        checkIndex(fromIndex);
        checkIndex(toIndex);
        int[] nue = new int[toIndex - fromIndex];
        System.arraycopy(values, fromIndex, nue, 0, nue.length);
        return new IntList(nue, true);
    }

    public int[] toIntArray() {
        return Arrays.copyOf(values, size);
    }

    public int getAsInt(int index) {
        checkIndex(index);
        return values[index];
    }

    public boolean removeLast() {
        if (size > 0) {
            size--;
            return true;
        }
        return false;
    }

    @Override
    public int size() {
        return size;
    }

    public void add(int value) {
        maybeGrow(size + 1);
        values[size++] = value;
    }

    public void addArray(int... arr) {
        maybeGrow(size + arr.length);
        System.arraycopy(arr, 0, values, size, arr.length);
        size += arr.length;
    }

    public int indexOf(int value) {
        for (int i = 0; i < size; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(int value) {
        for (int i = 0; i < size; i++) {
            if (values[i] == value) {
                return true;
            }
        }
        return false;
    }

    public void addAll(int... values) {
        maybeGrow(size + values.length);
        System.arraycopy(values, 0, this.values, size, values.length);
        size += values.length;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index not between 0-" + size + ": " + index);
        }
    }

    public void removeAt(int index) {
        checkIndex(index);
        if (index != size - 1) {
            System.arraycopy(values, index + 1, values, index, size - (index + 1));
        }
        size--;
    }

    public Integer remove(int index) {
        checkIndex(index);
        int old = values[index];
        if (index != size - 1) {
            System.arraycopy(values, index + 1, values, index, size - (index + 1));
        }
        size--;
        return old;
    }

    public void forEach(IntConsumer c) {
        for (int i = 0; i < size; i++) {
            c.accept(values[i]);
        }
    }

    private void maybeGrow(int newSize) {
        if (newSize >= values.length) {
            if (newSize % initialCapacity == 0) {
                newSize += initialCapacity;
            } else {
                newSize = initialCapacity * Math.max(initialCapacity * 2, (newSize / initialCapacity) + 1);
            }
            values = Arrays.copyOf(values, newSize);
        }
    }

    @Override
    public Integer get(int index) {
        checkIndex(index);
        return values[index];
    }

    @Override
    public void forEach(Consumer<? super Integer> action) {
        for (int i = 0; i < size; i++) {
            action.accept(values[i]);
        }
    }

    public void addAll(int index, int... nue) {
        checkIndex(index);
        maybeGrow(size + values.length);
        System.arraycopy(values, index, values, index + nue.length, size - index);
        System.arraycopy(nue, 0, values, index, values.length);
        size += values.length;
    }

    @Override
    public boolean addAll(int index, Collection<? extends Integer> c) {
        if (c.isEmpty()) {
            return false;
        }
        if (c.size() == 1) {
            return add(c.iterator().next());
        }
        int[] all = new int[c.size()];
        int i = 0;
        for (Iterator<? extends Integer> it = c.iterator(); it.hasNext(); i++) {
            all[i] = it.next();
        }
        addAll(index, all);
        return true;
    }

    @Override
    public void clear() {
        size = 0;
    }

    public int lastIndexOf(int i) {
        for (int j = size - 1; j >= 0; j--) {
            if (values[j] == i) {
                return j;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (!(o instanceof Integer)) {
            return -1;
        }
        return lastIndexOf(((Integer) o).intValue());
    }

    @Override
    public void add(int index, Integer element) {
        super.add(index, element); //To change body of generated methods, choose Tools | Templates.
    }

    public int set(int index, int value) {
        checkIndex(index);
        int old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public Integer set(int index, Integer element) {
        return set(index, element.intValue());
    }

    @Override
    public boolean add(Integer e) {
        add(e.intValue());
        return true;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iter();
    }

    private class Iter implements Iterator<Integer> {

        private int pos = -1;

        @Override
        public boolean hasNext() {
            return pos + 1 < size;
        }

        @Override
        public Integer next() {
            if (pos >= size) {
                throw new NoSuchElementException(pos + " of " + size);
            }
            return values[++pos];
        }
    }

    public String toString() {
        return StringUtils.join(',', this);
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        for (int i = 0; i < size; i++) {
            hashCode = 31 * hashCode + values[i];
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (o instanceof IntList) {
            IntList other = (IntList) o;
            if (other.size != size) {
                return false;
            }
            if (size == 0) {
                return true;
            }
            return Arrays.equals(values, 0, size, other.values, 0, size);
        } else {
            return super.equals(o);
        }
    }

}

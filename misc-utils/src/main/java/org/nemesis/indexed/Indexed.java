package org.nemesis.indexed;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/**
 * A minimal abstraction of a List that lets items be retrieved by index.
 *
 * @author Tim Boudreau
 */
public interface Indexed<T> {

    int indexOf(Object o);

    T forIndex(int index);

    int size();

    default Iterable<T> asIterable() {
        class I implements Iterable<T>, Iterator<T> {

            int ix = -1;

            @Override
            public Iterator<T> iterator() {
                return this;
            }

            @Override
            public boolean hasNext() {
                return ix + 1 < size();
            }

            @Override
            public T next() {
                return forIndex(++ix);
            }
        }
        return new I();
    }

    default List<T> populate(List<T> list) {
        int sz = size();
        for (int i = 0; i < sz; i++) {
            list.add(forIndex(i));
        }
        return list;
    }

    default List<T> toList() {
        return populate(new ArrayList<>(size()));
    }

    static Indexed<String> forSortedStringArray(String... strings) {
        return new StringArrayIndexed(strings);
    }

    static Indexed<String> forStringSet(Set<String> set) {
        String[] sortedArray = new String[set.size()];
        sortedArray = set.toArray(sortedArray);
        if (!(set instanceof SortedSet<?>)) {
            Arrays.sort(sortedArray);
        }
        return new StringArrayIndexed(sortedArray);
    }

    static Indexed<String> forStringList(List<String> set) {
        String[] sortedArray = new String[set.size()];
        sortedArray = set.toArray(sortedArray);
        Arrays.sort(sortedArray);
        return new StringArrayIndexed(sortedArray);
    }


    static <T> Indexed<T> forList(List<T> list) {
        assert new HashSet<>(list).size() == list.size();
        return new Indexed<T>() {
            @Override
            public int indexOf(Object o) {
                return list.indexOf(o);
            }

            @Override
            public T forIndex(int index) {
                return list.get(index);
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    default Collection<T> asCollection() {
        return new Collection<T>(){
            @Override
            public int size() {
                return Indexed.this.size();
            }

            @Override
            public boolean isEmpty() {
                return Indexed.this.size() == 0;
            }

            @Override
            public boolean contains(Object o) {
                int sz = size();
                for (int i=0; i < sz; i++) {
                    if (Objects.equals(o, Indexed.this.forIndex(i))) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Iterator<T> iterator() {
                return asIterable().iterator();
            }

            @Override
            public Object[] toArray() {
                Object[] objs = new Object[size()];
                for (int i = 0; i < objs.length; i++) {
                    objs[i] = forIndex(i);
                }
                return objs;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T[] toArray(T[] a) {
                int sz = size();
                if (sz != a.length) {
                    a = (T[]) Array.newInstance(a.getClass().getComponentType(), sz);
                }
                for (int i=0; i < sz; i++) {
                    a[i] = (T) forIndex(i);
                }
                return a;
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
            public boolean add(T e) {
                throw new UnsupportedOperationException("read-only");
            }

            @Override
            public boolean remove(Object o) {
                throw new UnsupportedOperationException("read-only");
            }

            @Override
            public boolean addAll(Collection<? extends T> c) {
                throw new UnsupportedOperationException("read-only");
            }

            @Override
            public boolean removeAll(Collection<?> c) {
                throw new UnsupportedOperationException("read-only");
            }

            @Override
            public boolean retainAll(Collection<?> c) {
                throw new UnsupportedOperationException("read-only");
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException("read-only");
            }
        };
    }
}

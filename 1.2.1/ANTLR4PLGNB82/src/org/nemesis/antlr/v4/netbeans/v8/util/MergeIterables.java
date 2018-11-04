package org.nemesis.antlr.v4.netbeans.v8.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Handles creating iterators that merge several iterables.
 *
 * @author Tim Boudreau
 */
public class MergeIterables<T> implements Iterable<T> {

    private final List<Iterable<T>> all = new LinkedList<>();

    @SafeVarargs
    public MergeIterables(Iterable<T>... iterables) {
        all.addAll(Arrays.asList(iterables));
    }

    public MergeIterables() {

    }

    public void add(Iterable<T> iterable) {
        all.add(iterable);
    }

    public Iterator<T> iterator() {
        if (all.isEmpty()) {
            return Collections.emptyIterator();
        } else if (all.size() == 1) {
            return all.iterator().next().iterator();
        }
        LinkedList<Iterator<T>> iterators = new LinkedList<>();
        for (Iterable<T> iterable : all) {
            iterators.add(iterable.iterator());
        }
        return new MergeIterator<T>(iterators);
    }

    private static final class MergeIterator<T> implements Iterator<T> {

        private final LinkedList<Iterator<T>> iterators;

        MergeIterator(LinkedList<Iterator<T>> iterators) {
            this.iterators = iterators;
        }

        private Iterator<T> iter() {
            if (iterators.isEmpty()) {
                return null;
            }
            Iterator<T> result = iterators.get(0);
            if (!result.hasNext()) {
                iterators.remove(0);
                return iter();
            }
            return result;
        }

        @Override
        public boolean hasNext() {
            Iterator<T> curr = iter();
            return curr == null ? false : curr.hasNext();
        }

        @Override
        public T next() {
            Iterator<T> iter = iter();
            if (iter == null) {
                throw new NoSuchElementException();
            }
            return iter.next();
        }

        @Override
        public void remove() {
            Iterator<T> iter = iter();
            if (iter == null) {
                throw new NoSuchElementException();
            }
            iter.remove();
        }
    }

}

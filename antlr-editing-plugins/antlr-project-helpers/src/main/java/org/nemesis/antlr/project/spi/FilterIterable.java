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
package org.nemesis.antlr.project.spi;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
final class FilterIterable<T> implements Iterable<T> {

    private final Iterable<T> original;
    private final Predicate<T> filter;

    public FilterIterable(Iterable<T> original, Predicate<T> filter) {
        this.original = original;
        this.filter = filter;
    }

    @Override
    public Iterator<T> iterator() {
        return new FilterIterator<>(original.iterator(), filter);
    }

    private static final class FilterIterator<T> implements Iterator<T> {

        private final Iterator<T> orig;
        private final Predicate<T> filter;
        private T next;

        public FilterIterator(Iterator<T> orig, Predicate<T> filter) {
            this.orig = orig;
            this.filter = filter;
            next = findNext();
        }

        private T findNext() {
            if (next != null) {
                return next;
            }
            if (!orig.hasNext()) {
                return null;
            }
            while (orig.hasNext()) {
                T result = orig.next();
                if (filter.test(result)) {
                    return result;
                }
            }
            return null;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            T result = next;
            next = findNext();
            return result;
        }
    }
}

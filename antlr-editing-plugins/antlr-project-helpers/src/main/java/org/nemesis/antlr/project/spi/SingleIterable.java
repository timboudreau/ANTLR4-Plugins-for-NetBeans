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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * The most lightweight iterables possible.
 *
 * @author Tim Boudreau
 */
final class SingleIterable<T> implements Iterable<T>, Iterator<T> {

    boolean used;
    private final T obj;

    public SingleIterable(T obj) {
        this.obj = obj;
    }

    @Override
    public String toString() {
        return "[" + obj + "]";
    }

    @Override
    public Iterator<T> iterator() {
        if (!used) {
            return this;
        }
        return new SingleIterable(obj);
    }

    @Override
    public boolean hasNext() {
        return !used;
    }

    @Override
    public T next() {
        if (used) {
            throw new NoSuchElementException();
        }
        used = true;
        return obj;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof SingleIterable<?>) {
            return Objects.equals(obj, ((SingleIterable<?>) o).obj);
        } else if (o instanceof Collection<?>) {
            int sz = ((Collection<?>) o).size();
            return sz == 1 && Objects.equals(obj, ((Collection<?>) o).iterator().next());
        } else if (o instanceof Iterable<?>) {
            Iterator<?> it = ((Iterable<?>) o).iterator();
            if (it.hasNext()) {
                Object cmp = it.next();
                if (Objects.equals(cmp, obj)) {
                    return !it.hasNext();
                }
            }
        }
        return false;
    }

    public int hashCode() {
        return Objects.hashCode(obj);
    }

    static final EmptyIterable<Object> EMPTY = new EmptyIterable<>();

    @SuppressWarnings("unchecked")
    static <T> Iterable<T> empty() {
        return (Iterable<T>) EMPTY;
    }

    static final class EmptyIterable<T> implements Iterable<T>, Iterator<T> {

        @Override
        public Iterator<T> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            throw new NoSuchElementException();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof EmptyIterable<?>) {
                return true;
            } else if (o instanceof Iterable<?> && (!(o instanceof SingleIterable<?>))) {
                return !((Iterable<?>) o).iterator().hasNext();
            }
            Collections.emptyList();
            return false;
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String toString() {
            return "[]";
        }
    }
}

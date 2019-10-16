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
package org.nemesis.misc.utils;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class ConvertIterable<T, F> implements Iterable<T> {

    private final Function<F,T> conversion;
    private final Iterable<F> delegate;

    ConvertIterable(Function<F, T> conversion, Iterable<F> delegate) {
        this.conversion = conversion;
        this.delegate = delegate;
    }

    @Override
    public Iterator<T> iterator() {
        return new ConvertIterator<>(conversion, delegate.iterator());
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        delegate.forEach(f -> {
            action.accept(conversion.apply(f));
        });
    }

    static final class ConvertIterator<T,F> implements Iterator<T> {
        private final Function<F,T> conversion;
        private final Iterator<F> delegate;

        ConvertIterator(Function<F, T> conversion, Iterator<F> delegate) {
            this.conversion = conversion;
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public T next() {
            return conversion.apply(delegate.next());
        }
    }

}

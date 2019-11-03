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
package org.nemesis.editor.utils;

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.util.concurrent.Callable;
import javax.swing.text.BadLocationException;

/**
 * A Supplier-like functional interface for running some code against a
 * document, which is guaranteed to be run with any locks or other features
 * configured.
 *
 * @see DocumentOperation
 * @param <T> The return type
 * @param <E> A custom exception type (use RuntimeException if none needed)
 */
@FunctionalInterface
public interface DocumentProcessor<T, E extends Exception> extends ThrowingSupplier<T> {

    T get() throws E, BadLocationException;

    static <T> DocumentProcessor<T, Exception> fromCallable(Callable<T> call) {
        return call::call;
    }

}

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

import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.text.BadLocationException;

/**
 * A supplier which throws a BadLocationException or other declared exception.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface BadLocationSupplier<T, E extends Exception> {

    T get() throws E, BadLocationException;

    default T run(Consumer<Runnable> runner) throws E, BadLocationException {
        Hold<T, E> hold = new Hold<>();
        runner.accept(() -> {
            try {
                hold.set(get());
            } catch (Exception ex) {
                hold.thrown(ex);
            }
        });
        return hold.get();
    }

    /**
     * Perform a get() catching any exception and returning either the string
     * value of the return value of get() or the exception. For logging
     * purposes.
     *
     * @return
     */
    default String toStringGet() {
        Object obj;
        try {
            obj = get();
        } catch (Exception ex) {
            obj = ex;
        }
        return Objects.toString(obj);
    }
}

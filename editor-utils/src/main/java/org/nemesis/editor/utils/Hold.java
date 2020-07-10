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

import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class Hold<T, E extends Exception> implements Supplier<T> {

    T obj;
    Exception thrown;

    void set(T obj) {
        this.obj = obj;
    }

    void thrown(Exception e) {
        this.thrown = e;
    }

    void rethrow() {
        if (thrown != null) {
            com.mastfrog.util.preconditions.Exceptions.chuck(thrown);
        }
    }

    public T get() {
        rethrow();
        return obj;
    }
}

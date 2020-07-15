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
package org.nemesis.editor.impl;

import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public final class Hold<T, E extends Exception> implements Supplier<T> {

    public T obj;
    public Exception thrown;

    public void set(T obj) {
        this.obj = obj;
    }

    public void thrown(Exception e) {
        this.thrown = e;
    }

    public void rethrow() {
        if (thrown != null) {
            com.mastfrog.util.preconditions.Exceptions.chuck(thrown);
        }
    }

    public T get() {
        rethrow();
        return obj;
    }
}

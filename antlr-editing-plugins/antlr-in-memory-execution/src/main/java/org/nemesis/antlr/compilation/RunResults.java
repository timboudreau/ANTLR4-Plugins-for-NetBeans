/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package org.nemesis.antlr.compilation;

/**
 * Pair object that disposes of the result of the grammar run once
 * retrieved - the grammar run result can be long lived, but if it
 * directly holds a reference to the embedded parser, that can leak
 * the entire contents of the generated classloader long after nothing
 * will use them again.
 *
 * @author Tim Boudreau
 */
public class RunResults<T> {
    private final GrammarRunResult<T> result;
    private T env;

    public RunResults(GrammarRunResult<T> result, T env) {
        this.result = result;
        this.env = env;
    }

    public GrammarRunResult<T> result() {
        return result;
    }

    public T env() {
        T e = env;
        env = null;
        return e;
    }
}

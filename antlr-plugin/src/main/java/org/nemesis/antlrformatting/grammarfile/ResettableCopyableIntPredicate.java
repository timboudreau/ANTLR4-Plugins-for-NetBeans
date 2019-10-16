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
package org.nemesis.antlrformatting.grammarfile;

import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 *
 * @author Tim Boudreau
 */
interface ResettableCopyableIntPredicate extends IntPredicate {

    ResettableCopyableIntPredicate copy();

    ResettableCopyableIntPredicate reset();

    /**
     * Convert this into an Object-taking predicate which uses the passed
     * function to resolve an integer.
     *
     * @param <R> The type the returned predicate takes
     * @param func A conversion function
     * @return A predicate
     */
    default <R> Predicate<R> convertedBy(ToIntFunction<R> func) {
        return new Predicate<R>() {
            public boolean test(R val) {
                return ResettableCopyableIntPredicate.this.test(func.applyAsInt(val));
            }

            public String toString() {
                return "convert(" + ResettableCopyableIntPredicate.this + " <- " + func + ")";
            }
        };
    }
}

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
package org.nemesis.charfilter;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface CharPredicate {

    static CharPredicate EVERYTHING = ignored -> true;

    boolean test(char c);

    default CharPredicate and(CharPredicate other) {
        return c -> {
            return CharPredicate.this.test(c) && other.test(c);
        };
    }

    default CharPredicate or(CharPredicate other) {
        return c -> {
            return CharPredicate.this.test(c) || other.test(c);
        };
    }

    default CharPredicate negate() {
        return c -> {
            return !CharPredicate.this.test(c);
        };
    }

    static CharPredicate combine(boolean or, CharPredicate... all) {
        if (all == null || all.length == 0) {
            return EVERYTHING;
        }
        CharPredicate curr = all[0];
        for (int i = 1; i < all.length; i++) {
            if (or) {
                curr = curr.or(all[i]);
            } else {
                curr = curr.and(all[i]);
            }
        }
        return curr;
    }
}

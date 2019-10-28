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

import static org.nemesis.charfilter.CharPredicate.EVERYTHING;

/**
 *
 * @author Tim Boudreau
 */
public interface CharFilter {

    boolean test(boolean isInitial, char typed);

    public static CharFilter ALL = (ignored1, ignored2) -> true;

    default boolean test(CharSequence string) {
        return test(string, false);
    }

    default boolean test(CharSequence string, boolean allowEmpty) {
        int max = string.length();
        for (int i = 0; i < max; i++) {
            if (!test(i == 0, string.charAt(i))) {
                return false;
            }
        }
        return allowEmpty ? max > 0 : true;
    }

    public static CharFilter excluding(CharPredicate all) {
        return excluding(all, all);
    }

    public static CharFilter excluding(CharPredicate initial, CharPredicate subsequent) {
        return new ExcludingCharFilter(initial, subsequent);
    }

    public static CharFilter excluding(CharPredicate[] initial, CharPredicate[] subsequent) {
        CharPredicate i = initial.length == 0 ? EVERYTHING : initial[0];
        for (int j = 1; j < initial.length; j++) {
            i = i.or(initial[j]);
        }
        CharPredicate s = subsequent.length == 0 ? EVERYTHING : subsequent[0];
        for (int j = 1; j < subsequent.length; j++) {
            s = s.or(subsequent[j]);
        }
        return excluding(i, s);
    }
}

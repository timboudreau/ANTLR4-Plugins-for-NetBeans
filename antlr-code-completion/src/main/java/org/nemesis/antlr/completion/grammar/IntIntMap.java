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
package org.nemesis.antlr.completion.grammar;

import java.util.Arrays;

/**
 *
 * @author Tim Boudreau
 */
final class IntIntMap {

    private final int[] keys;
    private final int[] vals;

    public IntIntMap(int[] keys, int[] vals) {
        this.keys = keys;
        this.vals = vals;
        if (keys.length != vals.length) {
            throw new IllegalArgumentException("Key and value arrays have"
                    + " different lengths: " + keys.length + " vs. "
                    + vals.length);
        }
        assert checkSorted(keys);
    }

    private boolean checkSorted(int[] keys) {
        int last = Integer.MIN_VALUE;
        for (int i = 0; i < keys.length; i++) {
            if (i > 0 && last >= keys.length) {
                throw new IllegalStateException("Keys contains duplicates "
                        + "or is unsorted: " + Arrays.toString(keys));
            }
        }
        return true;
    }

    public int getOrDefault(int val, int defaultValue) {
        int ix = Arrays.binarySearch(keys, val);
        return ix >= 0 ? vals[ix] : defaultValue;
    }

    public int get(int val) {
        int ix = Arrays.binarySearch(keys, val);
        return ix >= 0 ? vals[ix] : -1;
    }

    public int size() {
        return keys.length;
    }

    public boolean isEmpty() {
        return keys.length == 0;
    }
}

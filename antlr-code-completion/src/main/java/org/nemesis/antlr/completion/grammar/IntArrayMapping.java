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

import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.collections.IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;

/**
 * A tailored data structure for the data CodeCompletionCore collects which is
 * much smaller and many times faster than a
 * HashMap&lt;Integer,ArrayList&lt;Integer&gt;&gt;.
 *
 * @author Tim Boudreau
 */
final class IntArrayMapping {

    private final IntMap<IntList> values = IntMap.create(12, true, () -> IntList.create(16));

    public IntList get(int key0) {
        return values.getIfPresent(key0, null);
    }

    boolean containsKey(int key) {
        // broken in mf 2.6.6 - for now
//        return values.containsKey(key);
        return values.getIfPresent(key, null) != null;
    }

    public Set<Integer> keySet() {
        return values.keySet();
    }

    boolean isEmpty() {
        if (values.isEmpty()) {
            return true;
        }
        for (PrimitiveIterator.OfInt ki = values.keysIterator(); ki.hasNext();) {
            int k = ki.nextInt();
            if (!values.get(k).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    void put(int key, List<Integer> vals) {
        values.get(key).addAll(vals);
    }

    void put(int key, int a, int b) {
        IntList l = values.get(key);
        l.add(a);
        l.add(b);
        // 48=[104, 262]
        if (key == 48 && a == 104 && b == 262) {
            Thread.dumpStack();
        }
    }

    void put(int key, int... vals) {
        values.get(key).addArray(vals);
    }

    void clear() {
        values.clear();
    }

    void forEach(IntMap.IntMapConsumer<? super IntList> c) {
        values.forEach(c);
    }

    boolean forSome(IntMap.IntMapAbortableConsumer<? super IntList> c) {
        return values.forSomeKeys(c);
    }

    @Override
    public String toString() {
        return values.toString();
    }

    Iterable<Map.Entry<Integer, List<Integer>>> entrySet() { // for compatibility tests
        List<Map.Entry<Integer, List<Integer>>> result = new ArrayList<>();
        values.forEach((Integer k, IntList v) -> {
            result.add(new E(k, v));
        });
        return result;
    }

    private static final class E implements Map.Entry<Integer, List<Integer>> {

        private final Integer key;
        private final List<Integer> value;

        public E(Integer key, List<Integer> value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Integer getKey() {
            return key;
        }

        @Override
        public List<Integer> getValue() {
            return value;
        }

        @Override
        public List<Integer> setValue(List<Integer> value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }

}

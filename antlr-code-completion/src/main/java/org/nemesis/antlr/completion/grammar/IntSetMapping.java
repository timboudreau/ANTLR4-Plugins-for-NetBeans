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

import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import java.util.Collection;
import java.util.PrimitiveIterator;
import java.util.Set;

/**
 * A tailored data structure for the data CodeCompletionCore collects which is
 * much smaller and many times faster than a
 * HashMap&lt;Integer,HashSet&lt;Integer&gt;&gt;.
 *
 * @author Tim Boudreau
 */
class IntSetMapping {

    private final IntMap<IntSet> values = IntMap.create(12, true, () -> IntSet.create(5));

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

    public Set<Integer> keySet() {
        return values.keySet();
    }

    public IntSet getIfPresent(int key) {
        return values.getIfPresent(key, null);
    }

    boolean containsKey(int key) {
        // broken in mf 2.6.6 - for now
//        return values.containsKey(key);
        return values.getIfPresent(key, null) != null;
    }

    void forEach(IntMap.IntMapConsumer<? super IntSet> cons) {
        values.forEachPair(cons);
    }

    void clear() {
        values.clear();
    }

    void putReplace(int key, Collection<Integer> all) {
        if (all instanceof IntSet) {
            values.put(key, (IntSet) all);
//            assert values.getIfPresent(key, null) == all : "Wrong object returned";
//            assert values.containsKey(key) : "After put of " + all + " with key " + key + ", key is absent: " + values.keySet() + " - all: " + values;
        } else {
            IntSet result = IntSet.create(all);
            values.put(key, result);
//            assert values.containsKey(key);
//            assert values.getIfPresent(key, null) == result;
//            assert result.containsAll(all) || (all.isEmpty() && result.isEmpty());
//            assert result.size() == new HashSet<>(all).size();
        }
    }

    void put(int key, Collection<Integer> all) {
        if (all instanceof IntSet) {
            put(key, (IntSet) all);
        } else {
            IntSet l = values.get(key);
            l.addAll(all);
        }
    }

    void put(int key, IntSet all) {
        values.get(key).addAll(all);
    }

    void put(int key, int... vals) {
        values.get(key).addAll(vals);
    }

    public IntSet get(int key) {
        return values.get(key);
    }

    @Override
    public String toString() {
        return values.toString();
    }

}

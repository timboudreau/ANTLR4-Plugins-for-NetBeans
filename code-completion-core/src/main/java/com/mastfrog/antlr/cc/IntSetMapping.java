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
package com.mastfrog.antlr.cc;

import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import java.util.Collection;
import java.util.PrimitiveIterator;

/**
 * A tailored data structure for the data CodeCompletionCore collects which is
 * much smaller and many times faster than a
 * HashMap&lt;Integer,HashSet&lt;Integer&gt;&gt;.
 *
 * @author Tim Boudreau
 */
public final class IntSetMapping {

    private final IntMap<IntSet> values;

    public IntSetMapping(IntMap<IntSet> values) {
        this.values = values;
    }

    public IntSetMapping() {
        values = IntMap.create(64, true, IntSetMapping::newIntSet);
    }

    public IntSetMapping copy() {
        if (values.isEmpty()) {
            return new IntSetMapping();
        }
        IntMap<IntSet> result = IntMap.create(values.size());
        values.forEachPair((key, vals) -> {
            result.put(key, vals.copy());
        });
        assert result.equals(values) : "Copy not equal: " + values
                + " vs " + result;

        return new IntSetMapping(result);
    }

    public boolean isEmpty() {
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

    public IntSet keySet() {
        return values.keySet();
    }

    public IntSet getIfPresent(int key) {
        return values.getIfPresent(key, null);
    }

    public boolean containsKey(int key) {
        return values.containsKey(key);
    }

    public void forEach(IntMap.IntMapConsumer<? super IntSet> cons) {
        values.forEachPair(cons);
    }

    public void clear() {
        values.clear();
    }

    private static IntSet newIntSet() {
        return newIntSet(128);
    }

    private static IntSet newIntSet(int size) {
        return IntSet.bitSetBased(size);
    }

    public IntSet putNewSet(int key) {
        IntSet result = newIntSet();
        values.put(key, result);
        return result;
    }

    public void putReplace(int key, Collection<Integer> all) {
        // Assertions disabled for performance, since NetBeans is usually
        // run with -ea
        if (all instanceof IntSet) {
            values.put(key, (IntSet) all);
//            assert values.getIfPresent(key, null) == all : "Wrong object returned";
//            assert values.containsKey(key) : "After put of " + all + " with key "
//                    + key + ", key is absent: " + values.keySet() + " - all: "
//                    + values;
        } else if (all instanceof IntList) {
            values.put(key, ((IntList) all).toSet());
        } else {
            IntSet result = IntSet.create(all);
            values.put(key, result);
//            assert values.containsKey(key);
//            assert values.getIfPresent(key, null) == result;
//            assert result.containsAll(all) || (all.isEmpty() && result.isEmpty());
//            assert result.size() == new HashSet<>(all).size();
        }
    }

    public void put(int key, Collection<Integer> all) {
        if (all instanceof IntSet) {
            put(key, (IntSet) all);
        } else {
            IntSet l = values.get(key);
            l.addAll(all);
        }
    }

    public void put(int key, IntSet all) {
        values.get(key).addAll(all);
    }

    public void put(int key, int... vals) {
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

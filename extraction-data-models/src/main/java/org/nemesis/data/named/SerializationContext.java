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
package org.nemesis.data.named;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Special handling to allow multiple NamedSemanticRegions instances which have
 * overlapping names arrays to be serialized while serializing only a single
 * copy of each name.  This saves a considerable number of bytes when serializing a
 * large extraction.
 */
public final class SerializationContext implements Serializable {

    static ThreadLocal<SerializationContext> SER = new ThreadLocal<>();
    private transient Map<ArrayCacheKey, String[]> cache = new HashMap<>();
    private String[] strings;

    SerializationContext(Iterable<NamedSemanticRegions<?>> offsets) {
        Set<String> stringsLocal = new TreeSet<>();
        for (NamedSemanticRegions<?> o : offsets) {
            stringsLocal.addAll(Arrays.asList(o.nameArray()));
        }
        this.strings = stringsLocal.toArray(new String[stringsLocal.size()]);
    }

    public static SerializationContext createSerializationContext(Iterable<NamedSemanticRegions<?>> offsets) {
        return new SerializationContext(offsets);
    }

    public static void withSerializationContext(SerializationContext ctx, SerializationCallback c) throws IOException, ClassNotFoundException {
        SerializationContext old = SER.get();
        SER.set(ctx);
        try {
            c.run();
        } finally {
            SER.set(old);
        }
    }

    static SerializationContext currentSerializationContext() {
        return SER.get();
    }

    public String stringForIndex(int ix) {
        return strings[ix];
    }

    public short[] toArray(String[] strings, int size) {
        // Shorts?  Yes, shorts.  If you have a grammar with > 64k
        // rules, you have bigger problems than the serialization
        // of anything.
        short[] result = new short[size];
        for (int i = 0; i < size; i++) {
            result[i] = (short) Arrays.binarySearch(this.strings, strings[i]);
            assert result[i] >= 0 : "Missing string " + strings[i] + " in " + Arrays.toString(this.strings);
        }
        return result;
    }

    public String[] toStringArray(short[] indices) {
        // Use a cache so that where possible, deserialized instances are
        // using shared physical arrays
        ArrayCacheKey key = new ArrayCacheKey(indices);
        String[] result = null;
        if (cache != null) {
            result = cache.get(key);
        }
        if (result == null) {
            result = new String[indices.length];
            for (int i = 0; i < indices.length; i++) {
                result[i] = strings[indices[i]];
            }
            if (cache == null) {
                cache = new HashMap<>();
            }
            cache.put(key, result);
        }
        return result;
    }

    private static final class ArrayCacheKey {

        private final short[] keys;

        ArrayCacheKey(short[] keys) {
            this.keys = keys;
        }

        public boolean equals(Object o) {
            return o instanceof ArrayCacheKey && Arrays.equals(keys, ((ArrayCacheKey) o).keys);
        }

        public int hashCode() {
            return Arrays.hashCode(keys);
        }
    }
}

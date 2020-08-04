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
package org.nemesis.antlr.subscription;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class WeakValueMap<K, V> extends AbstractMap<K, V> {

    private final Map<K, Reference<V>> internal;
    private final Function<V, Reference<V>> referenceFactory;

    WeakValueMap(int initialSize) {
        internal = new HashMap<>();
        referenceFactory = WeakReference::new;
    }

    WeakValueMap(MapFactory factory, int initialSize, Function<V, Reference<V>> referenceFactory) {
        internal = factory.createMap(initialSize, false);
        this.referenceFactory = referenceFactory;
    }

    @Override
    public boolean isEmpty() {
        synchronized (internal) {
            if (internal.isEmpty()) {
                return true;
            }
            return size() == 0;
        }
    }

    @Override
    public int size() {
        int result = 0;
        synchronized (internal) {
            for (Entry<K, Reference<V>> e : internal.entrySet()) {
                if (e.getValue().get() != null) {
                    result++;
                }
            }
        }
        return result;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> result = new HashSet<>();
        synchronized (internal) {
            for (Entry<K, Reference<V>> e : internal.entrySet()) {
                V value = e.getValue().get();
                if (value != null) {
                    result.add(new E(e.getKey(), value));
                }
            }
        }
        return result;
    }

    @Override
    public Set<K> keySet() {
        Set<K> result = new HashSet<>();
        synchronized (internal) {
            for (Entry<K, Reference<V>> e : internal.entrySet()) {
                if (e.getValue().get() != null) {
                    result.add(e.getKey());
                }
            }
        }
        return result;
    }

    @Override
    public void clear() {
        internal.clear();
    }

    @Override
    public V put(K key, V value) {
        Reference<V> ref = referenceFactory.apply(value);
        synchronized (internal) {
            ref = internal.put(key, ref);
        }
        return ref == null ? null : ref.get();
    }

    @Override
    public V get(Object key) {
        Reference<V> ref;
        synchronized (internal) {
            ref = internal.get(key);
        }
        return ref == null ? null : ref.get();
    }

    @Override
    @SuppressWarnings("element-type-mismatch")
    public V getOrDefault(Object key, V defaultValue) {
        Reference<V> ref;
        synchronized (internal) {
            ref = internal.get(key);
        }
        if (ref == null) {
            return defaultValue;
        }
        V result = ref.get();
        if (result == null) {
            result = defaultValue;
        }
        return result;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Iterator<Entry<K, Reference<V>>> internalEntries;
        synchronized (internal) {
            internalEntries = internal.entrySet().iterator();
        }
        while (internalEntries.hasNext()) {
            Entry<K, Reference<V>> entry = internalEntries.next();
            Reference<V> val = entry.getValue();
            V value = val.get();
            if (value != null) {
                action.accept(entry.getKey(), value);
            }
        }
    }

    final class E implements Map.Entry<K, V> {

        private final K key;
        private final V value;

        public E(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            synchronized (internal) {
                internal.put(key, referenceFactory.apply(value));
            }
            return this.value;
        }
    }
}

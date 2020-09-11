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
package org.nemesis.antlr.live.execution.impl;

import com.mastfrog.util.collections.MapFactory;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.nemesis.antlr.live.execution.impl.TypedCache.K;

/**
 *
 * @author Tim Boudreau
 */
final class TypedCache {

    private final Map<K<?>, Object> internal;

    TypedCache(MapFactory f) {
        internal = f.createMap(16, true);
    }

    public <V> V get(K< V> key, Function<K<?>, V> supp) {
        if (supp == null) {
            Object val = internal.get(key);
            if (val != null) {
                return key.cast(val);
            }
        }
        Object val = internal.computeIfAbsent(key, supp);
        return val == null ? null : key.cast(val);
    }

    public void visitKeys(KeyVisitor kv) {
        internal.keySet().forEach(k -> kv.accept(k));
    }

    interface KeyVisitor {

        <V> void accept(K<V> k);
    }

    public static abstract class K<V> {

        protected final Class<? super V> baseType;
        private final String name;

        public K(Class<? super V> baseType, String name) {
            this.baseType = baseType;
            this.name = name;
        }

        protected final V cast(Object o) {
            return doCast(baseType.cast(o));
        }

        protected V doCast(Object o) {
            return (V) o;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 29 * hash + Objects.hashCode(this.baseType);
            hash = 29 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final K<?> other = (K<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return Objects.equals(this.baseType, other.baseType);
        }

        public String toString() {
            return "K<" + name + ">";
        }
    }
}

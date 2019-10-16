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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.key;

import org.nemesis.extraction.key.ExtractionKey;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ExtractionKeyTest {

    @Test
    public void testSomeMethod() {

        System.out.println("RAW: " + tk.rawType());
        System.out.println("TK: " + tk);
        assertEquals(tk, tk2);
        assertEquals(tk.hashCode(), tk2.hashCode());
        assertNotEquals(tk, tk3);
        assertNotEquals(tk.hashCode(), tk3.hashCode());

        System.out.println("IDL: " + tk.identityList());
    }

    private final TypeKey<Thing<Map<String, Integer>>> tk = new TypeKey<Thing<Map<String, Integer>>>() {
    };
    private final TypeKey<Thing<Map<String, Integer>>> tk2 = new TypeKey<Thing<Map<String, Integer>>>() {
    };
    private final TypeKey<Thing<List<String>>> tk3 = new TypeKey<Thing<List<String>>>() {
    };


    static class Thing<T>{

    }

    @Test
    public void testMap() {
        TypedMap m = new TypedMap(5);
        Foo foo1 = new Foo();
        Thing<Map<String, Integer>> thing1 = new Thing<Map<String, Integer>>();
//        EV<Thing<Map<String, Integer>>> ev = new EV<>(thing1);
        XEV ev = new XEV<Map<String,Integer>>(thing1);
        m.put(foo1, ev);

        iterList("FOO IDL: " , foo1.identityList());

        iterList("EV IDL: " , TypeKey.identityList(ev.getClass()));

//        ExtractionValue<Thing<Map<String, Integer>>> x = m.get(foo1);
    }

    static void iterList(String msg, List<?> l) {
        System.out.println("\n" + msg);
        for (int i=0; i < l.size(); i++) {
            System.out.println("  " + i + ": " + l.get(i));
        }
    }

    static class EV<X> implements ExtractionValue<X> {
        private final X x;

        public EV(X x) {
            this.x = x;
        }
    }

    static class XEV<Y> extends EV<Thing<Y>> {

        public XEV(Thing<Y> x) {
            super(x);
        }
    }


    static class Foo extends TypeKey<Thing<Map<String, Integer>>> {

    }

    static final class TypedMap implements Serializable {

        private final List<List<TypedMapEntry<?,?,?>>> entries;

        public TypedMap(int size) {
            assert size > 0;
            this.entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new ArrayList<>(5));
            }
        }

//        public <T, K extends ExtractionKey<? super T>, R extends ExtractionValue<? extends T>> void put(K key, R val) {
        public <T, K extends ExtractionKey<T>, R extends ExtractionValue<T>> void put(K key, R val) {
            int bucket = bucketFor(key);
            System.out.println("BUCKET " + bucket + " of " + entries.size());
            List<TypedMapEntry<?, ?, ?>> l = entries.get(bucketFor(key));
            TypedMapEntry<T,K,R> e = newEntry(key,val);
            l.add(e);
        }

        @SuppressWarnings("unchecked")
        public <T, R extends ExtractionValue<T>, K extends ExtractionKey<T>> R get(K key) {
            List<TypedMapEntry<?,?,?>> l = entries.get(bucketFor(key));
            for (TypedMapEntry<?,?,?> e : l) {
                if (e.matches(key)) {
                    return (R) e.value;
                }
            }
            return null;
        }

        private int bucketFor(ExtractionKey<?> key) {
            return Math.abs(key.hashCode() % (entries.size() - 1));
        }

        public boolean containsKey(ExtractionKey<?> key) {
            List<TypedMapEntry<?,?,?>> l = entries.get(bucketFor(key));
            for (TypedMapEntry<?,?,?> e : l) {
                if (e.matches(key)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof TypedMap) {
                TypedMap m = (TypedMap) o;
                return m.entrySet().equals(entrySet());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return entrySet().hashCode();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int bucket = 0; bucket < entries.size(); bucket++) {
                List<TypedMapEntry<?,?,?>> l = entries.get(bucket);
                for (int i = 0; i < l.size(); i++) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(bucket).append('.').append(i).append(": ");
                    TypedMapEntry<?,?,?> e = l.get(i);
                    sb.append(e.key).append(" = ").append(e.value);
                }
            }
            return sb.toString();
        }

        public Set<Map.Entry<? extends ExtractionKey<?>, ? extends ExtractionValue<?>>> entrySet() {
            Set<Map.Entry<? extends ExtractionKey<?>, ? extends ExtractionValue<?>>> result = new HashSet<>();
            for (List<TypedMapEntry<?,?,?>> l : entries) {
                result.addAll(l);
            }
            return result;
        }

        public int size() {
            int result = 0;
            for (List<TypedMapEntry<?,?,?>> l : entries) {
                result += l.size();
            }
            return result;
        }

        public boolean isEmpty() {
            boolean result = true;
            for (List<TypedMapEntry<?,?,?>> l : entries) {
                result = l.isEmpty();
                if (!result) {
                    break;
                }
            }
            return result;
        }

        public TypedMap clear() {
            int size = this.entries.size();
            this.entries.clear();
            for (int i = 0; i < size; i++) {
                entries.add(new ArrayList<>(5));
            }
            return this;
        }

        <T, K extends ExtractionKey<? super T>, V extends ExtractionValue<? extends T>> TypedMapEntry<T,K,V> newEntry(K k, V v) {
            return new TypedMapEntry<T,K,V>(k, v);
        }

        class TypedMapEntry<T, K extends ExtractionKey<? super T>, R extends ExtractionValue<? extends T>> implements Map.Entry<K, R>, Serializable {

            private final K key;
            private final R value;

            public TypedMapEntry(K key, R value) {
                this.key = key;
                this.value = value;
            }

            boolean matches(ExtractionKey<?> key) {
                if (key.type() == this.key.type()) {
                    if (key.hashCode() == this.key.hashCode()) {
                        if (key.equals(this.key)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public K getKey() {
                return key;
            }

            @Override
            public R getValue() {
                return value;
            }

            @Override
            public R setValue(R value) {
                throw new UnsupportedOperationException("Not mutable.");
            }

            @Override
            public int hashCode() {
                int hash = 7;
                hash = 89 * hash + Objects.hashCode(this.key);
                hash = 89 * hash + Objects.hashCode(this.value);
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
                final TypedMapEntry<?,?,?> other = (TypedMapEntry<?,?,?>) obj;
                return this.key.equals(other.key) && this.value.equals(other.value);
            }
        }
    }

    interface ExtractionValue<T> {

    }


    static abstract class TypeKey<T> implements Comparable<TypeKey<T>>, ExtractionKey<T> {

        public TypeKey() {
            Type sup = getClass().getGenericSuperclass();
            if (sup instanceof Class<?>) {
                throw new Error(sup + " is not a generic type");
            }
        }

        @SuppressWarnings("unchecked")
        public ExtractionValue<T> ifCompatibleCast(ExtractionValue<?> o) {

            return (ExtractionValue<T>) o;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<T> type() {
            return (Class<T>) rawType();
        }

        @Override
        public String name() {
            return toString();
        }

        public Class<?> rawType() {
            Type sup = getClass().getGenericSuperclass();
            ParameterizedType tp = (ParameterizedType) sup;
            Type x = tp.getActualTypeArguments()[0];
            if (x instanceof Class<?>) {
                return (Class<?>) x;
            } else {
                return (Class<?>) ((ParameterizedType) x).getRawType();
            }
        }

        interface TypeVisitor {

            void visit(Type t, boolean isClass, int depth);
        }

        private static void types(Type type, int depth, TypeVisitor bi) {
            bi.visit(type, type instanceof Class<?>, depth);
            if (type instanceof ParameterizedType) {
                ParameterizedType tp = (ParameterizedType) type;
                for (Type x : tp.getActualTypeArguments()) {
                    types(x, depth + 1, bi);
                }
            }
        }

        private static void types(Class<?> type, TypeVisitor bi) {
            types(type.getGenericSuperclass(), 0, bi);
        }

        private static String toS(Type t) {
            String s = t.getTypeName();
            int ix1 = s.indexOf('<');
            if (ix1 > 0) {
                s = s.substring(0, ix1);
            }
            int ix = s.lastIndexOf('$');
            if (ix < 0) {
                ix = s.lastIndexOf('.');
            }
            if (ix >= 0) {
                return s.substring(ix + 1);
            }
            return s;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            int[] lastDepth = new int[]{-1};
            types(getClass(), (type, clazz, depth) -> {
                if (lastDepth[0] != -1) {
                    if (depth > lastDepth[0]) {
                        sb.append('<');
                    } else if (depth < lastDepth[0]) {
                        sb.append('>');
                    } else if (depth == lastDepth[0]) {
                        sb.append(',');
                    }
                }
                if (clazz) {
                    sb.append("!");
                }
                sb.append(toS(type));
                lastDepth[0] = depth;
            });
            while (lastDepth[0] > 0) {
                sb.append('>');
                lastDepth[0]--;
            }
            return sb.toString();
        }

        static List<Object> identityList(Class<?> t) {
            List<Object> result = new ArrayList<>();
            types(t, (type, clazz, depth) -> {
                result.add(depth);
                result.add(type.getTypeName());
            });
            return result;
        }

        private List<Object> idList;

        List<Object> identityList() {
            if (idList != null) {
                return idList;
            }
            List<Object> result = new ArrayList<>();
            types(getClass(), (type, clazz, depth) -> {
                result.add(depth);
                result.add(type.getTypeName());
            });
            return idList = Collections.unmodifiableList(result);
        }

        @Override
        public int compareTo(TypeKey<T> o) {
            return 0;
        }

        public final boolean equals(Object o) {
            if (o instanceof TypeKey<?>) {
                return identityList().equals(((TypeKey<?>) o).identityList());
            }
            return false;
        }

        private int hash = -1;

        public final int hashCode() {
            if (hash != -1) {
                return hash;
            }
            int[] result = new int[1];
            types(getClass(), (type, clazz, depth) -> {
                result[0] += (71 * depth) + type.hashCode() + (clazz ? 7 : 3);
            });
            return hash = result[0];
        }
    }
}

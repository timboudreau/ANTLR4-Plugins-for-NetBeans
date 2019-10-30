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
package org.nemesis.extraction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;

/**
 * The set of encounters with somethibg which is expected to be a singleton -
 * data that occurs once per parsed document, but which, in a malformed source,
 * may not be.
 *
 * @param <KeyType>
 */
public class SingletonEncounters<KeyType> implements Iterable<SingletonEncounter<KeyType>>, Serializable, IndexAddressable<SingletonEncounter<KeyType>> {

    private final List<SingletonEncounter<KeyType>> encounters = new ArrayList<>(3);

    public SingletonEncounter<KeyType> first() {
        return hasEncounter() ? encounters.get(0) : null;
    }

    void add(KeyType key, int start, int end, Class<? extends ParserRuleContext> in) {
        encounters.add(new SingletonEncounter<>(start, end, key, in, encounters.size()));
    }

    /**
     * Returns true if there is only one encounter, and the value extracted for
     * it equals() the passed expected value.
     *
     * @param expectedValue The value to test equality against
     * @return true if they are equal
     */
    public boolean is(KeyType expectedValue) {
        return encounters.size() == 1 && Objects.equals(expectedValue, first().value());
    }

    @Override
    public Iterator<SingletonEncounter<KeyType>> iterator() {
        return Collections.unmodifiableList(encounters).iterator();
    }

    /**
     * Visit all encounters which are <i>not the first one</i> (useful for
     * marking these as errors).
     *
     * @param c A consumer
     * @return The number visited
     */
    public int visitOthers(Consumer<SingletonEncounter<KeyType>> c) {
        if (encounters.size() < 2) {
            return 0;
        }
        for (int i = 1; i < encounters.size(); i++) {
            c.accept(encounters.get(i));
            ;
        }
        return encounters.size() - 2;
    }

    public boolean hasEncounter() {
        return !encounters.isEmpty();
    }

    public boolean hasMultiple() {
        return encounters.size() > 1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < encounters.size(); i++) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(i).append(": ").append(encounters.get(i));
        }
        return sb.toString();
    }

    @Override
    public SingletonEncounter<KeyType> at(int position) {
        for (SingletonEncounter<KeyType> item : encounters) {
            if (item.contains(position) || item.end() == position) {
                return item;
            }
        }
        return null;
    }

    @Override
    public boolean isChildType(IndexAddressableItem item) {
        return item instanceof SingletonEncounter<?>;
    }

    @Override
    public SingletonEncounter<KeyType> forIndex(int index) {
        return encounters.get(index);
    }

    @Override
    public int size() {
        return encounters.size();
    }

    @Override
    public int indexOf(Object obj) {
        return encounters.indexOf(obj);
    }

    public static final class SingletonEncounter<KeyType> implements
            Serializable, IndexAddressable.IndexAddressableItem,
            Supplier<KeyType> {

        private final int start;
        private final int end;
        private final KeyType key;
        private final Class<? extends ParserRuleContext> in;
        private final int index;

        public SingletonEncounter(int start, int end, KeyType key, Class<? extends ParserRuleContext> in, int index) {
            this.start = start;
            this.end = end;
            this.key = key;
            this.in = in;
            this.index = index;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public KeyType get() {
            return key;
        }

        @Deprecated
        public KeyType value() {
            return get();
        }

        public Class<? extends ParserRuleContext> in() {
            return in;
        }

        @Override
        public String toString() {
            return key + "@" + start + ":" + end + "`" + in.getSimpleName();
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + this.start;
            hash = 67 * hash + this.end;
            hash = 67 * hash + Objects.hashCode(this.key);
            hash = 67 * hash + Objects.hashCode(this.in);
            hash = 67 * hash + this.index;
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
            final SingletonEncounter<?> other = (SingletonEncounter<?>) obj;
            if (this.start != other.start) {
                return false;
            }
            if (this.end != other.end) {
                return false;
            }
            if (this.index != other.index) {
                return false;
            }
            if (!Objects.equals(this.key, other.key)) {
                return false;
            }
            return Objects.equals(this.in, other.in);
        }
    }
}

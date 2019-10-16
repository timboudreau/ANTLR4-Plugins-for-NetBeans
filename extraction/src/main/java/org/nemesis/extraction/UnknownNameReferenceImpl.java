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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
final class UnknownNameReferenceImpl<T extends Enum<T>> implements UnknownNameReference<T> {

    private final Class<T> type;
    private final T expectedKind;
    private final int start;
    private final int end;
    private final String name;
    private final int index;

    UnknownNameReferenceImpl(Class<T> type, int start, int end, String name, int index) {
        this.type = type;
        this.expectedKind = null;
        this.start = start;
        this.end = end;
        this.name = name;
        this.index = index;
    }

    UnknownNameReferenceImpl(T expectedKind, int start, int end, String name, int index) {
        this.expectedKind = expectedKind;
        this.start = start;
        this.end = end;
        this.name = name;
        this.index = index;
        this.type = notNull("expectedKind", expectedKind).getDeclaringClass();
    }

    @Override
    public Class<T> kindType() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public int end() {
        return end;
    }

    public boolean isReference() {
        return true;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public String toString() {
        return name + "@" + start + ":" + end;
    }

    @Override
    public int hashCode() {
        return start + (end * 73) + 7 * Objects.hashCode(name);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof UnknownNameReference<?>) {
            UnknownNameReference<?> u = (UnknownNameReference<?>) o;
            return u.start() == start() && u.end() == end && Objects.equals(name(), u.name());
        }
        return false;
    }

    @Override
    public T expectedKind() {
        return expectedKind;
    }

}

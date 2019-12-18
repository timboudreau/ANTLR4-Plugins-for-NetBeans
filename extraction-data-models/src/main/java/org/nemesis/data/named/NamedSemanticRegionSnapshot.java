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

import java.util.Objects;

/**
 * Snapshot of a NamedSemanticRegion which does not retain a
 * reference to the regions object it belongs to, unlike flyweight
 * instances created on the fly by it.
 *
 * @author Tim Boudreau
 */
final class NamedSemanticRegionSnapshot<T extends Enum<T>> implements NamedSemanticRegion<T> {

    private final T kind;
    private final int ordering;
    private final int start;
    private final int end;
    private final boolean ref;
    private final String name;
    private final int index;

    NamedSemanticRegionSnapshot(NamedSemanticRegion<T> orig) {
        name = orig.name();
        kind = orig.kind();
        ordering = orig.ordering();
        start = orig.start();
        end = orig.end();
        ref = orig.isReference();
        index = orig.index();
    }

    @Override
    public NamedSemanticRegion<T> snapshot() {
        return this;
    }

    @Override
    public T kind() {
        return kind;
    }

    @Override
    public int ordering() {
        return ordering;
    }

    @Override
    public boolean isReference() {
        return ref;
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

    @Override
    public int index() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof NamedSemanticRegion<?>) {
            NamedSemanticRegion<?> other = (NamedSemanticRegion<?>) o;
            return start == other.start() && end == other.end() && name.equals(other.name()) && Objects.equals(kind, other.kind());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + (7 * start) + (1029 * end);
    }

    @Override
    public String toString() {
        return "snapshot:" + name + "@" + start + ":" + end;
    }

}

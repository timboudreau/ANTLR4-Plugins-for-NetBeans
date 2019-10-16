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
package org.nemesis.data;

import com.mastfrog.range.Range;

/**
 *
 * @author Tim Boudreau
 */
public final class FixedRangeImpl implements IndexAddressable.IndexAddressableItem {

    private final int index;
    private final int start;
    private final int size;

    public FixedRangeImpl(int index, int start, int size) {
        this.index = index;
        this.start = start;
        this.size = size;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public IndexAddressable.IndexAddressableItem newRange(int start, int size) {
        return new FixedRangeImpl(index, start, size);
    }

    @Override
    public IndexAddressable.IndexAddressableItem newRange(long start, long size) {
        return new FixedRangeImpl(index, (int) start, (int) size);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Range<?> && ((Range<?>) o).matches(this);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + this.start;
        hash = 37 * hash + this.size;
        return hash;
    }

    @Override
    public String toString() {
        return start() + ":" + end() + "(" + size() + "){" + index + "}";
    }

}

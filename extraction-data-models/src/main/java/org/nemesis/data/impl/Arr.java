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
package org.nemesis.data.impl;

import java.util.Arrays;

/**
 *
 * @author Tim Boudreau
 */
final class Arr implements SizedArrayValueSupplier {

    final int[] arr;

    Arr(int[] arr) {
        this.arr = arr;
    }

    @Override
    public int get(int index) {
        return arr[index];
    }

    @Override
    public int size() {
        return arr.length;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof ArrayEndSupplier) {
            return Arrays.equals(arr, ((ArrayEndSupplier) o).ends);
        } else if (o instanceof Arr) {
            return Arrays.equals(arr, ((Arr) o).arr);
        } else if (o instanceof SizedArrayValueSupplier) {
            SizedArrayValueSupplier other = (SizedArrayValueSupplier) o;
            if (other.size() == size()) {
                int sz = size();
                for (int i = 0; i < sz; i++) {
                    int a = get(i);
                    int b = other.get(i);
                    if (a != b) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return ArrayUtil.endSupplierHashCode(this);
    }

}

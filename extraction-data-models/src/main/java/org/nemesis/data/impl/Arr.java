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

    public int hashCode() {
        return ArrayUtil.endSupplierHashCode(this);
    }

}

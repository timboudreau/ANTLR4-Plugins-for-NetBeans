package org.nemesis.data.impl;

import java.util.Arrays;

/**
 *
 * @author Tim Boudreau
 */
public class ArrayEndSupplier implements MutableEndSupplier {

    final int[] ends;

    public ArrayEndSupplier(int size) {
        ends = new int[size];
        Arrays.fill(ends, -1);
    }

    public ArrayEndSupplier(int[] ends) {
        this.ends = ends;
    }

    @Override
    public int get(int index) {
        return ends[index];
    }

    @Override
    public void setEnd(int index, int val) {
        ends[index] = val;
    }

    public int size() {
        return ends.length;
    }

    @Override
    public void remove(int ix) {
        int size = ends.length;
        System.arraycopy(ends, ix + 1, ends, ix, size - (ix + 1));
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof ArrayEndSupplier) {
            return Arrays.equals(ends, ((ArrayEndSupplier) o).ends);
        } else if (o instanceof Arr) {
            return Arrays.equals(ends, ((Arr) o).arr);
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

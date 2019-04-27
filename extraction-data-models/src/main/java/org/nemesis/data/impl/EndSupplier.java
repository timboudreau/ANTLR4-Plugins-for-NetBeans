package org.nemesis.data.impl;

import java.io.Serializable;

/**
 *
 * @author Tim Boudreau
 */
public interface EndSupplier extends Serializable {

    int get(int index);

    /**
     * Note that this size method returns the size of the underlying array,
     * which may be greater than the size used by the owner. Used in
     * equality tests.
     *
     * @return A size
     */
    int size();

    default MutableEndSupplier toMutable(int size) {
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = get(i);
        }
        return new ArrayEndSupplier(result);
    }

}

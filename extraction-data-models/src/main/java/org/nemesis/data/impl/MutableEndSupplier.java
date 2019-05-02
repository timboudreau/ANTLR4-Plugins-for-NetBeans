package org.nemesis.data.impl;

/**
 *
 * @author Tim Boudreau
 */
public interface MutableEndSupplier extends SizedArrayValueSupplier {

    void setEnd(int index, int val);

    void remove(int index);

}

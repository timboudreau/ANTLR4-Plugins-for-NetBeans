/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.data.impl;

/**
 *
 * @author Tim Boudreau
 */
public interface MutableEndSupplier extends EndSupplier {

    void setEnd(int index, int val);

    void remove(int index);

}

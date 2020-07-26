/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.nemesis.antlr.subscription;

import java.util.Set;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public interface SetSupplier {

    public <T> Supplier<Set<T>> setSupplier(int initialSize, boolean threadSafe);
}

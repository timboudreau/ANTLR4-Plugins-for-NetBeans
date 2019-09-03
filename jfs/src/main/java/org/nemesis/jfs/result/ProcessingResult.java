/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.jfs.result;

import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public interface ProcessingResult {

    boolean isUsable();

    Optional<Throwable> thrown();

    UpToDateness currentStatus();

    default void rethrow() throws Throwable {
        Optional<Throwable> result = thrown();
        if (result.isPresent()) {
            throw result.get();
        }
    }
}

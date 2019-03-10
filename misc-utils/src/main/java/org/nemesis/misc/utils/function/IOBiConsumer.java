/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.misc.utils.function;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface IOBiConsumer<T, R> {

    void accept(T a, R b) throws IOException;

    default IOBiConsumer<T, R> andThen(IOBiConsumer<T, R> iob) {
        return (a, b) -> {
            IOBiConsumer.this.accept(a, b);
            iob.accept(a, b);
        };
    }

    default IOBiConsumer<T, R> andThen(BiConsumer<T, R> iob) {
        return (a, b) -> {
            IOBiConsumer.this.accept(a, b);
            iob.accept(a, b);
        };
    }
}

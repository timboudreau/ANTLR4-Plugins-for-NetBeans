/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.spi.language;

import java.util.function.Supplier;
import org.nemesis.extraction.Extractor;

/**
 *
 * @author Tim Boudreau
 */
final class DummySupplier implements Supplier<Extractor> {

    @Override
    public Extractor get() {
        throw new AssertionError("Infrastructure should never call an instance of DummySupplier");
    }

}

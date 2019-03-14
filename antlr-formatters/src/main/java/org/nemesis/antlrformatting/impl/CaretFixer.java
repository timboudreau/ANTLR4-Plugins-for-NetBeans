package org.nemesis.antlrformatting.impl;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A holder that can be updated with a revised caret position based on how the
 * document has changed.
 *
 * @author Tim Boudreau
 */
public final class CaretFixer implements IntConsumer, IntSupplier {

    private int caret = -1;

    @Override
    public int getAsInt() {
        return caret;
    }

    @Override
    public void accept(int value) {
        caret = value;
    }
}

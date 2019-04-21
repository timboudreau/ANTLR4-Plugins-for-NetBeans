package org.nemesis.antlr.completion;

import java.util.function.IntUnaryOperator;

/**
 *
 * @author Tim Boudreau
 */
public enum CompletionPriorities implements IntUnaryOperator {

    HIGH(100000),
    DEFAULT(20000),
    LOW(-100000);

    private final int add;

    CompletionPriorities(int add) {
        this.add = add;
    }

    @Override
    public int applyAsInt(int v) {
        return v + add;
    }
}

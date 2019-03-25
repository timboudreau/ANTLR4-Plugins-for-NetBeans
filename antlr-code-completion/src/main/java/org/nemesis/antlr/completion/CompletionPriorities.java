package org.nemesis.antlr.completion;

import org.nemesis.misc.utils.function.IntIntFunction;

/**
 *
 * @author Tim Boudreau
 */
public enum CompletionPriorities implements IntIntFunction {

    HIGH(100000),
    DEFAULT(20000),
    LOW(-100000);

    private final int add;

    CompletionPriorities(int add) {
        this.add = add;
    }

    @Override
    public int apply(int v) {
        return v + add;
    }
}

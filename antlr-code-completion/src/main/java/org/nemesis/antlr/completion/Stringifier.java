package org.nemesis.antlr.completion;

import java.util.function.BiFunction;

/**
 *
 * @author Tim Boudreau
 */
public interface Stringifier<I> extends BiFunction<StringKind, I, String> {

    @Override
    String apply(StringKind kind, I item);

}

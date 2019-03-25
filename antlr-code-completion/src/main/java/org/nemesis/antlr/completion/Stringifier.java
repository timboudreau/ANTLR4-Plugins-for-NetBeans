package org.nemesis.antlr.completion;

import java.util.function.BiFunction;
import org.nemesis.antlr.completion.StringKind;

/**
 *
 * @author Tim Boudreau
 */
public interface Stringifier<I> extends BiFunction<StringKind, I, String> {

    String apply(StringKind kind, I item);

}

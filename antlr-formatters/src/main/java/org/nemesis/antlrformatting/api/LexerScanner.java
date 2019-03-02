package org.nemesis.antlrformatting.api;

import java.util.function.IntPredicate;

/**
 *
 * @author Tim Boudreau
 */
interface LexerScanner {

    int countForwardOccurrencesUntilNext(IntPredicate toCount, IntPredicate stopType);

    int countBackwardOccurrencesUntilPrevious(IntPredicate toCount, IntPredicate stopType);

    int tokenCountToNext(boolean ignoreWhitespace, IntPredicate targetType);

    int tokenCountToPreceding(boolean ignoreWhitespace, IntPredicate targetType);

    int currentCharPositionInLine();

    int origCharPositionInLine();

}

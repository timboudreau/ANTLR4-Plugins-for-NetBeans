package org.nemesis.antlrformatting.api;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Facade interface to allow tests to construct a FormattingContextImpl
 * from old and new implementations of stream rewriters to check that
 * they produce the same results with and without optimizations.
 *
 * @author Tim Boudreau
 */
interface StreamRewriterFacade {

    void delete(Token tok);

    void delete(int tokenIndex);

    String getText();

    String getText(Interval interval);

    void insertAfter(Token tok, String text);

    void insertAfter(int index, String text);

    void insertBefore(Token tok, String text);

    void insertBefore(int index, String text);

    int lastNewlineDistance(int tokenIndex);

    void replace(Token tok, String text);

    void replace(int index, String text);

    void close();

    String rewrittenText(int index);
}

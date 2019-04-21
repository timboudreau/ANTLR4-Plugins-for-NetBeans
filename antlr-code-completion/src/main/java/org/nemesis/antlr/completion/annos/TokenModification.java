package org.nemesis.antlr.completion.annos;

import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 *
 * @author Tim Boudreau
 */
public interface TokenModification {

    default Interval getReplacementRange(List<Token> toks, Token caretToken) {
        Interval result = new Interval(Integer.MAX_VALUE, Integer.MIN_VALUE);
        updateReplacementRange(toks, caretToken, result);
        return result;
    }

    boolean isRange();

    Token findToken(List<Token> toks, Token caretToken);

    default void updateReplacementRange(List<Token> toks, Token caretToken, Interval into) {
        Token t = findToken(toks, caretToken);
        if (t != null) {
            into.a = Math.min(into.a, t.getStartIndex());
            into.b = Math.max(into.b, t.getStopIndex());
        }
    }

    public static Interval getReplacementRange(TokenModification first, Set<? extends TokenModification> insertActions, List<Token> toks, Token caretToken) {
        Interval result = new Interval(Integer.MAX_VALUE, Integer.MIN_VALUE);
        if (first != null) {
            first.updateReplacementRange(toks, caretToken, result);
        }
        for (TokenModification a : insertActions) {
            a.updateReplacementRange(toks, caretToken, result);
        }
        if (result.a == Integer.MAX_VALUE) {
            result.a = caretToken.getStartIndex();
            result.b = caretToken.getStartIndex();
        }
        return result;
    }

    public static Interval getReplacementRange(Set<? extends TokenModification> insertActions, List<Token> toks, Token caretToken) {
        return getReplacementRange(null, insertActions, toks, caretToken);
    }
}

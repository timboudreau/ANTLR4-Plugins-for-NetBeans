package org.nemesis.antlr.completion;

import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 *
 * @author Tim Boudreau
 */
public final class CompletionContext {

    private final List<Token> tokens;
    private final int caretPosition;
    private final Token targetToken;
    private final String matchedPattern;

    CompletionContext(List<Token> tokens, int caretPosition, Token targetToken, String matchedPattern) {
        this.tokens = tokens;
        this.caretPosition = caretPosition;
        this.targetToken = targetToken;
        this.matchedPattern = matchedPattern;
    }

    public final List<? extends Token> tokens() {
        return tokens;
    }

    public Token targetToken() {
        return targetToken;
    }

    public int caretPosition() {
        return caretPosition;
    }

    public String matchedPattern() {
        return matchedPattern;
    }
}

package org.nemesis.antlr.completion.annos;

import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 *
 * @author Tim Boudreau
 */
public enum InsertAction implements TokenModification {
    REPLACE_CURRENT_TOKEN,
    REPLACE_PRECEDING_TOKEN,
    REPLACE_NEXT_TOKEN,
    INSERT_BEFORE_CURRENT_TOKEN,
    INSERT_AFTER_CURRENT_TOKEN;

    @Override
    public boolean isRange() {
        switch (this) {
            case REPLACE_CURRENT_TOKEN:
            case REPLACE_NEXT_TOKEN:
            case REPLACE_PRECEDING_TOKEN:
                return true;
            default:
                return false;
        }
    }

    public Token findToken(List<Token> toks, Token caretToken) {
        int ix = caretToken.getTokenIndex();
        switch (this) {
            case REPLACE_CURRENT_TOKEN:
                return caretToken;
            case INSERT_BEFORE_CURRENT_TOKEN:
            case REPLACE_PRECEDING_TOKEN:
                if (ix > 0) {
                    return toks.get(ix - 1);
                }
            case INSERT_AFTER_CURRENT_TOKEN:
            case REPLACE_NEXT_TOKEN:
                if (ix < toks.size() - 1) {
                    return toks.get(ix + 1);
                }

        }
        return null;
    }



}

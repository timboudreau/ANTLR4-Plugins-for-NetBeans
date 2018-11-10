package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/**
 * A token implementation which retains the lexer mode from its creation.
 *
 * @author Tim Boudreau
 */
public class ModalToken extends CommonToken {

    private final int mode;
    private final String modeName;

    public ModalToken(Token oldToken, int mode, String modeName) {
        super(oldToken);
        this.mode = mode;
        this.modeName = modeName;
    }

    public int mode() {
        return mode;
    }

    public String modeName() {
        return modeName;
    }

    @Override
    public String toString() {
        return super.toString() + ", mode=" + modeName() + "=" + mode;
    }
}

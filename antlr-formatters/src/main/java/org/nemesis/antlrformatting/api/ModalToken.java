package org.nemesis.antlrformatting.api;

import java.util.Objects;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/**
 * A token implementation which retains the lexer mode at the time of its
 * creation.
 *
 * @author Tim Boudreau
 */
public final class ModalToken extends CommonToken {

    private final int mode;
    private final String modeName;

    public ModalToken(Token oldToken, int mode, String modeName) {
        super(oldToken);
        this.mode = mode;
        this.modeName = modeName;
    }

    public ModalToken withText(String newText) {
        ModalToken result = new ModalToken(this, mode, modeName);
        result.setText(text);
        return result;
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

    public boolean isProbablySame(ModalToken other, int fuzz) {
        if (other.equals(this)) {
            return true;
        }
        if (other.mode() == mode && Objects.equals(getText(), other.getText())) {
            if (other.getTokenIndex() == getTokenIndex()) {
                return true;
            }
            int offset = Math.abs(getTokenIndex() - other.getTokenIndex());
            if (offset <= fuzz) {
                return true;
            }
        }
        return false;
    }
}

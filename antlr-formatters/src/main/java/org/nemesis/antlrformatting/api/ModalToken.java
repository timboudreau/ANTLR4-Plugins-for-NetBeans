package org.nemesis.antlrformatting.api;

import com.mastfrog.util.collections.IntList;
import java.util.Arrays;
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
    private static final int[] EMPTY = new int[0];
    private int[] newlinePositions = EMPTY;

    public ModalToken(Token oldToken, int mode, String modeName) {
        super(oldToken);
        this.mode = mode;
        this.modeName = modeName;
        updateNewlinePositions(oldToken.getText());
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

    boolean isSane() {
        return getStartIndex() <= getStopIndex();
    }

    private void updateNewlinePositions(String text) {
        if (text != null) {
            IntList il = IntList.create(7);
            int max = text.length();
            for (int i = 0; i < max; i++) {
                if (text.charAt(i) == '\n') {
                    il.add(i);
                }
            }
            newlinePositions = il.isEmpty() ? EMPTY
                    : il.toIntArray();
        } else {
            newlinePositions = EMPTY;
        }
    }

    @Override
    public void setText(String text) {
        updateNewlinePositions(text);
        super.setText(text);
    }

    public int[] newlinePositions() {
        return newlinePositions.length == 0 ? EMPTY 
                : Arrays.copyOf(newlinePositions, newlinePositions.length);
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

package org.nemesis.antlrformatting.api;

import com.mastfrog.util.collections.IntList;
import java.util.Arrays;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/**
 * A token implementation which retains the lexer mode at the time of its
 * creation, and precomputes some information which is used repeatedly
 * when formatting.
 *
 * @author Tim Boudreau
 */
public final class ModalToken extends CommonToken {

    private final int mode;
    private final String modeName;
    private static final int[] EMPTY = new int[0];
    private int[] newlinePositions = EMPTY;
    private boolean isWhitespace;

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

    public int newlineCount() {
        return newlinePositions.length;
    }

    public boolean isWhitespace() {
        return isWhitespace;
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

    public int length() {
        return text == null ? 0 : text.length();
    }

    private void updateNewlinePositions(String text) {
        if (text != null) {
            boolean allWhitespace = true;
            IntList il = IntList.create(7);
            int max = text.length();
            for (int i = 0; i < max; i++) {
                char c = text.charAt(i);
                allWhitespace &= Character.isWhitespace(c);
                if (c == '\n') {
                    il.add(i);
                }
            }
            newlinePositions = il.isEmpty() ? EMPTY
                    : il.toIntArray();
            isWhitespace = allWhitespace;
        } else {
            newlinePositions = EMPTY;
            isWhitespace = true;
        }
    }

    int lastNewlinePosition() {
        return newlinePositions == EMPTY || newlinePositions.length == 0
                ? -1 : newlinePositions[newlinePositions.length-1];
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
}

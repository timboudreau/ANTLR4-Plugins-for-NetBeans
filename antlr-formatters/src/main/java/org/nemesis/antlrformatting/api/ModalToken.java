/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

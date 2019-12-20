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
package com.mastfrog.editor.features;

/**
 * Supports a microformat for specifying the location of the caret after
 * inserting text:  By default the character ^ indicates where the text
 * should be inserted and is elided from the insert text.  If ^ is part
 * of the template, a different character can be used by specifying it as
 * the first character followed by a \.  E.g. to insert the text <code>^^()^^</code>
 * with the caret between the parentheses, you would just specify a different
 * character to be the caret marker, such as $: <code>$\^^($)^^</code>.
 *
 * @author Tim Boudreau
 */
final class CaretPositionAndInsertText {

    /**
     * The number of characters to <i>back up</i> relative to the
     * <i>end</i> of the inserted text.
     */
    final int caretBackup;
    /**
     * The text to insert.
     */
    final String insertText;

    CaretPositionAndInsertText(int caretPosition, String insertText) {
        this.caretBackup = caretPosition;
        this.insertText = insertText;
    }

    @Override
    public String toString() {
        return "'" + insertText + "' backup " + caretBackup;
    }

    static CaretPositionAndInsertText parse(String txt) {
        if (txt.length() > 0) {
            if (!Character.isWhitespace(txt.charAt(0)) && txt.length() > 1 && '\\' == txt.charAt(1)) {
                String sub = txt.substring(2);
                int ix = sub.lastIndexOf(txt.charAt(0));
                if (ix < 0) {
                    return new CaretPositionAndInsertText(0, sub);
                } else {
                    String prefix = sub.substring(0, ix);
                    String suffix = sub.substring(ix + 1, sub.length());
                    int backup = suffix.length();
                    return new CaretPositionAndInsertText(backup, prefix + suffix);
                }
            }
            int ix = txt.lastIndexOf('^');
            if (ix >= 0) {
                String prefix = txt.substring(0, ix);
                String suffix = txt.substring(ix + 1, txt.length());
                int backup = suffix.length();
                return new CaretPositionAndInsertText(backup, prefix + suffix);
            }
        }
        return new CaretPositionAndInsertText(0, txt);
    }

}

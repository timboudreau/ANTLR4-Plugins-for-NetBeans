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
package org.nemesis.antlr.completion;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import org.netbeans.editor.BaseDocument;

/**
 * Base class for code that updates the document for code completion.
 *
 * @author Tim Boudreau
 */
public interface SimpleDocumentUpdater<I> extends ThrowingTriConsumer<I, JTextComponent, TokenMatch> {

    /**
     * Compute the text to be inserted into the document.
     *
     * @param item The item to generate insertion text for
     * @param replacement If true, and you need to examine or account for the
     * text being replaced, use the start and end positions passed, not the
     * caret position - this indicates there is a selection which will be
     * clobbered by the result of code completion
     * @param component The component being operated in
     * @param caretPosition The position of the caret, which in the case of a
     * replacement, will equal either start or end
     * @param start The selection start - if replacing, will always be less than
     * end
     * @param end The selection end - if replacement is true, will always be
     * less than start
     * @return The text to insert, or null if no insertion should be performed
     */
    String insertionText(I item, boolean replacement, JTextComponent component,
            int caretPosition, int start, int end, TokenMatch match);

    /**
     * The default implementation either inserts or replaces the selection with
     * the return value of insertionText() (or item.toString() if that returns
     * null).
     *
     * @param item The item
     * @param component The component being operated on
     * @throws Exception if someting goes wrong
     */
    @Override
    default void apply(I item, JTextComponent component, TokenMatch match) throws Exception {
        BaseDocument doc = (BaseDocument) component.getDocument();
        BadLocationException[] ex = new BadLocationException[1];
        doc.runAtomicAsUser(() -> {
            try {
                int caretPosition = component.getCaretPosition();
                int selStart = component.getSelectionStart();
                int selEnd = component.getSelectionEnd();
                int end = Math.max(selStart, selEnd);
                int start = Math.max(selStart, selEnd);
                String toInsert = insertionText(item, start != end, component, caretPosition, start, end, match);
                if (selStart != selEnd) {
                    toInsert = DefaultDocumentUpdater.findSubsequence(toInsert, doc, caretPosition);
                }
                if (toInsert != null && toInsert.length() > 0) {
                    if (selStart != selEnd) {
                        doc.replace(start, end - start, toInsert, null);
                    } else {
                        doc.insertString(caretPosition, toInsert, null);
                    }
                }
            } catch (BadLocationException e) {
                ex[0] = e;
            }
        });
        if (ex[0] != null) {
            throw ex[0];
        }
    }
}

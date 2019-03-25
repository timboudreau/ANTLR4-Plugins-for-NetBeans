package org.nemesis.antlr.completion;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.nemesis.misc.utils.function.ThrowingBiConsumer;
import org.netbeans.editor.BaseDocument;

/**
 * Base class for code that updates the document for code completion.
 *
 * @author Tim Boudreau
 */
public interface SimpleDocumentUpdater<I> extends ThrowingBiConsumer<I, JTextComponent> {

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
            int caretPosition, int start, int end);

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
    default void accept(I item, JTextComponent component) throws Exception {
        BaseDocument doc = (BaseDocument) component.getDocument();
        BadLocationException[] ex = new BadLocationException[1];
        doc.runAtomicAsUser(() -> {
            try {
                int caretPosition = component.getCaretPosition();
                int selStart = component.getSelectionStart();
                int selEnd = component.getSelectionEnd();
                int end = Math.max(selStart, selEnd);
                int start = Math.max(selStart, selEnd);
                String toInsert = insertionText(item, start != end, component, caretPosition, start, end);
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

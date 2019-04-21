package org.nemesis.antlr.completion;

import java.util.Collection;
import java.util.List;
import java.util.function.IntPredicate;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.antlr.v4.runtime.Token;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;

/**
 * The class that does the heavy lifting of deriving completion items from a
 * document. This is the only thing that absolutely must be implemented to
 * implement code completion.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface CompletionItemProvider<I> {

    /**
     * Fetch the items which will be rendered as code completion items.
     *
     * @param document The document being edited
     * @param caretPosition The caret position within the document
     * @param tokens An ordered list of all tokens in the document
     * @param tokenFrequencies The frequencies, by token id, with which each
     * token appears
     * @param caretToken The token the caret is in or next to
     * @param tokenPatternMatchName If token matching was used, this will be the
     * name of the pattern matched (assigned in your builder or annotation)
     * @return A collection of items
     * @throws Exception if something goes wrong
     */
    Collection<I> fetch(Document document, int caretPosition,
            List<Token> tokens, int[] tokenFrequencies, Token caretToken, TokenMatch tokenPatternMatchName)
            throws Exception;

    static String wordAtCaretPosition(Document doc, int caretPosition) throws BadLocationException {
        return wordAtCaretPosition(doc, caretPosition, Character::isWhitespace);
    }

    static String wordAtCaretPosition(Document doc, int caretPosition, IntPredicate stopOn) throws BadLocationException {
        StringBuilder sb = new StringBuilder();
        int len = doc.getLength();
        CharSequence seq = DocumentUtilities.getText(doc);
        char curr = seq.charAt(caretPosition);
        for (int i = caretPosition - 1; i >= 0; i--) {
            char prev = seq.charAt(i);
            if (!stopOn.test(prev)) {
                sb.insert(0, prev);
            } else {
                break;
            }
        }
        if (!stopOn.test(curr)) {
            sb.append(curr);
            for (int i = caretPosition + 1; i < len; i++) {
                char next = seq.charAt(i);
                if (!stopOn.test(next)) {
                    sb.append(next);
                } else {
                    break;
                }
            }
        }
        return sb.toString();
    }
}

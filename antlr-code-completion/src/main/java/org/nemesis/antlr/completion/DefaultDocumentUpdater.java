package org.nemesis.antlr.completion;

import java.util.function.BiFunction;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.completion.annos.InsertAction;
import org.nemesis.antlr.completion.annos.InsertPolicy;
import org.nemesis.misc.utils.function.ThrowingTriConsumer;
import org.netbeans.editor.BaseDocument;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultDocumentUpdater<I> implements ThrowingTriConsumer<I, JTextComponent, TokenMatch>, Stringifier<I> {

    static final DefaultDocumentUpdater<Object> INSTANCE = new DefaultDocumentUpdater<>();

    private final BiFunction<? super StringKind, ? super I, ? extends String> stringifier;

    DefaultDocumentUpdater() {
        this(null);
    }

    DefaultDocumentUpdater(BiFunction<? super StringKind, ? super I, ? extends String> stringifier) {
        this.stringifier = stringifier == null ? this : stringifier;
    }

    String insertionText(String text, TokenMatch match) {
        return isPunctuation(text) ? text : match.hasPolicy(InsertPolicy.INSERT_SPACE_AFTER)
                ? text + " " : text;
    }

    private boolean isPunctuation(String text) {
        boolean result = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c) || Character.isDigit(c)) {
                result = false;
                break;
            }
        }
        return result;
    }

    static String findSubsequence(String text, BaseDocument doc, int pos) throws BadLocationException {
        int len = Math.min(doc.getLength() - pos, text.length());

        String inDoc = doc.getText(pos, len);
        int common = 0;
        for (int i = 0; i < inDoc.length(); i++) {
            if (text.charAt(i) == inDoc.charAt(i)) {
                common++;
            }
        }
        if (common > 0) {
            return text.substring(common);
        }
        return text;
    }

    static String findPresentTrailingSubsequence(String text, BaseDocument doc, int pos) throws BadLocationException {
        String workingText = text;
        int len = Math.min(doc.getLength() - pos, text.length());
        if (len < text.length()) {
            workingText = workingText.substring(text.length() - len);
        }
        CharSequence seq = DocumentUtilities.getText(doc, pos, workingText.length());
        for (int j = 0; j < workingText.length(); j++) {
            String sub = workingText.substring(j);
            String ss = seq.subSequence(0, sub.length()).toString();
            if (sub.equals(ss)) {
                return text.substring(0, text.length() - ss.length());
            }
        }
        return text;
    }

    static String findRemainingSubsequence(String text, BaseDocument doc, int pos, boolean[] found) throws BadLocationException {
        int start = pos - text.length();
        String workingText = text;
        if (start < 0) {
            workingText = text.substring(0, text.length() + start);
            start = 0;
        }
        CharSequence seq = DocumentUtilities.getText(doc, start, workingText.length());
        for (int i = workingText.length(); i >= 1; i--) {
            String sub = workingText.substring(0, i);
            int off = workingText.length() - i;
            CharSequence seqSub = seq.subSequence(off, seq.length());
            if (seqSub.toString().equals(sub)) {
                found[0] = true;
                return text.substring(i);
            }
        }
        return text;
    }

    static String findTrailingSubsequence(String text, BaseDocument doc, int[] pos) throws BadLocationException {
        if (pos[0] == doc.getLength() - 1) {
            return text;
        }
        int end = Math.min(doc.getLength() - pos[0], text.length());

        int origPos = pos[0];
        CharSequence seq = DocumentUtilities.getText(doc, pos[0], end);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == seq.charAt(i)) {
                sb.append(text.charAt(i));
                pos[0]++;
            } else {
                break;
            }
        }
        if (pos[0] != origPos) {
            String result = text.substring(pos[0] - origPos);
//            System.out.println("Adjust for trailing '" + text + "' -> '" + result + "' for " + (pos[0] - origPos) + " eliding " + sb);
            return result;
        }
        return text;
    }

    @Override
    public void accept(I a, JTextComponent component, TokenMatch match) throws Exception {
        // XXX should be getting the caret position from the action
        BaseDocument doc = (BaseDocument) component.getDocument();
        int pos = component.getCaretPosition();
        int selStart = component.getSelectionStart();
        int selEnd = component.getSelectionEnd();
        int[] newCaretPosition = new int[]{pos};
        doUpdate(a, doc, pos, selStart, selEnd, newCaretPosition, match);
        if (newCaretPosition[0] != pos) {
            component.setCaretPosition(newCaretPosition[0]);
        }
    }

    void doUpdate(I a, BaseDocument doc, int pos, int selStart, int selEnd,
            int[] newCaretPosition, TokenMatch match) throws Exception {
        BadLocationException[] ex = new BadLocationException[1];
        doc.runAtomicAsUser(() -> {
            try {
                String text = stringifier.apply(StringKind.TEXT_TO_INSERT, a);
                System.out.println("MATCH " + match + " for '" + text + "'");
                if (match.isRange()) {
                    doc.replace(match.start(), match.length(), text, null);
                    newCaretPosition[0] = match.start() + text.length();
                    return;
                }
                int realPos = match.action() == InsertAction.INSERT_AFTER_CURRENT_TOKEN ?
                        match.start() : pos;
                if (text == null) {
                    text = a.toString();
                }
                boolean[] found = new boolean[1];
                String toInsert = insertionText(text, match);
                if (selStart == selEnd) {
                    if (match.hasPolicy(InsertPolicy.COALESCE_PRECEDING_MATCHING_TEXT)) {
                        toInsert = findRemainingSubsequence(toInsert, doc, realPos, found);
                    }
                    if (match.hasPolicy(InsertPolicy.COALESCE_FOLLOWING_MATCHING_TEXT)) {
                        int[] p = {realPos};
                        toInsert = findTrailingSubsequence(toInsert, doc, p);
                        if (p[0] != realPos) {
                            newCaretPosition[0] += p[0] - realPos;
                        }
                        realPos = p[0];
                        toInsert = findPresentTrailingSubsequence(toInsert, doc, realPos);
                    }
                }
                if (realPos < doc.getLength() - 1) {
                    String s = doc.getText(realPos, 1);
                    char c = s.charAt(0);
                    if (!Character.isLetter(c) && !Character.isDigit(c)) {
                        toInsert = toInsert.trim();
                    }
                }
                if (match.hasPolicy(InsertPolicy.INSERT_SPACE_BEFORE)) {
                    if (realPos > 0 && !found[0]) {
                        String s = doc.getText(realPos - 1, 1);
                        char c = s.charAt(0);
                        if (!Character.isWhitespace(c)) {
                            toInsert = " " + toInsert;
                        }
                    }
                }
                if (toInsert.isEmpty() && selStart == selEnd) {
                    newCaretPosition[0] = realPos + toInsert.length();
                    return;
                }
                if (selStart != selEnd) {
                    doc.replace(selStart, selEnd - selStart, toInsert, null);
                } else {
                    doc.insertString(realPos, toInsert, null);
                }
            } catch (BadLocationException e) {
                ex[0] = e;
            }
        });
        if (ex[0] != null) {
            throw ex[0];
        }
    }

    @Override
    public String apply(StringKind kind, I item) {
        return kind == StringKind.TEXT_TO_INSERT ? item.toString() : null;
    }

    @Override
    public String toString() {
        if (this == INSTANCE) {
            return getClass().getSimpleName() + ".INSTANCE";
        }
        return getClass().getSimpleName() + "{" + stringifier + "}";
    }
}

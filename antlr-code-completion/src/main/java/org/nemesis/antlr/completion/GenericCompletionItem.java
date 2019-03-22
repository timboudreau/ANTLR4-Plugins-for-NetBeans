package org.nemesis.antlr.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.Token;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.openide.awt.HtmlRenderer;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
class GenericCompletionItem implements CompletionItem {

    private final String text;
    private final String prefix;
    private final int frequencyInDocument;

    GenericCompletionItem(String text, Token caretToken, int frequencyInDocument) {
        this.frequencyInDocument = frequencyInDocument;
        this.text = text;
        String caretText = caretToken.getText();
        if (text.startsWith(caretText)) {
            prefix = caretText;
        } else {
            prefix = null;
        }
    }

    GenericCompletionItem(Token insertToken, Token caretToken, int frequencyInDocument) {
        this(insertToken.getText(), caretToken, frequencyInDocument);
    }

    String insertionText() {
        return isPunctuation() ? text : text + " ";
    }

    private boolean isPunctuation() {
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

    @Override
    public void defaultAction(JTextComponent component) {
        int pos = component.getCaretPosition();
        try {
            String toInsert = insertionText();
            if (pos < component.getDocument().getLength()) {
                String s = component.getDocument().getText(pos, pos+1);
                char c = s.charAt(0);
                if (!Character.isLetter(c) && !Character.isDigit(c)) {
                    toInsert = toInsert.trim();
                }
            }
            if (pos > 0) {
                String s = component.getDocument().getText(pos - 1, 1);
                char c = s.charAt(0);
                if (!Character.isWhitespace(c)) {
                    toInsert = " " + toInsert;
                }
            }
            int selStart = component.getSelectionStart();
            int selEnd = component.getSelectionEnd();
            if (selStart != selEnd) {
                ((BaseDocument) component.getDocument()).replace(selStart, selEnd-selStart, toInsert, null);
            } else {
                component.getDocument().insertString(pos, toInsert, null);
            }
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
        // do nothing
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        double width = HtmlRenderer.renderString(text, g, 5, 0,
                2000, 200, defaultFont, Color.BLACK, HtmlRenderer.STYLE_CLIP, false) + 10;
        return (int) Math.ceil(width);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
        Color color = g.getColor();
        int baseline = g.getFontMetrics(defaultFont).getMaxAscent();
        HtmlRenderer.renderHTML(text, g, 5, baseline, width, height, defaultFont, color,
                HtmlRenderer.STYLE_TRUNCATE, true);
    }

    @Override
    public CompletionTask createDocumentationTask() {
        return null;
    }

    @Override
    public CompletionTask createToolTipTask() {
        return null;
    }

    @Override
    public boolean instantSubstitution(JTextComponent component) {
        return false;
    }

    @Override
    public int getSortPriority() {
        int result = -frequencyInDocument;
        if (isPunctuation()) {
            result *= -1;
        }
        return result;
    }

    @Override
    public CharSequence getSortText() {
        return "";
    }

    @Override
    public CharSequence getInsertPrefix() {
        return prefix;
    }

}

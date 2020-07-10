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
package org.nemesis.antlr.completion.grammar;

import com.mastfrog.util.preconditions.Exceptions;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import com.mastfrog.antlr.code.completion.spi.CaretToken;
import com.mastfrog.antlr.code.completion.spi.CaretTokenRelation;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.AT_TOKEN_START;
import static com.mastfrog.antlr.code.completion.spi.CaretTokenRelation.WITHIN_TOKEN;
import com.mastfrog.antlr.code.completion.spi.CompletionItems;
import com.mastfrog.antlr.code.completion.spi.CompletionItems.CompletionApplier;
import javax.swing.text.Caret;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.api.editor.caret.MoveCaretsOrigin;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.openide.awt.HtmlRenderer;
import org.openide.text.NbDocument;

/**
 *
 * @author Tim Boudreau
 */
final class GCI implements CompletionItem, CompletionApplier {

    private final String name;
    private final CaretToken tokenInfo;
    private final Document doc;
    private final String desc;
    private boolean onlyItem;
    private CompletionItems.CompletionApplier applier;

    GCI(String name, CaretToken tokenInfo, Document doc, String desc) {
        this(name, tokenInfo, doc, desc, false);
    }

    GCI(String name, CaretToken tokenInfo, Document doc, String desc, boolean onlyItem) {
        this(name, tokenInfo, doc, desc, onlyItem, null);
    }

    GCI(String name, CaretToken tokenInfo, Document doc, String desc, boolean onlyItem, CompletionItems.CompletionApplier applier) {
        this.name = name;
        this.tokenInfo = tokenInfo;
        this.doc = doc;
        this.desc = desc;
        this.onlyItem = onlyItem;
        this.applier = applier == null ? this : applier;
    }

    String itemText() {
        return name;
    }

    @Override
    public void apply(JTextComponent comp, StyledDocument doc) {
        NbDocument.runAtomic((StyledDocument) doc, () -> {
            try {
                int end = applyToDocument(doc);
                Position endPosition = NbDocument.createPosition(doc, end, Position.Bias.Backward);
                if (isCommonEnclosingPair()) {
                    Caret caret = comp.getCaret();
                    if (caret instanceof EditorCaret) {
                        EditorCaret ec = (EditorCaret) caret;
                        ec.moveCarets((CaretMoveContext context) -> {
                            context.setDotAndMark(ec.getLastCaret(), endPosition,
                                    Position.Bias.Forward, endPosition, Position.Bias.Backward);
                        }, MoveCaretsOrigin.DISABLE_FILTERS);
                    } else {
                        EventQueue.invokeLater(() -> {
                            comp.getCaret().setDot(end - 1);
                        });
                    }
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }

    @Override
    public void defaultAction(JTextComponent component) {
        applier.apply(component, (StyledDocument) component.getDocument());
    }

    enum Op {
        DELETE_SUFFIX,
        DELETE_PREFIX,
        ELIDE_PREFIX,
        PREPEND_SPACE,
        PREPEND_PREFIX_AND_SUFFIX,
        APPEND_SPACE,
        PRESERVE_TRAILING_NEWLINES_AND_WHITESPACE,
        APPEND_PREFIX_AND_SUFFIX;
    }

    private int applyToDocument(Document doc) throws BadLocationException {
        StringBuilder sb = new StringBuilder(name);
        Set<Op> ops = insertionOps();
        Position pos = NbDocument.createPosition(doc,
                tokenInfo.caretPositionInDocument(), Position.Bias.Forward);
        for (Op o : ops) {
            switch (o) {
                case ELIDE_PREFIX:
                    sb.delete(0, tokenInfo.leadingTokenText().length());
                    break;
                case PREPEND_PREFIX_AND_SUFFIX:
                    sb.insert(0, tokenInfo.leadingTokenText() + tokenInfo.trailingTokenText());
                    break;
                case PREPEND_SPACE:
                    sb.insert(0, ' ');
                    break;
                case APPEND_SPACE:
                    sb.append(' ');
                    break;
                case PRESERVE_TRAILING_NEWLINES_AND_WHITESPACE:
                    sb.append(tokenInfo.trailingNewlinesAndWhitespace());
                    break;
                case DELETE_PREFIX:
                    doc.remove(tokenInfo.caretPositionInDocument(), tokenInfo.leadingTokenText().length());
                    break;
                case DELETE_SUFFIX:
                    doc.remove(tokenInfo.caretPositionInDocument(), tokenInfo.trailingTokenText().length());
                    break;
                case APPEND_PREFIX_AND_SUFFIX:
                    sb.append(tokenInfo.leadingTokenText()).append(tokenInfo.trailingTokenText());
            }
        }
        int offset = pos.getOffset();
        String toInsert = sb.toString();
        System.out.println("INSERT '" + toInsert + "' at " + offset + " for " + ops + " rel " + tokenInfo.caretRelation());
        doc.insertString(pos.getOffset(), toInsert, null);
        return offset + toInsert.length();
    }

    boolean isCommonEnclosingPair() {
        if (name.length() == 2 && isPunctuation()) {
            char first = name.charAt(0);
            char last = name.charAt(1);
            switch (first) {
                case '\'':
                    return last == '\'';
                case '"':
                    return last == '"';
                case '(':
                    return last == ')';
                case '{':
                    return last == '}';
                case '[':
                    return last == ']';
                case ':':
                case '.':
                    return false;
                default:
                    return first == last;
            }
        }
        return false;
    }

    private int sharedStartLength(String a, String b) {
        int max = Math.min(a.length(), b.length());
        for (int i = 0; i < max; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return  i;
            }
        }
        return max;
    }

    private Set<Op> insertionOps() {
        Set<Op> result = EnumSet.noneOf(Op.class);
        String pfx = tokenInfo.leadingTokenText();
        String sfx = tokenInfo.trailingTokenText();
        CaretToken prev = tokenInfo.before();
        CaretToken next = tokenInfo.after();
        CaretTokenRelation rel = tokenInfo.caretRelation();
        if (prev.isWhitespace() && !isPunctuation() && rel != CaretTokenRelation.WITHIN_TOKEN) {
            result.add(Op.PREPEND_SPACE);
        }
        switch (rel) {
            case WITHIN_TOKEN:
                if (!pfx.isEmpty() && name.startsWith(pfx)) {
                    result.add(Op.ELIDE_PREFIX);
                    result.add(Op.DELETE_SUFFIX);
                }
                if (pfx.isEmpty() && !sfx.isEmpty() && sharedStartLength(name, sfx) > 3) {
                    result.add(Op.DELETE_SUFFIX);
                }
                if (tokenInfo.isWhitespace()) {
                    result.add(Op.DELETE_PREFIX);
                    result.add(Op.DELETE_SUFFIX);
                    result.add(Op.PRESERVE_TRAILING_NEWLINES_AND_WHITESPACE);
                    if (!isPunctuation()) {
                        result.add(Op.PREPEND_SPACE);
                    } else {
                        if (!isCommonEnclosingPair()) {
                            result.add(Op.APPEND_PREFIX_AND_SUFFIX);
                        }
                    }
                }
                if (isPunctuation() && !tokenInfo.isPunctuation() && !tokenInfo.isWhitespace()) {
                    // We want to add punctuation to the end of the current
                    // word
                    result.add(Op.DELETE_PREFIX);
                    result.add(Op.DELETE_SUFFIX);
                    result.add(Op.PREPEND_PREFIX_AND_SUFFIX);
                }
                break;
            case AT_TOKEN_START:
                if (!sfx.isEmpty() && !tokenInfo.isWhitespace() && !next.isWhitespace() && !next.isPunctuation()) {
                    result.add(Op.APPEND_SPACE);
                }
            case AT_TOKEN_END:
                if (!prev.isWhitespace() && !prev.isPunctuation() && !tokenInfo.isWhitespace() && !isPunctuation()) {
                    result.add(Op.PREPEND_SPACE);
                }
        }
        System.out.println("OPS: " + result);
        return result;
    }

    private boolean isPunctuation() {
        return isPunctuation(name);
    }

    private static boolean isPunctuation(String name) {
        boolean result = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetter(c) || Character.isDigit(c)) {
                result = false;
                break;
            }
        }
        return result;
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
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
        if (onlyItem) {
            defaultAction(component);
            return true;
        }
        return false;
    }

    @Override
    public int getSortPriority() {
        return -1000;
    }

    @Override
    public CharSequence getSortText() {
        return name.toLowerCase();
    }

    @Override
    public CharSequence getInsertPrefix() {
        return "";
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        double width = HtmlRenderer.renderString(name + " (" + desc + ")", g, 5, 0,
                2000, 200, defaultFont, Color.BLACK, HtmlRenderer.STYLE_CLIP, false) + 10;
        return (int) Math.ceil(width);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
        Color color = g.getColor();
        int baseline = g.getFontMetrics(defaultFont).getMaxAscent();
        String renderText = desc == null ? name : name + " <i><font color='#888899'>(" + desc + ")";
        HtmlRenderer.renderHTML(renderText, g, 5, baseline, width, height, defaultFont, color,
                HtmlRenderer.STYLE_TRUNCATE, true);
    }

    void setOnly(boolean b) {
        this.onlyItem = true;
    }
}

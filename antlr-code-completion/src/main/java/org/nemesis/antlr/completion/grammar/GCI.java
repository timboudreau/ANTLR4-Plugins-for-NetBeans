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
import org.nemesis.antlr.completion.CaretTokenInfo;
import static org.nemesis.antlr.completion.CaretTokenInfo.CaretTokenRelation.AT_TOKEN_START;
import static org.nemesis.antlr.completion.CaretTokenInfo.CaretTokenRelation.WITHIN_TOKEN;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.openide.awt.HtmlRenderer;

/**
 *
 * @author Tim Boudreau
 */
public class GCI implements CompletionItem {

    private final String name;
    private final CaretTokenInfo tokenInfo;
    private final Document doc;
    private final String desc;
    private boolean onlyItem;

    GCI(String name, CaretTokenInfo tokenInfo, Document doc, String desc) {
        this(name, tokenInfo, doc, desc, false);
    }

    GCI(String name, CaretTokenInfo tokenInfo, Document doc, String desc, boolean onlyItem) {
        this.name = name;
        this.tokenInfo = tokenInfo;
        this.doc = doc;
        this.desc = desc;
        this.onlyItem = onlyItem;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        BaseDocument doc = (BaseDocument) component.getDocument();
        doc.runAtomicAsUser(() -> {
            try {
                int end = applyToDocument(doc);
                if (isCommonEnclosingPair()) {
                    EventQueue.invokeLater(() -> {
                        component.getCaret().setDot(end-1);
                    });
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
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

    private int applyToDocument(BaseDocument doc) throws BadLocationException {
        StringBuilder sb = new StringBuilder(name);
        Set<Op> ops = insertionOps();
        Position pos = doc.createPosition(tokenInfo.caretPositionInDocument(), Position.Bias.Forward);
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
                case APPEND_PREFIX_AND_SUFFIX :
                    sb.append(tokenInfo.leadingTokenText() + tokenInfo.trailingTokenText());
            }
        }
        int offset = pos.getOffset();
        String toInsert = sb.toString();
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

    private Set<Op> insertionOps() {
        Set<Op> result = EnumSet.noneOf(Op.class);
        String pfx = tokenInfo.leadingTokenText();
        String sfx = tokenInfo.trailingTokenText();
        CaretTokenInfo prev = tokenInfo.before();
        CaretTokenInfo next = tokenInfo.after();
        CaretTokenInfo.CaretTokenRelation rel = tokenInfo.caretRelation();
        if (prev.isWhitespace() && !isPunctuation()) {
            result.add(Op.PREPEND_SPACE);
        }
        switch (rel) {
            case WITHIN_TOKEN:
                if (name.startsWith(pfx)) {
                    result.add(Op.ELIDE_PREFIX);
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

/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package org.nemesis.antlr.live.language;

import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.AffineTransform;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"highlightLexerErrors=Highlight Lexer Syntax Errors", "highlightLexerErrorsDesc=Highlight syntax errors from the lexer"})
final class ToggleHighlightLexerErrorsAction extends AbstractPrefsKeyToggleAction {

    ToggleHighlightLexerErrorsAction(boolean icon) {
        super(icon, AdhocErrorHighlighter.PREFS_KEY_HIGHLIGHT_LEXER_ERRORS, Bundle.highlightLexerErrors(), Bundle.highlightLexerErrorsDesc());
    }

    @Override
    protected boolean currentValue() {
        return AdhocErrorHighlighter.highlightLexerErrors();
    }

    @Override
    protected boolean updateValue(boolean val) {
        return AdhocErrorHighlighter.highlightLexerErrors(val);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        String txt = "<?>";
        Graphics2D gg = (Graphics2D) g;
        Font f = c.getFont();
        FontMetrics fm = gg.getFontMetrics(f);
        float ht = fm.getAscent();
        float w = fm.stringWidth(txt);
        Insets ins = ((JComponent) c).getInsets();
        float availH = Math.max(4, (c.getHeight() - y) - ins.bottom);
        float availW = Math.max(4, ((c.getWidth() - x)) - ins.right);
        float scaleX = 1;
        float scaleY = 1;
        if (availW < w) {
            scaleX = w / availW;
        }
        if (availH < ht) {
            scaleY = ht / availH;
        }
        AffineTransform xform = AffineTransform.getScaleInstance(scaleX, scaleY);
        f = f.deriveFont(xform);
        gg.setFont(f);
        fm = gg.getFontMetrics();
        float left = x;
        float top = y;
        ht = fm.getAscent();
        w = fm.stringWidth(txt);
        top += (availH / 2) - (ht / 2);
        left += (availW / 2) - (w / 2);
        if (AdhocErrorHighlighter.highlightLexerErrors()) {
            g.setColor(c.getForeground());
        } else {
            g.setColor(UIManager.getColor("ScrollBar.thumbHighlight"));
        }
        gg.drawString(txt, left, top + fm.getAscent());
    }

    @Override
    public int getIconWidth() {
        return 24;
    }

    @Override
    public int getIconHeight() {
        return 24;
    }

}

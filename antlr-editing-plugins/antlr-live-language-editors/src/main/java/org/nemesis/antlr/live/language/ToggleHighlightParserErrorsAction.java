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

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"highlightParserErrors=Highlight Parse Errors", "highlightParserErrorsDesc=Highlight error nodes generated when parsing the document; " + "these are errors where the lexer recognized the tokens, but they did not " + "come in a sequence that made sense to the parser."})
final class ToggleHighlightParserErrorsAction extends AbstractPrefsKeyToggleAction {

    private final Line2D.Float line = new Line2D.Float();

    ToggleHighlightParserErrorsAction(boolean icon) {
        super(icon, AdhocErrorHighlighter.PREFS_KEY_HIGHLIGHT_PARSER_ERRORS, Bundle.highlightParserErrors(), Bundle.highlightParserErrorsDesc());
    }

    @Override
    protected boolean currentValue() {
        return AdhocErrorHighlighter.highlightParserErrors();
    }

    @Override
    protected boolean updateValue(boolean val) {
        return AdhocErrorHighlighter.highlightParserErrors(val);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D gg = (Graphics2D) g;
        Font f = c.getFont();
        FontMetrics fm = gg.getFontMetrics(f);
        float ht = fm.getAscent();
        Insets ins = ((JComponent) c).getInsets();
        float availH = Math.max(4, (c.getHeight() - y) - ins.bottom);
        float availW = Math.max(4, ((c.getWidth() - x)) - ins.right);
        float w = (availW - 2) / 4F;
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float top = availH / 4F;
        float sw = fm.stringWidth("x");
        float sh = fm.getAscent();
        float scale = top / sh;
        f = f.deriveFont(AffineTransform.getScaleInstance(scale, scale));
        gg.setFont(f);
        if (AdhocErrorHighlighter.highlightParserErrors()) {
            g.setColor(c.getForeground());
        } else {
            g.setColor(UIManager.getColor("ScrollBar.thumbHighlight"));
        }
        fm = gg.getFontMetrics();
        float sl = (x + (availW / 2F)) - sw / 2F;
        gg.drawString("x", sl, y + top + fm.getAscent());
        gg.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1));
        float xoff = x + (availW / 2) - (w / 2);
        line.setLine(xoff, y + top, xoff + w, y + top);
        gg.draw(line);
        line.setLine(x + 1, y + top + top, x + w + 1, y + top + top);
        gg.draw(line);
        line.setLine((x + availW - 1) - w, y + top + top, x + availW - (w + 1), y + top + top);
        gg.draw(line);
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

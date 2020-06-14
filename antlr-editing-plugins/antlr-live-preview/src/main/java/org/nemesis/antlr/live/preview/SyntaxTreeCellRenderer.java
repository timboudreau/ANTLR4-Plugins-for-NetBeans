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
package org.nemesis.antlr.live.preview;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import static org.nemesis.antlr.live.preview.RuleCellRenderer.escaper;
import org.nemesis.swing.cell.TextCell;
import org.nemesis.swing.cell.TextCellCellRenderer;

/**
 *
 * @author Tim Boudreau
 */
class SyntaxTreeCellRenderer implements ListCellRenderer<SyntaxTreeListModel.ModelEntry> {

//    private final HtmlRenderer.Renderer ren = HtmlRenderer.createRenderer();
    private final TextCellCellRenderer ren = new TextCellCellRenderer();
    private final int limit;

    SyntaxTreeCellRenderer(int limit) {
        this.limit = limit;
        ren.setOpaque(true);
    }

    SyntaxTreeCellRenderer() {
        this(70);
    }

    private String truncate(String s) {
        if (s.length() > limit) {
            s = s.substring(0, 69) + "\u2026";
        }
        return s;
    }

    private final RoundRectangle2D.Float rr = new RoundRectangle2D.Float(0, 0, 0, 0, 16, 16);
    private final SelectionIndicatorIcon icon = new SelectionIndicatorIcon();

    @Override
    public Component getListCellRendererComponent(JList<? extends SyntaxTreeListModel.ModelEntry> list,
            SyntaxTreeListModel.ModelEntry value, int index, boolean isSelected, boolean cellHasFocus) {
        Component result = ren;
        ren.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        ren.setForeground(isSelected ? list.getForeground() : list.getForeground());
        ren.setFont(list.getFont());
        TextCell cell = ren.cell().withText(escaper.escape(truncate(value.toString())));
        if (value.isError()) {
            Color c = UIManager.getColor("nb.errorForeground");
            if (c == null) {
                c = Color.RED;
            }
            cell.strikethrough().withForeground(c);
        } else if (value.isParserRule()) {
            cell.bold();
        }
        cell.bottomMargin(3);
        FontMetrics fm = list.getFontMetrics(list.getFont());
        int asc = fm.getAscent();
        cell.indent(5);
        ren.setToolTipText(value.tooltip());
        if (value.isTerminal()) {
            String type = value.lexerRuleName();
            if (type != null) {
                cell.inner(type, tc -> {
                    tc.italic().withForeground(list.getForeground().darker()).indent(asc / 2);
                });
            }
        }

        if (!isSelected) {
            ren.setIndent(asc + ((asc / 3) * value.depth()));
            SyntaxTreeListModel.ModelEntry sel = list.getSelectedValue();
            int dist = -1;
            if (sel != null) {
                dist = sel.distanceFrom(value);
            }
            if (dist != -1) {
                cell.withBackground(backgroundFor(dist, list, sel.depth()), rr).stretch();
            }
            ren.setIcon(null);
        } else {
            ren.setIcon(icon);
            icon.width = icon.height = asc;
        }
        return result;
    }

    private Color backgroundFor(int dist, JList<?> list, int totalDepth) {
        Color selBg = list.getSelectionBackground();
        Color bg = list.getBackground();
        float[] selBgHsb = new float[3];
        Color.RGBtoHSB(selBg.getRed(), selBg.getGreen(), selBg.getBlue(), selBgHsb);
        float[] bgHsb = new float[3];
        Color.RGBtoHSB(bg.getRed(), bg.getGreen(), bg.getBlue(), bgHsb);
        float[] changed = diff(selBgHsb, bgHsb, dist, totalDepth);
        return new Color(Color.HSBtoRGB(changed[0], changed[1], changed[2]));
    }

    private float[] diff(float[] a, float[] b, float dist, float of) {
        if (of == 0) {
            return a;
        }
        float frac = dist / of;
        float[] result = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            float av = a[i];
            float bv = b[i];
            float diff = (bv - av) * frac;
            result[i] = a[i] + diff;
        }
        return result;
    }
}

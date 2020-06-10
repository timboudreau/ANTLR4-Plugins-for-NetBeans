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

import com.mastfrog.util.strings.Escaper;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.nemesis.swing.html.HtmlRenderer;

/**
 *
 * @author Tim Boudreau
 */
class SyntaxTreeCellRenderer implements ListCellRenderer<SyntaxTreeListModel.ModelEntry> {

    private final HtmlRenderer.Renderer ren = HtmlRenderer.createRenderer();
    private final int limit;

    SyntaxTreeCellRenderer(int limit) {
        this.limit = limit;
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

    @Override
    public Component getListCellRendererComponent(JList<? extends SyntaxTreeListModel.ModelEntry> list, SyntaxTreeListModel.ModelEntry value, int index, boolean isSelected, boolean cellHasFocus) {
        Component result = ren.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        JLabel r = (JLabel) ren;
        if (value.isError()) {
            ren.setHtml(true);
            ren.setText("<font color='!nb.errorForeground'>" + Escaper.BASIC_HTML.escape(value.toString()));
        } else if (value.isParserRule()) {
            ren.setHtml(true);
            ren.setText("<b>" + Escaper.BASIC_HTML.escape(truncate(value.toString())));
        } else {
            ren.setHtml(true);
            ren.setText(Escaper.BASIC_HTML.escape(truncate(value.toString())));
        }
        r.setForeground(list.getForeground());
        ren.setIndent(5 * value.depth());
        if (list instanceof ParentCheckingFastJList<?>) {
            ren.setParentFocused(((ParentCheckingFastJList<?>) list).parentFocused);
        }
        r.setToolTipText(value.tooltip());
        if (isSelected) {
            ren.setCellBackground(list.getSelectionBackground());
        } else {
            SyntaxTreeListModel.ModelEntry sel = list.getSelectedValue();
            int dist = -1;
            if (sel != null) {
                dist = sel.distanceFrom(value);
            }
            if (dist == -1) {
                ren.setCellBackground(list.getBackground());
            } else {
                ren.setCellBackground(backgroundFor(dist, list, sel.depth()));
            }
        }
        ((JComponent) result).setOpaque(true);
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

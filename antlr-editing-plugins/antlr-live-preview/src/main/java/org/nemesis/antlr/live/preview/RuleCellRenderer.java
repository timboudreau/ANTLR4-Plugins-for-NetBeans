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
import java.awt.FontMetrics;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.swing.cell.TextCell;
import org.nemesis.swing.cell.TextCellLabel;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages("notPresent=(not present)")
final class RuleCellRenderer implements ListCellRenderer<String> {

    private final AdhocColorings colorings;
//    private final HtmlRenderer.Renderer ren = HtmlRenderer.createRenderer();
    private final TextCellLabel ren = new TextCellLabel();
    private final BiFunction<String, JList<?>, Color> listBackgroundColorFor;
    static final Escaper escaper = Escaper.CONTROL_CHARACTERS;
    private final Supplier<ParseTreeProxy> ptp;
    private final SelectionIndicatorIcon icon = new SelectionIndicatorIcon();

    RuleCellRenderer(AdhocColorings colorings, BiFunction<String, JList<?>, Color> listBackgroundColorFor, Supplier<ParseTreeProxy> ptp) {
        this.colorings = colorings;
        this.listBackgroundColorFor = listBackgroundColorFor;
        this.ptp = ptp;
        ren.setOpaque(true);
    }

    private final RoundRectangle2D.Float rr = new RoundRectangle2D.Float(0, 0, 16, 16, 10, 10);

    @Override
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus) {
        ren.setFont(list.getFont());
        String orig = value;
        value = escaper.escape(value);
        TextCell cell = ren.cell().withText(value).bottomMargin(3);
        Color back = listBackgroundColorFor.apply(value, list);
        if (isSelected) {
            ren.setIcon(icon);
            FontMetrics fm = list.getFontMetrics(list.getFont());
            icon.width = icon.height = fm.getAscent();
            ren.setBackground(list.getSelectionBackground());
            ren.setForeground(list.getSelectionForeground());
            cell.withBackground(list.getSelectionBackground());
            cell.withForeground(list.getSelectionForeground());
            cell.indent(5);
        } else {
            ren.setIcon(null);
            ren.setBackground(list.getBackground());
            ren.setForeground(list.getForeground());
            if (back != null) {
                cell.withBackground(back, rr).stretch();
            }
            FontMetrics fm = list.getFontMetrics(list.getFont());
            cell.indent(5 + fm.getAscent());
        }

        AdhocColoring col = colorings.get(value);
        if (col != null) {
            if (col.isActive()) {
                cell.bold();
            }
            if (Character.isUpperCase(value.charAt(0))) {
                cell.italic();
            }
            if (!isSelected) {
                ParseTreeProxy prox = ptp.get();
                boolean present = true;
                if (prox != null && orig.length() > 0 && orig.charAt(0) != '\'') {
                    if (!prox.presentRuleNames().contains(orig)) {
                        present = false;
                    }
                }
                Color fore = colorFor(orig, col, list, present);
                if (!present) {
                    cell.strikethrough().append(Bundle.notPresent(), tc -> {
                        tc.leftMargin(12).scaleFont(0.75F).withForeground(fore != null ? fore.darker() : list.getForeground().darker());
                    });
                } else {
                    if (fore != null) {
                        cell.withForeground(fore);
                    }
                }
            }
        } else {
            // We can be asked to paint after a color has been removed but before our
            // model has been updated
            cell.strikethrough().append("(unknown)", tc -> {
                tc.leftMargin(12).scaleFont(0.75F).withForeground(UIManager.getColor("controlShadow"));
            });
        }
        ren.setIndent(5);
        return ren;
    }

    private Color ibColor;
    private Color bColor;
    private Color iColor;
    private Color aibColor;
    private Color abColor;
    private Color aiColor;

    private Color colorFor(String name, AdhocColoring col, JList<?> list, boolean present) {
        boolean active = col.isActive();
        if (active) {
            if (col.isItalic() && col.isBold()) {
                if (aibColor == null) {
                    aibColor = deriveColor(list.getForeground(), 0.25F, active);
                }
                return aibColor;
            } else if (col.isItalic()) {
                if (aiColor == null) {
                    aiColor = deriveColor(list.getForeground(), 0.5F, active);
                }
                return aiColor;
            } else if (col.isBold()) {
                if (abColor == null) {
                    abColor = deriveColor(list.getForeground(), 0.75F, active);
                }
                return abColor;
            }
        } else {
            if (col.isItalic() && col.isBold()) {
                if (ibColor == null) {
                    ibColor = deriveColor(list.getForeground(), 0.25F, active);
                }
                return ibColor;
            } else if (col.isItalic()) {
                if (iColor == null) {
                    iColor = deriveColor(list.getForeground(), 0.5F, active);
                }
                return iColor;
            } else if (col.isBold()) {
                if (bColor == null) {
                    bColor = deriveColor(list.getForeground(), 0.75F, active);
                }
                return bColor;
            }
        }
        return list.getForeground();
    }

    private Color deriveColor(Color c, float hueAdjust, boolean active) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsb);
        hsb[0] += hueAdjust;
        if (hsb[0] > 1) {
            hsb[0] = 2 - hsb[0];
        }
        if (hsb[0] < 0) {
            hsb[0] += 1;
        }
        hsb[1] = active ? 0.375F : 0.25F;
        if (active) {
            hsb[2] = Math.max(0.1F, Math.min(0.9F, hsb[2]));
        } else {
            hsb[2] = Math.max(0.375F, Math.min(0.75F, hsb[2]));
        }
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }

}

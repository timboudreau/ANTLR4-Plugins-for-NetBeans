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
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.swing.html.HtmlRenderer;

/**
 *
 * @author Tim Boudreau
 */
final class RuleCellRenderer implements ListCellRenderer<String> {

    private final AdhocColorings colorings;
    private final HtmlRenderer.Renderer ren = HtmlRenderer.createRenderer();
    private final BiFunction<String, JList<?>, Color> listBackgroundColorFor;
    static final Escaper escaper = Escaper.CONTROL_CHARACTERS.and(Escaper.BASIC_HTML);
    private final Supplier<ParseTreeProxy> ptp;

    RuleCellRenderer(AdhocColorings colorings, BiFunction<String, JList<?>, Color> listBackgroundColorFor, Supplier<ParseTreeProxy> ptp) {
        this.colorings = colorings;
        this.listBackgroundColorFor = listBackgroundColorFor;
        this.ptp = ptp;
    }

    @Override
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus) {
        Component result = ren.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        ren.setHtml(true);
        String orig = value;
        value = escaper.escape(value);
        String prefix = "";
        AdhocColoring col = colorings.get(value);
        if (col.isActive()) {
            prefix = "<b>";
        }
        if (Character.isUpperCase(value.charAt(0))) {
            prefix += "<i>";
        }
        Color back = listBackgroundColorFor.apply(value, list);
        Color fore = colorFor(orig, col, list);
        if (fore != null) {
            prefix += RulePathStringifierImpl.foregroundColor(fore);
        }
        if (isSelected) {
            ren.setCellBackground(list.getSelectionBackground());
        } else if (back != null) {
            ren.setCellBackground(back);
        }
        ren.setText(prefix.isEmpty() ? value : prefix + value);
        ren.setIndent(5);
        return result;
    }

    private Color ibColor;
    private Color bColor;
    private Color iColor;
    private Color aibColor;
    private Color abColor;
    private Color aiColor;

    private Color colorFor(String name, AdhocColoring col, JList<?> list) {
        ParseTreeProxy prox = ptp.get();
        if (prox != null && name.length() > 0 && name.charAt(0) != '\'') {
            if (!prox.allRuleNames().contains(name)) {
                return UIManager.getColor("Button.disabledForeground");
            }
        }
        boolean active = col.isActive();
        if (active) {
            if (col.isItalic() && col.isBold()) {
                if (aibColor == null) {
                    aibColor = deriveColor(list.getForeground(), 0.5F, active);
                }
                return aibColor;
            } else if (col.isItalic()) {
                if (aiColor == null) {
                    aiColor = deriveColor(list.getForeground(), 0.625F, active);
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
                    ibColor = deriveColor(list.getForeground(), 0.5F, active);
                }
                return ibColor;
            } else if (col.isItalic()) {
                if (iColor == null) {
                    iColor = deriveColor(list.getForeground(), 0.625F, active);
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
        hsb[1] = active ? 0.75F : 0.67F;
        if (active) {
            hsb[2] = Math.max(0.1F, Math.min(0.75F, hsb[2]));
        } else {
            hsb[2] = Math.max(0.375F, Math.min(0.625F, hsb[2]));
        }
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }

}

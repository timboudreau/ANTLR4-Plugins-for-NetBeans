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
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
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
    RuleCellRenderer(AdhocColorings colorings, BiFunction<String, JList<?>, Color> listBackgroundColorFor) {
        this.colorings = colorings;
        this.listBackgroundColorFor = listBackgroundColorFor;
    }

    @Override
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus) {
        Component result = ren.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        ren.setHtml(true);
        value = escaper.escape(value);
        String prefix = "";
        AdhocColoring col = colorings.get(value);
        if (!col.isActive()) {
            prefix = RulePathStringifierImpl.dimmedForeground(list);
        }
        if (col.isBackgroundColor()) {
            prefix += "<b>";
        }
        if (col.isItalic()) {
            prefix += "<i>";
        }
        Color back = listBackgroundColorFor.apply(value, list);
        if (isSelected) {
            ren.setCellBackground(list.getSelectionBackground());
        } else if (back != null) {
            ren.setCellBackground(back);
        }
        ren.setText(prefix.isEmpty() ? value : prefix + value);
        ren.setIndent(5);
        return result;
    }
}

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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import java.awt.Color;
import java.awt.Component;
import java.util.function.Function;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocColoring;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocColorings;

/**
 *
 * @author Tim Boudreau
 */
final class RuleCellRenderer implements ListCellRenderer<String> {

    private final AdhocColorings colorings;
    private final HtmlRendererImpl ren = new HtmlRendererImpl();
    private final Function<String, Color> listBackgroundColorFor;

    public RuleCellRenderer(AdhocColorings colorings, Function<String, Color> listBackgroundColorFor) {
        this.colorings = colorings;
        this.listBackgroundColorFor = listBackgroundColorFor;
    }

    @Override
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus) {
        Component result = ren.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        ren.setHtml(true);
        String prefix = "";
        AdhocColoring col = colorings.get(value);
        if (!col.isActive()) {
            prefix += "<font color=#aaaaaa>";
        }
        if (col.isBackgroundColor()) {
            prefix += "<b>";
        }
        if (col.isItalic()) {
            prefix += "<i>";
        }
        Color back = listBackgroundColorFor.apply(value);
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

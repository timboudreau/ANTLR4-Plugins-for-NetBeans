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
package org.nemesis.debug.ui;

import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.nemesis.swing.html.HtmlRenderer;
/**
 *
 * @author Tim Boudreau
 */
class Ren implements ListCellRenderer<EmittedItem> {

    private final HtmlRenderer.Renderer ren = HtmlRenderer.createRenderer();

    private final Map<Long, Color> colorToThreadId = new HashMap<>();
    private float lastHue = 0F;

    @Override
    public Component getListCellRendererComponent(JList<? extends EmittedItem> list, EmittedItem value, int index, boolean isSelected, boolean cellHasFocus) {
        Component result = ren.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        String head = value.heading();
        ren.setHtml(true);
        ren.setIndent(8 * value.depth());
        if (list instanceof SizingList<?>) {
            ren.setParentFocused(((SizingList<?>) list).active);
        }
        if (value.isFailure()) {
            ren.setText("<font color='!nb.errorColor'>" + head + "</font> <font color='#BBBB99'>" + value.durationString());
        } else if (value.isCategory()) {
            ren.setText("<font color='#111189'><b>" + head + "</b></font> <font color='#BBBB99'>" + value.durationString());
        } else {
            ren.setText(head + " <font color=#bbbb99><i>" + value.durationString());
        }
        if (!isSelected) {
            result.setBackground(colorForThreadId(value.threadId()));
            ((JComponent) result).setOpaque(true);
        }
        return result;
    }

    public Color colorForThreadId(long id) {
        Color result = colorToThreadId.get(id);
        if (result == null) {
            float hue = lastHue + 0.1937F;
            if (hue > 1F) {
                hue -= 1f;
            }
            result = new Color(Color.HSBtoRGB(hue, 0.0725F, 0.9825F));
            colorToThreadId.put(id, result);
            lastHue = hue;
        }
        return result;
    }
}

/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.debug.ui;

import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.openide.awt.HtmlRenderer;

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

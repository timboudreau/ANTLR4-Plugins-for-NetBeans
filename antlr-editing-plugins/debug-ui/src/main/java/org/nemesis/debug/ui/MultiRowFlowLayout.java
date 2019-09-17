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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
final class MultiRowFlowLayout implements LayoutManager {

    int hgap = 5;
    int vgap = 5;

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Rectangle r = new Rectangle();
        layout(parent, (comp, x, y, w, h) -> {
            r.add(x + w, y + h);
        });
        Dimension result = r.getSize();
        result.height += vgap;
        return result;
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        layout(parent, (comp, x, y, w, h) -> {
            comp.setBounds(x, y, w, h);
        });
    }

    private void layout(Container container, QC qc) {
        Component[] comps = container.getComponents();
        int width = container.getWidth();
        if (comps.length == 0 || width == 0) {
            return;
        }

        List<List<Rectangle>> rects = new LinkedList<>();
        List<Rectangle> curr = new ArrayList<>(5);
        rects.add(curr);
        int x = 0;
        int y = vgap;
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            Dimension d = c.getPreferredSize();
            int maybeX = x + hgap;
            if (maybeX + d.width >= width && curr.size() > 0) {
                // redistribute
                int amt = (width - (x + hgap + hgap)) / curr.size();
                for (int j = 0; j < curr.size(); j++) {
                    Rectangle r = curr.get(j);
                    r.width += amt;
                    r.x += (j * amt);
                }
                curr = new ArrayList<>(5);
                rects.add(curr);
                y += d.height + vgap;
                x = 0;
                maybeX = x + hgap;
            }
            curr.add(new Rectangle(maybeX, y, d.width, d.height));
            x += d.width;
        }
        if (x != 0 && !curr.isEmpty()) {
            int amt = (width - (x + hgap + hgap)) / curr.size();
            for (int j = 0; j < curr.size(); j++) {
                Rectangle r = curr.get(j);
                r.width += amt;
                r.x += (j * amt);
            }
        }
        int ix = 0;
        for (List<Rectangle> l : rects) {
            for (Rectangle r : l) {
                qc.coordinates(comps[ix++], r.x, r.y, r.width, r.height);
            }
        }
    }

    interface QC {

        void coordinates(Component comp, int x, int y, int w, int h);
    }

}

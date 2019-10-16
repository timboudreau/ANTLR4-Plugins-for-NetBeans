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

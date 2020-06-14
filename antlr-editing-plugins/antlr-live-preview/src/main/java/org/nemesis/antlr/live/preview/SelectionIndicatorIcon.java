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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Since we are using background colors in interesting ways, we need an additional
 * indicator of selection.
 *
 * @author Tim Boudreau
 */
class SelectionIndicatorIcon implements Icon {

    int width = 16;
    int height = 16;
    int[] xPoints = new int[3];
    int[] yPoints = new int[3];

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        int half = getIconHeight() / 2;
        xPoints[0] = x;
        yPoints[0] = y;
        xPoints[1] = x + getIconWidth();
        yPoints[1] = y + half;
        xPoints[2] = x;
        yPoints[2] = y + getIconHeight();
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(0, 1);
        g.setColor(UIManager.getColor("List.selectionForeground"));
        g.fillPolygon(xPoints, yPoints, 3);
        g.setColor(UIManager.getColor("controlShadow"));
        g.drawPolygon(xPoints, yPoints, 3);
        g.translate(0, -1);
    }

    @Override
    public int getIconWidth() {
        return width % 2 != 0 ? height - 1 : height;
    }

    @Override
    public int getIconHeight() {
        return height % 2 != 0 ? height - 1 : height;
    }

}

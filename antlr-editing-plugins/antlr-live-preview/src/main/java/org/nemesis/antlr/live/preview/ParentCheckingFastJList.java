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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.Objects;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
class ParentCheckingFastJList<T> extends JList<T> {

    boolean parentFocused;
    boolean firstPaint = true;

    public ParentCheckingFastJList() {

    }

    public ParentCheckingFastJList(ListModel<T> dataModel) {
        super(dataModel);
    }

    @Override
    public void setFont(Font font) {
        if (!Objects.equals(getFont(), font)) {
            firstPaint = true;
            super.setFont(font);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        // Make sure one long item can't mush the editor to 0-width
        Dimension result = super.getPreferredSize();
        if (isPreferredSizeSet()) {
            return result;
        }
        if (isDisplayable()) {
            TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, this);
            if (tc != null) {
                int width = tc.getWidth();
                result.width = Math.min(result.width, width / 3);
            }
        } else {
            FontMetrics fm = getFontMetrics(getFont());
            if (fm != null) {
                result.width = Math.min(fm.charWidth('A') * 70, result.width);
            }
        }
        return result;
    }

    @Override
    public void paint(Graphics g) {
        TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, this);
        if (tc != null) {
            parentFocused = tc == TopComponent.getRegistry().getActivated();
        }
        if (firstPaint) {
            // performance - no need to render just to compute preferred size
            firstPaint = false;
            FontMetrics fm = g.getFontMetrics(getFont());
            super.setFixedCellHeight(fm.getHeight() + fm.getDescent());
        }
        super.paint(g);
    }
}

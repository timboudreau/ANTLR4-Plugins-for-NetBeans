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

import java.awt.FontMetrics;
import java.awt.Graphics;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
class SizingList<T> extends JList<T> {

    boolean active;

    void updateActive() {
        active = TopComponent.getRegistry().getActivated() == SwingUtilities.getAncestorOfClass(AntlrPluginDebugTopComponent.class, this);
    }

    private void computeFixedSize(Graphics g) {
        if (super.getFixedCellHeight() > 0) {
            return;
        }
        FontMetrics fm = g.getFontMetrics(getFont());
        super.setFixedCellHeight(fm.getMaxAscent() + fm.getMaxDescent());
    }

    @Override
    public void paint(Graphics g) {
        updateActive();
        computeFixedSize(g); // performance
        super.paint(g);
    }

}

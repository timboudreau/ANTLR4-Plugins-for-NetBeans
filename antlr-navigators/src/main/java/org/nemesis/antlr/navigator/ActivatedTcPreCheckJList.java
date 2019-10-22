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
package org.nemesis.antlr.navigator;

import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.openide.windows.TopComponent;

/**
 * Grabs the top components active state before painting commences, so that we
 * can paint a different selection color if the containing TC is active versus
 * not, as explorer components do.
 *
 * @param <T>
 */
final class ActivatedTcPreCheckJList<T> extends JList<T> implements ComponentIsActiveChecker {

    private boolean tcActive;
    private static final int MIN_CELL_HEIGHT = 16;
    private static final int MIN_MARGIN = 4;
    private boolean firstPaint;

    @Override
    public boolean isActive() {
        return tcActive;
    }

    @Override
    public void paint(Graphics g) {
        if (firstPaint) {
            // Optimization - if validation does not require rendering every
            // cell to determine preferred height, performance on trees
            // and lists is much better
            firstPaint = false;
            setFixedCellHeight(computeCellHeight((Graphics2D) g, this));
        }
        // The hierarchy lookups are somewhat expensive, and would be done
        // once for every cell to paint, if we put this logic in isTopComponentActive.
        // Since TopComponent activation changes happen on the event thread, and
        // so does painting, it cannot change while cells are being iterated
        // and rendered.  This lets us grab the state at the top of each
        // paint cycle
        TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, this);
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        tcActive = focused == null || tc == null ? false : tc.isAncestorOf(focused) || tc == focused;
        super.paint(g);
    }

    static int computeCellHeight(Graphics2D g, JComponent comp) {
        Font font = comp.getFont();
        int min = MIN_CELL_HEIGHT + MIN_MARGIN;
        FontMetrics fm = g.getFontMetrics(font);
        return Math.max(min, fm.getMaxAscent() + fm.getMaxDescent() + MIN_MARGIN);
    }
}

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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;
import static org.nemesis.antlr.navigator.ActivatedTcPreCheckJList.computeCellHeight;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
final class ActivatedTcPreCheckJTree extends JTree implements ComponentIsActiveChecker {

    private boolean tcActive;
    private boolean firstPaint;

    ActivatedTcPreCheckJTree(TreeModel mdl) {
        super(mdl);
        setLargeModel(true);
        setEditable(false);
        setExpandsSelectedPaths(true);
    }

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
            setRowHeight(computeCellHeight((Graphics2D) g, this));
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
}

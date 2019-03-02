package org.nemesis.antlr.navigator;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.openide.windows.TopComponent;

/**
 * Grabs the top components active state before painting commences, so that
 * we can paint a different selection color if the containing TC is active
 * versus not, as explorer components do.
 *
 * @param <T>
 */
final class ActivatedTcPreCheckJList<T> extends JList<T> {

    private boolean tcActive;

    boolean isActive() {
        return tcActive;
    }

    @Override
    public void paint(Graphics g) {
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

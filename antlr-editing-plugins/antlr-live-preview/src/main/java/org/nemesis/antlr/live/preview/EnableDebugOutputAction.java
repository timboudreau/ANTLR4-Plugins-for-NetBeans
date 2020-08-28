/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"enableDebugOutputAction=Enable Debug Output", "enableDebugOutputActionDescription=Prints expandable debug output" + " from the various phases of processing your grammar" + " in the output window."})
final class EnableDebugOutputAction extends AbstractAction implements Icon {

    @SuppressWarnings(value = "LeakingThisInConstructor")
    EnableDebugOutputAction() {
        putValue(Action.NAME, Bundle.enableDebugOutputAction());
        putValue(Action.SHORT_DESCRIPTION, Bundle.enableDebugOutputActionDescription());
        putValue(Action.LONG_DESCRIPTION, Bundle.enableDebugOutputActionDescription());
        putValue(Action.SMALL_ICON, this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JPopupMenu popup = OutputEnabledTasksImpl.getDefault().createEnablementPopup();
        int x = 0;
        int y = 0;
        Component target = null;
        AWTEvent evt = EventQueue.getCurrentEvent();
        if (evt.getSource() instanceof Component) {
            target = (Component) evt.getSource();
            if (evt instanceof MouseEvent) {
                MouseEvent me = (MouseEvent) evt;
                x = me.getX();
                y = me.getY();
            }
        }
        if (target == null) {
            target = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (target == null) {
                for (PropertyChangeListener pcl : getPropertyChangeListeners()) {
                    if (pcl instanceof Component) {
                        target = (Component) pcl;
                        break;
                    }
                }
            }
        }
        popup.show(target, x, y);
    }
    private final Line2D.Float ln = new Line2D.Float();

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Insets ins = c instanceof JComponent ? ((JComponent) c).getInsets() : new Insets(0, 0, 0, 0);
        float maxX = Math.min(c.getWidth(), x + getIconWidth()) - ins.right;
        float maxY = Math.min(c.getHeight(), y + getIconHeight()) - ins.bottom;
        int lineWidth = 3;
        Graphics2D gg = (Graphics2D) g;
        gg.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1));
        gg.setColor(UIManager.getColor("textText"));
        float lineLength = (maxX - x) + 4;
        float lineStart = x + 2;
        float lineGap = lineWidth - 1;
        float lineEnd = maxX - 2;
        float oneRowHeight = lineWidth + lineGap;
        float totalHeight = oneRowHeight * 3F;
        float availHeight = maxY - y;
        float centerY = y + (availHeight / 2F);
        float top = centerY - (totalHeight / 2F);
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 3; i++) {
            ln.setLine(lineStart, top, lineEnd, top);
            gg.draw(ln);
            top += oneRowHeight;
        }
    }

    @Override
    public int getIconWidth() {
        return 24;
    }

    @Override
    public int getIconHeight() {
        return 24;
    }

}

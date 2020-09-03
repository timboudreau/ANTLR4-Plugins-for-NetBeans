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
package org.nemesis.swing;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 *
 * @author Tim Boudreau
 */
public final class Scroller {

    private final Rectangle target = new Rectangle();
    private final JScrollPane pane;
    private final JComponent comp;
    private final InnerListener listener = new InnerListener();
    private final Timer timer = new Timer(30, listener);

    @SuppressWarnings(value = "LeakingThisInConstructor")
    Scroller(JComponent comp, JScrollPane pane) {
        this.pane = pane;
        this.comp = comp;
        comp.putClientProperty(Scroller.class.getName(), this);
        timer.setCoalesce(false);
    }

    public static Scroller get(JComponent comp) {
        Scroller s = (Scroller) comp.getClientProperty(Scroller.class.getName());
        if (s == null) {
            JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, comp);
            assert pane != null : "No scroll pane ancestor of " + comp;
            s = new Scroller(comp, pane);
        }
        return s;
    }

    public void beginScroll(JList<?> l, int index) {
        Rectangle r = l.getCellBounds(index, index);
        realTargetHeight = r.height;
        Rectangle viewBounds = SwingUtilities.convertRectangle(pane.getViewport(), pane.getViewport().getViewRect(), comp);
        int viewCenterY = viewBounds.y + (viewBounds.height / 2);
        int rCenterY = r.y + (r.height / 2);
        Rectangle targetRect;
        targetRect = new Rectangle(r.x, rCenterY - viewBounds.height / 2, r.width, viewBounds.height / 2);
        if (targetRect.y < 0) {
            targetRect.height += targetRect.y;
            targetRect.y = 0;
        }
        beginScroll(targetRect);
    }

    public void abortScroll() {
        done();
    }

    int realTargetHeight;
    int tick = 1;

    public void beginScroll(Rectangle bounds) {
        if (timer.isRunning()) {
            abortScroll();
        }
        if (bounds.height <= 0) {
            bounds.height = 17;
        }
        if (realTargetHeight == 0) {
            realTargetHeight = bounds.height;
        }
        tick = 0;
        target.setBounds(bounds);
        if (!Arrays.asList(comp.getComponentListeners()).contains(listener)) {
            comp.addComponentListener(listener);
            comp.addMouseWheelListener(listener);
            comp.addMouseListener(listener);
            comp.addPropertyChangeListener("ancestor", listener);
        }
        startTimer();
    }

    int start = 0;

    void startTimer() {
        BoundedRangeModel vmdl = pane.getVerticalScrollBar().getModel();
        int val = vmdl.getValue();
        start = val;
//        System.out.println("\nSTART " + val + " target " + target.y);
        timer.start();
    }

    class InnerListener extends ComponentAdapter implements ActionListener, MouseWheelListener, MouseListener, PropertyChangeListener {

        @Override
        public void componentHidden(ComponentEvent e) {
            done();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            done();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            // do nothing
        }

        @Override
        public void mousePressed(MouseEvent e) {
            done();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            // do nothing
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            // do nothing
        }

        @Override
        public void mouseExited(MouseEvent e) {
            // do nothing
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Scroller.this.actionPerformed(e);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("ancestor".equals(evt.getPropertyName())) {
                done();
            }
        }
    }

    interface EasingFunction {

        int ease(double percent, int elapsed, int start, int end, int total);
    }

    private int quadraticEase(double percent, double elapsed, double start, double end, double total) {
        double result;
        if ((elapsed /= total / 2) < 1) {
            result = end / 2 * elapsed * elapsed + start;
        } else {
            result = -end / 2 * ((--elapsed) * (elapsed - 2) - 1) + elapsed;
        }
        return (int) result;
    }

    int step(int distance) {
        distance = Math.abs(distance) / realTargetHeight;
        int result;
        if (distance > 200) {
            result = realTargetHeight * 60;
        } else if (distance > 40) {
            result = realTargetHeight * 20;
        } else if (distance > 40) {
            result = realTargetHeight * 15;
        } else if (distance > 20) {
            result = realTargetHeight * 10;
        } else if (distance > 15) {
            result = realTargetHeight * 6;
        } else if (distance > 10) {
            result = realTargetHeight * 2;
        } else if (distance > 5) {
            result = realTargetHeight * 1;
        } else if (distance > 3) {
            result = Math.max(1, realTargetHeight / 2);
        } else if (distance > 1) {
            result = Math.max(1, realTargetHeight / 4);
        } else {
            result = 2;
        }
        return result;
    }

    void done() {
        realTargetHeight = 0;
        timer.stop();
        comp.removeComponentListener(listener);
        comp.removeMouseWheelListener(listener);
        comp.removeMouseListener(listener);
        comp.removePropertyChangeListener("ancestor", listener);
    }

//    @Override
    public void xactionPerformed(ActionEvent e) {
        if (!comp.isDisplayable() || !comp.isVisible() || !comp.isShowing()) {
            timer.stop();
            return;
        }
        BoundedRangeModel vmdl = pane.getVerticalScrollBar().getModel();
        int val = vmdl.getValue();
        if (val == target.y) {
            done();
        }
        int total = 100;
//        int ydist = val - target.y;
        int ease = quadraticEase(0, tick, start, target.y, total);
//        System.out.println("Start " + start + " val " + val + " target " + target.y + " ease " + ease + " at " + tick + "/" + total);
        vmdl.setValue(ease);
        tick++;
        if (tick == total + 1) {
            vmdl.setValue(target.y);
            done();
        }
    }

    void actionPerformed(ActionEvent e) {
        if (!comp.isDisplayable() || !comp.isVisible() || !comp.isShowing()) {
            timer.stop();
            return;
        }
        BoundedRangeModel vmdl = pane.getVerticalScrollBar().getModel();
        int val = vmdl.getValue();
        int ydist = val - target.y;
        int step = step(val > target.y ? val - target.y : target.y - val);
        if (ydist > 0) {
            int newVal = val - step;
            if (newVal < 0) {
                done();
                return;
            }
            if (newVal < target.y) {
                newVal = target.y;
                done();
            }
            vmdl.setValue(newVal);
        } else if (ydist < 0) {
            int newVal = val + step;
            if (newVal > target.y) {
                newVal = target.y;
                done();
            }
            if (newVal > comp.getHeight()) {
                done();
                return;
            }
            vmdl.setValue(newVal);
        } else {
            done();
        }
    }

    public static void main(String[] args) {
        DefaultListModel<Integer> m = new DefaultListModel<>();
        for (int i = 0; i < 2000; i++) {
            m.addElement(i);
        }
        EventQueue.invokeLater(() -> {
            JPanel outer = new JPanel(new BorderLayout());
            JPanel pnl = new JPanel(new FlowLayout());
            JList<Integer> l = new JList<>(m);
            JTextArea jta = new JTextArea("500");
            outer.add(new JScrollPane(l), BorderLayout.CENTER);
            outer.add(pnl, BorderLayout.EAST);
            pnl.add(jta);
            JButton go = new JButton("Go");
            pnl.add(go);
            go.addActionListener(ae -> {
                String s = jta.getText();
                int ix = Integer.parseInt(s);
//                Rectangle r = l.getCellBounds(ix, ix);
                Scroller.get(l).beginScroll(l, ix);
            });
            JButton zero = new JButton("Zero");
            zero.addActionListener(ae -> {
//                Rectangle r = l.getCellBounds(0, 0);
                Scroller.get(l).beginScroll(l, 0);
            });
            pnl.add(zero);
            JButton fh = new JButton("1500");
            fh.addActionListener(ae -> {
//                Rectangle r = l.getCellBounds(1500, 1500);
                Scroller.get(l).beginScroll(l, 1500);
            });
            pnl.add(fh);
            JFrame jf = new JFrame();
            jf.setMinimumSize(new Dimension(500, 900));
            jf.setContentPane(outer);
            jf.pack();
            jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            jf.setVisible(true);
        });
    }
}

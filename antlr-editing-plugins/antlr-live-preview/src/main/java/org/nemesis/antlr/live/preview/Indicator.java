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
package org.nemesis.antlr.live.preview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 *
 * @author Tim Boudreau
 */
public final class Indicator extends JComponent implements ActionListener {

    private final Dimension size;
    private final Timer timer = new Timer(35, this);
    private static final int MAX_TICKS = 120;
    private int tick;
    private final int[] rgb = new int[3];

    Indicator(int size) {
        this.size = new Dimension(size, size);
    }

    Indicator() {
        this(24);
        setOpaque(true);
        setBackground(UIManager.getColor("control"));
        setForeground(Color.BLUE);
        timer.setCoalesce(false);
        timer.setRepeats(true);
        setFocusable(false);
    }
//
//    @Override
//    public void doLayout() {
//        // do nothing
//    }
//
//    @Override
//    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
//        // do nothing
//    }

    private float tickValue() {
        float max = MAX_TICKS;
        float t = tick;
        return (max - t) / max;
    }

    private float revTickValue() {
        float max = MAX_TICKS;
        float t = tick;
        float res = t / max;
//        return res + (res * (res / 3F));
        return res + (res / 1.5F);
    }

    Color color() {
        Color fg = getForeground();
        if (tick == 0 || !EventQueue.isDispatchThread()) {
            return fg;
        }
        Color bg = getBackground();
        float tv = tickValue();
        int alph = Math.max(0, (int) (tv * 255));
        for (int i = 0; i < 3; i++) {
            int fgc, bgc;
            switch (i) {
                case 0:
                    fgc = fg.getRed();
                    bgc = bg.getRed();
                    break;
                case 1:
                    fgc = fg.getGreen();
                    bgc = bg.getGreen();
                    break;
                case 2:
                    fgc = fg.getBlue();
                    bgc = bg.getBlue();
                    break;
                default:
                    throw new AssertionError();
            }
            int diff = fgc - bgc;
            int adj = (int) ((float) diff * tv);
            rgb[i] = Math.max(0, Math.min(255, bgc + adj));
        }
        return new Color(rgb[0], rgb[1], rgb[2], alph);
    }

    private static final Map<?, ?> HINTS
            = Collections.singletonMap(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    public void paint(Graphics g) {
        if (timer.isRunning()) {
            int sz = (int) Math.min(size.width, (size.width * revTickValue()));
            ((Graphics2D) g).addRenderingHints(HINTS);
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(color());
            int s2 = sz / 2;
            int xy = ((size.width / 2) - s2);
            g.fillRoundRect(xy, xy, sz, sz, size.width, size.width);
            g.drawRoundRect(xy, xy, sz, sz, size.width, size.width);
        } else {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void done() {
        timer.stop();
        tick = 0;
    }

    public void trigger() {
        tick = 0;
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    @Override
    public void removeNotify() {
        timer.stop();
        tick = 0;
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        return size;
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (tick++ > MAX_TICKS) {
            done();
        }
        paintImmediately(0, 0, getWidth(), getHeight());
    }

    public static void main(String[] args) {

        EventQueue.invokeLater(() -> {
            JPanel pnl = new JPanel(new FlowLayout());
            Indicator ind = new Indicator(24);
            JButton button = new JButton("Trigger");
            pnl.add(ind);
            pnl.add(button);
            button.addActionListener(ae -> {
                ind.trigger();
            });
            JFrame jf = new JFrame("Indicator demo");
            jf.setContentPane(pnl);
            jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            jf.pack();
            jf.setVisible(true);
        });
    }
}

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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.GeneralPath;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * A loading spinner component, because why not...
 *
 * @author Tim Boudreau
 */
public final class Spinner extends JComponent implements ActionListener, ComponentListener {

    private Shape shape;
    private final int elementCount;
    private double maxDim;
    private final Timer timer;
    private double centerX = 0;
    private double centerY = 0;
    private double dim = 0;
    private int tick;

    private Spinner() {
        this(15, 124, 180);
        setOpaque(true);
    }

    private Spinner(int elementCount, int maxDim, int tickMillis) {
        this.maxDim = maxDim;
        this.elementCount = elementCount;
        timer = new Timer(tickMillis, this);
        setBackground(UIManager.getColor("control"));
        setForeground(UIManager.getColor("controlShadow"));
        addComponentListener(this);
    }

    public static JComponent create() {
        return new Spinner();
    }

    public static JComponent create(int elementCount, int maxDimension,
            int tickMillis) {
        return new Spinner(elementCount, maxDimension, tickMillis);
    }

    @Override
    public Dimension getMaximumSize() {
        int amt = (int) (maxDim * 2);
        return new Dimension(amt, amt);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(48, 48);
    }

    @Override
    public Dimension getPreferredSize() {
        return getMaximumSize();
    }

    @Override
    public void reshape(int x, int y, int w, int h) {
        shape = null;
        dim = Math.min(maxDim, (double) Math.min(w, h) / 2D);
        centerX = (double) w / 2D;
        centerY = (double) h / 2D;
        super.reshape(x, y, w, h);
    }

    public void paint(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(getForeground());
        drawShapes((Graphics2D) g);
    }

    private Shape shape(double rotation) {
        if (shape != null) {
            return shape;
        }
        if (dim <= 0) {
            return new Rectangle();
        }
        double degrees = (360D / (double) elementCount) + rotation;

        GeneralPath gp = new GeneralPath();
        gp.moveTo(centerX, centerY);
        double halfDegrees = degrees / 2D;

        double firstDegree = Math.toRadians(-halfDegrees);
        double firstDegreeX = centerX + dim * Math.cos(firstDegree);
        double firstDegreeY = centerY + dim * Math.sin(firstDegree);

        gp.lineTo(firstDegreeX, firstDegreeY);

        double secondDegree = Math.toRadians(halfDegrees);
        double secondDegreeX = centerX + dim * Math.cos(secondDegree);
        double secondDegreeY = centerY + dim * Math.sin(secondDegree);

        gp.quadTo(centerX, centerY, secondDegreeX, secondDegreeY);
        gp.closePath();
        return shape = gp;
    }

    private void drawShapes(Graphics2D g) {
        tick++;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Shape sh = shape(0);
        if (sh instanceof Rectangle) {
            return;
        }
        double deg = 360D / (double) elementCount;

        int highlighted = tick % elementCount;
        for (int i = 0; i < elementCount; i++) {
            double amt = Math.toRadians(deg * (double) i - (((double) tick) * 1.5D));
            g.rotate(amt, centerX, centerY);
            Color c = colorForIndex(i);
            if (i == highlighted) {
                c = c.darker().darker();
            } else if (i == highlighted - 1 || i == highlighted + 1) {
                c = c.darker();
            }
            g.setColor(c);
            g.fill(sh);
            g.setColor(c.darker());
            g.draw(sh);
            g.rotate(-amt, centerX, centerY);
        }
    }

    Color colorForIndex(int ix) {
        float sat = 0.125F;
        float all = 1F / (float) elementCount;
        float hue = all * (float) ix;
        float bri = 0.85F;
        return new Color(Color.HSBtoRGB(hue, sat, bri));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void remove(Component comp) {
        timer.stop();
        super.remove(comp);
    }

    public static void main(String[] args) {
        JFrame jf = new JFrame();
//        jf.setMinimumSize(new Dimension(300, 300));
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jf.setContentPane(new Spinner());
        jf.pack();
        jf.setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        paintImmediately(0, 0, getWidth(), getHeight());
    }

    @Override
    public void componentResized(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {
        timer.start();
    }

    @Override
    public void componentHidden(ComponentEvent e) {
        timer.stop();
    }
}

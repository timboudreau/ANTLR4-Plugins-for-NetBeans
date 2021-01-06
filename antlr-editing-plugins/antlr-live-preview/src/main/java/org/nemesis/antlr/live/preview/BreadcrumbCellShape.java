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

import com.mastfrog.swing.cell.TextCell;
import com.mastfrog.swing.cell.TextCellLabel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;

/**
 *
 * @author Tim Boudreau
 */
public class BreadcrumbCellShape extends RectangularShape {

    private float x, y, w, h;
    private float reduceHeight;

    public BreadcrumbCellShape() {
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getWidth() {
        return w;
    }

    @Override
    public double getHeight() {
        return h;
    }

    @Override
    public boolean isEmpty() {
        return w <= 0 || h <= 0;
    }

    public BreadcrumbCellShape reduceHeight(float by) {
        this.reduceHeight = by;
        return this;
    }

    @Override
    public void setFrame(double x, double y, double w, double h) {
        this.x = (float) x;
        this.y = (float) y;
        this.w = (float) w;
        this.h = (float) h;
    }

    @Override
    public Rectangle2D getBounds2D() {
        return new Rectangle2D.Float(x, y, w, h);
    }

    @Override
    public boolean contains(double x, double y) {
        return getBounds2D().contains(x, y);
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        return getBounds2D().intersects(x, y, w, h);
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return getBounds2D().contains(x, y, w, h);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return new PI(at, x, y, w, h - reduceHeight);
    }

    private static final class PI implements PathIterator {

        private static final int MAX = 5;
        private final float x0, y0, x1, y1;
        private int cursor = 0;

        PI(AffineTransform xform, float x, float y, float w, float h) {
            float x1 = x + w;
            float y1 = y + h;
            if (xform != null) {
                float[] pts = new float[]{x, y, x1, y1};
                xform.transform(pts, 0, pts, 0, 2);
                x = pts[0];
                y = pts[1];
                x1 = pts[2];
                y1 = pts[3];
            }
            this.x0 = x;
            this.y0 = y;
            this.x1 = x1;
            this.y1 = y1;
        }

        @Override
        public int getWindingRule() {
            return PathIterator.WIND_NON_ZERO;
        }

        @Override
        public boolean isDone() {
            return cursor > MAX;
        }

        @Override
        public void next() {
            cursor++;
        }

        @Override
        public int currentSegment(float[] coords) {
            float heightIndent = (y1 - y0) / 2F;
            switch (cursor) {
                case 0:
                    coords[0] = x0 + heightIndent;
                    coords[1] = y0;
                    return SEG_MOVETO;
                case 1:
                    coords[0] = x1 - heightIndent;
                    coords[1] = y0;
                    return SEG_LINETO;
                case 2:
                    coords[0] = x1;
                    coords[1] = y0 + heightIndent;
                    return SEG_LINETO;
                case 3:
                    coords[0] = x1 - heightIndent;
                    coords[1] = y1;
                    return SEG_LINETO;
                case 4:
                    coords[0] = x0 + heightIndent;
                    coords[1] = y1;
                    return SEG_LINETO;
                case 5:
                    coords[0] = x0;
                    coords[1] = y1;
                    coords[2] = x0;
                    coords[3] = y0;
                    coords[4] = x0 + heightIndent;
                    coords[5] = y0;
                    return SEG_CUBICTO;
                default:
                    throw new IllegalStateException("Path finished");
            }
        }

        @Override
        public int currentSegment(double[] coords) {
            float height = y1 - y0;
            float heightIndent = height / 2F;
            switch (cursor) {
                case 0:
                    coords[0] = x0 + height;
                    coords[1] = y0;
                    return SEG_MOVETO;
                case 1:
                    coords[0] = x1 - heightIndent;
                    coords[1] = y0;
                    return SEG_LINETO;
                case 2:
                    coords[0] = x1;
                    coords[1] = y0 + heightIndent;
                    return SEG_LINETO;
                case 3:
                    coords[0] = x1 - heightIndent;
                    coords[1] = y1;
                    return SEG_LINETO;
                case 4:
                    coords[0] = x1 + height;
                    coords[1] = y1;
                    return SEG_LINETO;
                case 5:
                    coords[0] = x0;
                    coords[1] = y1;
                    coords[2] = x0;
                    coords[3] = y0;
                    coords[4] = x0 + height;
                    coords[5] = y0;
                    return SEG_CUBICTO;
                default:
                    throw new IllegalStateException("Path finished");
            }
        }
    }

    public static void main(String[] args) {
        BreadcrumbCellShape shape = new BreadcrumbCellShape();

        class IC implements Icon {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Ellipse2D.Double ell = new Ellipse2D.Double(x, y, getIconWidth(), getIconHeight());
                g.setColor(Color.RED);
                ((Graphics2D) g).fill(ell);
                g.setColor(Color.BLUE);
                ((Graphics2D) g).draw(ell);
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

        JPanel pnl = new JPanel(new BorderLayout());

        TextCell cell = new TextCell("Hello").withForeground(Color.BLUE).bold();
        cell.append("world", tx -> {
            tx.withBackground(Color.ORANGE, shape).indent(12).rightMargin(12);
        });
        cell.append("stuff", tx -> {
            tx.withForeground(new Color(0, 128, 0)).leftMargin(10).withFont(new Font("Times New Roman", Font.BOLD, 36))
                    .rightMargin(10).strikethrough();
        });
        cell.append("Goodbye", tx -> {
            tx.italic().withBackground(Color.GRAY, shape).withForeground(Color.WHITE).indent(12).rightMargin(12)
                    .strikethrough().pad(5);
        });
        cell.append("Wonderful", tx -> {
            tx.scaleFont(0.5F).withBackground(Color.ORANGE, shape).indent(10);
        });
        cell.append("plain", tx -> {
            tx.leftMargin(12).withBackground(Color.LIGHT_GRAY, shape).rightMargin(12).pad(5);
        });

        TextCellLabel lbl = new TextCellLabel(cell).setIcon(new IC()).setIconTextGap(1).setIndent(20);
        lbl.setFont(new Font("Arial", Font.PLAIN, 36));
//        lbl.setBackground(Color.YELLOW);
//        lbl.setOpaque(true);
//        lbl.setBorder(BorderFactory.createMatteBorder(3, 7, 5, 9, Color.MAGENTA));
        pnl.add(lbl, BorderLayout.CENTER);
        pnl.add(new JSlider(), BorderLayout.NORTH);

        lbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String txt = lbl.textAt(e.getPoint());
                    if (txt != null) {
                        JOptionPane.showMessageDialog(pnl, txt);
                    }
                }
            }
        });

        JFrame jf = new JFrame();
        jf.setContentPane(pnl);
        jf.pack();
        Font f = lbl.getFont();
        shape.reduceHeight(jf.getFontMetrics(f).getDescent() / 2);
        jf.setLocation(400, 400);
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }
}

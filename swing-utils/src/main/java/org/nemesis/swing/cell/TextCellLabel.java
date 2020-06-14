/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a newCellLikeThis of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.swing.cell;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 *
 * @author Tim Boudreau
 */
public class TextCellLabel extends JComponent {

    private TextCell cell = new TextCell(" ");
    private final Rectangle2D.Float size = new Rectangle2D.Float();
    private Icon icon;
    private int gap;
    private int indent;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public TextCellLabel() {
        setBackground(UIManager.getColor("Label.background"));
        setForeground(UIManager.getColor("Label.foreground"));
        Font f = UIManager.getFont("Label.font");
        if (f == null) {
            f = UIManager.getFont("controlFont");
        }
        if (f != null) {
            setFont(f);
        }
        setOpaque(false);
    }

    public TextCellLabel(String text) {
        this();
        setText(text);
    }

    public TextCellLabel(TextCell cell) {
        this();
        this.cell = cell;
    }

    public TextCellLabel setIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public TextCellLabel setIconTextGap(int gap) {
        this.gap = gap;
        return this;
    }

    public TextCellLabel setIndent(int indent) {
        this.indent = indent;
        return this;
    }

    public Icon getIcon() {
        return icon;
    }

    public Dimension getPreferredSize() {
        size.x = size.y = size.width = size.height = 0;
        Insets ins = getInsets();
        cell.bounds(getFont(), size, ins.left, ins.top, this::getFontMetrics);
        Dimension d = new Dimension((int) Math.ceil(size.width + ins.left + ins.right) + indent,
                (int) Math.ceil(size.height + ins.top + ins.bottom));
        if (icon != null) {
            d.height = Math.max(d.height, icon.getIconHeight() + ins.top + ins.bottom);
            d.width += icon.getIconWidth() + gap;
        }
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        g.setColor(getForeground());
        g.setFont(getFont());
        Insets ins = getInsets();
        int x = ins.left + indent;
        int y = ins.top;
        int w = getWidth() - (ins.left + ins.right);
        int h = getHeight() - (ins.top + ins.bottom);
        int iconY = 0;
        int iconX = indent;
        if (icon != null) {
            int iw = icon.getIconWidth();
            w -= iw + gap;
            x += iw + gap;
        }
        size.width = size.height = size.x = size.y = 0;
        float baseline = cell.paint((Graphics2D) g, x, y, x + w, y + h, size);
        if (icon != null) {
            int ih = icon.getIconHeight();
            int availIconHeight = (int) (baseline - y);
            if (availIconHeight < ih) {
                iconY = 0;
//                int offset = (availIconHeight - ih) / 2;
//                iconY = y + offset;
            } else {
                iconY = (int) (baseline - ih);
            }
            icon.paintIcon(this, g, iconX, iconY);
        }
    }

    public void setText(String text) {
        setCell(cell.newCellLikeThis(text));
    }

    public TextCell getCell(TextCell cell) {
        return cell;
    }

    public TextCell cell() {
        return cell.reset();
    }

    public void setCell(TextCell cell) {
        this.cell = cell;
        invalidate();
        revalidate();
        repaint();
    }

    public static void main(String[] args) {

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

        JPanel pnl = new JPanel();

        TextCell cell = new TextCell("Hello").withForeground(Color.BLUE).bold();
        cell.inner("world", tx -> {
            tx.withBackground(Color.ORANGE, new Ellipse2D.Float());
        });
        cell.inner("stuff", tx -> {
            tx.withForeground(new Color(0, 128, 0)).margin(10).withFont(new Font("Times New Roman", Font.BOLD, 36))
                    .rightMargin(10).strikethrough();
        });
        cell.inner("Goodbye", tx -> {
            tx.italic().withBackground(Color.GRAY).withForeground(Color.WHITE).indent(12).rightMargin(12)
                    .strikethrough();
        });
        cell.inner("Wonderful", tx -> {
            tx.scaleFont(0.5F).withBackground(Color.ORANGE, new RoundRectangle2D.Float(0, 0, 0, 0, 17, 14)).indent(10)
                    .stretch();
        });
        cell.inner("plain", tx -> {
            tx.margin(12);
        });

        TextCellLabel lbl = new TextCellLabel(cell).setIcon(new IC()).setIconTextGap(1).setIndent(20);
        lbl.setFont(new Font("Arial", Font.PLAIN, 36));
        lbl.setBackground(Color.YELLOW);
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createMatteBorder(3, 7, 5, 9, Color.MAGENTA));
        pnl.add(lbl);

        JFrame jf = new JFrame();
        jf.setContentPane(pnl);
        jf.pack();
        jf.setLocation(400, 400);
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jf.setVisible(true);

    }
}

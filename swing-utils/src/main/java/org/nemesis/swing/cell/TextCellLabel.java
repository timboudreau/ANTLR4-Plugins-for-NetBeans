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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * A label which can display "cells" of text that have different
 * font/color/background shape attributes.  For use as a Swing cell renderer,
 * use the subclass TextCellCellRenderer.
 *
 * @author Tim Boudreau
 */
public class TextCellLabel extends JComponent {

    private TextCell cell = new TextCell(" ");
    private final Rectangle2D.Float size = new Rectangle2D.Float();
    private Icon icon;
    private int gap;
    private int indent;
    private boolean useFullTextAsToolTip;
    private String fullTextDelimiter = " ";

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

    /**
     * Set an icon on this label.
     *
     * @param icon An icon or null
     * @return this
     */
    public TextCellLabel setIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Set the gap between icon and the start of the first cell's contents.
     *
     * @param gap The gap
     * @return this
     */
    public TextCellLabel setIconTextGap(int gap) {
        this.gap = gap;
        return this;
    }

    /**
     * Set the amount all contents of this label should be indented.
     *
     * @param indent The indent amount
     * @return this
     */
    public TextCellLabel setIndent(int indent) {
        this.indent = indent;
        return this;
    }

    /**
     * If set, use the full text of the text cell(s) as the tooltip for this
     * component.
     *
     * @param delimiter The delimiter to place between cells so text does not
     * run together.
     * @return this
     */
    public TextCellLabel useFullTextAsToolTip(String delimiter) {
        this.fullTextDelimiter = delimiter;
        return this;
    }

    /**
     * If set, use the full text of the text cell(s) as the tooltip for this
     * component.
     *
     * @return this
     */
    public TextCellLabel useFullTextAsToolTip() {
        setUseFullTextAsToolTip(true);
        return this;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ensureNotRegisteredWithTooltipManagerIfNeeded();
    }

    @Override
    public void removeNotify() {
        ensureNotRegisteredWithTooltipManagerIfNeeded();
        super.removeNotify();
    }

    public String getFullText() {
        return cell.fullText(fullTextDelimiter);
    }

    public void setFullTextDelimiter(String delmiter) {
        this.fullTextDelimiter = delmiter;
    }

    public void setUseFullTextAsToolTip(boolean val) {
        if (val != useFullTextAsToolTip) {
            useFullTextAsToolTip = val;
            if (val) {
                ensureRegisteredWithTooltipManager();
            } else {
                ensureNotRegisteredWithTooltipManagerIfNeeded();
            }
        }
    }

    private void ensureRegisteredWithTooltipManager() {
        if (!isCellRenderer() && isDisplayable()) {
            ToolTipManager.sharedInstance().registerComponent(this);
        }
    }

    private void ensureNotRegisteredWithTooltipManagerIfNeeded() {
        if (!isCellRenderer()) {
            ToolTipManager.sharedInstance().unregisterComponent(this);
        }
    }

    public boolean isEmpty() {
        return cell.isEmpty();
    }

    @Override
    public String getToolTipText() {
        if (useFullTextAsToolTip) {
            return cell.fullText(fullTextDelimiter);
        }
        return super.getToolTipText();
    }

    /**
     * Overridden by TextCellCellRenderer to return true and disable registering
     * with ToolTipManager.
     *
     * @return false by default
     */
    protected boolean isCellRenderer() {
        return false;
    }

    /**
     * Get the currently set icon.
     *
     * @return An icon
     */
    public Icon getIcon() {
        return icon;
    }

    /**
     * Computes the preferred size based on the contained cell(s0.
     *
     * @return A dimension
     */
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
        d.width = Math.max(5, d.width);
        if (d.height == 0) {
            FontMetrics fm = getFontMetrics(getFont());
            d.height = fm.getHeight() + fm.getDescent();
        }
        return d;
    }

    /**
     * Returns the preferred size.
     *
     * @return The minimum size
     */
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    /**
     * Get the text of the cell painted at a given point, if any.
     *
     * @param p The point
     * @return The text
     */
    public String textAt(Point p) {
        Insets ins = getInsets();
        int x = ins.left + indent;
        int y = ins.top;
        int w = getWidth() - (ins.left + ins.right);
        int h = getHeight() - (ins.top + ins.bottom);
        if (icon != null) {
            int iw = icon.getIconWidth() + gap;
            w -= iw;
            x += iw;
        }
        return cell.textAt(p, x, y, x + w, y + h, getFont(), this::getFontMetrics);
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
            int iw = icon.getIconWidth() + gap;
            w -= iw;
            x += iw;
        }
        size.width = size.height = size.x = size.y = 0;
        float baseline = cell.paint((Graphics2D) g, x, y, x + w, y + h, size);
        if (icon != null) {
            int ih = icon.getIconHeight();
            int availIconHeight = (int) (baseline - y);
            if (availIconHeight < ih) {
                iconY = 0;
            } else {
                iconY = (int) (baseline - ih);
            }
            icon.paintIcon(this, g, iconX, iconY);
        }
    }

    /**
     * Set the displayed text.  This replaces the cell with one which
     * retains color, shape, margin, padding and font settings, but not
     * any child cells, with the text set to the passed text.
     *
     * @param text
     */
    public void setText(String text) {
        setCell(cell.newCellLikeThis(text));
    }

    /**
     * Get the text cell, without altering its contents.
     *
     * @param cell The cell
     * @return The cell
     */
    public TextCell getCell() {
        return cell;
    }

    /**
     * Get the cell, resetting its coloring and all attributes of it -
     * use this method when replacing the entire contents which may
     * involve a sequence of child cells, to remove previously painted
     * state (particularly useful in cell renderers).
     *
     * @return The cell, with its contents and state cleared
     */
    public TextCell cell() {
        return cell.reset();
    }

    /**
     * Replace the cell contents.
     *
     * @param cell The cell
     */
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

        JPanel pnl = new JPanel(new BorderLayout());

        TextCell cell = new TextCell("Hello").withForeground(Color.BLUE).bold();
        cell.append("world", tx -> {
            tx.withBackground(Color.ORANGE, new Ellipse2D.Float());
        });
        cell.append("stuff", tx -> {
            tx.withForeground(new Color(0, 128, 0)).leftMargin(10).withFont(new Font("Times New Roman", Font.BOLD, 36))
                    .rightMargin(10).strikethrough();
        });
        cell.append("Goodbye", tx -> {
            tx.italic().withBackground(Color.GRAY).withForeground(Color.WHITE).indent(12).rightMargin(12)
                    .strikethrough();
        });
        cell.append("Wonderful", tx -> {
            tx.scaleFont(0.5F).withBackground(Color.ORANGE, new RoundRectangle2D.Float(0, 0, 0, 0, 17, 14)).indent(10)
                    ;
        });
        cell.append("plain", tx -> {
            tx.leftMargin(12).withBackground(Color.LIGHT_GRAY, new RoundRectangle2D.Float(0, 0, 0, 0, 17, 14));
        });

        TextCellLabel lbl = new TextCellLabel(cell).setIcon(new IC()).setIconTextGap(1).setIndent(20);
        lbl.setFont(new Font("Arial", Font.PLAIN, 36));
        lbl.setBackground(Color.YELLOW);
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createMatteBorder(3, 7, 5, 9, Color.MAGENTA));
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
        jf.setLocation(400, 400);
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }
}

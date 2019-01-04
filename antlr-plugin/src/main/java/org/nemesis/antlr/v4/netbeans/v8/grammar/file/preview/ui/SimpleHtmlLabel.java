package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.openide.awt.HtmlRenderer;

/**
 * Fork of the original in openide.awt which is broken.
 *
 * @author Tim Boudreau
 */
final class SimpleHtmlLabel extends JComponent {

    private String text = " ";
    private Dimension cachedSize;

    SimpleHtmlLabel() {
        setOpaque(true);
        setBackground(UIManager.getColor("control"));
        setForeground(UIManager.getColor("textText"));
    }

    public void setText(String text) {
        if (!this.text.equals(text)) {
            cachedSize = null;
            this.text = text;
            invalidate();
            revalidate();
            repaint();
        }
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    public Dimension getPreferredSize() {
        if (cachedSize != null) {
            return cachedSize;
        }
        Graphics2D g = HtmlRendererImpl.scratchGraphics();
        Font font = getFont();
        FontMetrics fm = g.getFontMetrics(font);
        Insets ins = getInsets();
        int y = fm.getMaxAscent();
        int h = fm.getHeight();
        int w = (int) Math.ceil(HtmlRenderer.renderHTML(text, g, 0, y, Integer.MAX_VALUE, h, font, getForeground(), HtmlRenderer.STYLE_TRUNCATE, false));
        if (ins != null) {
            w += ins.left + ins.right;
            h += ins.top + ins.bottom;
        }
        return cachedSize = new Dimension(Math.max(24, w), Math.max(16, h));
    }

    @Override
    public void doLayout() {
        cachedSize = null;
        super.doLayout();
    }

    @Override
    @SuppressWarnings(value = "deprecation")
    public void reshape(int x, int y, int w, int h) {
        cachedSize = null;
        super.reshape(x, y, w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isOpaque() && getBackground() != null) {
            Color c = getBackground();
            g.setColor(c);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        Font font = getFont();
        g.setColor(getForeground());
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        int x = 0;
        int y = fm.getMaxAscent();
        int h = getHeight();
        Insets ins = getInsets();
        if (ins != null) {
            x += ins.left;
        }
        if (h > y) {
            y += (h - y) / 2;
        }
        HtmlRenderer.renderHTML(text, g, x, y, getWidth(), h, font, getForeground(), HtmlRenderer.STYLE_TRUNCATE, true);
    }

}

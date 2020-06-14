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

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.function.Consumer;
import java.util.function.Function;
import org.nemesis.swing.html.HtmlRenderer;

/**
 *
 * @author Tim Boudreau
 */
public class TextCell {

    private Paint foreground;
    private Paint background;
    private Font font;
    private String text;
    private RectangularShape bgShape;
    private TextCell child;
    private TextCell oldChild;
    private boolean isChild;
    private int indent;
    private int margin;
    private int rightMargin;
    private int padding;
    private int bottomMargin;
    private boolean bold;
    private boolean italic;
    private boolean stretch;
    private boolean strikethrough;
    private AffineTransform scaleFont;

    TextCell(String text, boolean isChild) {
        this.text = text;
        this.isChild = isChild;
    }

    public TextCell(String text) {
        this.text = text;
    }

    public TextCell reset(String newText) {
        reset();
        this.text = newText;
        return this;
    }

    public TextCell reset() {
        text = "";
        oldChild = child;
        child = null;
        strikethrough = false;
        foreground = null;
        background = null;
        font = null;
        indent = 0;
        bottomMargin = 0;
        margin = 0;
        padding = 0;
        bold = false;
        italic = false;
        stretch = false;
        scaleFont = null;
        rightMargin = 0;
        return this;
    }

    public TextCell newCellLikeThis(String text) {
        TextCell nue = new TextCell(text, false);
        nue.foreground = foreground;
        nue.background = background;
        nue.font = font;
        nue.bgShape = bgShape;
        nue.indent = indent;
        nue.bottomMargin = bottomMargin;
        nue.margin = margin;
        nue.padding = padding;
        nue.bold = bold;
        nue.strikethrough = strikethrough;
        nue.italic = italic;
        nue.stretch = stretch;
        nue.scaleFont = scaleFont;
        nue.rightMargin = rightMargin;
        return nue;
    }

    public TextCell bottomMargin(int bottomMargin) {
        this.bottomMargin = bottomMargin;
        return this;
    }

    public TextCell strikethrough() {
        this.strikethrough = true;
        return this;
    }

    public TextCell withText(String text) {
        this.text = text;
        return this;
    }

    public TextCell stretch() {
        this.stretch = true;
        return this;
    }

    public TextCell bold() {
        bold = true;
        return this;
    }

    public TextCell italic() {
        italic = true;
        return this;
    }

    public TextCell rightMargin(int margin) {
        this.rightMargin = margin;
        return this;
    }

    public TextCell withFont(Font font) {
        this.font = font;
        return this;
    }

    public TextCell withForeground(Paint fg) {
        this.foreground = fg;
        return this;
    }

    public TextCell withBackground(Paint bg, RectangularShape bgShape) {
        this.bgShape = bgShape;
        this.background = bg;
        return this;
    }

    public TextCell withBackground(Paint bg) {
        this.background = bg;
        return this;
    }

    public TextCell indent(int by) {
        indent = by;
        return this;
    }

    public TextCell margin(int margin) {
        this.margin = margin;
        return this;
    }

    public TextCell pad(int padding) {
        this.padding = padding;
        return this;
    }

    public TextCell scaleFont(double by) {
        if (by == 1D || by <= 0D) {
            scaleFont = null;
        } else {
            scaleFont = AffineTransform.getScaleInstance(by, by);
        }
        return this;
    }

    public TextCell scaleFont(float by) {
        if (by == 1F || by <= 0F) {
            scaleFont = null;
        } else {
            scaleFont = AffineTransform.getScaleInstance(by, by);
        }
        return this;
    }

    public TextCell inner(String text, Consumer<TextCell> childConsumer) {
        if (oldChild != null) {
            child = oldChild.reset();
            oldChild = null;
            childConsumer.accept(child.withText(text));;
            return this;
        }
        if (child != null) {
            child.inner(text, childConsumer);
        } else {
            child = new TextCell(text, true);
            childConsumer.accept(child);
        }
        return this;
    }

    private Font _font(Font initialFont) {
        Font f = font == null ? initialFont : font;
        if (bold && italic && (!f.isBold() || !f.isItalic())) {
            f = f.deriveFont(Font.BOLD | Font.ITALIC);
        } else if (bold && !f.isBold()) {
            f = f.deriveFont(Font.BOLD);
        } else if (italic && !f.isItalic()) {
            f = f.deriveFont(Font.ITALIC);
        }
        if (scaleFont != null) {
            f = f.deriveFont(scaleFont);
        }
        return f;
    }

    public void bounds(Font initialFont, Rectangle2D.Float into, float x, float y, Function<Font, FontMetrics> func) {
        if (text == null || text.isEmpty()) {
            if (child != null) {
                child.bounds(initialFont, into, x, y, func);
            } else {
                into.width = into.height = 0;
            }
            return;
        }
        FontMetrics fm = func.apply(_font(initialFont));
        int w = fm.stringWidth(text);
        int h = fm.getHeight() + fm.getDescent();
        if (into.isEmpty()) {
            into.x = x;
            into.y = y;
            into.width = margin + rightMargin + indent + w + (padding * 2);
            into.height = h + (padding * 2) + bottomMargin;
        } else {
            into.width += margin + rightMargin + indent + w + (padding * 2);
            into.height = Math.max(into.height, h + (padding * 2) + bottomMargin);
        }
        if (child != null) {
            child.bounds(initialFont, into, x + into.width, y, func);
        }
    }

    private static final BasicStroke STROKE = new BasicStroke(1);
    private final Rectangle2D.Float RECT = new Rectangle2D.Float();
    private static final Line2D.Float STRIKE = new Line2D.Float();

    public float paint(Graphics2D g, float x, float y, float maxX, float maxY, Rectangle2D.Float painted) {
        return paint(g, x, y, maxX, maxY, -1, painted);
    }

    private float baseline(float y, Graphics2D g, float lastBaseline) {
        Font f = _font(font == null ? g.getFont() : font);
        FontMetrics fm = g.getFontMetrics(f);
        float result = Math.max(lastBaseline, y + fm.getAscent()) + padding;
        if (child != null) {
            result = Math.max(result, child.baseline(y, g, result));
        }
        return result;
    }

    private float paint(Graphics2D g, float x, float y, float maxX, float maxY, float baseline, Rectangle2D.Float painted) {
        if (text == null || text.isEmpty()) {
            if (child != null) {
                return child.paint(g, x, y, maxX, maxY, baseline, painted);
            }
        }
        Font oldFont = g.getFont();
        Paint oldPaint = g.getPaint();
        Font f = _font(font == null ? g.getFont() : font);
        if (oldFont != f) {
            g.setFont(f);
        }
        if (baseline < 0) {
            baseline = baseline(y, g, baseline);
        }
        FontMetrics fm = g.getFontMetrics();
        float baselineAdjust = baseline - (y + fm.getAscent());

        int w = fm.stringWidth(text);
        int h = fm.getHeight() + fm.getDescent();
        if (!isChild) {
            g.addRenderingHints(HtmlRenderer.hintsMap());
            Object rhval = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            if (rhval == null || rhval == RenderingHints.VALUE_TEXT_ANTIALIAS_OFF) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
        }
        if (background != null) {
            RectangularShape shape = bgShape == null ? RECT : bgShape;
            float shapeW;
            if (!stretch) {
                shapeW = Math.min(rightMargin + margin + w + (padding * 2) + indent, maxX - x);
            } else {
                shapeW = Math.max(rightMargin + margin + w + (padding * 2) + indent, maxX - x);
            }
            float shapeY = y + (baselineAdjust / 2);
            shape.setFrame(x + margin, shapeY, shapeW, Math.min(y + h + (padding * 2) + fm.getDescent(), maxY - y) - y);
            if (!isChild) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            Stroke oldStroke = g.getStroke();
            g.setStroke(STROKE);
            g.setPaint(background);
            g.draw(shape);
            g.fill(shape);
            g.setStroke(oldStroke);
            if (painted.isEmpty()) {
                painted.setRect(shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight());
            } else {
                if (shape != RECT) {
                    RECT.setFrame(shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight());
                }
                painted.add(RECT);
            }
        } else {
            if (painted.isEmpty()) {
                painted.setFrame(x, y, w + margin + indent + (padding * 2), h + (padding * 2));
            } else {
                RECT.setFrame(x, y, w + margin + indent + (padding * 2), h + (padding * 2));
                painted.add(RECT);
            }
        }
        float textY = baseline;
        if (foreground != null) {
            g.setPaint(foreground);
        } else {
            g.setPaint(oldPaint);
        }
        float textX = x + margin + indent + padding;
        g.drawString(text, textX, textY);
        if (strikethrough) {
            LineMetrics lm = fm.getLineMetrics(text, g);
            float lineY = textY + lm.getStrikethroughOffset();
            float thick = lm.getStrikethroughThickness();
            BasicStroke stroke = new BasicStroke(thick);
            Stroke oldStroke = g.getStroke();
            g.setStroke(stroke);
            STRIKE.setLine(textX, lineY, textX + w, lineY);
            g.draw(STRIKE);
            g.setStroke(oldStroke);
        }
        if (oldFont != f) {
            g.setFont(oldFont);
        }
        g.setPaint(oldPaint);
        if (child != null) {
            child.paint(g, x + w + margin + indent + (padding * 2) + rightMargin, y, maxX, maxY, baseline, painted);
        }
        return baseline;
    }

    @Override
    public String toString() {
        return "TextCell{" + "foreground=" + foreground + ", background=" + background + ", font=" + font + ", text=" + text + ", bgShape=" + bgShape + ", child=" + child + ", isChild=" + isChild + ", indent=" + indent + ", margin=" + margin + ", padding=" + padding + ", bold=" + bold + ", italic=" + italic + ", stretch=" + stretch + '}';
    }
}

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
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.UIManager;
import org.nemesis.swing.html.HtmlRenderer;

/**
 * A single cell of text which may have its own foreground, background, font,
 * font style and scaling, left and right and bottom margins, padding,
 * properties, and optionally a RectangularShape which wraps around the
 * background.
 *
 * @author Tim Boudreau
 */
public class TextCell {

    private static final BasicStroke STROKE = new BasicStroke(1);
    private final Rectangle2D.Float RECT = new Rectangle2D.Float();
    private static final Line2D.Float STRIKE = new Line2D.Float();
    private Paint foreground;
    private Paint background;
    private Font font;
    private String text;
    private RectangularShape bgShape;
    private TextCell child;
    private TextCell oldChild;
    private boolean isChild;
    private int indent;
    private int leftMargin;
    private int rightMargin;
    private int padding;
    private int bottomMargin;
    private boolean bold;
    private boolean italic;
    private boolean stretch;
    private boolean strikethrough;
    private boolean shapeOutlinePainted;
    private int topMargin;
    private boolean monospaced;
    private AffineTransform scaleFont;
    private AffineTransform lastScaleFont;
    private static Font monospacedFont;

    Font monospacedFont() {
        if (monospacedFont != null) {
            return monospacedFont;
        }
        Font f = UIManager.getFont("EditorPane.font");
        // in a bare swing application, this happens
        if ("Dialog".equals(f.getName())) {
            f = null;
        }
        if (f == null) {
//            f = new Font("Monospaced", Font.PLAIN, 12);
            f = new Font("Courier New", Font.PLAIN, 12);
        } else {
            if (f.getStyle() != Font.PLAIN) {
                f = f.deriveFont(Font.PLAIN);
            }
        }
        return monospacedFont = f;
    }

    TextCell(String text, boolean isChild) {
        this.text = text;
        this.isChild = isChild;
    }

    public TextCell(String text) {
        this.text = text;
    }

    public TextCell monospaced() {
        this.monospaced = true;
        return this;
    }

    public TextCell reset(String newText) {
        reset();
        this.text = newText;
        return this;
    }

    /**
     * Wipe the contents and properties of this cell, leaving it ready to be set
     * up with new contents and properties.
     *
     * @return this
     */
    public TextCell reset() {
        text = "";
        oldChild = child;
        if (scaleFont != null) {
            lastScaleFont = scaleFont;
        }
        child = null;
        monospaced = false;
        strikethrough = false;
        foreground = null;
        background = null;
        font = null;
        indent = 0;
        bottomMargin = 0;
        shapeOutlinePainted = false;
        leftMargin = 0;
        padding = 0;
        bold = false;
        italic = false;
        stretch = false;
        scaleFont = null;
        rightMargin = 0;
        topMargin = 0;
        return this;
    }

    /**
     * Create a cell which shares all the properties of this one other than
     * child cells, but uses the passed text as its text.
     *
     * @param text The text
     * @return A new cell
     */
    public TextCell newCellLikeThis(String text) {
        TextCell nue = new TextCell(text, false);
        nue.foreground = foreground;
        nue.topMargin = topMargin;
        nue.background = background;
        nue.font = font;
        nue.bgShape = bgShape;
        nue.indent = indent;
        nue.bottomMargin = bottomMargin;
        nue.leftMargin = leftMargin;
        nue.padding = padding;
        nue.bold = bold;
        nue.strikethrough = strikethrough;
        nue.italic = italic;
        nue.stretch = stretch;
        nue.scaleFont = scaleFont;
        nue.rightMargin = rightMargin;
        nue.shapeOutlinePainted = shapeOutlinePainted;
        nue.lastScaleFont = lastScaleFont;
        nue.monospaced = monospaced;
        return nue;
    }

    /**
     * Set the bottom leftMargin in pixels.
     *
     * @param bottomMargin The bottom leftMargin
     * @return this
     */
    public TextCell bottomMargin(int bottomMargin) {
        this.bottomMargin = bottomMargin;
        return this;
    }

    /**
     * Set this cell to strike through its text.
     *
     * @return this
     */
    public TextCell strikethrough() {
        this.strikethrough = true;
        return this;
    }

    /**
     * Change the text of this cell.
     *
     * @param text The new text.
     * @return
     */
    public TextCell withText(String text) {
        this.text = text == null ? "" : text;
        return this;
    }

    /**
     * Cause the background shape (if any) to stretch to the maxiumum x position
     * when painting, such that any child cells also are contained within it.
     *
     * @return this
     */
    public TextCell stretch() {
        this.stretch = true;
        return this;
    }

    /**
     * Add bold to the font styles.
     *
     * @return this
     */
    public TextCell bold() {
        bold = true;
        return this;
    }

    /**
     * Add italic to the font style.
     *
     * @return this
     */
    public TextCell italic() {
        italic = true;
        return this;
    }

    /**
     * Set the right leftMargin in pixels.
     *
     * @param margin The leftMargin
     * @return this
     */
    public TextCell rightMargin(int margin) {
        this.rightMargin = margin;
        return this;
    }

    /**
     * Set the font to use (the passed font will be used as a base for deriving
     * the actual font painted with, if the font scaling or style is set).
     *
     * @param font The font
     * @return this
     */
    public TextCell withFont(Font font) {
        this.font = font;
        return this;
    }

    /**
     * Set the foreground paint.
     *
     * @param fg The foreground paint.
     * @return this
     */
    public TextCell withForeground(Paint fg) {
        this.foreground = fg;
        return this;
    }

    /**
     * Set the background paint and a shape to use for the background.
     *
     * @param bg The background paint
     * @param bgShape The background shape
     * @return this
     */
    public TextCell withBackground(Paint bg, RectangularShape bgShape) {
        this.bgShape = bgShape;
        this.background = bg;
        return this;
    }

    /**
     * Set the background paint.
     *
     * @param bg The background
     * @return this
     */
    public TextCell withBackground(Paint bg) {
        this.background = bg;
        return this;
    }

    /**
     * Set the indentation of this cell - empty space before any background
     * shape or text is painted.
     *
     * @param by The indent
     * @return this
     */
    public TextCell indent(int by) {
        indent = by;
        return this;
    }

    /**
     * Set the left leftMargin of this cell - empty space which may come after
     * the left edge of the background shape (if any) before text painting
     * begins.
     *
     * @param margin The leftMargin
     * @return this
     */
    public TextCell leftMargin(int margin) {
        this.leftMargin = margin;
        return this;
    }

    /**
     * Set the padding - pixels added to all sides of this cell when painting or
     * computing the preferred size.
     *
     * @param padding The padding
     * @return this
     */
    public TextCell pad(int padding) {
        this.padding = padding;
        return this;
    }

    /**
     * Set scaling on the font when painting.
     *
     * @param by A value &gt; 1 to make the font larger, or &lt; 1 to make it
     * smaller
     * @return this
     */
    public TextCell scaleFont(double by) {
        if (by == 1D || by <= 0D) {
            scaleFont = null;
        } else {
            if (lastScaleFont != null && lastScaleFont.getScaleX() == by && lastScaleFont.getScaleY() == by) {
                scaleFont = lastScaleFont;
            } else {
                scaleFont = AffineTransform.getScaleInstance(by, by);
            }
        }
        return this;
    }

    /**
     * Set scaling on the font when painting.
     *
     * @param by A value &gt; 1 to make the font larger, or &lt; 1 to make it
     * smaller
     * @return this
     */
    public TextCell scaleFont(float by) {
        return scaleFont((double) by);
    }

    public TextCell topMargin(int margin) {
        this.topMargin = topMargin;
        return this;
    }

    /**
     * Append a new cell to this one (technically adding a child cell which is
     * passed to the passed consumer for configuration) - the new cell will be
     * painted adjacent to this one and may have its own color and other
     * properties.
     *
     * @param text The text to use for the subsequent cell
     * @param childConsumer A consumer which is passed the child cell
     * @return this, <b>not</b> the child cell
     */
    public TextCell append(String text, Consumer<TextCell> childConsumer) {
        if (oldChild != null) {
            child = oldChild.reset();
            oldChild = null;
            childConsumer.accept(child.withText(text));;
            return this;
        }
        if (child != null) {
            child.append(text, childConsumer);
        } else {
            child = new TextCell(text, true);
            childConsumer.accept(child);
        }
        return this;
    }

    private Font baseFont(Font initialFont) {
        Font result;
        if (font == null) {
            if (monospaced) {
                result = monospacedFont();
                if (result.getSize2D() != initialFont.getSize2D() || result.getStyle() != initialFont.getStyle()) {
                    result = result.deriveFont(initialFont.getStyle(), initialFont.getSize2D());
                }
                if (initialFont.isTransformed()) {
                    result = result.deriveFont(initialFont.getTransform());
                }
            } else {
                result = initialFont;
            }
        } else {
            result = font;
        }
        return result;
    }

    private Font _font(Font initialFont) {
        Font f = baseFont(initialFont);
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

    /**
     * Compute the bounds of this cell and its children into the passed
     * Rectangle.'
     *
     * @param initialFont The font to use if no font is explicitly set on this
     * cell or its children
     * @param into A rectangle to reconfigure with the target bounds
     * @param x The starting x coordinate
     * @param y The starting y coordinate
     * @param func A function for retrieving a screen-tuned FontMetrics for a
     * font - in a <code>Component</code>, pass
     * <code>this::getFontMetrics</code>, or a handle to the equivalent method
     * on a <code>Graphics2D</code>.
     */
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
            into.width = leftMargin + rightMargin + indent + w + (padding * 2);
            into.height = h + (padding * 2) + bottomMargin + topMargin;
        } else {
            into.width += leftMargin + rightMargin + indent + w + (padding * 2);
            into.height = Math.max(into.height, h + (padding * 2) + bottomMargin + topMargin);
        }
        if (child != null) {
            child.bounds(initialFont, into, x + into.width, y, func);
        }
    }

    /**
     * Get the text of this cell and any child cells, prepending the passed
     * delimiter before the text of child cells.
     *
     * @param delimiter A delmiter
     * @return The text of this and all descendant child cells, concatenated
     */
    public String fullText(String delimiter) {
        StringBuilder sb = new StringBuilder();
        fullText(sb, delimiter);
        return sb.toString();
    }

    private void fullText(StringBuilder sb, String delimiter) {
        if (text != null && !text.isEmpty()) {
            sb.append(text).append(delimiter);
        }
        if (child != null) {
            child.fullText(sb, delimiter);
        }
    }

    /**
     * Returns true if the text of this and any child cells is null, empty or
     * entirely whitespace.
     *
     * @return True if the text is effectively empty
     */
    public boolean isEmpty() {
        boolean result = text.trim().isEmpty();
        if (child != null) {
            result |= child.isEmpty();
        }
        return result;
    }

    /**
     * Paint this cell and its children.
     *
     * @param g A graphics
     * @param x The starting x coordinate
     * @param y The starting y coordinate
     * @param maxX The maximum x coordinate
     * @param maxY The maximum y coordinate
     * @param painted A rectangle to configure with the bounds modified by
     * painting
     * @return The text baseline in the y axis
     */
    public float paint(Graphics2D g, float x, float y, float maxX, float maxY, Rectangle2D.Float painted) {
        return paint(g, x, y, maxX, maxY, -1, painted);
    }

    public TextCell shapeOutlinePainted() {
        shapeOutlinePainted = true;
        return this;
    }

    private float baseline(float y, Graphics2D g, float lastBaseline) {
        Font f = _font(font == null ? g.getFont() : font);
        FontMetrics fm = g.getFontMetrics(f);
        float result = Math.max(lastBaseline, y + fm.getAscent()) + padding;
        if (child != null) {
            result = Math.max(result + topMargin, child.baseline(y, g, result));
        } else {
            result += topMargin;
        }
        return result;
    }

    String textAt(Point point, float x, float y, float maxX, float maxY, Font baseFont, Function<Font, FontMetrics> mx) {
        if (point.x < x || point.y < y || point.x > maxX || point.y > maxY) {
            return null;
        }
        if (text == null || text.isEmpty()) {
            if (child != null) {
                return child.textAt(point, x, y, maxX, maxY, baseFont, mx);
            }
            return null;
        }
        Font f = _font(font == null ? baseFont : font);
        FontMetrics fm = mx.apply(f);
        int w = fm.stringWidth(text);
        int h = fm.getHeight() + fm.getDescent();
        float textX = x + leftMargin + indent + padding;
        float textMaxX = Math.min(maxX, textX + w + padding + rightMargin);
        if (point.x >= textX && point.x <= textMaxX) {
            return text;
        }
        if (child != null) {
            return child.textAt(point, textMaxX, y, maxX, maxY, baseFont, mx);
        }
        return null;
    }

    private float paint(Graphics2D g, float x, float y, float maxX, float maxY, float baseline, Rectangle2D.Float painted) {
//        System.out.println("Paint cell '" + text + "' at " + x + ", " + y + " maxes " + maxX + "," + maxY);
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
        int h = fm.getAscent() + fm.getDescent();
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
                shapeW = Math.min(rightMargin + leftMargin + w + (padding * 2) + indent, maxX - x);
            } else {
                shapeW = Math.max(rightMargin + leftMargin + w + (padding * 2) + indent, maxX - x);
            }
            float shapeHeight = Math.min(y + h + (padding * 2), maxY - y) - y;
            float shapeY = y + baselineAdjust + topMargin;
            if (shapeY + shapeHeight < y + h) {
                shapeY += h - shapeHeight;
            }
            shape.setFrame(x + leftMargin, shapeY, shapeW, shapeHeight);
            if (!isChild) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            Stroke oldStroke = g.getStroke();
            g.setStroke(STROKE);
            if (shapeOutlinePainted) {
                g.setPaint(foreground);
            } else {
                g.setPaint(background);
            }
            g.draw(shape);
            if (shapeOutlinePainted) {
                g.setPaint(background);
            }
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
                painted.setFrame(x, y, w + leftMargin + indent + (padding * 2), h + (padding * 2));
            } else {
                RECT.setFrame(x, y, w + leftMargin + indent + (padding * 2), h + (padding * 2));
                painted.add(RECT);
            }
        }
        float textY = baseline;
        if (foreground != null) {
            g.setPaint(foreground);
        } else {
            g.setPaint(oldPaint);
        }
        float textX = x + leftMargin + indent + padding;
        g.drawString(text, textX, textY);
        if (strikethrough) {
            LineMetrics lm = fm.getLineMetrics(text, g);
            float lineY = textY + lm.getStrikethroughOffset() + topMargin;
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
            child.paint(g, x + w + leftMargin + indent + (padding * 2) + rightMargin, y, maxX, maxY, baseline, painted);
        }
        return baseline;
    }

    @Override
    public String toString() {
        return "TextCell{" + "foreground=" + foreground + ", background=" + background + ", font="
                + font + ", text=" + text + ", bgShape=" + bgShape + ", isChild=" + isChild + ", indent="
                + indent + ", margin=" + leftMargin + ", padding=" + padding + ", bold=" + bold + ", italic="
                + italic + ", stretch=" + stretch + ", child=" + child + '}';
    }
}

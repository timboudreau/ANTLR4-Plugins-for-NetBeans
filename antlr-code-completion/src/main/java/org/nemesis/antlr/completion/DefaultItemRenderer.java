package org.nemesis.antlr.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.function.BiFunction;
import org.openide.awt.HtmlRenderer;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultItemRenderer<I> implements ItemRenderer<I>, Stringifier<I> {

    static DefaultItemRenderer<Object> INSTANCE = new DefaultItemRenderer<>();
    private BiFunction<? super StringKind, ? super I, ? extends String> stringifier;

    DefaultItemRenderer() {
        this(null);
    }

    DefaultItemRenderer(BiFunction<? super StringKind, ? super I, ? extends String> stringifier) {
        this.stringifier = stringifier == null ? this : stringifier;
    }

    @Override
    public int getPreferredWidth(I item, Graphics g, Font defaultFont) {
        String name = stringifier.apply(StringKind.DISPLAY_NAME, item);
        String desc = stringifier.apply(StringKind.DISPLAY_DIFFERENTIATOR, item);
        String text = name + (desc != null ? " " + desc : "");
        double width = HtmlRenderer.renderString(text, g, 5, 0, 2000, 200, defaultFont, Color.BLACK, HtmlRenderer.STYLE_CLIP, false) + 10;
        return (int) Math.ceil(width);
    }

    static int avg(Color col) {
        return (col.getRed() + col.getGreen() + col.getBlue()) / 3;
    }

    private static String toHex(int val) {
        String result = Integer.toHexString(val);
        if (result.length() == 1) {
            result = "0" + result;
        }
        return result;
    }

    private static String htmlColor(Color c) {
        return "#" + toHex(c.getRed()) + toHex(c.getGreen()) + toHex(c.getBlue());
    }

    private static int[] lastColors = new int[2];
    private static String lastString;

    private static String dim(Color fg, Color bg) {
        if (lastString != null && fg.getRGB() == lastColors[0] && bg.getRGB() == lastColors[1]) {
            return lastString;
        }
        // could compute brightness instead - this will show an
        // equal average for, say, full saturation red on blue
        int avgFg = avg(fg);
        int avgBg = avg(bg);
        lastColors[0] = fg.getRGB();
        lastColors[1] = bg.getRGB();
        if (avgFg > avgBg) {
            return lastString = htmlColor(fg.darker());
        } else {
            return lastString = htmlColor(fg.brighter());
        }
    }

    private static String toHtml(String name, String desc, Color defaultColor, Color backgroundColor) {
        if (desc != null && !desc.isEmpty()) {
            return name + "<font color=\"" + dim(defaultColor, backgroundColor) + "\"> "
                    + desc;
        }
        return name;
    }

    @Override
    public void render(I item, Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
        String name = stringifier.apply(StringKind.DISPLAY_NAME, item);
        String desc = stringifier.apply(StringKind.DISPLAY_DIFFERENTIATOR, item);
        Color color = g.getColor();
        int baseline = g.getFontMetrics(defaultFont).getMaxAscent();
        HtmlRenderer.renderHTML(toHtml(name, desc, defaultColor, backgroundColor), g, 5, baseline, width, height, defaultFont, color, HtmlRenderer.STYLE_TRUNCATE, true);
    }

    @Override
    public String apply(StringKind kind, I item) {
        switch (kind) {
            case DISPLAY_NAME:
                return item.toString();
            default:
                return null;
        }
    }

    public String toString() {
        if (this == INSTANCE) {
            return getClass().getSimpleName() + ".INSTANCE";
        }
        return getClass().getSimpleName() + "{" + stringifier + "}";
    }

}

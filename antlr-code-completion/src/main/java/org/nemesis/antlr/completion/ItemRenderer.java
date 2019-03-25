package org.nemesis.antlr.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

/**
 *
 * @author Tim Boudreau
 */
public interface ItemRenderer<I> {

    public int getPreferredWidth(I item, Graphics g, Font defaultFont);

    public void render(I item, Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected);

}

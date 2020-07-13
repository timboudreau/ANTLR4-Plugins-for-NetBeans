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
package org.nemesis.antlr.completion.grammar;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.function.Consumer;
import org.nemesis.swing.cell.TextCell;

/**
 *
 * @author Tim Boudreau
 */
public class CellItemRenderer {

    private final TextCell cell;
    private final Rectangle2D.Float bds = new Rectangle2D.Float();
    private static final CellItemRenderer INSTANCE = new CellItemRenderer();

    CellItemRenderer() {
        this.cell = new TextCell("");
    }

    TextCell cell() {
        return cell.reset();
    }
    
    static CellItemRenderer borrow(Consumer<TextCell> cell) {
        boolean borrowed = INSTANCE._borrow(cell);
        if (!borrowed) {
            CellItemRenderer nue = new CellItemRenderer();
            cell.accept(nue.cell());
            return nue;
        }
        return INSTANCE;
    }

    private volatile boolean inUse;

    private boolean _borrow(Consumer<TextCell> c) {
        boolean used = inUse;
        if (used) {
            return false;
        }
        inUse = true;
        try {
            c.accept(cell());
        } finally {
            inUse = false;
        }
        return true;
    }

    public int getPreferredWidth(Graphics g, Font defaultFont) {
        cell.withFont(defaultFont).bounds(defaultFont, bds, 0, 0, g::getFontMetrics);
        return (int) Math.ceil(bds.width);
    }

    public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
        g.setColor(backgroundColor);
        g.fillRect(0, 0, width, height);
        g.setFont(defaultFont);
        g.setColor(defaultColor);
        cell.paint((Graphics2D) g, 0, 0, width, height, bds);
    }
}

/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package org.nemesis.antlr.live.language.editoractions;

import java.awt.Component;
import java.awt.Point;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * A popup menu subclass that exposes the last popup location for
 * actions to locate the caret token.
 */
final class PopupPositionExposingPopupMenu extends JPopupMenu {

    private Component lastComponent;
    private int lastX = -1;
    private int lastY = -1;

    public PopupPositionExposingPopupMenu() {
    }

    public PopupPositionExposingPopupMenu(String label) {
        super(label);
    }

    public Point lastPopupPosition(JTextComponent inCoordinateSpaceOf) {
        if (lastX < 0 || lastY < 0 || lastComponent == null) {
            return null;
        }
        int x = lastX;
        int y = lastY;
        lastX = -1;
        lastY = -1;
        lastComponent = null;
        if (lastComponent == inCoordinateSpaceOf || lastComponent == null) {
            return new Point(x, y);
        } else {
            Point p = new Point(x, y);
            return SwingUtilities.convertPoint(lastComponent, p, inCoordinateSpaceOf);
        }
    }

    @Override
    public void show(Component invoker, int x, int y) {
        lastX = x;
        lastY = y;
        lastComponent = invoker;
        super.show(invoker, x, y);
    }

}

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
package org.nemesis.antlrformatting.impl;

import java.awt.Rectangle;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import com.mastfrog.function.throwing.ThrowingConsumer;

/**
 *
 * @author Tim Boudreau
 */
public final class CaretInfo {

    public static CaretInfo NONE = new CaretInfo();
    private final int start;
    private final int end;

    public CaretInfo(int start, int end) {
        assert start >= 0;
        assert end >= 0;
        this.start = start;
        this.end = end;
    }

    public CaretInfo(int pos) {
        this.start = this.end = pos;
    }

    private CaretInfo() {
        this.start = -1;
        this.end = -1;
    }

    public static CaretInfo create(JTextComponent comp) {
        int selStart = comp.getSelectionStart();
        int selEnd = comp.getSelectionEnd();
        int caretPos = comp.getCaretPosition();
        return selStart == selEnd
                ? new CaretInfo(caretPos)
                : new CaretInfo(Math.min(selStart, selEnd),
                        Math.max(selStart, selEnd));
    }

    public boolean isViable() {
        return start >= 0 && end >= 0;
    }

    public void ifViable(ThrowingConsumer<CaretInfo> c) throws Exception {
        if (isViable()) {
            c.accept(this);
        }
    }

    public CaretInfo withEnd(int end) {
        return new CaretInfo(start, end);
    }

    public CaretInfo withLength(int offset) {
        if (offset < 0) {
            return new CaretInfo(Math.max(0, start - offset), start);
        }
        return new CaretInfo(start, start + offset);
    }

    public Rectangle bounds(JTextComponent comp) throws BadLocationException {
        if (!isViable()) {
            return new Rectangle(0, 0);
        }
//        Rectangle result = comp.modelToView2D(start).getBounds(); // JDK 9/10
        Rectangle result = comp.modelToView(start);

        if (isSelection()) {
//            result.add(comp.modelToView2D(end).getBounds()); // JDK 9/10
            result.add(comp.modelToView(end));
        }
        return result;
    }

    public void apply(JTextComponent comp) throws BadLocationException {
        if (!isViable()) {
            return;
        }
        // -1 because all documents terminate with a \n, even empty ones
        // according to swing
        int len = comp.getDocument().getLength() - 1;
        if (isSelection()) {
            comp.setSelectionStart(Math.max(0, Math.min(start, len)));
            comp.setSelectionEnd(Math.max(0, Math.min(end, len)));
        } else {
            comp.setCaretPosition(start);
        }
    }

    public boolean isSelection() {
        return start != end;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    @Override
    public String toString() {
        return start == end
                ? Integer.toString(start)
                : start + ":" + end;
    }
}

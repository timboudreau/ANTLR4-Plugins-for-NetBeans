package org.nemesis.antlrformatting.impl;

import java.awt.Rectangle;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.nemesis.misc.utils.function.ThrowingConsumer;

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
        return start > 0 && end > 0;
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
        Rectangle result = comp.modelToView2D(start).getBounds();

        if (isSelection()) {
            result.add(comp.modelToView2D(end).getBounds());
        }
        return result;
    }

    public void apply(JTextComponent comp) throws BadLocationException {
        if (!isViable()) {
            System.out.println("NOT APPLYING " + this + " to " + comp.getDocument());
            return;
        }
        // -1 because all documents terminate with a \n, even empty ones
        // according to swing
        int len = comp.getDocument().getLength() - 1;
        if (isSelection()) {
            System.out.println("APPLY SELECTION " + this);
            comp.setSelectionStart(Math.max(0, Math.min(start, len)));
            comp.setSelectionEnd(Math.max(0, Math.min(end, len)));
        } else {
            System.out.println("APPLY CARET POSITION " + this);
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

package org.nemesis.antlrformatting.impl;

import java.util.function.Supplier;
import javax.swing.text.Document;
import org.netbeans.modules.editor.indent.spi.Context;

/**
 * A holder that can be updated with a revised caret position based on how the
 * document has changed.
 *
 * @author Tim Boudreau
 */
public abstract class CaretFixer implements Supplier<CaretInfo> {

    CaretFixer() {

    }

    public static CaretFixer forContext(Context ctx) {
        Document doc = ctx.document();
        int start = ctx.startOffset();
        int end = ctx.endOffset();
        int len = doc.getLength();
        if (start == 0 && (end == len - 1 || end == len)) {
            return new WholeDocumentCaretFixer(start);
        }
        return new SelectionCaretFixer();
    }

    public abstract void updateStart(int start);

    public void updateLength(int length) {
        // do nothing
    }

    private static final class WholeDocumentCaretFixer extends CaretFixer {

        private CaretInfo caret;

        WholeDocumentCaretFixer(int caretPos) {
            caret = new CaretInfo(caretPos);
        }

        @Override
        public synchronized CaretInfo get() {
            System.out.println("WholeDocumentCaretFixer get() returning " + caret);
            return caret;
        }

        public synchronized void updateStart(int start) {
            caret = new CaretInfo(start);
            System.out.println("WholeDocumentCaretFixer updateStart " + start + " info now " + caret);
        }
    }

    private static final class SelectionCaretFixer extends CaretFixer {

        private CaretInfo caret = CaretInfo.NONE;

        @Override
        public synchronized CaretInfo get() {
            System.out.println("SelectionCaretFixer get() returning " + caret);
            return caret;
        }

        @Override
        public synchronized void updateStart(int start) {
            caret = new CaretInfo(start);
            System.out.println("SelectionCaretFixer updateStart " + start + " info now " + caret);
        }

        @Override
        public synchronized void updateLength(int length) {
            if (caret.isViable()) {
                caret = caret.withLength(length);
                System.out.println("SelectionCaretFixer updateLength " + length + " info now " + caret);
            } else {
                throw new IllegalStateException("updateLength() "
                        + "called but updateStart has not been");
            }
        }
    }
}

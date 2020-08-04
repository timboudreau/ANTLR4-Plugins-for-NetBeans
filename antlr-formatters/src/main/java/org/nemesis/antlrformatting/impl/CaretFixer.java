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

import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.editor.ops.DocumentOperator;
import org.netbeans.modules.editor.indent.spi.Context;
import org.openide.util.Exceptions;

/**
 * A holder that can be updated with a revised caret position based on how the
 * document has changed.
 *
 * @author Tim Boudreau
 */
public abstract class CaretFixer implements Supplier<CaretInfo> {

    CaretFixer() {

    }

    public static CaretFixer none() {
        return new CaretFixer() {
            @Override
            public void updateStart(int start) {

            }

            @Override
            public CaretInfo get() {
                return CaretInfo.NONE;
            }
        };
    }

    public static CaretFixer forContext(Context ctx) {
        Document doc = ctx.document();
        int start = ctx.startOffset();
        int end = ctx.endOffset();
        int len = doc.getLength();
        Caret caret = null;
        JTextComponent comp = DocumentOperator.findComponent(doc);
        if (comp != null) {
            caret = comp.getCaret();
            // It seems that frequently the Context instance arrives with zero
            // as the caret position for no discernable reason
            if (ctx.caretOffset() != caret.getDot() && ctx.caretOffset() != caret.getMark()) {
                try {
                    ctx.setCaretOffset(caret.getDot());
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        if (start == 0 && (end == len - 1 || end == len)) {
            if (caret != null) {
                return new WholeDocumentCaretFixer(CaretInfo.create(caret));
            }
            return new WholeDocumentCaretFixer(ctx.caretOffset());
        }
        if (caret != null) {
            return new SelectionCaretFixer(CaretInfo.create(caret));
        }
        return new SelectionCaretFixer(new CaretInfo(ctx.caretOffset()));
    }

    public abstract void updateStart(int start);

    public void updateLength(int length) {
        // do nothing
    }

    private static final class WholeDocumentCaretFixer extends CaretFixer {

        private CaretInfo caret;

        WholeDocumentCaretFixer(CaretInfo info) {
            this.caret = info;
        }
        WholeDocumentCaretFixer(int caretPos) {
            caret = new CaretInfo(caretPos);
        }

        @Override
        public synchronized CaretInfo get() {
            return caret;
        }

        public synchronized void updateStart(int start) {
            int old = caret == null ? -1 : caret.start();
            caret = new CaretInfo(start);
            if (start == 0 && old > start) {
                new Exception("Probably incorrect caret detection " + old + " -> " + start)
                        .printStackTrace(System.out);
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " with " + caret + " viable ? "
                    + (caret != null ? caret.isViable() : "n/a");
        }
    }

    private static final class SelectionCaretFixer extends CaretFixer {

        private CaretInfo caret = CaretInfo.NONE;

        SelectionCaretFixer() {

        }

        SelectionCaretFixer(CaretInfo info) {
            this.caret = info;
        }

        @Override
        public synchronized CaretInfo get() {
            return caret;
        }

        @Override
        public synchronized void updateStart(int start) {
            caret = new CaretInfo(start);
        }

        @Override
        public synchronized void updateLength(int length) {
            if (caret.isViable()) {
                caret = caret.withLength(length);
            } else {
                throw new IllegalStateException("updateLength() "
                        + "called but updateStart has not been");
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " with " + caret + " viable ? "
                    + (caret != null ? caret.isViable() : "n/a");
        }
    }
}

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
            int old = caret == null ? -1 : caret.start();
            caret = new CaretInfo(start);
            System.out.println("\n\n*********\nWholeDocumentCaretFixer updateStart " + start + " info now " + caret + "\n");
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

        @Override
        public String toString() {
            return getClass().getSimpleName() + " with " + caret + " viable ? "
                    + (caret != null ? caret.isViable() : "n/a");
        }
    }
}

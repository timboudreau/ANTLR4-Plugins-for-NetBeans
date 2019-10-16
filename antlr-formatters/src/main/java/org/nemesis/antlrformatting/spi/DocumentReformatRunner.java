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
package org.nemesis.antlrformatting.spi;

import java.util.Arrays;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlrformatting.api.FormattingResult;
import org.nemesis.antlrformatting.impl.CaretFixer;
import org.nemesis.antlrformatting.impl.CaretInfo;
import org.nemesis.antlrformatting.impl.FormattingAccessor;
import com.mastfrog.function.throwing.ThrowingRunnable;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.netbeans.modules.editor.indent.spi.Context;
import org.netbeans.spi.lexer.MutableTextInput;
import org.openide.util.Parameters;

/**
 * Does the actual heavy lifting of mating the reformatting API to NetBeans
 * context/documents, rewriting documents, etc.
 *
 * @author Tim Boudreau
 */
final class DocumentReformatRunner<C, StateEnum extends Enum<StateEnum>> {

    private final AntlrFormatterProvider<C, StateEnum> prov;

    DocumentReformatRunner(AntlrFormatterProvider<C, StateEnum> prov) {
        this.prov = prov;
    }

    private FormattingResult populateAndRunReformat(Lexer lexer, int start, int end, C config, CaretInfo caret, CaretFixer newCaret, RuleNode ruleNode) {
        AntlrFormatterProvider.RulesAndState rs = prov.populate(config);
        String[] modeNames = prov.modeNames();
        if (modeNames == null || modeNames.length == 0) {
            throw new IllegalStateException(prov + " does not correctly implement modeNames() - got " + arrToString(modeNames));
        }
        return FormattingAccessor.getDefault().reformat(start, end,
                prov.indentSize(config), rs.rules, rs.state,
                prov._whitespace(), prov.debugLogPredicate(),
                lexer, modeNames, caret, newCaret, ruleNode);
    }

    /**
     * Perform one reformatting operation.
     *
     * @param cntxt The editing context
     */
    void reformat(Context cntxt) {
        // Ask the provider to create a configuration object for us - outside of
        // unit tests or custom implementation, it will be the preferences returned
        // by CodeStylePreferences
        C config = prov.configuration(cntxt);
        Document document = (Document) cntxt.document();
        int start = cntxt.startOffset();
        int end = cntxt.endOffset();
        // Find the editor (may be null in case of a batch reformat - all
        // code that takes it checks that)
        JTextComponent comp = EditorRegistry.findComponent(document);
        // An IntConsumer we can pass into FormattingContextImpl, which will set
        // the offset of the original caret position in the new document
        CaretFixer caretFixer = CaretFixer.forContext(cntxt);
        EditorScrollPositionManager scrollHandler = new EditorScrollPositionManager(comp, caretFixer);
        try {
            scrollHandler.invokeWithEditorDisabled((currentCursorPos) -> {
                withDocumentLockedandInputDisabledAndReformatLock(document, () -> {
                    try {
                        Lexer lexer = prov.createLexer(document);
                        RuleNode ruleNode = prov.parseAndExtractRootRuleNode(lexer);
                        if (ruleNode != null) {
                            lexer = prov.createLexer(document);
                        }
                        lexer.removeErrorListeners();
                        FormattingResult reformatted = populateAndRunReformat(lexer, start, end, config, currentCursorPos, caretFixer, ruleNode);
//                        System.out.println("GOT RESULT " + reformatted);
                        boolean updated = replaceTextInDocument(document, reformatted);
                        if (updated) {
                            scrollHandler.addCaretPositionUndoableEdit();
                        }
                    } catch (Throwable t) {
                        t.printStackTrace(System.out);
                    }
                });
            });
        } catch (Throwable e) {
            AntlrFormatterProvider.LOGGER.log(Level.SEVERE, "Exception replacing text", e);
        }
    }

    private static void withMutableTextInputDisabled(Document document, ThrowingRunnable run) throws Exception {
        MutableTextInput<?> mti = (MutableTextInput<?>) document.getProperty(MutableTextInput.class);
        if (mti != null) {
            mti.tokenHierarchyControl().setActive(false);
        }
        try {
            run.run();
        } finally {
            if (mti != null) {
                mti.tokenHierarchyControl().setActive(true);
            }
        }
    }

    private static void withReformatLock(Document doc, ThrowingRunnable run) throws Exception {
        // Not sure this does anything other than discover that we return null from
        // ExtraLock on our task and return
        Reformat r = Reformat.get(doc);
        r.lock();
        try {
            run.run();
        } finally {
            r.unlock();
        }
    }

    /**
     * Does *all* the locking needed to perform a reformat
     *
     * @param document The document
     * @param run The thing to run once locks are acquired
     * @throws Exception If something goes wrong
     */
    private static void withDocumentLockedandInputDisabledAndReformatLock(Document document, ThrowingRunnable run) throws Exception {
        withReformatLock(document, () -> {
            withDocumentLock(document, () -> {
                withMutableTextInputDisabled(document, () -> {
                    run.run();
                });
            });
        });
    }

    private static void withDocumentLock(Document doc, ThrowingRunnable run) throws Exception {
        // In theory, it will always be a BaseDocument
        if (doc instanceof BaseDocument) {
            ((BaseDocument) doc).runAtomic(tryCatch(run));
        } else {
            doc.render(tryCatch(run));
        }
    }

    private static boolean replaceTextInDocument(Document doc, FormattingResult result) throws BadLocationException {
        Parameters.notNull("doc", doc);
        Parameters.notNull("result", result);

        String replacement = result.text();
//        System.out.println("\n\nFORMAT RESULT TEXT LENGTH " + replacement.length());
//        System.out.println("START " + result.startOffset());
//        System.out.println("END " + result.endOffset());
        if (result.isEmpty()) {
//            System.out.println("result is empty, give up");
            return false;
        }

        int docStart = doc.getStartPosition().getOffset();
        int docEnd = doc.getEndPosition().getOffset();

        int start = result.startOffset();
        int end = result.endOffset();

//        System.out.println("DOCSTART " + docStart + " DOCEND " + docEnd + " reformat " + start
//                + ":" + end);

        if (start > docStart || end < docEnd - 1) {
            // Working over a selection
            String text = doc.getText(start, end - start);
//            System.out.println("\n\nREPLACE TEXT");
//            System.out.println(text);
//            System.out.println("\nWITH");
//            System.out.println(replacement);
            if (!replacement.equals(text)) {
                if (doc instanceof BaseDocument) {
                    ((BaseDocument) doc).replace(start, end - start, replacement, null);
                } else {
                    doc.remove(start, end - start);
                    doc.insertString(start, replacement, null);
                }
                return true;
            }
        } else {
            String text = doc.getText(docStart, docEnd);
            if (!text.equals(replacement)) {
                if (doc instanceof BaseDocument) {
                    ((BaseDocument) doc).replace(start, end, replacement, null);
                } else {
                    doc.remove(start, end - start);
                    doc.insertString(start, replacement, null);
                }
                return true;
            }
        }
        return false;
    }

    static String arrToString(String[] arr) {
        if (arr == null) {
            return "null";
        }
        return Arrays.toString(arr);
    }

    static boolean tryCatchRun(ThrowingRunnable r) {
        FailableRunnable result = tryCatch(r);
        result.run();
        return !result.failed();
    }

    static FailableRunnable tryCatch(ThrowingRunnable r) {
        return new FailableRunnable(r);
    }

    // We call a bunch of things on the EDT or in document locks which take
    // runnables that can fail, and in most cases there is nothing to do
    // about it (we're calling foreign code that could do anything) - so this
    // just logs it, and eliminates mountains of nested try-catches which would
    // all just log the exception anyway
    static class FailableRunnable implements Runnable {

        private final ThrowingRunnable run;
        private Throwable failure;

        FailableRunnable(ThrowingRunnable run) {
            this.run = run;
        }

        public boolean failed() {
            return failure() != null;
        }

        public synchronized Throwable failure() {
            return failure;
        }

        @Override
        public String toString() {
            return "FailableRunnable{" + run + "}";
        }

        @Override
        public void run() {
            try {
                run.run();
            } catch (Throwable ex) {
                AntlrFormatterProvider.LOGGER.log(Level.SEVERE, "Exception processing " + run, ex);
                ex.printStackTrace(System.out);
                synchronized (this) {
                    failure = ex;
                }
                if (ex instanceof Error) {
                    throw ((Error) ex);
                } else if (ex instanceof ThreadDeath) {
                    throw ((ThreadDeath) ex);
                }
            }
        }
    }
}

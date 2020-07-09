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
import java.util.logging.Logger;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.utils.CaretInformation;
import org.nemesis.editor.utils.DocumentOperationContext;
import org.nemesis.editor.utils.DocumentOperator;
import org.nemesis.editor.utils.DocumentPreAndPostProcessor;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.netbeans.modules.editor.indent.spi.Context;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Parameters;

/**
 * Does the actual heavy lifting of mating the reformatting API to NetBeans
 * context/documents, rewriting documents, etc.
 *
 * @author Tim Boudreau
 */
final class DocumentReformatRunner<C, StateEnum extends Enum<StateEnum>> {

    private static final Logger LOG = Logger.getLogger(DocumentReformatRunner.class.getName());
    private final AntlrFormatterProvider<C, StateEnum> prov;

    DocumentReformatRunner(AntlrFormatterProvider<C, StateEnum> prov) {
        this.prov = prov;
    }

    private FormattingResult populateAndRunReformat(Lexer lexer, int start, int end, C config, CaretInfo caret, CaretFixer newCaret, RuleNode ruleNode) {
        AntlrFormatterProvider.RulesAndState rs = prov.populate(config);
        String[] modeNames = prov.modeNames();
        if (modeNames == null || modeNames.length == 0) {
            throw new IllegalStateException(prov + " does not correctly implement modeNames()");
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
    @Messages({"reformatFailed=Reformat failed", "reformatSucceeded=Reformatted"})
    void reformat(Context cntxt) {
        try {
            C config = prov.configuration(cntxt);
            int start = cntxt.startOffset();
            int end = cntxt.endOffset();
            CaretFixer fixer = CaretFixer.forContext(cntxt);
            StyledDocument document = (StyledDocument) cntxt.document();
            Boolean reformatSuccess = DocumentOperator.builder()
                    .restoringCaretPosition((CaretInformation caret, JTextComponent comp, Document doc) -> {
                        return ibc -> {
                            CaretInfo ifo = fixer.get();
                            LOG.log(Level.FINER, "Restore caret position to {0}", ifo);
                            ibc.accept(ifo.start(), ifo.end());
                        };
                    })
                    .add(ReformatLocker::new)
                    .acquireAWTTreeLock()
                    .disableTokenHierarchyUpdates()
                    .blockIntermediateRepaints()
                    .lockAtomic()
                    .writeLock()
                    .singleUndoTransaction()
                    .build().<Boolean, RuntimeException>operateOn(document)
                    .operate((DocumentOperationContext ctx) -> {
                        Lexer lexer = prov.createLexer(document);
                        RuleNode ruleNode = prov.parseAndExtractRootRuleNode(lexer);
                        if (ruleNode != null) {
                            lexer = prov.createLexer(document);
                        }
                        lexer.removeErrorListeners();
                        FormattingResult reformatted = populateAndRunReformat(lexer, start, end, config,
                                fixer.get(), fixer, ruleNode);
                        boolean result = replaceTextInDocument(document, reformatted);
                        if (result) {
                            CaretInfo ci2 = fixer.get();
                            cntxt.setCaretOffset(ci2.start());
                        }
                        return result;
                    });
            if (reformatSuccess == null || !reformatSuccess) {
//                StatusDisplayer.getDefault().setStatusText(Bundle.reformatFailed());
            } else {
//                StatusDisplayer.getDefault().setStatusText(Bundle.reformatSucceeded());
            }
        } catch (Exception ex) {
            LOG.log(Level.INFO, "Exception reformatting " + cntxt.document(), ex);
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
            String text = doc.getText(start, end - start);
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

    static class ReformatLocker implements DocumentPreAndPostProcessor {

        private final Reformat reformat;

        ReformatLocker(StyledDocument doc) {
            this.reformat = Reformat.get(doc);
        }

        @Override
        public void before(DocumentOperationContext ctx) throws BadLocationException {
            reformat.lock();
        }

        @Override
        public void after(DocumentOperationContext ctx) throws BadLocationException {
            reformat.unlock();
        }

        public String toString() {
            return "REFORMAT-LOCK";
        }
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.actions;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElement;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseAction;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({"next-usage=Next Usage", "no-rule=No Rule Under Caret"})
@EditorActionRegistration(
        name = "next-usage",
        weight = Integer.MAX_VALUE,
        category = "Editing",
        popupText = "Next Usage",
        menuPath = "Source",
        menuText = "Next Usage",
        mimeType = "text/g-4")
public class NextUsageAction extends BaseAction {

    private int direction;
    public NextUsageAction(boolean forwards) {
        super(SAVE_POSITION | SELECTION_REMOVE);
        this.direction = forwards ? 1 : -1;
    }

    public NextUsageAction() {
        this(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae, final JTextComponent jtc) {
        final Document doc = jtc.getDocument();
        final int caret = jtc.getSelectionStart();
        try {
            ParserManager.parseWhenScanFinished(Collections.singleton(Source.create(doc)), new UserTask() {
                @Override
                public void run(ResultIterator ri) throws Exception {
                    Parser.Result res = ri.getParserResult();
                    if (res instanceof NBANTLRv4Parser.ANTLRv4ParserResult) {
                        NBANTLRv4Parser.ANTLRv4ParserResult pr = (NBANTLRv4Parser.ANTLRv4ParserResult) res;
                        ANTLRv4SemanticParser semantics = pr.semanticParser();
                        RuleElement el = semantics.ruleElementAtPosition(caret);
                        if (el == null) {
                            StatusDisplayer.getDefault().setStatusText(
                                    NbBundle.getMessage(NextUsageAction.class, "no-rule"));
                            return;
                        }
                        moveCaretToNext(el, doc, caret, jtc, semantics);
                    }
                }
            });
        } catch (ParseException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private void moveCaretToNext(RuleElement el, Document doc, int caret, JTextComponent jtc, ANTLRv4SemanticParser sem) {
        List<RuleElement> all = sem.allReferencesTo(el);
        if (all.size() < 2) {
            return;
        }
        int ix = all.indexOf(el);
        if (ix >= 0) {
            int nextIndex = ix + direction;
            if (nextIndex >= all.size()) {
                nextIndex = 0;
            } else if (nextIndex < 0) {
                nextIndex = all.size() -1;
            }
            RuleElement next = all.get(nextIndex); // is presorted
            EventQueue.invokeLater(new CaretShifter(jtc, caret, next.getStartOffset()));
        }
    }

    static final class CaretShifter implements Runnable {
        private final JTextComponent comp;
        private final int expectedCaretPosition;
        private final int targetCaretPosition;

        public CaretShifter(JTextComponent comp, int expectedCaretPosition, int targetCaretPosition) {
            this.comp = comp;
            this.expectedCaretPosition = expectedCaretPosition;
            this.targetCaretPosition = targetCaretPosition;
        }

        @Override
        public void run() {
            assert EventQueue.isDispatchThread();
            if (!comp.isShowing()) { // may have changed
                return;
            }
            if (expectedCaretPosition == comp.getSelectionStart()) {
                int length = comp.getDocument().getLength();
                if (length < targetCaretPosition) { // user may have moved caret
                    comp.setSelectionStart(targetCaretPosition);
                }
            }
        }

    }
}

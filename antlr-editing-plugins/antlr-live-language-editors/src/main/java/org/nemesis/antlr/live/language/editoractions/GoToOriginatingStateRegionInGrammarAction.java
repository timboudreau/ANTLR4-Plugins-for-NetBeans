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

import com.mastfrog.range.Range;
import com.mastfrog.util.collections.CollectionUtils;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.compilation.AntlrGenerationAndCompilationResult;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.language.AdhocEditorKit;
import org.nemesis.antlr.live.language.AdhocLanguageHierarchy;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.editor.function.DocumentSupplier;
import org.nemesis.editor.ops.DocumentOperator;
import static org.nemesis.editor.util.EditorSelectionUtils.navigateTo;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 * Navigates to the grammar region that was used to parse the token the
 * popup was invoked over or the at the caret position in the case
 * of keyboard invocation.
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"goToReason=Originating Location in Grammar"})
public final class GoToOriginatingStateRegionInGrammarAction extends AbstractAction implements DocumentSupplier<Segment, RuntimeException> {

    private final JTextComponent comp;
    private final PopupPositionExposingPopupMenu pop;
    private final String mimeType;

    public GoToOriginatingStateRegionInGrammarAction(JTextComponent comp, PopupPositionExposingPopupMenu pop, String mimeType) {
        super(Bundle.goToReason());
        this.comp = comp;
        this.pop = pop;
        this.mimeType = mimeType;
        boolean isLexer = Boolean.TRUE.equals(comp.getDocument().getProperty("isLexer"));
        setEnabled(!isLexer);
    }

    @Override
    @SuppressWarnings(value = "deprecation")
    public void actionPerformed(ActionEvent e) {
        Point popupPoint = pop.lastPopupPosition(comp);
        int position;
        if (popupPoint != null) {
            position = comp.getUI().viewToModel(comp, popupPoint);
        } else {
            position = comp.getSelectionStart();
        }
        setEnabled(false);
        Cursor oldCursor = comp.getCursor();
        Cursor setCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
        comp.setCursor(setCursor);
        try {
            Segment text = DocumentOperator.render(comp.getDocument(), this);
            AdhocEditorKit.popupPool().submit(new Go(position, oldCursor, setCursor, text));
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public Segment get() throws RuntimeException, BadLocationException {
        Segment result = new Segment();
        comp.getDocument().getText(0, comp.getDocument().getLength(), result);
        return result;
    }

    @NbBundle.Messages(value = {"# {0} - mimeType", "noParserFor=Could not find an embedded parser for {0}", "parseHadErrors=File parsed with errors; cannot find trigger rule.", "# {0} - position", "noElementAt=No parse tree found at position {0}"})
    private String performNavigation(int position, Segment text) {
        EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mimeType);
        if (parser != null) {
            try {
                return doNavigation(parser, position, text);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
                return ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
        } else {
            return Bundle.noParserFor(mimeType);
        }
    }

    private String doNavigation(EmbeddedAntlrParser parser, int position, Segment text) throws Exception {
        EmbeddedAntlrParserResult res = parser.parse(text);
        if (res.isUsable()) {
            AntlrProxies.ParseTreeProxy proxy = res.proxy();
            if (proxy != null && !proxy.isUnparsed()) {
                GrammarRunResult<?> runResult = res.runResult();
                if (runResult != null) {
                    AntlrGenerationAndCompilationResult genResult = runResult.genResult();
                    if (genResult != null) {
                        AntlrGenerationResult generationResult = genResult.generationResult();
                        if (generationResult != null && generationResult.mainGrammar != null) {
                            return doNavigation(position, proxy, generationResult.mainGrammar);
                        }
                    }
                }
            }
        }
        return Bundle.parseHadErrors();
    }

    @NbBundle.Messages(value = {"# {0} - token", "# {1} - position", "noRuleElementToken=Could not find a rule element containing {0} at {1}", "# {0} - state", "# {1} - token", "# {2} - position", "# {3} - tokenTypeName", "noRegion=Grammar does not contain a region for state {0} for token {1} at {2} of type {3}"})
    private String doNavigation(int position, AntlrProxies.ParseTreeProxy proxy, Grammar mainGrammar) throws BadLocationException, IOException {
        AntlrProxies.ProxyToken pt = proxy.tokenAtPosition(position);
        if (pt != null) {
            List<AntlrProxies.ParseTreeElement> refs = CollectionUtils.reversed(proxy.referencedBy(pt));
            AntlrProxies.ParseTreeElement lastOther = null;
            for (AntlrProxies.ParseTreeElement el : refs) {
                if (el instanceof AntlrProxies.RuleNodeTreeElement) {
                    AntlrProxies.RuleNodeTreeElement ruleEl = (AntlrProxies.RuleNodeTreeElement) el;
                    int state = ruleEl.invokingState();
                    Interval region = mainGrammar.getStateToGrammarRegion(state);
                    if (region != null) {
                        org.antlr.runtime.CommonToken startTok = (org.antlr.runtime.CommonToken) mainGrammar.originalTokenStream.get(region.a);
                        org.antlr.runtime.CommonToken stopTok = region.b == region.a ? startTok : (org.antlr.runtime.CommonToken) mainGrammar.originalTokenStream.get(region.b);
                        return doNavigation(proxy, startTok.getStartIndex(), stopTok.getStopIndex() + 1);
                    } else {
                        AntlrProxies.ProxyTokenType type = proxy.typeOf(pt);
                        return Bundle.noRegion(state, proxy.textOf(pt), pt.getStartIndex() + ":" + pt.getEndIndex(), type.name());
                    }
                } else {
                    lastOther = el;
                }
            }
            if (lastOther != null) {
                // XXX go to the token
            }
            return Bundle.noRuleElementToken(proxy.textOf(pt), pt.getStartIndex() + ":" + pt.getEndIndex());
        }
        return Bundle.noElementAt(position);
    }

    @NbBundle.Messages(value = {"# {0} - grammarFile", "couldNotFindGrammarFile=Could not find grammar file {0}"})
    private String doNavigation(AntlrProxies.ParseTreeProxy proxy, int startOffset, int endOffset) throws BadLocationException, IOException {
        boolean result = navigateTo(proxy.grammarPath(), Range.ofCoordinates(startOffset, endOffset));
        return result ? null : Bundle.couldNotFindGrammarFile(proxy.grammarPath());
    }

    class Go implements Runnable {

        private final int position;
        private final Cursor origCursor;
        private final Cursor newlySetCursor;
        private final Segment text;
        private volatile String failure;

        public Go(int position, Cursor origCursor, Cursor setCursor, Segment text) {
            this.position = position;
            this.origCursor = origCursor;
            this.newlySetCursor = setCursor;
            this.text = text;
        }

        @Override
        public void run() {
            if (EventQueue.isDispatchThread()) {
                setEnabled(true);
                comp.setCursor(origCursor);
                if (failure != null) {
                    JOptionPane.showMessageDialog(comp, failure);
                }
            } else {
                try {
                    failure = performNavigation(position, text);
                } finally {
                    EventQueue.invokeLater(this);
                }
            }
        }
    }

}

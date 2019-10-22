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
package org.nemesis.antlr.live.preview;

import java.awt.Color;
import java.awt.Toolkit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.common.AntlrConstants;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.text.Line;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.windows.FoldHandle;
import org.openide.windows.IOColorLines;
import org.openide.windows.IOColors;
import org.openide.windows.IOFolding;
import org.openide.windows.IOProvider;
import org.openide.windows.IOSelect;
import org.openide.windows.IOTab;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;
import org.openide.windows.OutputWriter;
import org.openide.windows.TopComponent;

/**
 * Updates the output window after a reparse.
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - The grammar name",
    "io_tab={0} (Antlr)",
    "success=Antlr parse successful",
    "unparsed=\tParse failed; code generation or compile unsuccessful.",
    "tip=Shows parse errors from Antlr parsing",
    "generationFailed=\tAntlr generation failed (errors in grammar?)",
    "compileFailed=\tFailed to compile generated parser/lexer/extractor",
    "exception=\tException thrown:",})
// TriConsumer<Document, GrammarRunResult<?>, AntlrProxies.ParseTreeProxy>>
final class ErrorUpdater implements BiConsumer<Document, EmbeddedAntlrParserResult>, Runnable {

    private AtomicReference<EmbeddedAntlrParserResult> info = new AtomicReference<>();
    private boolean writingFirstOutputWindowOutput = true;
    private final JEditorPane editorPane;
    private final RulePathStringifier stringifier;

    ErrorUpdater(JEditorPane editorPane, RulePathStringifier stringifier) {
        this.editorPane = editorPane;
        this.stringifier = stringifier;
    }

    void update(EmbeddedAntlrParserResult res) {
        // Ensure both are updated atomically by wrapping the update of the
        // second in an update of the first
        info.set(res);
    }

    static final class ParseInfo {

        public final GrammarRunResult<?> res;
        public final AntlrProxies.ParseTreeProxy proxy;

        public ParseInfo(GrammarRunResult<?> res, AntlrProxies.ParseTreeProxy proxy) {
            this.res = res;
            this.proxy = proxy;
        }
    }

    public void run() {
        EmbeddedAntlrParserResult ifo = info.get();
        if (ifo != null) {
            updateErrorsInOutputWindow(ifo);
        }
    }

    private void updateErrorsInOutputWindow(EmbeddedAntlrParserResult info) {
        if (info == null || info.proxy() == null) {
            return;
        }
        if (Thread.interrupted()) {
            return;
        }
        InputOutput io = IOProvider.getDefault().getIO(Bundle.io_tab(info.proxy().grammarName()), false);
        if (IOTab.isSupported(io)) {
            IOTab.setToolTipText(io, Bundle.tip());
            IOTab.setIcon(io, AntlrConstants.parserIcon());
        }
        ParseTreeProxy proxy = info.proxy();
        boolean failure = info.proxy().isUnparsed() || !info.proxy().syntaxErrors().isEmpty();
        boolean folds = IOFolding.isSupported(io);
        if (writingFirstOutputWindowOutput && failure) {
            if (IOSelect.isSupported(io)) {
                IOSelect.select(io, EnumSet.of(IOSelect.AdditionalOperation.OPEN, IOSelect.AdditionalOperation.REQUEST_VISIBLE));
            } else {
                io.setOutputVisible(true);
//                io.setFocusTaken(true);
            }
            writingFirstOutputWindowOutput = false;
        }
        try (final OutputWriter writer = io.getOut()) {
            writer.reset();
            if (proxy.isUnparsed()) {
                // XXX get the full result and print compiler diagnostics?
                ioPrint(io, Bundle.unparsed(), IOColors.OutputType.ERROR);
                GrammarRunResult<?> buildResult = info.runResult();
                if (buildResult != null) {
                    boolean wasGenerate = !buildResult.genResult().isUsable();
                    if (wasGenerate) {
                        ioPrint(io, Bundle.generationFailed(), IOColors.OutputType.LOG_DEBUG);
                        buildResult.genResult().grammarGenerationErrors();
                        for (ParsedAntlrError err : buildResult.genResult().grammarGenerationErrors()) {
                            ioPrint(io, err.message(), IOColors.OutputType.ERROR, new OutputListener() {
                                @Override
                                public void outputLineSelected(OutputEvent oe) {
                                    // do nothing
                                }

                                @Override
                                public void outputLineAction(OutputEvent oe) {
                                    Path pth = err.path();
                                    GrammarSource<?> src = GrammarSource.find(pth, "text/x-g4");
                                    Optional<Document> doc= src.lookup(Document.class);
                                    if (doc.isPresent()) {
                                        Document d = doc.get();
                                        JTextComponent comp = EditorRegistry.findComponent(d);
                                        if (comp != null) {
                                            TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, comp);
                                            Line ln = NbEditorUtilities.getLine(d, err.fileOffset(), true);
                                            ln.show(Line.ShowOpenType.REUSE_NEW, Line.ShowVisibilityType.FOCUS);
                                            tc.requestActive();
                                        }
                                    }
                                }

                                @Override
                                public void outputLineCleared(OutputEvent oe) {
                                    //do nothing
                                }
                            });
                        }
                    } else {
                        boolean wasCompile = !buildResult.genResult().compileFailed() && !buildResult.genResult().compiledSourceFiles().isEmpty();
                        if (wasCompile) {
                            if (!buildResult.diagnostics().isEmpty()) {
                                for (JavacDiagnostic diag : buildResult.diagnostics()) {
                                    if (!diag.isError()) {
                                        continue;
                                    }
                                    ioPrint(io, diag.toString(), IOColors.OutputType.LOG_FAILURE);
                                    writer.println();
                                }
                            }
                        }
                    }
                    if (buildResult.thrown().isPresent()) {
                        ioPrint(io, Bundle.exception(), IOColors.OutputType.LOG_FAILURE);
                        buildResult.thrown().get().printStackTrace(writer);
                    }
                }
            } else if (proxy.syntaxErrors().isEmpty()) {
                ioPrint(io, Bundle.success(), IOColors.OutputType.LOG_SUCCESS);
            } else {
                FoldHandle fold = null;
                if (folds) {
                    fold = IOFolding.startFold(io, true);
                }
                for (AntlrProxies.ProxySyntaxError e : proxy.syntaxErrors()) {
                    ErrOutputListener listener = listenerForError(io, proxy, e);
                    assert listener != null;
                    ioPrint(io, e.message(), IOColors.OutputType.HYPERLINK_IMPORTANT, listener);
                    FoldHandle innerFold = null;
                    if (folds && fold != null) {
                        innerFold = fold.startFold(true);
                    }
                    listener.printDescription(writer);
                    if (innerFold != null) {
                        innerFold.finish();
                    }
                }
                if (fold != null) {
                    fold.finish();
                }
            }
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
        }
    }

    private static void ioPrint(InputOutput io, String s, IOColors.OutputType type, OutputListener l) throws IOException {
        if (IOColors.isSupported(io) && IOColorLines.isSupported(io)) {
            Color c = IOColors.getColor(io, type);
            if (l != null) {
                IOColorLines.println(io, s, l, true, c);
            } else {
                IOColorLines.println(io, s, c);
            }
        } else {
            io.getOut().println(s);
        }
    }

    private static void ioPrint(InputOutput io, String s, IOColors.OutputType type) throws IOException {
        ioPrint(io, s, type, null);
    }

    private ErrOutputListener listenerForError(InputOutput io, AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxySyntaxError e) {
        return new ErrOutputListener(io, prx, e);
    }

    @Override
    public void accept(Document a, EmbeddedAntlrParserResult res) {
        updateErrorsInOutputWindow(res);
    }

    final class ErrOutputListener implements OutputListener {

        private final InputOutput io;
        private final AntlrProxies.ParseTreeProxy prx;
        private final AntlrProxies.ProxySyntaxError e;

        public ErrOutputListener(InputOutput io, AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxySyntaxError e) {
            this.io = io;
            this.prx = prx;
            this.e = e;
        }

        private void print(CharSequence sb, IOColors.OutputType type) throws IOException {
            ioPrint(io, sb.toString(), type);
            if (sb instanceof StringBuilder) {
                ((StringBuilder) sb).setLength(0);
            }
        }

        @NbBundle.Messages(value = {"# {0} - the line number", "# {1} - the character position within the line", "lineinfo=\tat {0}:{1}", "# {0} - The token type (symbolic, literal, display names and code)", "type=\tType: {0}", "# {0} - the list of rules this token particpates in", "rules=\tRules: {0}", "# {0} - the token text", "text=\tText: '{0}'"})
        void printDescription(OutputWriter out) throws IOException {
            AntlrProxies.ProxyToken tok = null;
            print(Bundle.lineinfo(Integer.valueOf(e.line()), Integer.valueOf(e.charPositionInLine())), IOColors.OutputType.LOG_FAILURE);
            if (e.hasFileOffsetsAndTokenIndex()) {
                int ix = e.tokenIndex();
                tok = prx.tokens().get(ix);
            } else if (e.line() >= 0 && e.charPositionInLine() >= 0) {
                tok = prx.tokenAtLinePosition(e.line(), e.charPositionInLine());
            } else {
                return;
            }
            if (tok != null) {
                AntlrProxies.ProxyTokenType type = prx.tokenTypeForInt(tok.getType());
                List<AntlrProxies.ParseTreeElement> refs = prx.referencedBy(tok);
                AntlrProxies.ParseTreeElement rule = refs.isEmpty() ? null : refs.get(0);
                print(Bundle.type(type.names()), IOColors.OutputType.LOG_DEBUG);
                if (rule != null) {
                    StringBuilder sb = new StringBuilder();
                    stringifier.tokenRulePathString(prx, tok, sb, false);
                    print(Bundle.rules(sb), IOColors.OutputType.LOG_DEBUG);
                }
                print(Bundle.text(prx.textOf(tok)), IOColors.OutputType.OUTPUT);
            }
        }

        @Override
        public void outputLineAction(OutputEvent oe) {
            boolean activate = false;
            if (e.hasFileOffsetsAndTokenIndex()) {
                int ix = e.tokenIndex();
                if (ix >= 0) {
                    AntlrProxies.ProxyToken pt = prx.tokens().get(ix);
                    int start = pt.getStartIndex();
                    int end = pt.getEndIndex();
                    editorPane.setSelectionStart(start);
                    editorPane.setSelectionEnd(end);
                    activate = true;
                }
            } else {
                Element lineRoot = ((BaseDocument) editorPane.getDocument()).getParagraphElement(0).getParentElement();
                Element line = lineRoot.getElement(e.line());
                if (line == null) {
                    Toolkit.getDefaultToolkit().beep();
                } else {
                    int docOffset = line.getStartOffset() + e.charPositionInLine();
                    editorPane.setSelectionStart(docOffset);
                    editorPane.setSelectionEnd(docOffset);
                    activate = true;
                }
            }
            if (activate) {
                Mutex.EVENT.readAccess(() -> {
                    TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, editorPane);
                    if (tc != null) {
                        tc.requestActive();
                        editorPane.requestFocus();
                    }
                });
            }
        }

        @Override
        public void outputLineSelected(OutputEvent oe) {
            // do nothing
        }

        @Override
        public void outputLineCleared(OutputEvent oe) {
            // do nothing
        }
    }

}

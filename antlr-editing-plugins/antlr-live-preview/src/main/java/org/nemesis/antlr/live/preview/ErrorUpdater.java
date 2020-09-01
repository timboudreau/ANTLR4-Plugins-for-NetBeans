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

import com.mastfrog.function.IntBiConsumer;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.strings.Strings;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import javax.tools.StandardLocation;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.common.AntlrConstants;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.compilation.AntlrGenerationAndCompilationResult;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.ParsingUtils;
import static org.nemesis.antlr.live.language.AdhocErrorHighlighter.toggleHighlightAmbiguitiesAction;
import static org.nemesis.antlr.live.language.AdhocErrorHighlighter.toggleHighlightLexerErrorsAction;
import static org.nemesis.antlr.live.language.AdhocErrorHighlighter.toggleHighlightParserErrorsAction;
import org.nemesis.antlr.live.language.AdhocLanguageHierarchy;
import org.nemesis.antlr.live.language.AdhocMimeDataProvider;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.SourceInvalidator;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.antlr.memory.spi.AntlrOutput;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.FoldHandle;
import org.openide.windows.IOColorLines;
import org.openide.windows.IOColors;
import org.openide.windows.IOColors.OutputType;
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

    private final AtomicReference<EmbeddedAntlrParserResult> info = new AtomicReference<>();
    private boolean writingFirstOutputWindowOutput = true;
    private final JEditorPane editorPane;
    private final RulePathStringifier stringifier;
    private final Runnable onForcedReparse;

    ErrorUpdater(JEditorPane editorPane, RulePathStringifier stringifier, Runnable onForcedReparse) {
        this.editorPane = editorPane;
        this.stringifier = stringifier;
        this.onForcedReparse = onForcedReparse;
    }

    boolean update(EmbeddedAntlrParserResult res) {
        // Ensure both are updated atomically by wrapping the update of the
        // second in an update of the first
        if (res != null) {
            return info.getAndSet(res) != res;
        }
        return false;
    }

    static final class ParseInfo {

        public final GrammarRunResult<?> res;
        public final AntlrProxies.ParseTreeProxy proxy;

        ParseInfo(GrammarRunResult<?> res, AntlrProxies.ParseTreeProxy proxy) {
            this.res = res;
            this.proxy = proxy;
        }
    }

    @Override
    public void run() {
        EmbeddedAntlrParserResult ifo = info.get();
        if (ifo != null) {
            updateErrorsInOutputWindow(ifo);
        }
        // Ensure we don't leak the data - it's one and done
//        info.compareAndSet(ifo, null);
    }

    private static final Consumer<FileObject> INV = SourceInvalidator.create();
    private final Action[] actions = new Action[]{new RerunAction(), new EnableDebugOutputAction(),
        toggleHighlightAmbiguitiesAction(), toggleHighlightParserErrorsAction(),
        toggleHighlightLexerErrorsAction()};

    private final Map<String, Reference<InputOutput>> ioForName = new HashMap<>();

    InputOutput io(String name) {
        Reference<InputOutput> ioRef = ioForName.get(name);
        InputOutput result = ioRef != null ? ioRef.get() : null;
        if (result == null) {
            result = IOProvider.getDefault().getIO(Bundle.io_tab(name), actions);
            ioForName.put(name, new WeakReference<>(result));
        }
        if (IOTab.isSupported(result)) {
            IOTab.setToolTipText(result, Bundle.tip());
            IOTab.setIcon(result, AntlrConstants.parserIcon());
        }
        return result;
    }

    private String tabName(EmbeddedAntlrParserResult info, ParseTreeProxy proxy) {
        return proxy != null ? proxy.grammarName() : info.grammarName();
    }

    @Messages({
        "# {0} - grammarName",
        "# {1} - timestamp",
        "outputHeader=Reaprse of {0} at {1}"
    })
    private void updateErrorsInOutputWindow(EmbeddedAntlrParserResult info) {
        if (info == null) {
            return;
        }

        ParseTreeProxy proxy = info.proxy();
        InputOutput io = io(tabName(info, proxy));

        boolean failure = proxy == null ? true : proxy.isUnparsed() || !proxy.syntaxErrors().isEmpty();
        boolean folds = IOFolding.isSupported(io);
        if (writingFirstOutputWindowOutput && failure) {
            if (IOSelect.isSupported(io)) {
                IOSelect.select(io, EnumSet.of(IOSelect.AdditionalOperation.OPEN,
                        IOSelect.AdditionalOperation.REQUEST_VISIBLE));
            } else {
                io.setOutputVisible(true);
            }
            writingFirstOutputWindowOutput = false;
        }
        boolean genErrorsShown = false;
        try (final OutputWriter writer = io.getOut()) {
            printOutputHeader(info, io);
            writer.reset();
            boolean foldsDisplayed = false;
            if (proxy != null && proxy.isUnparsed()) {
                // XXX get the full result and print compiler diagnostics?
                ioPrint(io, Bundle.unparsed(), IOColors.OutputType.ERROR);
                printOutputSectionsFolded(info, io, folds);
                foldsDisplayed = true;
                GrammarRunResult<?> buildResult = info.runResult();
                if (buildResult != null) {
                    boolean wasGenerate = !buildResult.genResult().isUsable();
                    if (wasGenerate) {
                        ioPrint(io, Bundle.generationFailed(), IOColors.OutputType.LOG_DEBUG);
                        genErrorsShown = true;
                        List<ParsedAntlrError> errors = buildResult.genResult().grammarGenerationErrors();
                        ioPrintErrors(errors, io);
                        Optional<Throwable> thrown = info.runResult().thrown();
                        if (thrown.isPresent()) {
                            thrown.get().printStackTrace(io.getOut());
                        }
                    }
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
                if (buildResult != null && buildResult.thrown() != null && buildResult.thrown().isPresent()) {
                    ioPrint(io, Bundle.exception(), IOColors.OutputType.LOG_FAILURE);
                    buildResult.thrown().get().printStackTrace(writer);
                }
            } else if (proxy != null && proxy.syntaxErrors().isEmpty()) {
                ioPrint(io, Bundle.success(), IOColors.OutputType.LOG_SUCCESS);
            }
            GrammarRunResult<?> rr = info.runResult();
            if (rr != null) {
                for (JavacDiagnostic j : rr.diagnostics()) {
                    ioPrint(io, j.toString(), IOColors.OutputType.ERROR);
                    if (info.runResult() != null && info.runResult().jfs() != null) {
                        JFS jfs = info.runResult().jfs();
                        UnixPath path = UnixPath.get(j.sourceRootRelativePath());
                        JFSFileObject fo = jfs.get(StandardLocation.SOURCE_PATH, path);
                        if (fo == null) {
                            fo = jfs.get(StandardLocation.SOURCE_OUTPUT, path);
                        }
                        if (fo != null) {
                            String ctx = j.context(fo);
                            ioPrint(io, ctx, IOColors.OutputType.LOG_FAILURE);
                        }
                    }
                }
                AntlrGenerationAndCompilationResult g = rr.genResult();
                if (g != null) {
                    AntlrGenerationResult gen = g.generationResult();
                    if (gen != null) {
                        if (!genErrorsShown) {
                            genErrorsShown = true;
                            ioPrintErrors(gen.errors(), io);
                        }
                        List<String> msgs = gen.infoMessages();
                        for (String msg : msgs) {
                            ioPrint(io, msg, OutputType.OUTPUT);
                        }
                    }
                }
                if (g.thrown().isPresent()) {
                    ioPrint(io, Strings.toString(g.thrown().get()), OutputType.ERROR);
                }
                if (!foldsDisplayed) {
                    printOutputSectionsFolded(info, io, folds);
                }
            }
            if (proxy != null && proxy.syntaxErrors() != null && !proxy.syntaxErrors().isEmpty()) {
                for (AntlrProxies.ProxySyntaxError e : proxy.syntaxErrors()) {
                    ErrOutputListener listener = listenerForError(io, proxy, e);
                    assert listener != null;
                    ioPrint(io, e.message(), IOColors.OutputType.HYPERLINK, listener);
                    listener.printDescription(writer);
                }
            }
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
        }
    }

    void printOutputSectionsFolded(EmbeddedAntlrParserResult info1, InputOutput io, boolean folds) throws IOException {
        Supplier<? extends CharSequence> genOut = AntlrOutput.getDefault()
                .outputFor(info1.grammarFile(), AntlrLoggers.STD_TASK_GENERATE_ANTLR);
        printFoldedProcessingOutput(genOut, io, Bundle.antlrRunOutput(), folds);

        Supplier<? extends CharSequence> grammarCompileOut = AntlrOutput.getDefault()
                .outputFor(info1.grammarFile(), AntlrLoggers.STD_TASK_COMPILE_GRAMMAR);
        printFoldedProcessingOutput(grammarCompileOut, io, Bundle.antlrCompilationOutput(), folds);

        Supplier<? extends CharSequence> exGenOut = AntlrOutput.getDefault()
                .outputFor(info1.grammarFile(), AntlrLoggers.STD_TASK_GENERATE_ANALYZER);
        printFoldedProcessingOutput(grammarCompileOut, io, Bundle.extractorGeneration(), folds);

        Supplier<? extends CharSequence> anaCompileOut = AntlrOutput.getDefault()
                .outputFor(info1.grammarFile(), AntlrLoggers.STD_TASK_COMPILE_ANALYZER);
        printFoldedProcessingOutput(anaCompileOut, io, Bundle.extractorCompilationOutput(), folds);
    }

    @Messages({
        "antlrCompilationOutput=Grammar Compilation Output",
        "extractorCompilationOutput=Analyzer Compilation Output",
        "extractorGeneration=Analyzer Generation Output",})
    void printFoldedProcessingOutput(Supplier<? extends CharSequence> genOut, InputOutput io, String msg, boolean folds) throws IOException {
        if (genOut != null) {
            CharSequence seq = genOut.get();
            if (seq.length() > 0) {
                ioPrint(io, msg, OutputType.OUTPUT);
                FoldHandle fold = null;
                if (folds) {
                    fold = IOFolding.startFold(io, false);
                }
                ioPrint(io, seq, IOColors.OutputType.LOG_WARNING);
                if (fold != null) {
                    fold.finish();
                }
            }
        }
    }

    @Messages("antlrRunOutput=Antlr Run Output (expand)")
    void printOutputHeader(EmbeddedAntlrParserResult res, InputOutput io) throws IOException {
        long timestamp;
        if (res.runResult() != null) {
            timestamp = res.runResult().timestamp();
        } else {
            timestamp = System.currentTimeMillis();
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        String gn = res.proxy().grammarName();
        String ts = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ldt);
        ioPrint(io, Bundle.outputHeader(gn, ts), OutputType.OUTPUT);
    }

    void ioPrintErrors(List<ParsedAntlrError> errors, InputOutput io) throws IOException {
        for (ParsedAntlrError err : errors) {
            ioPrint(io, err.message(), IOColors.OutputType.ERROR, new AntlrErrorLineListener(err));
        }
    }

    private static int offsetsOf(ParsedAntlrError error, StyledDocument sdoc, IntBiConsumer startEnd) {
        // XXX use LineDocument - can copy from antlr error highlighter
        int docLength = sdoc.getLength();
        Element el = NbDocument.findLineRootElement(sdoc);
        int lineNumber = error.lineNumber() - 1 >= el.getElementCount()
                ? el.getElementCount() - 1 : error.lineNumber() - 1;
        if (lineNumber < 0) {
            lineNumber = error.lineNumber();
            if (lineNumber < 0) {
                lineNumber = 0;
            }
        }
        int lineOffsetInDocument = NbDocument.findLineOffset(sdoc, lineNumber);
        int errorStartOffset = Math.max(0, lineOffsetInDocument + error.lineOffset());
        int errorEndOffset = Math.min(docLength - 1, errorStartOffset + error.length());
        if (errorStartOffset < errorEndOffset) {
            startEnd.accept(Math.min(docLength - 1, errorStartOffset), Math.min(docLength - 1, errorEndOffset));
        } else {
            Logger.getLogger(ErrorUpdater.class.getName()).log(Level.INFO, "Computed nonsensical error start offsets "
                    + "{0}:{1} for line {2} of {3} for error {4}",
                    new Object[]{
                        errorStartOffset, errorEndOffset,
                        lineNumber, el.getElementCount(), error
                    });
        }
        return docLength;
    }

    private static void ioPrint(InputOutput io, CharSequence s, IOColors.OutputType type, OutputListener l) throws IOException {
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

    private static void ioPrint(InputOutput io, CharSequence s, IOColors.OutputType type) throws IOException {
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

        ErrOutputListener(InputOutput io, AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxySyntaxError e) {
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

        @NbBundle.Messages(value = {"# {0} - the line number",
            "# {1} - the character position within the line",
            "lineinfo=\tat {0}:{1}",
            "# {0} - The token type (symbolic, literal, display names and code)",
            "type=\tType: {0}", "# {0} - the list of rules this token particpates in",
            "rules=\tRules: {0}", "# {0} - the token text", "text=\tText: ''{0}''"})
        void printDescription(OutputWriter out) throws IOException {
            AntlrProxies.ProxyToken tok = null;
            print(Bundle.lineinfo(Integer.valueOf(e.line()), Integer.valueOf(e.charPositionInLine())),
                    IOColors.OutputType.LOG_FAILURE);
            if (e.hasFileOffsetsAndTokenIndex()) {
                int ix = e.tokenIndex();
                if (ix >= prx.tokens().size()) {
                    Logger.getLogger(ErrorUpdater.class.getName())
                            .log(Level.WARNING, "Error has offset and token index but the token "
                                    + "index is out of range: {0} of {1}. Probably the code "
                                    + "that set it is broken: {2}",
                                    new Object[]{ix, prx.tokens().size(), e});
                    tok = prx.tokenAtLinePosition(e.line(), e.charPositionInLine());
                } else {
                    tok = prx.tokens().get(ix);
                }
            } else if (e.line() >= 0 && e.charPositionInLine() >= 0) {
                tok = prx.tokenAtLinePosition(e.line(), e.charPositionInLine());
            } else {
                tok = prx.tokens().get(0);
            }
            if (tok != null) {
                AntlrProxies.ProxyTokenType type = prx.tokenTypeForInt(tok.getType());
                List<AntlrProxies.ParseTreeElement> refs = prx.referencedBy(tok);
                AntlrProxies.ParseTreeElement rule = refs.isEmpty() ? null : refs.get(0);
                print(Bundle.type(type.names()), IOColors.OutputType.LOG_DEBUG);
                if (rule != null) {
                    StringBuilder sb = new StringBuilder();
                    stringifier.tokenRulePathString(prx, tok, sb, false, editorPane);
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

    static class AntlrErrorLineListener implements OutputListener {

        private final ParsedAntlrError err;

        AntlrErrorLineListener(ParsedAntlrError err) {
            this.err = err;
        }

        @Override
        public void outputLineAction(OutputEvent oe) {
            Path pth = err.path();
            GrammarSource<?> src = GrammarSource.find(pth, ANTLR_MIME_TYPE);
            Optional<StyledDocument> doc = src.lookup(StyledDocument.class);
            if (doc.isPresent()) {
                StyledDocument d = doc.get();
                JTextComponent comp = EditorRegistry.findComponent(d);
                if (comp != null) {
                    TopComponent tc = NbEditorUtilities.getOuterTopComponent(comp);
//                    TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, comp);
                    offsetsOf(err, d, (start, end) -> {
                        Line ln = NbEditorUtilities.getLine(d, start, false);
                        ln.show(Line.ShowOpenType.REUSE_NEW, Line.ShowVisibilityType.FOCUS);
                        tc.requestActive();
                    });
                }
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

    @Messages("rerun=Force Reparse")
    class RerunAction extends AbstractAction implements Runnable, Icon {

        @SuppressWarnings({"LeakingThisInConstructor", "OverridableMethodCallInConstructor"})
        RerunAction() {
            putValue(Action.NAME, Bundle.rerun());
            putValue(Action.SHORT_DESCRIPTION, Bundle.rerun());
            putValue(Action.LONG_DESCRIPTION, Bundle.rerun());
            putValue(Action.SMALL_ICON, this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            PreviewPanel pp = (PreviewPanel) SwingUtilities.getAncestorOfClass(PreviewPanel.class, editorPane);
            if (pp != null) {
                pp.outputThreadPool().submit(this);
            }
        }

        @Override
        public void run() {
            try {
                // Some paranoid stuff to ensure NOTHING survives
                // Well, a JFSClassLoader with classes loaded in it might...
                // ...but everything else
                PreviewPanel pp = (PreviewPanel) SwingUtilities.getAncestorOfClass(PreviewPanel.class, editorPane);
                EmbeddedAntlrParserResult emres = pp == null ? null : pp.internalLookup().lookup(EmbeddedAntlrParserResult.class);
                if (emres == null) {
                    emres = info.get();
                } else {
                    // so the subsequent call to run will have the right value
                    // if it is not replaced
                    info.set(emres);
                }
                if (emres != null && emres.runResult() != null) {
                    EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(editorPane.getContentType());
                    parser.clean();
                    if (emres.runResult().genResult() != null && emres.runResult().genResult().generationResult() != null) {
                        emres.runResult().genResult().generationResult().cleanOldOutput();
                    }
                    try {
                        emres.runResult().jfs().clear(StandardLocation.CLASS_OUTPUT);
                        emres.runResult().jfs().clear(StandardLocation.SOURCE_OUTPUT);
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
                // Invalidate and start from scratch on EVERYTHING
                Path grammarSource = AdhocMimeTypes.grammarFilePathForMimeType(editorPane.getContentType());
                FileObject grammarFileObject = FileUtil.toFileObject(grammarSource.toFile());
                INV.accept(grammarFileObject);
                AdhocMimeDataProvider.getDefault().gooseLanguage(editorPane.getContentType());
                FileObject sampleFile = NbEditorUtilities.getFileObject(editorPane.getDocument());
                INV.accept(sampleFile);
                DataObject grammarDob = DataObject.find(grammarFileObject);
                EditorCookie ck = grammarDob.getLookup().lookup(EditorCookie.class);
                ParsingUtils.parse(ck.openDocument(), res -> {
                    ParsingUtils.parse(editorPane.getDocument());
                    AdhocMimeDataProvider.getDefault().gooseLanguage(editorPane.getContentType());
                    // This is synchronous, so a new parser result should be in the
                    // lookup after
                    onForcedReparse.run();
                    if (pp != null) {
                        EmbeddedAntlrParserResult newParserResult = pp.internalLookup().lookup(EmbeddedAntlrParserResult.class);
                        if (newParserResult != null) {
                            info.set(newParserResult);
                        }
                    }
                    return null;
                });
            } catch (Exception | Error ex) {
                Exceptions.printStackTrace(ex);
            }

            ErrorUpdater.this.run();
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D gg = (Graphics2D) g;
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(162, 245, 162));
            int[] xpts = new int[]{x + 2, x + 20, x + 2};
            int[] ypts = new int[]{y + 2, y + 10, y + 20};
            g.fillPolygon(xpts, ypts, 3);
            g.setColor(UIManager.getColor("controlShadow"));
            g.drawPolygon(xpts, ypts, 3);
        }

        @Override
        public int getIconWidth() {
            return 24;
        }

        @Override
        public int getIconHeight() {
            return 24;
        }
    }
}

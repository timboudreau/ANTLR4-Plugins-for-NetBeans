/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.language;

import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.coloring.AdhocColoringsRegistry;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.tool.Grammar;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.AntlrGenerationAndCompilationResult;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.SourceInvalidator;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFSFileObject;
import org.netbeans.lib.editor.util.swing.DocumentListenerPriority;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 * Manages choreographing reparsing and repaints for highlighters, which is a
 * bit complex - reparse may be triggered by the the underlying grammar document
 * being edited, the document being highlighted being edited, or the colorings
 * being updated. We use the shared EmbeddedAntlrParser to listen for receiving
 * a new "environment" - i.e. the grammar was edited, recompiled with extractor
 * into a JFS and a new JFSClassLoader environment was set on the embedded
 * parser, meaning we may get a different highlighting result and should force a
 * reparse - this triggers calling down into ParserManager, which will
 * indirectly cause us to be called back with the new EmbeddedAntlrParserResult
 * once the parser has run. Complicated enough?
 *
 * @author Tim Boudreau
 */
final class AdhocHighlighterManager {

    protected static final RequestProcessor THREAD_POOL = new RequestProcessor("antlr-highlighting", 5, true);
    private static final Logger LOG = Logger.getLogger(AdhocHighlighterManager.class.getName());
    protected final static int REPARSE_DELAY = 400;
    protected final static int REPAINT_DELAY = 75;
    private final AtomicReference<HighlightingInfo> lastParseInfo = new AtomicReference<>();
    private final RequestProcessor.Task repaintHighlightsTask;
    private final RequestProcessor.Task reparseDocumentTask;
    private final RequestProcessor.Task reparseGrammarTask;
    private final CompL compl = new CompL();
    private final String mimeType;
    private final Context ctx;
    private final AdhocColorings colorings;
    private final AbstractAntlrHighlighter[] highlighters;
    private final ComponentListener cl;
    private volatile boolean showing;

    static {
        LOG.setLevel(Level.ALL);
    }

    AdhocHighlighterManager(String mimeType, Context ctx) {
        this.mimeType = mimeType;
        this.ctx = ctx;
        colorings = AdhocColoringsRegistry.getDefault().get(mimeType);
        repaintHighlightsTask = THREAD_POOL.create(this::repaintHighlights);
        reparseDocumentTask = THREAD_POOL.create(this::documentReparse);
        reparseGrammarTask = THREAD_POOL.create(this::grammarReparse);
        JTextComponent c = ctx.getComponent();
        c.addComponentListener(cl = WeakListeners.create(
                ComponentListener.class, compl, c));
        c.addPropertyChangeListener("ancestor",
                WeakListeners.propertyChange(compl, c));
        highlighters = new AbstractAntlrHighlighter[]{
            new AdhocErrorHighlighter(this),
            new AdhocRuleHighlighter(this)
        };
        if (c.isShowing()) {
            compl.setShowing(c, true);
        }
        LOG.log(Level.INFO, "Created an AdhocHighlighterManager for {0} "
                + "currently showing? {1}", new Object[]{ctx.getDocument(), showing});
    }

    AdhocColorings colorings() {
        return colorings;
    }

    boolean showing() {
        return showing;
    }

    String mimeType() {
        return mimeType;
    }

    JTextComponent component() {
        return ctx.getComponent();
    }

    Document document() {
        return ctx.getDocument();
    }

    RequestProcessor threadPool() {
        return THREAD_POOL;
    }

    @Override
    public String toString() {
        FileObject fo = NbEditorUtilities.getFileObject(ctx.getDocument());
        return "AdhocHighlighterManager(" + AdhocMimeTypes.loggableMimeType(mimeType)
                + "-" + (fo == null ? ctx.getDocument().toString() : fo.getNameExt());
    }

    AbstractAntlrHighlighter[] highlighters() {
        return highlighters;
    }

    private void addNotify(JTextComponent comp) {
        LOG.log(Level.FINER, "{0} addNotify", this);
        for (AbstractAntlrHighlighter hl : highlighters) {
            hl.addNotify(comp);
        }
    }

    private void removeNotify(JTextComponent comp) {
        LOG.log(Level.FINER, "{0} removeNotify", this);
        for (AbstractAntlrHighlighter hl : highlighters) {
            hl.removeNotify(comp);
        }
    }

    class CompL extends ComponentAdapter implements PropertyChangeListener,
            DocumentListener, BiConsumer<Extraction, GrammarRunResult<?>>,
            ChangeListener {

        private final XC xc = new XC();

        private void setShowing(JTextComponent comp, boolean nowShowing) {
            if (showing != nowShowing) {
                LOG.log(Level.FINER, "Showing change on {0} to {1}",
                        new Object[]{AdhocHighlighterManager.this, nowShowing});
                showing = nowShowing;
                Document dd = comp.getDocument();
                if (!nowShowing) {
                    repaintHighlightsTask.cancel();
                    reparseDocumentTask.cancel();
                    dd.removeDocumentListener(this);
                    colorings.removeChangeListener(this);
                    clearLastParseInfo();
                    removeNotify(comp);
                    DocumentUtilities.removePriorityDocumentListener(dd, this,
                            DocumentListenerPriority.LEXER);
                    AdhocReparseListeners.unlisten(mimeType, dd, xc);
                } else {
                    addNotify(comp);
                    AdhocLanguageHierarchy.onNewEnvironment(mimeType, this);
                    AdhocReparseListeners.listen(mimeType, dd, xc);
                    DocumentUtilities.addPriorityDocumentListener(dd, this,
                            DocumentListenerPriority.LEXER);
                    colorings.addChangeListener(this);
                    scheduleGrammarReparse();
                }
            }
        }

        @Override
        public void componentHidden(ComponentEvent e) {
            setShowing((JTextComponent) e.getComponent(), false);
        }

        @Override
        public void componentShown(ComponentEvent e) {
            setShowing((JTextComponent) e.getComponent(), true);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("ancestor".equals(evt.getPropertyName())) {
                setShowing((JTextComponent) evt.getSource(), evt.getNewValue() != null);
            }
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            scheduleReparse();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            scheduleReparse();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            scheduleReparse();
        }

        @Override
        public void accept(Extraction t, GrammarRunResult<?> u) {
            LOG.log(Level.FINE, "Highlight-manager new environment {0}, {1}",
                    new Object[]{t.tokensHash(), u});
            Debug.message("reparse-listener-notify "
                    + AdhocHighlighterManager.this.toString() + " "
                    + t.tokensHash(), u::toString);
            scheduleReparse();
        }

        @Override
        public void stateChanged(ChangeEvent e) {
//            scheduleRepaint();
            System.out.println("\n\nhighlighters got colorings colorings change");
            repaintHighlights();
        }

        class XC implements BiConsumer<Document, EmbeddedAntlrParserResult> {

            @Override
            public void accept(Document t, EmbeddedAntlrParserResult u) {
                Document d = document();
                if (t == d) {
                    Debug.run(this, "hl-mgr-xc-accept " + u.grammarTokensHash(), () -> {
                        onNewParse(t, u);
                    });
                } else {
                    LOG.log(Level.WARNING, "Got surprise document {0} expecting {1}", new Object[]{t, d});
                    Debug.failure("Got surprise document", () -> {
                        return "Expected: " + d + "\nGot: " + t + "\nIn: " + this;
                    });
                }
            }

            @Override
            public String toString() {
                return "XC->CompL->" + AdhocHighlighterManager.this;
            }
        }
    }

    private void repaintHighlights() {
        HighlightingInfo info = lastParseInfo.get();
        if (info != null) {
            refresh(info);
        } else {
            LOG.log(Level.FINE, "Refresh called, but have no highlighting info: {0}", this);
        }
    }

    private void refresh(HighlightingInfo info) {
        LOG.log(Level.FINE, "Refresh {0} with {1}", new Object[]{this, info});
        for (AbstractAntlrHighlighter hl : highlighters) {
            hl.refresh(info);
        }
    }

    static final Consumer<FileObject> INVALIDATOR
            = SourceInvalidator.create();

    void grammarReparse() {
        Debug.run(this, "adhoc-hlmgr-grammar-reparse", this::toString, () -> {
            Path pth = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
            if (pth != null) {
                File f = FileUtil.normalizeFile(pth.toFile());
                if (f != null) {
                    FileObject fo = FileUtil.toFileObject(f);
                    if (fo != null) {
                        INVALIDATOR.accept(fo);
                        try {
                            DataObject dob = DataObject.find(fo);
                            EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                            if (ck != null) {
                                Document doc = ck.getDocument();
                                if (doc != null) {
                                    Debug.message("reparse-document", doc::toString);
                                    ParsingUtils.parse(doc);
                                    documentReparse();
                                    return;
                                }
                            }

                            ParsingUtils.parse(fo);
                            Debug.message("reparse-file", fo::toString);
                            documentReparse();
                        } catch (Exception ex) {
                            Debug.thrown(ex);
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            }
        });
    }

    void documentReparse() {
        Debug.run(this, "adhoc-hlmgr-doc-reparse " + AdhocMimeTypes.loggableMimeType(mimeType), () -> {
            Document d = ctx.getDocument();
            HighlightingInfo info = lastInfo();
            if (info != null && d == null) {
                d = info.doc;
            }
            try {
                ParsingUtils.parse(d, (Parser.Result res) -> {
                    if (res instanceof AdhocParserResult) {
                        AdhocParserResult ahpr = (AdhocParserResult) res;
                        Debug.message("parser new proxy - "
                                + ahpr.grammarHash() + " " + ahpr.parseTree().loggingInfo(),
                                () -> {
                                    StringBuilder sb = new StringBuilder("INFO:\n");

                                    AntlrGenerationAndCompilationResult gr = ahpr.result().runResult().genResult();

                                    sb.append("AntlrGenerationAndCompilationResult: ")
                                            .append(gr).append("\n");

                                    for (Path p : gr.compiledSourceFiles()) {
                                        sb.append("  ").append(p).append('\n');
                                    }

                                    AntlrGenerationResult genR = gr.generationResult();
                                    sb.append("\nAntlrGenerationResult: ").append(genR).append("\n");
                                    sb.append("  status: ").append(genR.currentStatus());
                                    sb.append("  modified:\n");
                                    for (Map.Entry<JFSFileObject, Long> e : genR.modifiedFiles.entrySet()) {
                                        sb.append("    ").append(e.getKey()).append('\n');
                                    }
                                    sb.append("  new:\n");
                                    for (JFSFileObject f : genR.newlyGeneratedFiles) {
                                        sb.append("    ").append(f).append('\n');
                                    }

                                    Grammar g = genR.mainGrammar;
                                    Vocabulary vocab = g.getVocabulary();
                                    sb.append("\nGRAMMAR: ").append(g.name).append(" / ").append(g.fileName);
                                    for (int i = 1; i < vocab.getMaxTokenType(); i++) {
                                        sb.append("TT: ").append(vocab.getSymbolicName(i)).append(":").append(vocab.getLiteralName(i))
                                                .append('\n');
                                    }
                                    sb.append("\nGRAMMAR TEXT:\n").append(g.text).append("\n\n");


                                    return sb.toString();
                                });
//                        onNewParse(ctx.getDocument(), res.)
                        HighlightingInfo nue = new HighlightingInfo(ctx.getDocument(), ahpr.parseTree());
                        lastParseInfo.set(nue);
                        scheduleRepaint();
                    }
                    return null;
                });
//                String txt = d.getText(0, d.getLength());
//                scheduleRepaint();
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        });
    }

    private void onNewParse(Document doc, EmbeddedAntlrParserResult res) {
        LOG.log(Level.FINE, "onNewParse {0} with {1} and {2}",
                new Object[]{this, doc, res});
        AntlrProxies.ParseTreeProxy s = res.proxy();
        if (s.text() == null) {
            Debug.failure("Null text", res::toString);
            return;
        }
        HighlightingInfo last = lastParseInfo.get();
        if (last != null && res.proxy() == last.semantics) {
            // We can be called two ways here
            return;
        }
        Debug.run(this, getClass().getSimpleName() + "-notified-reparse " + s.loggingInfo(), res::toString, () -> {
            Debug.message(NbEditorUtilities.getFileObject(doc) + " (fileobject)", () -> {
                try {
                    return "PROXY TEXT:\n" + s.toString() + "\n\nDOC TEXT:\n"
                            + doc.getText(0, doc.getLength());
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                    return ex.toString();
                }
            });
            HighlightingInfo info = new HighlightingInfo(doc, res.proxy());
            lastParseInfo.set(info);
//            scheduleRepaint();
            repaintHighlights();
        });
    }

    protected HighlightingInfo lastInfo() {
        return lastParseInfo.get();
    }

    final void scheduleRepaint() {
        if (showing) {
            repaintHighlightsTask.schedule(REPAINT_DELAY);
        }
    }

    final void scheduleReparse() {
        if (showing) {
            reparseDocumentTask.schedule(REPARSE_DELAY);
        }
    }

    final void scheduleGrammarReparse() {
        if (showing) {
            reparseGrammarTask.schedule(REPARSE_DELAY);
        }
    }

    void clearLastParseInfo() {
        lastParseInfo.set(null);
    }

    final UserTask ut = new UserTask() {
        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            Debug.runObjectThrowing(this, "get-parser-result " + mimeType, () -> {
                Parser.Result res = resultIterator.getParserResult();
                if (res == null) {
                    Debug.failure("null-parse-result", () -> "");
                } else {
                    Debug.success("parse-result", () -> {
                        return res.toString();
                    });
                }
                return res;
            });
        }
    };

    static final class HighlightingInfo {

        final Document doc;
        final AntlrProxies.ParseTreeProxy semantics;

        public HighlightingInfo(Document doc, AntlrProxies.ParseTreeProxy semantics) {
            this.doc = doc;
            this.semantics = semantics;
        }

        public String toString() {
            return doc + "/" + semantics.loggingInfo();
        }
    }
}

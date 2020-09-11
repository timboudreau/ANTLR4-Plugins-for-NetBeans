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
import java.util.Objects;
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
import static javax.swing.text.Document.StreamDescriptionProperty;
import javax.swing.text.JTextComponent;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.SourceInvalidator;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.netbeans.lib.editor.util.swing.DocumentListenerPriority;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
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

    protected static final RequestProcessor THREAD_POOL = new RequestProcessor("antlr-highlighting", 5, false);
    private static final Logger LOG = Logger.getLogger(AdhocHighlighterManager.class.getName());
    protected final static int REPARSE_DELAY = 750;
    protected final static int REPAINT_DELAY = 75;
    private final AtomicReference<HighlightingInfo> lastParseInfo = new AtomicReference<>();
    private final RequestProcessor.Task repaintHighlightsTask;
    private final RequestProcessor.Task reparseSampleTextTest;
    private final RequestProcessor.Task reparseGrammarTask;
    private final CompL compl = new CompL();
    private final String mimeType;
    private final Context ctx;
    private final AdhocColorings colorings;
    private final AbstractAntlrHighlighter[] highlighters;
    // Need to hold a strong reference
    private final @SuppressWarnings("unused") ComponentListener cl;
    private volatile boolean showing;

    AdhocHighlighterManager(String mimeType, Context ctx) {
        this.mimeType = mimeType;
        this.ctx = ctx;
        colorings = AdhocColoringsRegistry.getDefault().get(mimeType);
        repaintHighlightsTask = THREAD_POOL.create(this::repaintHighlights);
        reparseSampleTextTest = THREAD_POOL.create(this::documentReparse);
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
        LOG.log(Level.FINE, "Created an AdhocHighlighterManager for {0} "
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
                    reparseSampleTextTest.cancel();
                    dd.removeDocumentListener(this);
                    colorings.removeChangeListener(this);
                    clearLastParseInfo();
                    removeNotify(comp);
                    DocumentUtilities.removePriorityDocumentListener(dd, this,
                            DocumentListenerPriority.LEXER);
                    AdhocReparseListeners.unlisten(mimeType, dd, xc);
                    LOG.log(Level.FINE, "not-showing - stop listening {0}", AdhocHighlighterManager.this);
                } else {
                    LOG.log(Level.FINE, "showing - start listening {0}", AdhocHighlighterManager.this);
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
            scheduleSampleTextReparse();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            scheduleSampleTextReparse();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
        }

        @Override
        public void accept(Extraction t, GrammarRunResult<?> u) {
            LOG.log(Level.FINE, "Highlight-manager new environment {0}, {1}",
                    new Object[]{t.tokensHash(), u});
            Debug.message("reparse-listener-notify "
                    + AdhocHighlighterManager.this.toString() + " "
                    + t.tokensHash(), u::toString);
            if (!u.isUsable() && u.genResult() != null && u.genResult().generationResult() != null) {
                boolean throttled = RebuildSubscriptions.throttle().incrementThrottleIfBad(t, u.genResult().generationResult());
                if (throttled) {
                    return;
                }
            } else if (!u.isUsable()) {
                return;
            }
            scheduleSampleTextReparse();
        }

        @Override
        public void stateChanged(ChangeEvent e) {
//            scheduleRepaint();
//            System.out.println("\n\nhighlighters got colorings colorings change");
            repaintHighlights();
        }

        class XC implements BiConsumer<Document, EmbeddedAntlrParserResult> {

            @Override
            public void accept(Document t, EmbeddedAntlrParserResult u) {
                Document d = document();
                LOG.log(Level.FINEST, "New embedded parser result received for {0} : {1}",
                        new Object[]{t.getProperty(StreamDescriptionProperty), u});
                if (t == d || d.getProperty(StreamDescriptionProperty) == null) { // XXX why???
                    Debug.run(this, "hl-mgr-xc-accept " + u.grammarTokensHash() + " for "
                            + t.getProperty(StreamDescriptionProperty), () -> {
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
                File f = pth.toFile();
                if (f != null) {
                    FileObject fo = FileUtil.toFileObject(f);
                    if (fo != null) {
                        if (reparseOrFindExtraction(fo)) {
                            scheduleSampleTextReparse();
                        }
                    }
                }
            }
        });
    }

    private boolean reparseOrFindExtraction(FileObject grammar) {
        Extraction ext = NbAntlrUtils.extractionFor(grammar);
        if (ext == null || ext.isPlaceholder() || ext.isDisposed()) {
            return false;
        }
        if (RebuildSubscriptions.throttle().isThrottled(ext)) {
            return false;
        }
        return true;
    }

    static boolean isTrue(Boolean bool) {
        return bool == null ? false : bool;
    }

    private Boolean checkParserResult(Parser.Result result) {
        Extraction ext = ExtractionParserResult.extraction(result);
        if (ext != null) {
            if (RebuildSubscriptions.throttle().isThrottled(ext)) {
                return false;
            }
        }
        return ext != null;
    }

    void documentReparse() {
        inReparse = true;
        try {
            Document d = ctx.getDocument();
            Debug.run(this, "adhoc-hlmgr-doc-reparse", this::documentName, () -> {
                EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mimeType);
                CharSequence text = DocumentUtilities.getText(d);
                try {
//                    synchronized (this) {
                        EmbeddedAntlrParserResult result = parser.parse(text);
                        AntlrProxies.ParseTreeProxy prox = result.proxy();
                        if (prox.isUnparsed()) {
                            Debug.failure("Got unparsed proxy", prox::loggingInfo);
                        } else {
                            Debug.success("Got parsed proxy", prox::loggingInfo);
                        }
                        lastParseInfo.set(new HighlightingInfo(d, prox));
//                    }
                    scheduleRepaint();
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
        } finally {
            inReparse = false;
        }
    }

    private void onNewParse(Document doc, EmbeddedAntlrParserResult res) {
        LOG.log(Level.FINE, "onNewParse {0} with {1} and {2}",
                new Object[]{this, doc, res});
        AntlrProxies.ParseTreeProxy s = res.proxy();
        if (s.text() == null || s.text().length() < 2) {
            Debug.failure("Null text", res::toString);
            return;
        }
        HighlightingInfo last = lastParseInfo.get();
        if (last != null && res.proxy() == last.semantics) {
            LOG.log(Level.FINEST, "Got same proxy as last time");
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
            LOG.log(Level.FINE, "Schedule repaint highlights");
//            repaintHighlights();
            scheduleRepaint();
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

    private String documentName() {
        Document doc = ctx.getDocument();
        Object sdp = doc.getProperty(StreamDescriptionProperty);
        if (sdp instanceof DataObject) {
            return ((DataObject) sdp).getName();
        } else if (sdp instanceof FileObject) {
            return ((FileObject) sdp).getName();
        }
        return Objects.toString(sdp);
    }

    private boolean inReparse;

    final void scheduleSampleTextReparse() {
        if (inReparse) {
            // XXX should be a threadlocal?
            return;
        }
        if (showing) {
            reparseSampleTextTest.schedule(REPARSE_DELAY);
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

package org.nemesis.antlr.highlighting;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Mutex;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 * Base class for highlighters that takes care of most of the boilerplate of
 * listening and re-running as needed.
 *
 * @author Tim Boudreau
 */
abstract class GeneralHighlighter<T> implements Runnable {

    protected static final RequestProcessor THREAD_POOL = new RequestProcessor("antlr-highlighting", 3, true);
    private final AtomicReference<Future<?>> future = new AtomicReference<>();
    protected final static int REFRESH_DELAY = 100;
    protected final Document doc;
    private RequestProcessor.Task refreshTask;
    protected final Logger LOG = Logger.getLogger(getClass().getName());
    private final int refreshDelay;
    private final AntlrHighlighter implementation;
    private final Object key = new Object();

    {
        LOG.setLevel(Level.ALL);
    }

    public GeneralHighlighter(Context ctx, int refreshDelay, AntlrHighlighter implementation) {
        doc = ctx.getDocument();
        this.refreshDelay = refreshDelay <= 0 ? REFRESH_DELAY : refreshDelay;
        this.implementation = implementation;
        OffsetsBag bag = new OffsetsBag(doc, implementation.mergeHighlights());
        // This avoids us holding a reference from GeneralHighlighter -> OffsetsBag -> Document
        // forever
        doc.putProperty(key, bag);
        log(Level.FINE, "Create for {0} with {1}", doc, implementation);
        // Ensure we access the text component on the event thread, since we
        // may add listeners to it
        Mutex.EVENT.readAccess(() -> {
            JTextComponent comp = ctx.getComponent();
            postInit(comp, doc);
        });
    }

    final void log(Level level, String msg, Object... args) {
        LOG.log(level, msg, args);
    }

    protected final void refresh(Document doc, T argument, Extraction ext, Parser.Result result, OffsetsBag bag) {
        implementation.refresh(doc, ext, result, bag);
    }

    protected void postInit(JTextComponent pane, Document doc) {
        // do nothing
    }

    protected final void scheduleRefresh() {
        if (refreshTask == null) {
            refreshTask = THREAD_POOL.create(this);
        }
        refreshTask.schedule(refreshDelay);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    protected final Optional<Document> document() {
        return Optional.of(doc);
    }

    public final OffsetsBag getHighlightsBag() {
        return (OffsetsBag) doc.getProperty(key);
    }

    protected T getArgument() {
        return null;
    }

    protected boolean shouldProceed(T argument) {
        return true;
    }

    @Override
    public final void run() {
        Optional<Document> doc = document();
        if (doc.isPresent()) {
            if (Thread.interrupted()) {
                return;
            }
            Document d = doc.get();
            LOG.log(Level.FINEST, "Run with {0}", doc.get());
            T argument = getArgument();
            if (!shouldProceed(argument)) {
                LOG.log(Level.FINEST, "Should not proceed for {0}", argument);
                return;
            }
            try {
                Source src = Source.create(d);
                Collection<Source> sources = Collections.singleton(src);
                Future<?> oldFuture = future.get();
                if (oldFuture != null && !oldFuture.isDone()) {
                    LOG.log(Level.FINEST, "Cancel a previously scheduled run");
                    oldFuture.cancel(true);
                }
                future.set(ParserManager.parseWhenScanFinished(sources, ut));
            } catch (ParseException ex) {
                LOG.log(Level.FINE, "Exception parsing", ex);
            }
        } else {
            LOG.log(Level.FINE, "Document is absent");
        }
    }

    interface OwnableTask {

        // all this to deal with inner classes and generics
        GeneralHighlighter<?> owner();
    }

    // The parsing module seems to assume that UserTask instances are long-lived
    // and have a very stable identity, which is utilized when asking the parser
    // for a result associated with a task
    private final UT ut = new UT();

    final class UT extends UserTask implements OwnableTask {

        @Override
        public void run(ResultIterator ri) throws Exception {
            LOG.log(Level.FINEST, "parseWhenScanFinished completed on {0}", Thread.currentThread());
            try {
                withParseResult(doc, ri, getArgument());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Exception rebuilding highlights", e);
            }
        }

        @Override
        public String toString() {
            return "Task-" + GeneralHighlighter.this.toString();
        }

        @Override
        public GeneralHighlighter<?> owner() {
            return GeneralHighlighter.this;
        }

        @Override
        public boolean equals(Object o) {
            return o == this ? true
                    : o != null && o instanceof OwnableTask
                    && ((OwnableTask) o).owner() == owner();
        }

        @Override
        public int hashCode() {
            return owner().hashCode();
        }
    }

    private Extraction deriveSemanticsObject(Parser.Result result) {
        if (result instanceof ExtractionParserResult) {
            return ((ExtractionParserResult) result).extraction();
        }
        return null;
    }

    private void refresh(Document doc, Parser.Result result, T argument) {
        Extraction semantics = deriveSemanticsObject(result);
        if (semantics == null) { // should be fixed now
            FileObject fo = result.getSnapshot().getSource().getFileObject();
            Logger.getLogger(GeneralHighlighter.class.getName()).log(Level.WARNING, "Null "
                    + "semantic parser for " + fo == null ? "null" : fo.getPath());
            return;
        }
        LOG.log(Level.FINEST, "Call refresh now");
        OffsetsBag bag = getHighlightsBag();
        if (bag != null) {
            refresh(doc, argument, semantics, result, bag);
        }
    }

    private void withParseResult(Document doc, ResultIterator ri, T argument) throws Exception {
        Parser.Result res = ri.getParserResult();
        LOG.log(Level.FINEST, "Got parse result {0}", res);
        if (res instanceof ExtractionParserResult) {
            refresh(doc, res, argument);
        }
    }

    protected static final <R extends Parser.Result & ExtractionParserResult> Function<R, Extraction> findExtraction() {
        return res -> {
            return res.extraction();
        };
    }

    static final class DocumentOriented<T> extends GeneralHighlighter<T> implements DocumentListener {

        @SuppressWarnings("LeakingThisInConstructor")
        public DocumentOriented(Context ctx, int refreshDelay, AntlrHighlighter implementation) {
            super(ctx, refreshDelay, implementation);
            Document doc = ctx.getDocument();
            doc.addDocumentListener(WeakListeners.document(this, doc));
            scheduleRefresh();
        }

        @Override
        protected void postInit(JTextComponent pane, Document doc) {
            if (pane != null) {
                scheduleRefresh();
            }
        }

        @Override
        public final void insertUpdate(DocumentEvent e) {
            scheduleRefresh();
        }

        @Override
        public final void removeUpdate(DocumentEvent e) {
            scheduleRefresh();
        }

        @Override
        public final void changedUpdate(DocumentEvent e) {
            scheduleRefresh();
        }
    }

    static final class CaretOriented extends GeneralHighlighter<Integer> implements CaretListener {

        private Reference<JTextComponent> component;
        public CaretOriented(Context ctx, int refreshDelay, AntlrHighlighter implementation) {
            super(ctx, refreshDelay, implementation);
        }

        @Override
        protected void postInit(JTextComponent pane, Document doc) {
            if (pane != null) {
                component = new WeakReference<>(pane);
                CaretListener cl = WeakListeners.create(CaretListener.class, this, pane);
                pane.addCaretListener(cl);
            }
        }

        @Override
        protected Integer getArgument() {
            JTextComponent comp = component.get();
            if (comp == null) {
                return -1;
            }
            int start = comp.getSelectionStart();
            int end = comp.getSelectionEnd();
            if (start == end) {
                return end;
            }
            return -2;
        }

        @Override
        protected boolean shouldProceed(Integer argument) {
            return argument >= 0;
        }

        @Override
        public void caretUpdate(CaretEvent e) {
            OffsetsBag bag = getHighlightsBag();
            if (bag != null) {
                bag.clear();
                scheduleRefresh();
            }
        }
    }
}

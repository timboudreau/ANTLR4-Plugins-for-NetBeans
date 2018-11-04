package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Mutex;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 * Base class for highlighters that takes care of most of the boilerplate of
 * listening and re-running as needed.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractAntlrHighlighter<T, R extends Parser.Result, S> implements Runnable {

    protected final OffsetsBag bag;
    protected final WeakReference<Document> weakDoc;

    protected static final RequestProcessor threadPool = new RequestProcessor("antlr-highlighting", 3, true);
    protected final static int REFRESH_DELAY = 100;
    private RequestProcessor.Task refreshTask;
    protected JTextComponent comp;
    private final Class<R> parserResultType;
    private final Function<R, S> semanticsDeriver;
    protected final Logger LOG = Logger.getLogger(getClass().getName());

//    {
//        if (getClass().getName().startsWith("Ad")) {
//            LOG.setLevel(Level.ALL);
//        }
//    }
    public AbstractAntlrHighlighter(Document doc, Class<R> parserResultType, Function<R, S> semanticsDeriver) {
        this.parserResultType = parserResultType;
        this.semanticsDeriver = semanticsDeriver;
        bag = new OffsetsBag(doc);
        weakDoc = new WeakReference<>(doc);
        DataObject dobj = NbEditorUtilities.getDataObject(doc);
        LOG.log(Level.FINE, "Created a highlighter for {0} has DataObject? {1}",
                new Object[]{doc, dobj != null});
        // XXX pass the Context object, which can return the text component
        if (dobj != null) {
            // The only time this is not called on the event thread
            // is during a module reload
            Mutex.EVENT.readAccess((Runnable) () -> {
                EditorCookie pane = dobj.getLookup().lookup(EditorCookie.class);
                JEditorPane[] panes = pane.getOpenedPanes();
                if (panes != null && panes.length > 0) {
                    comp = panes[0];
                    postInit(panes[0], dobj);
                } else {
                    postInit(null, dobj);
                }
            });
        }
    }

    protected void postInit(JTextComponent pane, DataObject ob) {
        // do nothing
    }

    protected Optional<JTextComponent> component() {
        if (comp != null) {
            return Optional.of(comp);
        }
        Optional<Document> doc = document();
        if (doc.isPresent()) {
            DataObject dobj = NbEditorUtilities.getDataObject(doc.get());
            if (dobj != null) {
                EditorCookie pane = dobj.getLookup().lookup(EditorCookie.class);
                JEditorPane[] panes = pane.getOpenedPanes();
                if (panes != null && panes.length > 0) {
                    comp = panes[0];
                    return Optional.of(panes[0]);
                }
            }
        } else {
            LOG.log(Level.FINE, "Document has disappeared");
        }
        return Optional.empty();
    }

    protected final void scheduleRefresh() {
        Future<?> fut = future.get();
        if (fut != null) {
//            LOG.log(Level.FINEST, "Cancel a previously "
//                    + "scheduled run for new refresh");
//            fut.cancel(true);
        }
        if (refreshTask == null) {
            refreshTask = threadPool.create(this);
        }
        refreshTask.schedule(REFRESH_DELAY);
    }

    public String toString() {
        return getClass().getSimpleName();
    }

    protected final Optional<Document> document() {
        return Optional.ofNullable(weakDoc.get());
    }

    public final OffsetsBag getHighlightsBag() {
        return bag;
    }

    private AtomicReference<Future<?>> future = new AtomicReference<>();

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
//                    LOG.log(Level.FINEST, "Cancel a previously scheduled run");
//                    oldFuture.cancel(true);
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
        AbstractAntlrHighlighter<?, ?, ?> owner();
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
                withParseResult(document().get(), ri, getArgument());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Exception rebuilding highlights", e);
            }
        }

        public String toString() {
            return "Task-" + AbstractAntlrHighlighter.this.toString();
        }

        @Override
        public AbstractAntlrHighlighter<?, ?, ?> owner() {
            return AbstractAntlrHighlighter.this;
        }

        public boolean equals(Object o) {
            return o == this ? true
                    : o != null && o instanceof OwnableTask
                    && ((OwnableTask) o).owner() == owner();
        }

        public int hashCode() {
            return owner().hashCode();
        }
    }

    private final S deriveSemanticsObject(R obj) {
        return semanticsDeriver.apply(obj);
    }

    private final void refresh(Document doc, R result, T argument) {
        S semantics = deriveSemanticsObject(result);
        if (semantics == null) { // should be fixed now
            FileObject fo = result.getSnapshot().getSource().getFileObject();
            Logger.getLogger(AbstractAntlrHighlighter.class.getName()).log(Level.WARNING, "Null "
                    + "semantic parser for " + fo == null ? "null" : fo.getPath());
            return;
        }
        LOG.log(Level.FINEST, "Call refresh now");
        refresh(doc, argument, semantics, result);
    }

    protected abstract void refresh(Document doc, T argument, S semantics, R result);

    private void withParseResult(Document doc, ResultIterator ri, T argument) throws Exception {
        Parser.Result res = ri.getParserResult();
        LOG.log(Level.FINEST, "Got parse result {0}", res);
        if (parserResultType.isInstance(res)) {
            refresh(doc, parserResultType.cast(res), argument);
        } else {
            LOG.log(Level.FINEST, "Parse result is not a {0}: {1}", new Object[]{
                parserResultType.getName(), res});

        }
    }

    public static final Function<ANTLRv4ParserResult, ANTLRv4SemanticParser> GET_SEMANTICS
            = (ANTLRv4ParserResult result) -> {
                return result.semanticParser();
            };

    public static abstract class DocumentOriented<T, R extends Parser.Result, S> extends AbstractAntlrHighlighter<T, R, S> implements DocumentListener {

        public DocumentOriented(Document doc, Class<R> type, Function<R, S> func) {
            super(doc, type, func);
            doc.addDocumentListener(WeakListeners.document(this, doc));
            scheduleRefresh();
        }

        @Override
        protected void postInit(JTextComponent pane, DataObject ob) {
            if (pane != null) {
                scheduleRefresh();
            }
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            scheduleRefresh();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            scheduleRefresh();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            scheduleRefresh();
        }
    }

    public static abstract class CaretOriented<R extends Parser.Result, S> extends AbstractAntlrHighlighter<Integer, R, S> implements CaretListener {

        public CaretOriented(Document doc, Class<R> type, Function<R, S> func) {
            super(doc, type, func);
        }

        @Override
        protected void postInit(JTextComponent pane, DataObject ob) {
            if (pane != null) {
                pane.addCaretListener(this);
            }
        }

        @Override
        protected Integer getArgument() {
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
            bag.clear();
            scheduleRefresh();
        }
    }
}

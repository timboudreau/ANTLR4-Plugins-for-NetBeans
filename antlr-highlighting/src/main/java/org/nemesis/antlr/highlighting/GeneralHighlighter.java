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
package org.nemesis.antlr.highlighting;

import java.awt.EventQueue;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
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

    protected static final RequestProcessor THREAD_POOL = new RequestProcessor("antlr-highlighting", 5, true);
    private final AtomicReference<Future<?>> future = new AtomicReference<>();
    protected final static int REFRESH_DELAY = 100;
    protected final Document doc;
    private RequestProcessor.Task refreshTask;
    protected final Logger LOG = Logger.getLogger(getClass().getName());
    private final int refreshDelay;
    private final AntlrHighlighter implementation;
    private final HighlighterBagKey key;

    GeneralHighlighter(Context ctx, int refreshDelay, AntlrHighlighter implementation) {
        doc = ctx.getDocument();
        this.refreshDelay = refreshDelay <= 0 ? REFRESH_DELAY : refreshDelay;
        this.implementation = implementation;
        key = new HighlighterBagKey(this, implementation);
        OffsetsBag bag = new OffsetsBag(doc, implementation.mergeHighlights());
        // This avoids us holding a reference from GeneralHighlighter -> OffsetsBag -> Document
        // forever
        doc.putProperty(key, bag);
        log(Level.FINE, "Create for {0} with {1} for key {2}", doc, implementation, key);
        // Ensure we access the text component on the event thread, since we
        // may add listeners to it
        Mutex.EVENT.readAccess(() -> {
            JTextComponent comp = ctx.getComponent();
            postInit(comp, doc);
        });
    }

    @Override
    public String toString() {
        return "GeneralHighlighter{" + implementation + " key=" + key + "}";
    }

    final void log(Level level, String msg, Object... args) {
        LOG.log(level, msg, args);
    }

    protected final void refresh(Document doc, T argument, Extraction ext, OffsetsBag bag) {
        Integer caret = argument instanceof Integer ? (Integer) argument : null;
        implementation.refresh(doc, ext, bag, caret);
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
        if (Thread.interrupted()) {
            LOG.log(Level.FINEST, "Skip highlighting for thread interrupt", this);
            return;
        }
        T argument = getArgument();
        LOG.log(Level.FINEST, "Invoke a parse with {0} for {1} with {2}",
                new Object[]{doc, this, argument});

        if (!shouldProceed(argument)) {
            LOG.log(Level.FINEST, "Should not proceed for {0}", argument);
            return;
        }
        try {
            Source src = Source.create(doc);
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
            LOG.log(Level.FINEST, "parseWhenScanFinished completed on {0} "
                    + "for {1}", new Object[]{
                        Thread.currentThread(),
                        GeneralHighlighter.this
                    });
            Extraction ext = findExtraction(ri.getParserResult());
            // Ensure we don't do a bunch of long-running stuff while
            // holding the parser manager's lock
            THREAD_POOL.submit(() -> {
                try {
                    withParseResult(doc, ext, getArgument());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Exception rebuilding highlights", e);
                }
            });
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

    private Extraction findExtraction(Parser.Result result) {
        if (result instanceof ExtractionParserResult) {
            return ((ExtractionParserResult) result).extraction();
        }
        return null;
    }

    private void refresh(Document doc, Extraction semantics, T argument) {
        LOG.log(Level.FINEST, "{0} update highlights for {1}",
                new Object[]{doc, this});
        OffsetsBag papasGotA = getHighlightsBag();
        if (papasGotA != null) {
            OffsetsBag brandNewBag = new OffsetsBag(doc);
            refresh(doc, argument, semantics, brandNewBag);
            if (EventQueue.isDispatchThread()) {
                papasGotA.setHighlights(brandNewBag);
            } else {
                // We can get into complex deadlocks with the parser
                // when the document picks up changes fired by the bag
                // on a non-EQ thread
                EventQueue.invokeLater(() -> {
                    try {
                        papasGotA.setHighlights(brandNewBag);
                    } finally {
                        brandNewBag.discard();
                    }
                });
            }
        } else {
            LOG.log(Level.WARNING, "Got null OffsetsBag for {0} in {1}",
                    new Object[]{doc, this});
        }
    }

    private void withParseResult(Document doc, Extraction extraction, T argument) throws Exception {
        LOG.log(Level.FINEST, "{0} got parse result", new Object[]{this});
        if (extraction == null) {
            LOG.log(Level.INFO, "Recieved null parse result", new Exception());
            return;
        }
        refresh(doc, extraction, argument);
    }

    protected static final <R extends Parser.Result & ExtractionParserResult> Function<R, Extraction> findExtraction() {
        return res -> {
            return res.extraction();
        };
    }

    static final class DocumentOriented<T> extends GeneralHighlighter<T> implements DocumentListener, LookupListener {

        private final Lookup.Result<FontColorSettings> res;

        @SuppressWarnings("LeakingThisInConstructor")
        public DocumentOriented(Context ctx, int refreshDelay, AntlrHighlighter implementation) {
            super(ctx, refreshDelay, implementation);
            doc.addDocumentListener(WeakListeners.document(this, doc));
            MimePath mimePath = MimePath.parse(NbEditorUtilities.getMimeType(doc));
            res = MimeLookup.getLookup(mimePath).lookupResult(FontColorSettings.class);
            res.addLookupListener(this);
            res.allInstances();
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

        @Override
        public void resultChanged(LookupEvent ev) {
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
            Caret caret = comp.getCaret();
            if (caret == null) {
                // avoid
                // java.lang.NullPointerException
                // at java.desktop/javax.swing.text.JTextComponent.getSelectionStart(JTextComponent.java:1825)
                return -3;
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

    // Just a loggable key object of a type that cannot possibly
    // be created outside this class.
    private static final class HighlighterBagKey {

        private final int idHash;
        private final int idHash2;

        HighlighterBagKey(GeneralHighlighter<?> h, AntlrHighlighter implementation) {
            idHash = System.identityHashCode(h);
            idHash2 = System.identityHashCode(implementation);
        }

        @Override
        public String toString() {
            return "GeneralHighlighter-bag-" + idHash + "-" + idHash2;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + this.idHash;
            hash = 67 * hash + this.idHash2;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (!(obj instanceof HighlighterBagKey)) {
                return false;
            }
            final HighlighterBagKey other = (HighlighterBagKey) obj;
            return this.idHash == other.idHash && this.idHash2 == other.idHash2;
        }
    }
}

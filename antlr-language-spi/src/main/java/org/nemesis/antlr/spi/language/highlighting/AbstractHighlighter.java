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
package org.nemesis.antlr.spi.language.highlighting;

import com.mastfrog.function.state.Bool;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Mutex;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 * A generic highlighter or error annotator, which correctly implements several
 * things that can be tricky:
 * <ul>
 * <li>When to start and stop listening on the document</li>
 * <li>Updating highlights without causing flashing</li>
 * </ul>
 * This class makes no particular assumptions about how updating of highlights
 * is (re-)triggered - it simply provides the hooks to detect when to start and
 * stop listening to the highlighting context (editor), and a way to update
 * highlights that will avoid flashing and other bad behavior that are common
 * problems.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractHighlighter {

    private static final Map<Class<?>, RequestProcessor> rpByType = new HashMap<>();
    protected final HighlightsLayerFactory.Context ctx;
    private final CompL compl = new CompL();
    private final OffsetsBag bag;
    private final boolean mergeHighlights;
    protected final Logger LOG;

    protected AbstractHighlighter(HighlightsLayerFactory.Context ctx) {
        this(ctx, true);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    protected AbstractHighlighter(HighlightsLayerFactory.Context ctx, boolean mergeHighlights) {
        this.LOG = Logger.getLogger(getClass().getName());
        this.ctx = ctx;
        this.mergeHighlights = mergeHighlights;
        Document doc = ctx.getDocument();
        // XXX listen for changes, etc
        JTextComponent theEditor = ctx.getComponent();
        // Listen for component events
        theEditor.addComponentListener(WeakListeners.create(
                ComponentListener.class, compl, theEditor));
        // Also listen for ancestor events, because component events can be
        // deceptive, but the combination of both ensures we catch all adds/removes
        theEditor.addPropertyChangeListener("ancestor",
                WeakListeners.propertyChange(compl, "ancestor", theEditor));
        bag = new OffsetsBag(ctx.getDocument(), mergeHighlights);
        LOG.log(Level.FINE, "Create {0} for {1}", new Object[]{getClass().getName(), doc});
        // Ensure we are initialized, and don't assume we are constructed in the
        // event thread; calling isShowing() in any other thread is unsafe
        EventQueue.invokeLater(() -> {
            if (ctx.getComponent().isShowing()) {
                LOG.log(Level.FINER, "Component is showing, set active");
                compl.setActive(true);
            }
        });
    }

    /**
     * Called when the editor this instance is highlighting is made visible.
     * Perform whatever logic you need to begin listening to a file or document
     * for changes that should trigger rerunning highlighting, and enqueue an
     * initial highlighting run, here.
     *
     * @param file The file
     * @param doc The document
     */
    protected abstract void activated(FileObject file, Document doc);

    /**
     * <i>Fully</i> detach listeners here, cancel any pending tasks, etc.
     *
     * @param file The file
     * @param doc The document
     */
    protected abstract void deactivated(FileObject file, Document doc);

    /**
     * To update highlighting of the document, call this method with a closure
     * that accepts an OffsetsBag, and returns <code>true</code> if there were
     * any highlights added to the bag, and <code>false</code> if the bag was
     * left empty (any existing highlights created by previous calls will be
     * cleared0.
     *
     * @param highlightsUpdater A predicate which modifies the empty OffsetsBag
     * it is passed, adding highlights where needed, and returns true if it
     * added any highlights to it, false if not.
     */
    protected final void updateHighlights(Predicate<OffsetsBag> highlightsUpdater) {
        OffsetsBag brandNewBag = new OffsetsBag(ctx.getDocument(), mergeHighlights);
        Bool doUpdate = Bool.create();
        try {
            doUpdate.set(highlightsUpdater.test(brandNewBag));
        } finally {
            Mutex.EVENT.readAccess(() -> {
                if (doUpdate.getAsBoolean()) {
                    bag.setHighlights(brandNewBag);
                } else {
                    bag.clear();
                }
                brandNewBag.discard();
            });
        }
    }

    /**
     * Returns true if the document is visible to the user and highlighting
     * should be performed.
     *
     * @return
     */
    protected final boolean isActive() {
        return compl.active;
    }

    final HighlightsContainer bag() {
        return bag;
    }

    /**
     * Returns a single-thread request processor created for all instances of
     * this class, which can be used for asynchronous tasks while guaranteeing
     * more than one of such tasks cannot be run concurrently.
     *
     * @return A request processor
     */
    protected final RequestProcessor threadPool() {
        return threadPool(getClass());
    }

    @SuppressWarnings("DoubleCheckedLocking")
    private static final RequestProcessor threadPool(Class<?> type) {
        RequestProcessor result = rpByType.get(type);
        if (result == null) {
            synchronized (rpByType) {
                result = rpByType.get(type);
                if (result == null) {
                    result = new RequestProcessor(type.getName() + "-subscribe", 1, false);
                    rpByType.put(type, result);
                }
            }
        }
        return result;
    }

    public static final HighlightsLayerFactory factory(String layerTypeId, ZOrder zOrder,
            Function<? super HighlightsLayerFactory.Context, ? extends AbstractHighlighter> highlighterCreator) {
        return new Factory(layerTypeId, zOrder, highlighterCreator);
    }

    private static class Factory implements HighlightsLayerFactory {

        private static final HighlightsLayer[] EMPTY = new HighlightsLayer[0];
        private final ZOrder zOrder;
        private final Function<? super Context, ? extends AbstractHighlighter> highlighterCreator;
        private final String layerTypeId;

        Factory(String layerTypeId, ZOrder zorder, Function<? super Context, ? extends AbstractHighlighter> highlighterCreator) {
            this.zOrder = zorder;
            this.highlighterCreator = highlighterCreator;
            this.layerTypeId = layerTypeId;
        }

        @Override
        public HighlightsLayer[] createLayers(HighlightsLayerFactory.Context ctx) {
            Document doc = ctx.getDocument();
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if (fo == null) { // preview pane, etc.
                return EMPTY;
            }
            AbstractHighlighter highlighter = highlighterCreator.apply(ctx);
            return new HighlightsLayer[]{
                HighlightsLayer.create(layerTypeId, zOrder,
                true, highlighter.bag())
            };
        }
    }

    private final class CompL extends ComponentAdapter implements Runnable, PropertyChangeListener {

        // Volatile because while highlighters are only attached and detached from the
        // event thread, it can be read from any thread that checks state
        private volatile boolean active;
        private final RequestProcessor.Task task = threadPool().create(this);

        @Override
        public void componentShown(ComponentEvent e) {
            LOG.log(Level.FINEST, "Component shown {0}", ctx.getDocument());
            setActive(true);
        }

        @Override
        public void componentHidden(ComponentEvent e) {
            LOG.log(Level.FINEST, "Component hidden {0}", ctx.getDocument());
            setActive(false);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("ancestor".equals(evt.getPropertyName())) {
                setActive(evt.getNewValue() != null);
            }
        }

        void setActive(boolean active) {
            boolean act = this.active;
            if (active != act) {
                this.active = act = active;
                LOG.log(Level.FINE, "Set active to {0} for {1}", new Object[]{act, ctx.getDocument()});
                if (act) {
                    task.schedule(350);
                } else {
                    task.cancel();
                    unsubscribe();
                }
            }
        }

        void unsubscribe() {
            Document doc = ctx.getDocument();
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            try {
                synchronized (this) {
                    LOG.log(Level.FINE, "Activating against {0}", fo);
                    deactivated(fo, doc);
                }
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Exception deactivating against " + fo + " / " + doc, ex);
            }
        }

        @Override
        public void run() {
            Document doc = ctx.getDocument();
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if (active) {
                try {
                    synchronized (this) {
                        LOG.log(Level.FINE, "Activating against {0}", fo);
                        activated(fo, doc);
                    }
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, "Exception activating against " + fo + " / " + doc, ex);
                }
            } else {
                LOG.log(Level.FINE, "Not active, don't subscribe to rebuilds of {0}", ctx.getDocument());
            }
        }
    }
}

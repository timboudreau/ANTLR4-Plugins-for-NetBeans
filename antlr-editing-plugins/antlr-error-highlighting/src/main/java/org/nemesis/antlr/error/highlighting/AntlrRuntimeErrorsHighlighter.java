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
package org.nemesis.antlr.error.highlighting;

import com.mastfrog.function.state.Bool;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.error.highlighting.spi.AntlrHintGenerator;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRuntimeErrorsHighlighter implements Subscriber {

    protected final OffsetsBag bag;
    static final Logger LOG = Logger.getLogger(
            AntlrRuntimeErrorsHighlighter.class.getName());

    private final Context ctx;
    private final static RequestProcessor subscribe = new RequestProcessor("antlr-runtime-errors", 1, false);
    private final CompL compl = new CompL();
    private ComponentListener cl;

    @SuppressWarnings("LeakingThisInConstructor")
    AntlrRuntimeErrorsHighlighter(Context ctx) {
        this.ctx = ctx;
        Document doc = ctx.getDocument();
        bag = new OffsetsBag(doc, true);
        // XXX listen for changes, etc
        JTextComponent c = ctx.getComponent();
        c.addComponentListener(cl = WeakListeners.create(
                ComponentListener.class, compl, c));
        c.addPropertyChangeListener("ancestor",
                WeakListeners.propertyChange(compl, c));
        // This can race - double check
        EventQueue.invokeLater(() -> {
            if (ctx.getComponent().isShowing()) {
                LOG.log(Level.FINER, "Component is showing, set active");
                compl.setActive(true);
            }
        });
        LOG.log(Level.FINE, "Create an AntlrRuntimeErrorsHighlighter for {0}", doc);
    }

    private final class CompL extends ComponentAdapter implements Runnable, PropertyChangeListener {

        // Gets subscribing, which can trigger parsing of poms of all
        // dependencies, out of the critical path of opening an editor,
        // and turns listening off when the component is not visible
        private Runnable unsubscriber;
        private boolean active;
        private final RequestProcessor.Task task = subscribe.create(this);

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
            if (active != this.active) {
                this.active = active;
                LOG.log(Level.FINE, "Set active to {0} for {1}", new Object[]{active, ctx.getDocument()});
                if (active) {
                    task.schedule(350);
                } else {
                    task.cancel();
                    unsubscribe();
                }
            }
        }

        void unsubscribe() {
            if (unsubscriber != null) {
                LOG.log(Level.FINE, "Unsubscribe from rebuilds of {0}", ctx.getDocument());
                unsubscriber.run();
                unsubscriber = null;
            }
        }

        @Override
        public void run() {
            if (active) {
                FileObject fo = NbEditorUtilities.getFileObject(ctx.getDocument());
                if (fo != null) {
                    LOG.log(Level.FINE, "Subscribing to rebuilds of {0}", fo);
                    unsubscriber = RebuildSubscriptions.subscribe(fo, AntlrRuntimeErrorsHighlighter.this);
                } else {
                    LOG.log(Level.WARNING, "No FileObject to subscribe to for {0}", ctx.getDocument());
                }
            } else {
                LOG.log(Level.FINE, "Not active, don't subscribe to rebuilds of {0}", ctx.getDocument());
            }
        }
    }

    HighlightsContainer bag() {
        return bag;
    }
    private final AtomicInteger uses = new AtomicInteger();

    @Override
    public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {
        if (res == null || !fixes.active()) {
            LOG.log(Level.FINER, "no result or no fixes, skip hints: {0}", extraction.source());
            return;
        }

        LOG.log(Level.FINE, "onRebuilt {0}", extraction.source());
        Optional<Document> doc = extraction.source().lookup(Document.class);
        if (!doc.isPresent()) {
            LOG.log(Level.FINE, "Doc not present from source {0}", extraction.source());
            return;
        }
        Document d = doc.get();
        if (!d.equals(ctx.getDocument())) {
            LOG.log(Level.INFO, "Called with wrong extraction: {0} expecting {1}",
                    new Object[]{d, ctx.getDocument()});
            // Currently we can be notified about any document in the
            // project
            return;
        }
        int runIndex = uses.getAndIncrement();
        try {
            long lm = extraction.source().lastModified();
            if (extraction.isSourceProbablyModifiedSinceCreation()) {
                LOG.log(Level.INFO, "Discarding error highlight pass for {0} "
                        + " - source last modified date is {1}ms newer "
                        + "than at the time of parsing. It should be reparsed "
                        + "again presently.", new Object[]{
                            extraction.source(), (lm - res.grammarFileLastModified)});
                return;
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        PositionFactory positions = PositionFactory.forDocument(d);

        OffsetsBag brandNewBag = new OffsetsBag(ctx.getDocument(), true);
        Bool anyHighlights = Bool.create();
        boolean usingResults = true;
        try {
            for (AntlrHintGenerator gen : AntlrHintGenerator.all()) {
                try {
                    boolean highlightsAdded = gen.generateHints(tree, extraction, res, populate, fixes, d, positions, brandNewBag);
                    if (highlightsAdded) {
                        anyHighlights.set();
                    }
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
                if (runIndex != uses.get()) {
//                    LOG.log(Level.INFO, "Not finishing hint run due to reentry {0}", extraction.tokensHash());
//                    usingResults = false;
//                    break;
                }
            }
        } finally {
            if (!usingResults) {
                brandNewBag.discard();
            } else {
                Mutex.EVENT.readAccess(() -> {
                    try {
                        if (anyHighlights.getAsBoolean()) {
                            LOG.log(Level.FINEST, "Update highlights");
                            bag.setHighlights(brandNewBag);
                        } else {
                            LOG.log(Level.FINEST, "No highlights; clear bag");
                            bag.clear();
                        }
                    } finally {
                        brandNewBag.discard();
                    }
                });
            }
        }
    }
}

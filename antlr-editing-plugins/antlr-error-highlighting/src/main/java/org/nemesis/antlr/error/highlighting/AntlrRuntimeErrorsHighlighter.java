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

import org.nemesis.antlr.spi.language.highlighting.AbstractHighlighter;
import com.mastfrog.function.state.Bool;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.error.highlighting.spi.AntlrHintGenerator;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 * Implements error highlighting and hints, using the error output from Antlr
 * itself, for Antlr grammar files. Has an SPI for plugging in individual hint
 * generators.
 *
 * @author Tim Boudreau
 */
public class AntlrRuntimeErrorsHighlighter extends AbstractHighlighter implements Subscriber {

    private Runnable unsubscriber;

    public AntlrRuntimeErrorsHighlighter(HighlightsLayerFactory.Context ctx) {
        super(ctx, true);
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, "Create an errors highlighter", new Exception("Create error higlighter " + id()));
        }
    }

    public String toString() {
        return "ErrHl(" + id() + ")";
    }

    private String id() {
        Object o = ctx.getDocument().getProperty(StreamDescriptionProperty);
        String fileName;
        if (o instanceof DataObject) {
            fileName = ((DataObject) o).getName();
        } else if (o instanceof FileObject) {
            fileName = ((FileObject) o).getName();
        } else {
            fileName = Objects.toString(o);
        }
        JTextComponent editor = ctx.getComponent();
        boolean displayable = editor == null ? false : editor.isDisplayable();
        boolean showing = editor == null ? false : editor.isShowing();
        int h = editor == null ? 0 : System.identityHashCode(editor);
        return fileName + "-" + h + " displayable=" + displayable + " showing=" + showing;
    }

    @Override
    protected void activated(FileObject file, Document doc) {
        if (file == null) {
            return;
        }
        unsubscriber = RebuildSubscriptions.subscribe(file, this);
    }

    @Override
    protected void deactivated(FileObject file, Document doc) {
        Runnable un = unsubscriber;
        unsubscriber = null;
        if (un != null) {
            un.run();
        }
    }

    private final AtomicInteger uses = new AtomicInteger();


    @Override
    public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {

        // Once late addition of hints is working without clobbering things,
        // this should be the way it is done.
        // This code currently runs on every single parser result creation,
        // and for every change, multiple threads do reparses, so while
        // it is very fast, it should run once per flurry-of-parses, delayed
        // and coalesced
//        coa.coalesce(tree, extraction, res, populate, fixes);
//    }
//    public void internalOnRebuilt(GrammarFileContext tree,
//            String mimeType, Extraction extraction,
//            AntlrGenerationResult res, ParseResultContents populate,
//            Fixes fixes) {
        if (res == null || !fixes.active() || !isActive() || extraction == null) {
            LOG.log(Level.FINER, "no result? {0}; fixes inactive? {1}, or inactive? {2}. Skip hints for : {3}",
                    new Object[]{(res == null), !fixes.active(), !isActive(), extraction == null
                        ? "null" : extraction.source().name()});
            return;
        }

        LOG.log(Level.FINE, "onRebuilt {0} in {1}", new Object[]{extraction.source().name(), this});
        Optional<Document> doc = extraction.source().lookup(Document.class);
        if (!doc.isPresent()) {
            LOG.log(Level.FINE, "Doc not present from source {0} for {1}",
                    new Object[]{extraction.source().name(), this});
            return;
        }
        Document d = doc.get();
        if (!d.equals(ctx.getDocument())) {
            LOG.log(Level.INFO, "Called {0} with extraction of wrong doc: {1}",
                    new Object[]{this, d});
            // Currently we can be notified about any document in the
            // project
            return;
        }
        try {
            long lm = extraction.source().lastModified();
            if (extraction.isSourceProbablyModifiedSinceCreation()) {
                LOG.log(Level.INFO, "Discarding error highlight pass for {0} "
                        + " - source last modified date is {1}ms newer "
                        + "than at the time of parsing. It should be reparsed "
                        + "again presently.", new Object[]{
                            extraction.source(), (lm - res.grammarFileLastModified)});
//                return;
            }
        } catch (Exception | Error ex) {
            LOG.log(Level.SEVERE, "Checking extraction up to date for "
                    + extraction.source().name(), ex);
            return;
        }
        PositionFactory positions = PositionFactory.forDocument(d);
        try {
            updateHighlights(brandNewBag -> {
                Bool anyHighlights = Bool.create();
                for (AntlrHintGenerator gen : AntlrHintGenerator.all()) {
                    try {
                        boolean highlightsAdded = gen.generateHints(tree,
                                extraction, res, populate, fixes, d, positions, brandNewBag);
                        if (highlightsAdded) {
                            LOG.log(Level.FINEST, "{0} added highlights", gen);
                            anyHighlights.set();
                        }
                    } catch (Exception | Error ex) {
                        LOG.log(Level.SEVERE, "Checking extraction up to date for "
                                + extraction.source().name(), ex);
                    }
                }
                return anyHighlights.getAsBoolean();
            });
        } finally {
            uses.getAndIncrement();
        }
    }

    int runIndex() {
        return uses.get();
    }

    @MimeRegistrations({
        @MimeRegistration(mimeType = ANTLR_MIME_TYPE,
                service = HighlightsLayerFactory.class, position = 50)
    })
    public static HighlightsLayerFactory factory() {
        return AbstractHighlighter.factory("antlr-runtime-errors",
                ZOrder.SYNTAX_RACK,
                ctx -> new AntlrRuntimeErrorsHighlighter(ctx), true);
    }

    /*
    private final Coalescer coa = new Coalescer();

    final class Coalescer implements Runnable {

        private final AtomicReference<RebuildInfo> info = new AtomicReference<>();
        private final RequestProcessor.Task task = threadPool().create(this);

        @Override
        public void run() {
            RebuildInfo local = this.info.get();
            internalOnRebuilt(local.tree, ANTLR_MIME_TYPE, local.extraction, local.res, local.populate, local.fixes);
        }

        void coalesce(GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
            if (tree == null) {
                tree = NbAntlrUtils.currentTree(GrammarFileContext.class);
            }
            RebuildInfo nue = new RebuildInfo(tree, extraction, res, populate, fixes);
            RebuildInfo old = info.get();
            if (old == null || old.extraction.sourceLastModifiedAtExtractionTime() < extraction.sourceLastModifiedAtExtractionTime()) {
                info.set(nue);
            }
            task.schedule(350);
        }
    }

    private static final class RebuildInfo {

        private final ANTLRv4Parser.GrammarFileContext tree;
        private final Extraction extraction;
        private final AntlrGenerationResult res;
        private final ParseResultContents populate;
        private final Fixes fixes;

        public RebuildInfo(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
            this.tree = tree;
            this.extraction = extraction;
            this.res = res;
            this.populate = populate;
            this.fixes = fixes;
        }
    }
     */
}

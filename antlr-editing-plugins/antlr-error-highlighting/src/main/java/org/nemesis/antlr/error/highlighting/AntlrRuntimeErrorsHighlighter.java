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
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
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
import org.openide.util.Exceptions;

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
    }

    public String toString() {
        return "ARErrorsHighlight(" + ctx.getDocument().getProperty(StreamDescriptionProperty)
                + ")";
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
        currentTokensHash = null;
        unsubscriber = null;
        if (un != null) {
            un.run();
        }
    }

    private final AtomicInteger uses = new AtomicInteger();
    private volatile String currentTokensHash;

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
        if (res == null || !fixes.active() || !isActive()) {
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
//        if (Objects.equals(currentTokensHash, extraction.tokensHash())) {
//            LOG.log(Level.INFO, "Called with already processed tokens hash - ignore");
//            return;
//        }
        currentTokensHash = extraction.tokensHash();
        updateHighlights(brandNewBag -> {
            Bool anyHighlights = Bool.create();
            boolean usingResults = true;
            for (AntlrHintGenerator gen : AntlrHintGenerator.all()) {
                try {
                    boolean highlightsAdded = gen.generateHints(tree, extraction, res, populate, fixes, d, positions, brandNewBag);
                    if (highlightsAdded) {
                        anyHighlights.set();
                    }
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
//                if (runIndex != uses.get()) {
//                    LOG.log(Level.INFO, "Not finishing hint run due to reentry of {0} with {1}",
//                            new Object[]{extraction.source(), extraction.tokensHash()});
//                    usingResults = false;
//                    break;
//                }
            }
            return usingResults && anyHighlights.getAsBoolean();
        });
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

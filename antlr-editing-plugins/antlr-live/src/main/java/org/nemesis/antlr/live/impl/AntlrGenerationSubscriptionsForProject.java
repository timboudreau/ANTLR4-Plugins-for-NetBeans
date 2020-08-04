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
package org.nemesis.antlr.live.impl;

import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.util.path.UnixPath;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.tools.StandardLocation;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.AntlrConstants;
import org.nemesis.antlr.live.BrokenSourceThrottle;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.memory.AntlrGeneratorBuilder;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.ParseResultHook;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.subscription.MapCache;
import org.nemesis.antlr.subscription.MapFactories;
import org.nemesis.antlr.subscription.SetTypes;
import org.nemesis.antlr.subscription.Subscribable;
import org.nemesis.antlr.subscription.SubscribableBuilder;
import org.nemesis.antlr.subscription.SubscribableNotifier;
import org.nemesis.antlr.subscription.SubscribersStore;
import org.nemesis.antlr.subscription.SubscribersStoreController;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileModifications;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrGenerationSubscriptionsForProject extends ParseResultHook<ANTLRv4Parser.GrammarFileContext> implements Subscribable<FileObject, Subscriber> {

    private static final Logger LOG = Logger.getLogger(AntlrGenerationSubscriptionsForProject.class.getName());
    private static final RequestProcessor svc = new RequestProcessor("antlr-project-events", 5);
    private final Subscribable<FileObject, Subscriber> subscribableDelegate;
    private final SubscribableNotifier<? super FileObject, ? super AntlrRegenerationEvent> dispatcher;
    private final SubscribersStore<FileObject, Subscriber> subscribersStore;
    private final PerProjectJFSMappingManager mappingManager;
    private final MapCache<FileObject, ObjectGraph<UnixPath>> dependencyGraphCache;
    private final MapCache<FileObject, GrammarFileHashAndTimestamp> tokenHashCache;
    private final SubscribersStoreController<FileObject, Subscriber> subscribersManager;

    AntlrGenerationSubscriptionsForProject(Project project, JFSManager jfses, Runnable onProjectDeleted) {
        super(ANTLRv4Parser.GrammarFileContext.class);
        mappingManager = new PerProjectJFSMappingManager(project, jfses, onProjectDeleted, this::onFileReplaced);
        SubscribableBuilder.SubscribableContents<FileObject, FileObject, Subscriber, AntlrRegenerationEvent> sinfo = // need a defensive copy
        SubscribableBuilder.withKeys(FileObject.class).withEventApplier((FileObject fo, AntlrRegenerationEvent rebuildInfo, Collection<? extends Subscriber> subscribers) -> {
            for (Subscriber s : subscribers.toArray(new Subscriber[0])) {
                // need a defensive copy
                rebuildInfo.accept(s);
            }
        }).storingSubscribersIn(SetTypes.WEAK_HASH).threadSafe().withCoalescedAsynchronousEventDelivery(EventQueue::isDispatchThread, svc, MapFactories.EQUALITY, 2, TimeUnit.SECONDS).build();
        subscribableDelegate = sinfo.subscribable;
        dispatcher = sinfo.eventInput;
        subscribersStore = sinfo.store;
        subscribersManager = sinfo.subscribersManager;
        dependencyGraphCache = sinfo.caches.<ObjectGraph<UnixPath>>createCache(ObjectGraph.class, MapFactories.EQUALITY, fo -> null);
        tokenHashCache = sinfo.caches.<GrammarFileHashAndTimestamp>createCache(GrammarFileHashAndTimestamp.class, MapFactories.WEAK, fo -> null);
    } // need a defensive copy

    private void onFileReplaced(FileObject oldFile, FileObject newFile) {
        if (newFile == null && oldFile != null) {
            subscribableDelegate.destroyed(oldFile);
        } else if (oldFile != null && newFile != null && !oldFile.equals(newFile)) {
            Collection<? extends Subscriber> oldSubscribers = subscribersStore.subscribersTo(oldFile);
            if (oldSubscribers != null && !oldSubscribers.isEmpty()) {
                subscribersManager.removeAll(oldFile);
                for (Subscriber sub : oldSubscribers) {
                    subscribersManager.add(newFile, sub);
                }
            }
        }
    }

    long newestGrammarLastModified() {
        return mappingManager.newestGrammarLastModified();
    }

    Project die() {
        mappingManager.die();
        return mappingManager.project;
    }

    private void forceParse(FileObject file) {
        Document doc = mappingManager.documentFor(file);
        try {
            if (doc == null) {
                ParsingUtils.parse(file);
            } else {
                ParsingUtils.parse(doc);
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, file + "", ex);
        }
    }

    @Override
    public void subscribe(FileObject key, Subscriber consumer) {
        subscribableDelegate.subscribe(key, consumer);
        ParseResultHook.register(key, this);
        forceParse(key);
    }

    @Override
    public void unsubscribe(FileObject key, Subscriber consumer) {
        subscribableDelegate.unsubscribe(key, consumer);
        if (subscribersStore.subscribersTo(key).isEmpty()) {
            ParseResultHook.deregister(key, this);
        }
    }

    boolean hasNoSubscribers() {
        return subscribersStore.subscribedKeys().isEmpty();
    }

    @Override
    public void destroyed(FileObject key) {
        subscribableDelegate.destroyed(key);
    }

    private UnixPath parentPath(UnixPath path) {
        UnixPath result = path.getParent();
        if (result == null) {
            result = UnixPath.empty();
        }
        return result;
    }

    private boolean isAntlrFile(UnixPath path) {
        return "g4".equals(path.extension()) || "g".equals(path.extension()) || "tokens".equals(path.extension());
    }

    private static boolean isJavaFile(UnixPath path) {
        return "java".equals(path.extension());
    }

    private UnixPath jfsJavaSourcePathForGrammarFile(FileObject fo) {
        JFSCoordinates loc = mappingManager.mappings.forFileObject(fo);
        if (loc == null) {
            return null;
        }
        UnixPath parent = loc.path().getParent();
        UnixPath result;
        String javaFileName = loc.path().getFileName().rawName() + ".java";
        if (parent == null) {
            result = UnixPath.get(javaFileName);
        } else {
            result = parent.resolve(javaFileName);
        }
        return result;
    }

    private void invalidateReverseDependencies(FileObject fo, JFSCoordinates mappedPath, ObjectGraph<UnixPath> graph, FileObject initiator) {
        Set<UnixPath> paths = graph.reverseClosureOf(mappedPath.path());
        for (UnixPath up : paths) {
            FileObject reverseDep = mappingManager.mappings.originalFile(mappedPath);
            if (reverseDep != null && !fo.equals(reverseDep) && !subscribersStore.subscribersTo(reverseDep).isEmpty() && !Objects.equals(initiator, reverseDep)) {
                LOG.log(Level.FINE, "Invalidate source of {0} due to regeneration of {1}", new Object[]{reverseDep.getPath(), fo.getPath()});
                NbAntlrUtils.invalidateSource(reverseDep);
                // If a dependency of a file that has parses throttled due to repeated failures
                // changes, we should reset its throttled state, since the fix may be in the
                // file that just changed
                Path path = ParsingUtils.toPath(reverseDep);
                if (path != null) {
                    AntlrGenerationSubscriptionsImpl.throttle().clearThrottle(path);
                }
            }
        }
    }

    private JFSCoordinates coordinatesFor(FileObject fo) {
        JFSCoordinates jfsMappedGrammarFilePath = mappingManager.mappings.forFileObject(fo);
        if (jfsMappedGrammarFilePath == null) {
            mappingManager.initMappings();
            jfsMappedGrammarFilePath = mappingManager.mappings.forFileObject(fo);
        }
        if (jfsMappedGrammarFilePath == null) {
            jfsMappedGrammarFilePath = mappingManager.addSurpriseMapping(Folders.ANTLR_GRAMMAR_SOURCES, fo);
        }
        return jfsMappedGrammarFilePath;
    }

    @Override
    protected void onReparse(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) throws Exception {
        BrokenSourceThrottle throttle = AntlrGenerationSubscriptionsImpl.throttle();
        if (throttle.isThrottled(extraction)) {
            LOG.log(Level.INFO, "Throttling known-bad tokens hash for {0}", extraction.tokensHash());
            return;
        }
        // Check extraction against tokens hash
        // Keep set of paths where building another file in the same project
        // should be let through because a reparse was forced due to us rebuilding
        // the antlr sources for some other file.
        // If the generation status is up to date, we shouldn't actually
        // run regenration again for that file
        extraction.source().lookup(FileObject.class, fo -> {
            tokenHashCache.put(fo, new GrammarFileHashAndTimestamp(extraction.sourceLastModifiedAtExtractionTime(), extraction.tokensHash()));
            // We may be reentrant - if we parsed, say, a lexer depended on by a
            ReentrantAntlrGenerationContext context = ctx.get();
            if (context != null) {
                // Don't pass a generation result to subsscribers twice in one reentrant session
                if (context.wasRegenerationEventAlreadyDelivered(fo)) {
                    return;
                }
                // See if we have a cached result
                AntlrGenerationResult res = context.resultFor(fo);
                if (res != null) {
                    AntlrRegenerationEvent info = new AntlrRegenerationEvent(tree, extraction, res, populate, fixes);
                    LOG.log(Level.FINER, "Reentry - using canned generation result for {0} in context {1}", new Object[]{fo.getPath(), context});
                    dispatcher.onEvent(fo, info);
                    return;
                }
            }
            if (context != null) {
                // Ensure we don't cyclically reparse because something else
                // indirectly triggered another reparse of the same file - can
                // happen if no dependency graph for a file exists yet
                context.regenerationEventDelivered(fo);
            }
            Path originalFile = ParsingUtils.toPath(fo);
            // Should never be null unless we're dealing with MemoryFileSystem or
            // grammars inside a JAR file or similar, which we do not care about
            if (originalFile != null) {
                JFSCoordinates jfsMappedGrammarFilePath = coordinatesFor(fo);
                if (jfsMappedGrammarFilePath == null) {
                    LOG.log(Level.WARNING, "Cannot figure out any reasonable mapping for {0}.  Giving up.", fo.getPath());
                    return;
                }
                UnixPath grammarPath = jfsMappedGrammarFilePath.path();
                // Get all the files other than this one which are mapped into the JFS
                Set<FileObject> siblings = mappingManager.siblingsOf(fo);
                UnixPath parentPath = parentPath(jfsMappedGrammarFilePath.path());
                // Run Antlr generation
                AntlrGeneratorBuilder<AntlrGenerator> bldr = AntlrGenerator
                        .builder(mappingManager::jfs)
                        .withOriginalFile(originalFile)
                        .withTokensHash(extraction.tokensHash())
                        .grammarSourceInputLocation(jfsMappedGrammarFilePath.location())
                        .withPathHints(mappingManager.mappings)
                        .generateAllGrammars(true)
                        .generateDependencies(true);
                if (!parentPath.isEmpty()) {
                    bldr.generateIntoJavaPackage(parentPath.toString('.'));
                }
                AntlrGenerator generator = bldr.building(parentPath, AntlrGenerationSubscriptionsImpl.IMPORTS);
                String grammarName = jfsMappedGrammarFilePath.path().getFileName().toString();
                FileObject foFinal = fo;
                try {
                    // This call will reinitialize mappings if the old JFS was zapped
                    // due to inactivity
                    JFS jfs = mappingManager.jfs();
                    // Track what files are new or modified after generation
                    JFSFileModifications beforeStatus = jfs.status(EnumSet.of(StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT));
                    AntlrGenerationResult generationResult = jfs.whileWriteLocked(() -> {
                        jfs.status(EnumSet.of(StandardLocation.SOURCE_OUTPUT));
                        AntlrGenerationResult result;
                        try (final PrintStream output = AntlrLoggers.getDefault().printStream(originalFile, AntlrLoggers.STD_TASK_GENERATE_ANTLR)) {
                            result = generator.run(grammarName, output, true);
                        }
                        // Update the state of throttling if the parse was unusable
                        extraction.source().lookup(Path.class, pth -> {
                            throttle.incrementThrottleIfBad(extraction, result);
                        });
                        // We keep a cache of the file dependency graph - which files MemoryTool
                        // read in order to parse this one, which we can use to be more selective
                        // about what sibling grammars in the project need a re-regenerate when one
                        // they depend on changes
                        ObjectGraph<UnixPath> dependencies = result.dependencyGraph();
                        dependencyGraphCache.put(fo, dependencies);
                        // Iterate all direct and indirect dependers on this file - they should
                        // get a real reparse the next time one is requested, since their state
                        // may have changed because of changes in this file
                        invalidateReverseDependencies(foFinal, jfsMappedGrammarFilePath, dependencies, null);
                        if (!siblings.isEmpty() && result.isSuccess()) {
                            // Now go through and find all of the other sources that ought to get a
                            // reparse because this file changed, and get those happening
                            JFSFileModifications.FileChanges changes = beforeStatus.changes();
                            // Files whose output java source(s) have been rewritten in this pass,
                            // so we can assume we don't need an explicit regeneration pass on them -
                            // for example, building a parser will often result in rebuilding java sources
                            // for its lexer, so there is no need to go and do that explicitly - we can
                            // just pass a variation on the AntlrGenerationResult we already have with that
                            // grammar as the main grammar - we have all the info we need for that
                            Set<FileObject> alreadyRegenerated = new HashSet<>();
                            // Files that were not regenerated
                            Set<FileObject> notRegenerated = new HashSet<>();
                            // Files that should get a fresh run of Antlr code generation because of dependency
                            // and which were not regenerated by this pass
                            Set<FileObject> notRegeneratedButNeedReparse = new HashSet<>();
                            for (FileObject sib : siblings) {
                                // Filter out those that do not have an open editor with something
                                // paying attention to the mapping
                                if (!subscribersStore.subscribersTo(sib).isEmpty() && !fo.equals(sib) && AntlrConstants.ANTLR_MIME_TYPE.equals(sib.getMIMEType())) {
                                    // If we did it once this cycle, don't do it again
                                    boolean skip = context == null ? false : context.wasAlreadyRegenerated(sib);
                                    if (skip) {
                                        continue;
                                    }
                                    JFSCoordinates sibMapping = mappingManager.mappings.forFileObject(sib);
                                    if (sibMapping != null) {
                                        UnixPath javaFile = jfsJavaSourcePathForGrammarFile(sib);
                                        //                                            boolean mod = result.outputFiles.get(grammarPath).contains(javaFile);
                                        Optional<ObjectGraph<UnixPath>> siblingDependencyGraph = dependencyGraphCache.getOptional(sib);
                                        if ( /* mod || */ changes.isCreatedOrModified(javaFile)) {
                                            alreadyRegenerated.add(sib);
                                            if (!foFinal.equals(sib)) {
                                                NbAntlrUtils.invalidateSource(sib);
                                                if (siblingDependencyGraph.isPresent()) {
                                                    invalidateReverseDependencies(sib, sibMapping, siblingDependencyGraph.get(), null);
                                                }
                                            }
                                        } else {
                                            // If we have a dependency graph because the other file was built once
                                            // already, use it to be selective and only rebuild it if there is a
                                            // direct dependency from it to this file
                                            //
                                            // Otherwise, just tee it up for regeneration and that will get us
                                            // the dependency graph for next time
                                            if (siblingDependencyGraph.isPresent()) {
                                                ObjectGraph<UnixPath> siblingGraph = siblingDependencyGraph.get();
                                                if (siblingGraph.closureOf(sibMapping.path()).contains(grammarPath)) {
                                                    // Force the next call to ParserManager.parse() with it to
                                                    // not return a cached parser result from inside the NetBeans
                                                    // parsing API plumbing
                                                    NbAntlrUtils.invalidateSource(sib);
                                                    // XXX it might be sufficient just to leave the source
                                                    // invalidated, and whenever something in the future tries
                                                    // to parse it, all of this will be filled in
                                                    notRegeneratedButNeedReparse.add(sib);
                                                }
                                            } else {
                                                // Just an unrelated grammar file
                                                notRegenerated.add(sib);
                                            }
                                        }
                                    }
                                }
                            }
                            if (!alreadyRegenerated.isEmpty() || !notRegeneratedButNeedReparse.isEmpty()) {
                                // If there is more to do, do it
                                regenerateDependencies(fo, alreadyRegenerated, changes, tree, extraction, result, notRegenerated, jfs, notRegeneratedButNeedReparse);
                            }
                            // Publish the event after, so that if event processing generates new
                            // regenerate calls, we don't do extra work (at least when called synchronously
                            // so the context is present)
                            AntlrRegenerationEvent info = new AntlrRegenerationEvent(tree, extraction, result, populate, fixes);
                            dispatcher.onEvent(fo, info);
                        } else {
                            AntlrRegenerationEvent info = new AntlrRegenerationEvent(tree, extraction, result, populate, fixes);
                            dispatcher.onEvent(fo, info);
                        }
                        return result;
                    });
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
    }
    private final ThreadLocal<ReentrantAntlrGenerationContext> ctx = new ThreadLocal();

    /**
     * Reentrant scope for triggering regeneration / reevaluation of
     * dependencies when a grammar others depend on is parsed, which exists
     * only as long as the calling thread is within the scope of the
     * outermost regeneration event.
     */
    class ReentrantAntlrGenerationContext {

        private final Map<FileObject, AntlrGenerationResult> generationWasRunFor = new HashMap<>();
        private final FileObject root;
        private final LinkedList<FileObject> fileStack = new LinkedList<>();
        private final Set<FileObject> delivered = new HashSet<>(16);

        ReentrantAntlrGenerationContext(FileObject root) {
            this.root = root;
        }

        AntlrGenerationResult resultFor(FileObject fo) {
            return generationWasRunFor.get(fo);
        }

        boolean wasAlreadyRegenerated(FileObject fo) {
            return generationWasRunFor.containsKey(fo);
        }

        ReentrantAntlrGenerationContext regenerationEventDelivered(FileObject fo) {
            delivered.add(fo);
            return this;
        }

        boolean wasRegenerationEventAlreadyDelivered(FileObject fo) {
            return root.equals(fo) || delivered.contains(fo);
        }

        void enter(FileObject grammarFile, ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, Set<FileObject> alreadyRegenerated, Set<FileObject> notRegenerated, AntlrGenerationResult generationResult, JFS jfs, Set<FileObject> notRegeneratedButNeedReparse) {
            boolean inProgress = fileStack.contains(grammarFile);
            fileStack.push(grammarFile);
            try {
                generationWasRunFor.put(grammarFile, generationResult);
                for (FileObject fo : alreadyRegenerated) {
                    if (!generationWasRunFor.containsKey(fo)) {
                        // If we are up-to-date with the current tokens hash / timestamp of the file,
                        // we can just create a fake AntlrGenerationResult for that file from this one,
                        // since this pass already regenerated that one as well (e.g. lexer grammar was
                        // rebuilt because of parser grammar rebuild, and we have complete information
                        // to publish the regeneration event to subscribers already)
                        boolean regenDone = simulateRegenerationEventFor(fo, generationResult, extraction);
                        if (!regenDone) {
                            notRegeneratedButNeedReparse.add(fo);
                        }
                    }
                }
                // Now explicitly generate the rest - since we're doiong it inside this context,
                // we won't wind up accidentally doing it more than once
                for (FileObject fo : notRegeneratedButNeedReparse) {
                    if (!generationWasRunFor.containsKey(fo)) {
                        forceParse(fo);
                    }
                }
            } finally {
                fileStack.pop();
            }
        }

        /**
         * Create an event by just retargeting the existing generation
         * result for a sibling grammar that happened to be generated as a
         * consequence of generating some other one.
         *
         * @param fo The file
         * @param generationResult Its generation result
         * @param extraction The extraction
         * @return
         */
        boolean simulateRegenerationEventFor(FileObject fo, AntlrGenerationResult generationResult, Extraction extraction) {
            // In this case, the AntlrGenerationResult was for a different file (say, a parser grammar),
            // but resulted in regenerating the files for another grammar that has subscribers.  We
            // simply ask the AntlrGenerationResult to transform itself so the main grammar
            JFSCoordinates coords = mappingManager.mappings.forFileObject(fo);
            String tokensHash = null;
            Optional<GrammarFileHashAndTimestamp> info = tokenHashCache.cachedValue(fo);
            if (info.isPresent()) {
                if (fo.lastModified().getTime() <= info.get().lastModified) {
                    tokensHash = info.get().tokensHash;
                }
            }
            if (tokensHash == null) {
                tokensHash = "-unknown-";
            }
            AntlrGenerationResult xlate = generationResult.forSiblingGrammar(ParsingUtils.toPath(fo), coords.path(), tokensHash);
            if (xlate != null) {
                generationWasRunFor.put(fo, xlate);
                forceParse(fo);
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("RegenerationContext(");
            sb.append(root.getPath());
            for (FileObject stackItem : fileStack) {
                sb.append(", ").append(stackItem.getPath());
            }
            return sb.append(')').toString();
        }
    }

    private void regenerateDependencies(FileObject grammarFile, Set<FileObject> alreadyRegenerated, JFSFileModifications.FileChanges changes, ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, AntlrGenerationResult generationResult, Set<FileObject> notRegenerated, JFS jfs, Set<FileObject> notRegeneratedButNeedReparse) {
        ReentrantAntlrGenerationContext context = ctx.get();
        boolean outer = context == null;
        if (context == null) {
            context = new ReentrantAntlrGenerationContext(grammarFile);
            ctx.set(context);
        }
        try {
            context.enter(grammarFile, tree, extraction, alreadyRegenerated, notRegenerated, generationResult, jfs, notRegeneratedButNeedReparse);
        } finally {
            if (outer) {
                ctx.remove();
            }
        }
    }

}
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
package org.nemesis.antlr.live.execution;

import com.mastfrog.function.TriConsumer;
import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Obj;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.path.UnixPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.common.ShutdownHooks;
import org.nemesis.antlr.compilation.AntlrGenerationAndCompilationResult;
import org.nemesis.antlr.compilation.AntlrGeneratorAndCompiler;
import org.nemesis.antlr.compilation.AntlrRunBuilder;
import org.nemesis.antlr.compilation.GrammarProcessingOptions;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.compilation.RunResults;
import org.nemesis.antlr.compilation.WithGrammarRunner;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.misc.utils.concurrent.WorkCoalescer;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import static org.nemesis.antlr.memory.spi.AntlrLoggers.STD_TASK_COMPILE_ANALYZER;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.jfs.javac.JavacOptions;
import org.nemesis.misc.utils.CachingSupplier;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRunSubscriptions {

    public static final String BASE_PATH = "antlr/invokers/";
    static final Logger LOG = Logger.getLogger(AntlrRunSubscriptions.class.getName());
    private static final Set<String> WARNED = new HashSet<>(3);
    private final Map<Class<?>, Entry<?, ?>> subscriptionsByType = new HashMap<>();
    private static final Supplier<AntlrRunSubscriptions> INSTANCE_SUPPLIER
            = CachingSupplier.of(AntlrRunSubscriptions::new);

    static AntlrRunSubscriptions instance() {
        return INSTANCE_SUPPLIER.get();
    }

    public static <T> InvocationSubscriptions<T> forType(Class<T> type) {
        return new InvocationSubscriptions<>(type);
    }

    @SuppressWarnings("unchecked")
    <T> Runnable _subscribe(FileObject fo, Class<T> type, TriConsumer<Extraction, GrammarRunResult<T>, T> c) {
        Entry<T, ?> e;
        synchronized (this) {
            e = (Entry<T, ?>) subscriptionsByType.get(type);
            if (e == null) {
                InvocationRunner<T, ?> runner = find(type);
                if (runner != null) {
                    e = new Entry<>(runner, fo, c, this::remove);
                    LOG.log(Level.FINER, "Created an entry {0} to subscribe {1}", new Object[]{runner, c});
                    subscriptionsByType.put(type, e);
                } else {
                    LOG.log(Level.WARNING, "No registered reflective invoker for {0} under {1}",
                            new Object[]{type.getName(), pathForType(type)}
                    );
                }
            } else {
                boolean subscribed = e.subscribe(fo, c);
                if (!subscribed) { // was empty but not yet removed
                    Entry<T, ?> newEntry = new Entry<>(e.runner, fo, c, this::remove);
                    LOG.log(Level.FINER, "Created an entry {0} to subscribe {1}"
                            + "replacing dead {3}", new Object[]{newEntry, c, e});
                    e = newEntry;
                    subscriptionsByType.put(type, e);
                } else {
                    LOG.log(Level.FINEST, "Subscribed to rebuilds of {0} over {1}: {2}",
                            new Object[]{fo.getPath(), type.getName(), c});
                }
            }
        }
        if (e == null) {
            return null;
        }
        Entry<T, ?> en = e;
        // XXX need to track consumers by file object
        Runnable unsubscribeFromNotifications = RebuildSubscriptions.subscribe(fo, en);
        LOG.log(Level.FINER, "Subscribed {0} to rebuilds of {1}", new Object[]{c, fo});
        return () -> {
            if (en.unsubscribe(fo, c)) {
                LOG.log(Level.FINEST, "Explicit unsubscribe from rebuilds of {0} over{1}: {2}",
                        new Object[]{fo.getPath(), type.getName(), c});
                unsubscribeFromNotifications.run();

            }
        };
    }

    private synchronized void remove(Entry<?, ?> e) {
        if (subscriptionsByType.get(e.type()) == e) {
            subscriptionsByType.remove(e.type());
        }
    }

    public static String pathForType(Class<?> type) {
        return BASE_PATH + type.getName().replace('.', '/').replace('$', '/');
    }

    private static <T> InvocationRunner<T, ?> find(Class<T> type) {
        String path = pathForType(type);
        InvocationRunner<?, ?> runner = Lookups.forPath(path).lookup(InvocationRunner.class);
        if (runner != null) {
            if (!type.equals(runner.type())) {
                LOG.log(Level.SEVERE, "InvocationRunner returns type " + runner.type()
                        + " but is registered on the path " + path + " which would be for "
                        + type.getClass().getName(), new AssertionError(type.getName()));
                return null;
            }
            LOG.log(Level.FINE, "Found InvocationRunner {0} for {1} with type {2}",
                    new Object[]{runner, path, runner.type().getName()});
            return (InvocationRunner<T, ?>) runner;
        }
        if (!WARNED.contains(type.getName())) {
            WARNED.add(type.getName());
            LOG.log(Level.SEVERE, "No InvocationRunner<" + type.getSimpleName() + "> registered for "
                    + type.getName() + " on the path " + path + ".  Missing a module to support it?",
                    new IOException(path));
        }
        return null;
    }

    static final class Entry<T, A> implements Subscriber {

        private final InvocationRunner<T, A> runner;
//        private final Set<ConsumerReference> refs = Collections.synchronizedSet(new HashSet<>());
        private final Map<FileObject, Set<ConsumerReference>> refs
                = CollectionUtils.concurrentSupplierMap(ConcurrentHashMap::newKeySet);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        private final Consumer<Entry<?, ?>> onEmpty;
        private boolean disposed;
        private final FileObject fo;
        private final WorkCoalescer<Obj<GrammarRunResult<T>>> coa;
        private final AtomicReference<Obj<GrammarRunResult<T>>> coaRef = new AtomicReference<>(Obj.create());

        public Entry(InvocationRunner<T, A> runner,
                FileObject fo,
                TriConsumer<Extraction, GrammarRunResult<T>, T> res,
                Consumer<Entry<?, ?>> onEmpty) {
            this.runner = runner;
            coa = new WorkCoalescer<>("antlr-run-subscriptions-builds-" + fo.getNameExt());
            refs.get(fo).add(new ConsumerReference(res));
            this.onEmpty = onEmpty;
            this.fo = fo;
        }

        @Override
        public String toString() {
            return "Entry(" + runner + " with " + refs.size() + " subscribers";
        }

        Class<T> type() {
            return runner.type();
        }

        void disposed() {
            LOG.log(Level.FINE, "Disposed {0}", this);
            assert lock.isWriteLockedByCurrentThread();
            disposed = true;
            runner.onDisposed(fo);
            onEmpty.accept(this);
        }

        void remove(ConsumerReference ref) {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            try {
                writeLock.lock();
                refs.remove(ref);
                if (refs.isEmpty()) {
                    disposed();
                }
            } finally {
                writeLock.unlock();
            }
        }

        boolean unsubscribe(FileObject fo, TriConsumer<Extraction, GrammarRunResult<T>, T> res) {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            boolean found = false;
            try {
                Set<ConsumerReference> toRemove = new HashSet<>();
                writeLock.lock();
                if (this.refs.containsKey(fo)) {
                    Set<ConsumerReference> set = refs.get(fo);
                    for (ConsumerReference ref : set) {
                        TriConsumer<Extraction, GrammarRunResult<T>, T> bc = ref.get();
                        if (bc == res) {
                            found = true;
                        }
                        if (found || bc == null) {
                            LOG.log(Level.FINEST, "Unsubscribe {0} from {1}", new Object[]{res, fo.getNameExt()});
                            toRemove.add(ref);
                        }
                    }
                    LOG.log(Level.FINE, "Remove {0}", toRemove);
                    set.removeAll(toRemove);
                    if (set.isEmpty()) {
                        LOG.log(Level.FINEST, "All subscribers to {0} gone - stop listening",
                                fo.getPath());
                        disposed();
                    } else {
                        LOG.log(Level.FINEST, "Still have subscribers to {0} gone: {1}",
                                new Object[]{fo.getPath(), refs.get(fo)});
                    }
                }
            } finally {
                writeLock.unlock();
            }
            return found;
        }

        boolean subscribe(FileObject fo, TriConsumer<Extraction, GrammarRunResult<T>, T> res) {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            try {
                writeLock.lock();
                if (disposed) {
                    LOG.log(Level.INFO, "Trying to subscribe {0} to a disposed subscription {1}",
                            new Object[]{res, this});
                    return false;
                }
                LOG.log(Level.FINE, "Subscribe {0} to {1}", new Object[]{res, fo.getNameExt()});
                refs.get(fo).add(new ConsumerReference(res));
            } finally {
                writeLock.unlock();
            }
            return true;
        }

        void run(Extraction ex, GrammarRunResult<T> res, T obj) {
            Optional<FileObject> ofo = ex.source().lookup(FileObject.class);
            if (!ofo.isPresent()) {
                Debug.failure("No file object in extraction source lookup", ex.logString());
                LOG.log(Level.INFO, "No FileObject in lookup - nothing to notify");
                return;
            }
            FileObject fo = ofo.get();
            ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
            Set<ConsumerReference> toRemove = new HashSet<>();
            try {
                readLock.lock();
                if (refs.containsKey(fo)) {
                    for (Iterator<ConsumerReference> it = refs.get(fo).iterator(); it.hasNext();) {
                        ConsumerReference ref = it.next();
                        TriConsumer<Extraction, GrammarRunResult<T>, T> toInvoke = ref.get();
                        if (toInvoke == null) {
                            toRemove.add(ref);
                        } else {
                            try {
                                LOG.log(Level.FINEST, "Notify {0}", toInvoke);
                                toInvoke.accept(ex, res, obj);
                            } catch (Exception e) {
                                LOG.log(Level.SEVERE, "Exception running " + toInvoke, ex);
                            }
                        }
                    }
                }
            } finally {
                readLock.unlock();
            }
            if (!toRemove.isEmpty()) {
                LOG.log(Level.FINE, "Remove {0}", toRemove);
                ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
                try {
                    writeLock.lock();
                    refs.get(fo).removeAll(toRemove);
                    if (refs.isEmpty()) {
                        disposed();
                    }
                } finally {
                    writeLock.unlock();
                }
            }
        }

        byte[] hash(JFS jfs) {
            try {
                MessageDigest dig = MessageDigest.getInstance("SHA-1");
                List<JFSFileObject> files = new ArrayList<>();
                jfs.list(StandardLocation.SOURCE_PATH, (loc, fo) -> {
                    files.add(fo);
                });
                jfs.list(StandardLocation.SOURCE_OUTPUT, (loc, fo) -> {
                    files.add(fo);
                });
                Collections.sort(files);
                for (JFSFileObject fo : files) {
                    fo.hash(dig);
                }
                return dig.digest();
            } catch (NoSuchAlgorithmException ex) {
                throw new AssertionError(ex);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
                return new byte[0];
            }
        }

        static class CachedResults<A> {

            private CompileResult lastCompileResult;
            private WithGrammarRunner lastRunner;
            private A lastArg;
            private JFSFileModifications lastStatus;
            private String grammarTokensHash = "`";

            CachedResults() {

            }

            CachedResults(CompileResult res, WithGrammarRunner runner, A arg, JFSFileModifications mods,
                    String grammarTokensHash) {
                this.lastCompileResult = res;
                this.lastRunner = runner;
                this.lastArg = arg;
                this.grammarTokensHash = grammarTokensHash;
            }

            private synchronized WithGrammarRunner maybeReuse(InvocationRunner<?, A> run, JFS jfs, Extraction ext, byte[] hash) throws IOException {
                if (lastRunner == null) {
                    return null;
                }
                if (lastArg != null) {
                    if (!run.isStillValid(lastArg)) {
                        return null;
                    }
                }
                if (lastCompileResult != null && !lastCompileResult.areOutputFilesPresentIn(jfs)) {
                    return null;
                }
                if (lastCompileResult != null) {
                    AntlrGenerationAndCompilationResult genR = lastRunner.lastGenerationResult();
                    if (genR != null) {
                        if (genR.isUsable()) {
                            if (genR.generationResult().timestamp < lastCompileResult.timestamp()) {
                                return lastRunner;
                            }
                        }
                    }
                }
                return lastCompileResult != null && lastStatus != null
                        //                        && lastLastModified == ext.source().lastModified()
                        && lastCompileResult.isUsable()
                        && lastCompileResult.areClassesUpToDateWithSources(jfs)
                        //                        && lastCompileResult.currentStatus().isUpToDate()
                        && grammarTokensHash.equals(ext.tokensHash())
                        && lastStatus.changes().isUpToDate()
                        ? lastRunner : null;
            }

            synchronized void update(CompileResult res, WithGrammarRunner runner, A arg,
                    String grammarTokensHash) {
                this.lastCompileResult = res;
                this.lastRunner = runner;
                this.lastArg = arg;
                this.grammarTokensHash = grammarTokensHash;
            }
        }

        private final Map<FileObject, CachedResults<A>> resultsCache
                = Collections.synchronizedMap(new WeakHashMap<>(20));

        CachedResults<A> cachedResults(Extraction ext) {
            Optional<FileObject> fo = ext.source().lookup(FileObject.class);
            CachedResults<A> results;
            if (fo.isPresent()) { // should always be unless source is an NB virtual file, or nbjfsutils not installed
                FileObject f = fo.get();
                synchronized (resultsCache) {
                    results = resultsCache.get(f);
                    if (results == null) {
                        results = new CachedResults();
                        resultsCache.put(f, results);
                    }
                }
            } else {
                results = new CachedResults();
            }
            return results;
        }

        @Override
        public void onRebuilt(GrammarFileContext tree,
                String mimeType, Extraction extraction,
                AntlrGenerationResult res, ParseResultContents populate,
                Fixes fixes) {
            if (!res.isSuccess() || ShutdownHooks.isShuttingDown()) {
                LOG.log(Level.FINE, "Unusable generation result {0}", res);
                if (res.thrown != null) {
                    LOG.log(Level.INFO, "Generating " + extraction.source(), res.thrown);
                }
                return;
            }
            FileObject file = extraction.source().lookupOrDefault(FileObject.class, null);
            if (file != null && !file.equals(fo)) {
                String msg = "Passed gen result for wrong file: " + file + " vs " + fo
                        + " gen result is for " + res.originalFilePath + " " + res.grammarName
                        + " " + res.grammarFile;
                LOG.log(Level.FINER, msg, new Exception(msg));
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(res.originalFilePath.toFile()));
                extraction = NbAntlrUtils.extractionFor(fo);
            }
            JFS jfs = res.jfsSupplier.get();
            Extraction extFinal = extraction;
            try {
                coa.coalesceComputation(() -> {
                    try {
                        return Debug.runObjectThrowing(this,
                                "AntlrRunSubscriptions.onRebuilt " + extFinal.tokensHash(),
                                extFinal::toString, () -> {
                                    return doTheThing(tree, extFinal, res, jfs);
                                });
                    } catch (IOException ex) {
                        Logger.getLogger(Entry.class.getName()).log(Level.WARNING,
                                "Exception configuring compiler to parse "
                                + extFinal.source(), ex);
                        return null;
                    } catch (Exception ex) {
                        Logger.getLogger(Entry.class.getName()).log(Level.SEVERE,
                                "Exception configuring compiler to parse "
                                + extFinal.source(), ex);
                        return null;
                    } catch (Error err) {
                        handleEiiE(err, jfs);
                        throw err;
                    }
                }, grr -> {
//                    System.out.println("COA JOB GETS " + grr);
                }, coaRef);
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            } catch (WorkCoalescer.ComputationFailedException ex) {
                Exceptions.printStackTrace(ex);
            }
            System.out.println("Run-Sub coalescence " + coa.coalescence());
        }

        private Obj<GrammarRunResult<T>> doTheThing(GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, JFS jfs) throws IOException {
            // Try to reuse existing stuff - we can be called in the event
            // thread during a re-lex because of an insert or delete,
            // and recompiling and rebuilding will cause a noticable pause
            Obj<GrammarRunResult<T>> result = Obj.create();
            WithGrammarRunner rb;
            CompileResult cr;
            A arg;
            boolean created = false;
            byte[] newHash = hash(res.jfsSupplier.get());
            Bool regenerated = Bool.create();
            CachedResults<A> cache = cachedResults(extraction);
            boolean tokensHashChanged = !Objects.equals(cache.grammarTokensHash, extraction.tokensHash());
            rb = cache.maybeReuse(runner, jfs, extraction, newHash);
            LOG.log(Level.FINER, "Reuse cached run result? {0}", rb != null);
            if (rb == null) {
                Debug.message("New compileBuilder for " + extraction.tokensHash());
                LOG.log(Level.FINEST, "Need a new compile builder for {0}", extraction.source());
                created = true;

                JFSCompileBuilder bldr = new JFSCompileBuilder(res.jfsSupplier);
                bldr.withMaxErrors(1).setOptions(
                        JavacOptions.fastDefaults()
                                .withDebugInfo(JavacOptions.DebugInfo.LINES)
                                .runAnnotationProcessors(false)
                                .withCharset(jfs.encoding())
                ).runAnnotationProcessors(false)
                        .addSourceLocation(SOURCE_PATH)
                        .addSourceLocation(SOURCE_OUTPUT);

//                                bldr.verbose().nonIdeMode().withMaxErrors(10)
//                                        .withMaxWarnings(10); // XXX for debugging, will wreak havoc
                try (Writer writer = AntlrLoggers.getDefault().writer(res.originalFilePath, STD_TASK_COMPILE_ANALYZER)) {

                    bldr.compilerOutput(writer);

                    CSC csc = new CSC();
                    Obj<UnixPath> singleSource = Obj.create();
                    arg = runner.configureCompilation(tree, res, extraction, jfs, bldr, res.packageName(), csc, singleSource);

                    bldr.addSourceLocation(SOURCE_PATH);
                    bldr.addSourceLocation(SOURCE_OUTPUT);

                    // XXX compile-single now works and is more efficient,
                    // but our tests for up-to-dateness don't differentiate
                    // between the compilation result for the extractor and
                    // that of the grammar
                    if (!singleSource.isSet()) {
                        bldr.addSourceLocation(CLASS_OUTPUT);
                        cr = jfs.whileWriteLocked(bldr::compile);
                    } else {
                        cr = jfs.whileWriteLocked(() -> bldr.compileSingle(singleSource.get()));
                    }

                    if (!cr.isUsable()) {
                        writer.write(cr.compileFailed() ? "Compile failed.\n"
                                : "Compile succeeded.\n");
                        writer.write("Compile result not usable. If the error "
                                + "looks like it could be stale sources, will retry.\n");
                        // Detect if we had a cannot find symbol or
                        // cannot find location, and if present, wipe and
                        // rebuild again
                        cr = analyzeAndLogCompileFailureAndMaybeRetry(writer, cr, res, bldr, true, regenerated);

                        if (!cr.isUsable()) {
                            writer.write(cr.compileFailed() ? "Compile failed on clean retry.\n"
                                    : "Compile succeeded on clean retry.\n");
                            writer.write("Compile result not usable.\n");
                            analyzeAndLogCompileFailureAndMaybeRetry(writer, cr, res, bldr, false, regenerated);
                            onCompileFailure(cr, res);
                            LOG.log(Level.FINE, "Unusable second compile result {0}", cr);
                            return result;
                        } else {
                            Debug.success("Usable compile", cr::toString);
                        }
                    }
                    AntlrGeneratorAndCompiler compiler = AntlrGeneratorAndCompiler.fromResult(
                            res, bldr, cr);

                    AntlrGenerationAndCompilationResult agcr = null;
                    if (cache.lastRunner != null) {
                        agcr = cache.lastRunner.lastGenerationResult();
                    }
                    AntlrRunBuilder runBuilder = AntlrRunBuilder
                            .fromGenerationPhase(compiler)
                            .withLastGenerationResult(agcr)
                            .isolated();

                    if (csc.classloaderSupplier != null) {
                        runBuilder.withParentClassLoader(csc.classloaderSupplier);
                    } else {
                        runBuilder.withParentClassLoader(() -> {
                            try {
                                return jfs.getClassLoader(true, Thread.currentThread().getContextClassLoader(),
                                        StandardLocation.CLASS_OUTPUT, StandardLocation.CLASS_PATH);
                            } catch (IOException ex) {
                                throw new IllegalStateException(ex);
                            }
                        });
                    }

                    rb = runBuilder
                            .build(extraction.source().name());
                }
            } else {
                LOG.log(Level.FINE, "Using cached compile result and argument to create embedded parser"
                        + " for {0} for extraction {1} / {2}", new Object[]{
                            extraction.source(),
                            rb.grammarTokensHash(),
                            extraction.tokensHash()});
                arg = cache.lastArg;
                cr = cache.lastCompileResult;
                Debug.message("Using last arg and compile result ", () -> {
                    return "arg " + cache.lastArg + "\n" + cache.lastCompileResult;
                });;
                LOG.log(Level.FINEST, "Reuse cached {0} and {1}",
                        new Object[]{arg, cr});
            }

            // XXX this is a pretty draconian set of options
            Set<GrammarProcessingOptions> opts = EnumSet.noneOf(GrammarProcessingOptions.class);
            if (tokensHashChanged && !regenerated.getAsBoolean()) {
                boolean utd = false;
                if (cache.lastRunner != null && cache.lastRunner.lastGenerationResult() != null) {
                    if (cache.lastRunner.lastGenerationResult().generationResult() != null) {
                        utd = cache.lastRunner.lastGenerationResult().generationResult().areOutputFilesUpToDate();
                    }
                }
                if (!utd) {
                    opts.add(GrammarProcessingOptions.REGENERATE_GRAMMAR_SOURCES);
                    opts.add(GrammarProcessingOptions.REBUILD_JAVA_SOURCES);
                }
            }
//                            if (!regenerated.getAsBoolean()) {
//                                opts.add(GrammarProcessingOptions.REBUILD_JAVA_SOURCES);
//                            }

            if (regenerated.getAsBoolean() || tokensHashChanged) {
                opts.add(GrammarProcessingOptions.REBUILD_JAVA_SOURCES);
            }
            LOG.log(Level.FINE, "Run in classloader with {0}", opts);
            RunResults<T> rrx = rb.run(arg, runner, opts);
            GrammarRunResult<T> rr = rrx.result();
            result.set(rr);
            if (rr.isUsable()) {
                T env = rr.get();
                if (created) {
                    cache.update(cr, rb, arg,
                            extraction.tokensHash());
                }
                run(extraction, rr, env);
                if (!created) {
                    cache.lastRunner.resetFileModificationStatusForReuse();
                }
            } else {
                LOG.log(Level.FINER, "Non-usable run result {0}", rr);
                if (LOG.isLoggable(Level.FINEST) && rr.thrown().isPresent()) {
                    LOG.log(Level.FINEST, null, rr.thrown().get());
//                                    if (cr.hasErrors("compiler.err.cant.resolve.location")) {
                    LOG.log(Level.FINEST, "Looks like a source not found.  JFS listing follows.");
                    rr.jfs().list(StandardLocation.SOURCE_PATH, (loc, fo) -> {
                        LOG.log(Level.FINEST, "{0}: {1}", new Object[]{loc, fo.path()});
                    });
                    rr.jfs().list(StandardLocation.SOURCE_OUTPUT, (loc, fo) -> {
                        LOG.log(Level.FINEST, "{0}: {1}", new Object[]{loc, fo.path()});
                    });
                }
                Debug.failure("Non-usable run result", () -> {
                    StringBuilder sb = new StringBuilder("compileFailed? ").append(rr.compileFailed()).append('\n');
                    sb.append("Diags: ").append(rr.diagnostics()).append('\n');
                    sb.append(rr);
                    return sb.toString();
                });
            }
            return result;

        }

        void onCompileFailure(CompileResult crFinal, AntlrGenerationResult res) {
            Debug.failure("Unusable compilation result", () -> {
                StringBuilder sb = new StringBuilder();
                sb.append(crFinal).append('\n');
                sb.append("failed ").append(crFinal.compileFailed());
                sb.append("diags ").append(crFinal.diagnostics());
                boolean hasFatal = false;
                if (crFinal.diagnostics() != null && !crFinal.diagnostics().isEmpty()) {
                    for (JavacDiagnostic diag : crFinal.diagnostics()) {
                        sb.append("\n").append(diag.kind()).append(' ')
                                .append(diag.message()).append("\n  at")
                                .append(diag.lineNumber()).append(':')
                                .append(diag.columnNumber()).append("\n")
                                .append(diag.sourceCode()).append('\n');
                        hasFatal |= diag.isError();
                    }
                }
                Optional<Throwable> th = crFinal.thrown();
                if (th != null && th.isPresent()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    th.get().printStackTrace(new PrintStream(baos));
                    sb.append(new String(baos.toByteArray(), UTF_8));
                }
                if (hasFatal) {
                    JFS jfs = res.originalJFS();
                    if (jfs != null) { // should not be, but theoretically possible
                        for (Path pth : crFinal.sources()) {
                            JFSFileObject jfo = jfs.get(StandardLocation.SOURCE_PATH, UnixPath.get(pth));
                            if (jfo == null) {
                                jfo = jfs.get(StandardLocation.SOURCE_OUTPUT, UnixPath.get(pth));
                            }
                            if (jfo == null) {
                                jfo = jfs.get(StandardLocation.CLASS_OUTPUT, UnixPath.get(pth));
                            }
                            if (jfo != null && jfo.getName().endsWith("Extractor.java")) {
                                try {
                                    sb.append(jfo.getCharContent(true));
                                } catch (IOException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            }
                        }
                    }
                }
                return sb.toString();
            });
        }

        CompileResult analyzeAndLogCompileFailureAndMaybeRetry(final Writer writer, CompileResult cr,
                AntlrGenerationResult res, JFSCompileBuilder bldr, boolean rebuild, Bool regenerated) throws IOException {
            Consumer<String> pw = new PrintWriter(writer)::println;
//                    AntlrLoggers.isActive(writer) ? new PrintWriter(writer)::println
//                    : System.out::println;
            if (AntlrLoggers.isActive(writer)) {
                pw.accept("COMPILE RESULT: " + cr);
                pw.accept("GEN PACKAGE: " + res.packageName);
                pw.accept("GRAMMAR SRC LOC: " + res.grammarSourceLocation);
                pw.accept("SOURCE OUT LOC: " + res.javaSourceOutputLocation);
                pw.accept("ORIG FILE: " + res.originalFilePath);
                pw.accept("FULL JFS LISTING:");
                res.jfs().listAll((loc, fo) -> {
                    pw.accept(" * " + loc + "\t" + fo);
                });
            }
            boolean foundCantResolveLocation = false;
            for (JavacDiagnostic diag : cr.diagnostics()) {
                if (rebuild && "compiler.err.cant.resolve.location".equals(diag.sourceCode())) {
                    foundCantResolveLocation = true;
                }
                printOneDiagnostic(pw, diag, res);
            }
            if (rebuild && foundCantResolveLocation) {
                if (AntlrLoggers.isActive(writer)) {
                    pw.accept("");
                    pw.accept("Error may be due to old source files obsoleted by grammar changes.  Deleting all generated files and retrying.");
                    LOG.log(Level.FINE, "Compiler could not resolve files.  Deleting "
                            + "generated code, regenerating and recompiling: {0}", cr.diagnostics());
                }
                JFS jfs = res.jfsSupplier.get();
                if (!jfs.id().equals(res.jfsId)) {
                    LOG.log(Level.FINE, "JFS {0} has been replaced with new JFS {1}", new Object[]{res.jfsId, jfs.id()});
                }
                bldr.setOptions(bldr.options().rebuildAllSources());

                cr = jfs.whileWriteLocked(() -> {
                    res.rebuild();
                    regenerated.set(true);
                    return bldr.compile();
                });
                writer.write("Compile took " + cr.elapsedMillis() + "ms");
            }
            return cr;
        }

        void printOneDiagnostic(Consumer<String> pw, JavacDiagnostic diag, AntlrGenerationResult res) {
            pw.accept(diag.message() + " at " + diag.lineNumber() + ":" + diag.columnNumber() + " in " + diag.fileName());
            JFSFileObject fo = res.jfs().get(StandardLocation.SOURCE_OUTPUT, UnixPath.get(diag.sourceRootRelativePath()));
            if (fo == null) {
                fo = res.jfs().get(StandardLocation.SOURCE_PATH, UnixPath.get(diag.sourceRootRelativePath()));
            }
            if (fo != null) {
                pw.accept(diag.context(fo));
            }
        }

        static class CSC implements Consumer<Supplier<ClassLoader>> {

            private Supplier<ClassLoader> classloaderSupplier;

            @Override
            public void accept(Supplier<ClassLoader> t) {
                this.classloaderSupplier = t;
            }
        }

        final class ConsumerReference extends WeakReference<TriConsumer<Extraction, GrammarRunResult<T>, T>>
                implements Runnable {

            ConsumerReference(TriConsumer<Extraction, GrammarRunResult<T>, T> c) {
                super(c, Utilities.activeReferenceQueue());
            }

            @Override
            public void run() {
                remove(this);
            }
        }
    }

    private static void handleEiiE(Error ex, JFS jfs) {
        try {
            LOG.log(Level.SEVERE, "Classpath: " + jfs.currentClasspath(), ex);
        } catch (Throwable t) {
            ex.addSuppressed(t);
            LOG.log(Level.SEVERE, t.toString(), ex);
            LOG.log(Level.SEVERE, null, t);
            throw ex;
        }
    }
}

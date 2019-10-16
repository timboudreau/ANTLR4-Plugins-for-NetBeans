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

import com.mastfrog.util.collections.CollectionUtils;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.compilation.AntlrGeneratorAndCompiler;
import org.nemesis.antlr.compilation.AntlrRunBuilder;
import org.nemesis.antlr.compilation.GrammarProcessingOptions;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.compilation.WithGrammarRunner;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.misc.utils.CachingSupplier;
import org.openide.filesystems.FileObject;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRunSubscriptions {

    public static final String BASE_PATH = "antlr/invokers/";
    private static final Logger LOG = Logger.getLogger(AntlrRunSubscriptions.class.getName());
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
    <T> Runnable _subscribe(FileObject fo, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        Entry<T, ?> e;
        synchronized (this) {
            e = (Entry<T, ?>) subscriptionsByType.get(type);
            if (e == null) {
                InvocationRunner<T, ?> runner = find(type);
                if (runner != null) {
                    e = new Entry<>(runner, fo, c, this::remove);
                    LOG.log(Level.FINER, "Created an entry {0} to subscribe {1}", new Object[]{runner, c});
                    subscriptionsByType.put(type, e);
                }
            } else {
                boolean subscribed = e.subscribe(fo, c);
                if (!subscribed) { // was empty but not yet removed
                    Entry<T, ?> newEntry = new Entry<>(e.runner, fo, c, this::remove);
                    LOG.log(Level.FINER, "Created an entry {0} to subscribe {1}"
                            + "replacing dead {3}", new Object[]{newEntry, c, e});
                    e = newEntry;
                    subscriptionsByType.put(type, e);
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

        public Entry(InvocationRunner<T, A> runner,
                FileObject fo,
                BiConsumer<Extraction, GrammarRunResult<T>> res,
                Consumer<Entry<?, ?>> onEmpty) {
            this.runner = runner;
            refs.get(fo).add(new ConsumerReference(res));
            this.onEmpty = onEmpty;
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

        boolean unsubscribe(FileObject fo, BiConsumer<Extraction, GrammarRunResult<T>> res) {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            boolean found = false;
            try {
                Set<ConsumerReference> toRemove = new HashSet<>();
                writeLock.lock();
                if (this.refs.containsKey(fo)) {
                    for (ConsumerReference ref : refs.get(fo)) {
                        BiConsumer<Extraction, GrammarRunResult<T>> bc = ref.get();
                        if (bc == res) {
                            found = true;
                        }
                        if (found || bc == null) {
                            toRemove.add(ref);
                        }
                    }
                    LOG.log(Level.FINE, "Remove {0}", toRemove);
                    refs.get(fo).removeAll(toRemove);
                    if (refs.isEmpty()) {
                        disposed();
                    }
                }
            } finally {
                writeLock.unlock();
            }
            return found;
        }

        boolean subscribe(FileObject fo, BiConsumer<Extraction, GrammarRunResult<T>> res) {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            try {
                writeLock.lock();
                if (disposed) {
                    return false;
                }
                refs.get(fo).add(new ConsumerReference(res));
            } finally {
                writeLock.unlock();
            }
            return true;
        }

        void run(Extraction ex, GrammarRunResult<T> res) {
            Optional<FileObject> ofo = ex.source().lookup(FileObject.class);
            if (!ofo.isPresent()) {
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
                        BiConsumer<Extraction, GrammarRunResult<T>> toInvoke = ref.get();
                        if (toInvoke == null) {
                            toRemove.add(ref);
                        } else {
                            try {
                                toInvoke.accept(ex, res);
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
                Collections.sort(files, (a, b) -> {
                    return a.getName().compareTo(b.getName());
                });
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
            private long lastLastModified;
            private byte[] lastHash;

            CachedResults() {

            }

            CachedResults(CompileResult res, WithGrammarRunner runner, A arg, JFSFileModifications mods,
                    long lastModified, byte[] hash) {
                this.lastCompileResult = res;
                this.lastRunner = runner;
                this.lastArg = arg;
                this.lastLastModified = lastModified;
                this.lastHash = hash;
            }

            private synchronized WithGrammarRunner maybeReuse(Extraction ext, byte[] hash) throws IOException {
                return lastCompileResult != null && lastStatus != null && lastHash != null
                        && lastLastModified == ext.source().lastModified()
                        && lastCompileResult.isUsable()
                        && lastCompileResult.currentStatus().isUpToDate()
                        && lastStatus.changes().isUpToDate()
                        && Arrays.equals(lastHash, hash)
                        ? lastRunner : null;
            }

            synchronized void update(CompileResult res, WithGrammarRunner runner, A arg, JFSFileModifications mods,
                    long lastModified, byte[] hash) {
                this.lastCompileResult = res;
                this.lastRunner = runner;
                this.lastArg = arg;
                this.lastLastModified = lastModified;
                this.lastHash = hash;
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
        public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
                String mimeType, Extraction extraction,
                AntlrGenerationResult res, ParseResultContents populate,
                Fixes fixes) {
            if (!res.isUsable()) {
                LOG.log(Level.FINE, "Unusable generation result {0}", res);
                return;
            }
            try {
                Debug.runThrowing(this,
                        "AntlrRunSubscriptions.onRebuilt " + extraction.tokensHash() + "",
                        extraction::toString, () -> {
                            // Try to reuse existing stuff - we can be called in the event
                            // thread during a re-lex because of an insert or delete,
                            // and recompiling and rebuilding will cause a noticable pause
                            WithGrammarRunner rb;
                            CompileResult cr;
                            A arg;
                            JFSFileModifications grammarStatus;
                            long lastModified = Long.MIN_VALUE;
                            boolean created = false;
                            byte[] newHash = hash(res.jfs());
                            CachedResults<A> cache = cachedResults(extraction);
                            rb = cache.maybeReuse(extraction, newHash);
                            if (rb == null) {
                                Debug.message("New compileBuilder for " + extraction.tokensHash());
                                LOG.log(Level.FINEST, "Need a new compile builder for {0}", extraction.source());
                                created = true;
                                JFSCompileBuilder bldr = new JFSCompileBuilder(res.jfs());
                                CSC csc = new CSC();
                                arg = runner.configureCompilation(tree, res, extraction, res.jfs(), bldr, res.packageName(), csc);

                                bldr.addSourceLocation(StandardLocation.SOURCE_OUTPUT);
                                cr = bldr.compile();

                                if (!cr.isUsable()) {
                                    LOG.log(Level.FINE, "Unusable compile result {0}", cr);
                                    return;
                                }

                                AntlrGeneratorAndCompiler compiler = AntlrGeneratorAndCompiler.fromResult(res, bldr);

                                AntlrRunBuilder runBuilder = AntlrRunBuilder
                                        .fromGenerationPhase(compiler).isolated();

                                if (csc.t != null) {
                                    runBuilder.withParentClassLoader(csc.t);
                                }

                                rb = runBuilder
                                        .build(extraction.source().name());

                            } else {
                                System.out.println("USE CACHED");
                                arg = cache.lastArg;
                                cr = cache.lastCompileResult;
                                LOG.log(Level.FINEST, "Reuse cached {0} and {1}",
                                        new Object[] { arg, cr});
                            }
                            GrammarRunResult<T> rr = rb.run(arg, runner, EnumSet.of(GrammarProcessingOptions.RETURN_LAST_GOOD_RESULT_ON_FAILURE, GrammarProcessingOptions.REGENERATE_GRAMMAR_SOURCES, GrammarProcessingOptions.REBUILD_JAVA_SOURCES));
                            if (rr.isUsable()) {
                                if (created) {
                                    grammarStatus = res.filesStatus;
                                    cache.update(cr, rb, arg, grammarStatus, lastModified, newHash);
                                }
                                run(extraction, rr);
                                if (!created) {
                                    cache.lastRunner.resetFileModificationStatusForReuse();
                                }
                            }
                        });
            } catch (IOException ex) {
                Logger.getLogger(Entry.class.getName()).log(Level.WARNING,
                        "Exception configuring compiler to parse "
                        + extraction.source(), ex);
            } catch (Exception ex) {
                Logger.getLogger(Entry.class.getName()).log(Level.SEVERE,
                        "Exception configuring compiler to parse "
                        + extraction.source(), ex);
            } catch (Error err) {
                handleEiiE(err, res.jfs());
                throw err;
            }
        }

        static class CSC implements Consumer<Supplier<ClassLoader>> {

            private Supplier<ClassLoader> t;

            @Override
            public void accept(Supplier<ClassLoader> t) {
                this.t = t;
            }
        }

        final class ConsumerReference extends WeakReference<BiConsumer<Extraction, GrammarRunResult<T>>>
                implements Runnable {

            ConsumerReference(BiConsumer<Extraction, GrammarRunResult<T>> c) {
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

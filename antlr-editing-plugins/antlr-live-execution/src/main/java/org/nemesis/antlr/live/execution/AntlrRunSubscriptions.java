package org.nemesis.antlr.live.execution;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.compilation.AntlrGeneratorAndCompiler;
import org.nemesis.antlr.compilation.AntlrRunBuilder;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.compilation.WithGrammarRunner;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
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

    static {
        LOG.setLevel(Level.ALL);
    }
    private static Set<String> WARNED = new HashSet<>(3);
    private final Map<Class<?>, Entry<?>> subscriptionsByType = new HashMap<>();

    private static volatile AntlrRunSubscriptions INSTANCE;

    static AntlrRunSubscriptions instance() {
        AntlrRunSubscriptions result = INSTANCE;
        if (result == null) {
            synchronized (AntlrRunSubscriptions.class) {
                result = INSTANCE;
                if (result == null) {
                    result = INSTANCE = new AntlrRunSubscriptions();
                }
            }
        }
        return result;
    }

    public static <T> InvocationSubscriptions<T> subscribe(Class<T> type) {
        return new InvocationSubscriptions<>(type);
    }

    @SuppressWarnings("unchecked")
    synchronized <T> Runnable _subscribe(FileObject fo, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        Entry<T> e = (Entry<T>) subscriptionsByType.get(type);
        if (e == null) {
            InvocationRunner<T> runner = find(type);
            if (runner != null) {
                e = new Entry<>(runner, c, this::remove);
                LOG.log(Level.FINER, "Created an entry {0} to subscribe {1}", new Object[]{runner, c});
                subscriptionsByType.put(type, e);
            }
        } else {
            boolean subscribed = e.subscribe(c);
            if (!subscribed) { // was empty but not yet removed
                Entry<T> newEntry = new Entry<>(e.runner, c, this::remove);
                LOG.log(Level.FINER, "Created an entry {0} to subscribe {1}"
                        + "replacing dead {3}", new Object[]{newEntry, c, e});
                e = newEntry;
                subscriptionsByType.put(type, e);
            }
        }
        if (e == null) {
            return null;
        }
        Entry<T> en = e;
        // XXX need to track consumers by file object
        Runnable unsubscribeFromNotifications = RebuildSubscriptions.subscribe(fo, en);
        System.out.println("SUBSCRIBE " + fo + " " + en + " unsubscriber " + unsubscribeFromNotifications);
        return () -> {
            if (en.unsubscribe(c)) {
                unsubscribeFromNotifications.run();
            }
        };
    }

    private synchronized void remove(Entry<?> e) {
        if (subscriptionsByType.get(e.type()) == e) {
            subscriptionsByType.remove(e.type());
        }
    }

    static String pathForType(Class<?> type) {
        return BASE_PATH + type.getName().replace('.', '/').replace('$', '/');
    }

    private static <T> InvocationRunner<T> find(Class<T> type) {
        String path = pathForType(type);
        InvocationRunner<?> runner = Lookups.forPath(path).lookup(InvocationRunner.class);
        if (runner != null) {
            if (!type.equals(runner.type())) {
                LOG.log(Level.SEVERE, "InvocationRunner returns type " + runner.type()
                        + " but is registered on the path " + path + " which would be for "
                        + type.getClass().getName(), new AssertionError(type.getName()));
                return null;
            }
            LOG.log(Level.FINE, "Found InvocationRunner {0} for {1} with type {2}",
                    new Object[]{runner, path, runner.type().getName()});
            return (InvocationRunner<T>) runner;
        }
        if (!WARNED.contains(type.getName())) {
            WARNED.add(type.getName());
            LOG.log(Level.SEVERE, "No InvocationRunner<" + type.getSimpleName() + "> registered for "
                    + type.getName() + " on the path " + path + ".  Missing a module to support it?",
                    new IOException(path));
        }
        return null;
    }

    static final class Entry<T> implements Subscriber {

        private final InvocationRunner<T> runner;
        private final Set<ConsumerReference> refs = Collections.synchronizedSet(new HashSet<>());
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        private final Consumer<Entry<?>> onEmpty;
        private boolean disposed;

        public Entry(InvocationRunner<T> runner, BiConsumer<Extraction, GrammarRunResult<T>> res, Consumer<Entry<?>> onEmpty) {
            this.runner = runner;
            refs.add(new ConsumerReference(res));
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

        boolean unsubscribe(BiConsumer<Extraction, GrammarRunResult<T>> res) {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            boolean found = false;
            try {
                Set<ConsumerReference> toRemove = new HashSet<>();
                writeLock.lock();
                for (Iterator<ConsumerReference> it = refs.iterator(); it.hasNext();) {
                    ConsumerReference ref = it.next();
                    BiConsumer<Extraction, GrammarRunResult<T>> bc = ref.get();
                    if (bc == res) {
                        found = true;
                    }
                    if (found || bc == null) {
                        toRemove.add(ref);
                    }
                }
                LOG.log(Level.FINE, "Remove {0}", toRemove);
                refs.removeAll(toRemove);
                if (refs.isEmpty()) {
                    disposed();
                }
            } finally {
                writeLock.unlock();
            }
            return found;
        }

        boolean subscribe(BiConsumer<Extraction, GrammarRunResult<T>> res) {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            try {
                writeLock.lock();
                if (disposed) {
                    return false;
                }
                refs.add(new ConsumerReference(res));
            } finally {
                writeLock.unlock();
            }
            return true;
        }

        void run(Extraction ex, GrammarRunResult<T> res) {
            ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
            Set<ConsumerReference> toRemove = new HashSet<>();
            try {
                readLock.lock();
                for (Iterator<ConsumerReference> it = refs.iterator(); it.hasNext();) {
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
            } finally {
                readLock.unlock();
            }
            if (!toRemove.isEmpty()) {
                LOG.log(Level.FINE, "Remove {0}", toRemove);
                ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
                try {
                    writeLock.lock();
                    refs.removeAll(toRemove);
                    if (refs.isEmpty()) {
                        disposed();
                    }
                } finally {
                    writeLock.unlock();
                }
            }
        }

        @Override
        public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
                String mimeType, Extraction extraction,
                AntlrGenerationResult res, ParseResultContents populate,
                Fixes fixes) {
            System.out.println("ENTRY.onRebuilt " + extraction.source());
            JFSCompileBuilder bldr = new JFSCompileBuilder(res.jfs());
            try {
                runner.onBeforeCompilation(res.jfs(), bldr, res.packageName());
                bldr.addSourceLocation(StandardLocation.SOURCE_OUTPUT);
                CompileResult cr = bldr.compile();
                System.out.println("COMPILE RESULT " + cr);
                System.out.println("DIAGS " + cr.diagnostics());

                AntlrGeneratorAndCompiler compiler = AntlrGeneratorAndCompiler.fromResult(res, bldr);
                WithGrammarRunner rb = AntlrRunBuilder.fromGenerationPhase(compiler).build(extraction.source().name());
                GrammarRunResult<T> rr = rb.run(runner);
                run(extraction, rr);
            } catch (IOException ex) {
                Logger.getLogger(Entry.class.getName()).log(Level.WARNING,
                        "Exception configuring compiler to parse "
                        + extraction.source(), ex);
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
}

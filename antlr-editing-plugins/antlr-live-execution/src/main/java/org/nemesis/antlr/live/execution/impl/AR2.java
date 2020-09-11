/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.execution.impl;

import com.mastfrog.function.state.Int;
import com.mastfrog.subscription.SubscribableBuilder;
import com.mastfrog.subscription.SubscribableBuilder.SubscribableContents;
import com.mastfrog.util.cache.MapCache;
import com.mastfrog.util.collections.MapFactories;
import com.mastfrog.util.collections.SetFactories;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import static javax.swing.text.Document.StreamDescriptionProperty;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.live.execution.AntlrRunSubscriptions;
import org.nemesis.antlr.live.execution.InvocationRunner;
import org.nemesis.antlr.live.execution.impl.TypedCache.K;
import org.nemesis.antlr.live.execution.impl.TypedCache.KeyVisitor;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;
import org.nemesis.misc.utils.CachingSupplier;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.lookup.Lookups;
import org.w3c.dom.Document;

/**
 * Subscription management for "subscribing" to rebuilds/recompiles of an antlr
 * grammar - clients subscribe and then generate java sources into the JFS of
 * the grammar, compile it and run against it in an isolated classloader.
 *
 * THIS class should not contain any logic related to that - just the rather
 * complex bookkeeping needed to make this all work.
 *
 * @author Tim Boudreau
 */
public class AR2 {

    public static final String BASE_PATH = "antlr/invokers/";
    static final Logger LOG = Logger.getLogger(AntlrRunSubscriptions.class.getName());
    private static final Set<String> WARNED = new HashSet<>(3);
    private static final Supplier<AR2> INSTANCE_SUPPLIER
            = CachingSupplier.of(AR2::new);
    private final TypedCache subscriptions = new TypedCache(MapFactories.EQUALITY_CONCURRENT);
    private final Map<Class<?>, InvocationRunnerLookupKey<?>> keyForType = new HashMap<>();
    private final SubscriberImpl regenerationSubscriber = new SubscriberImpl(this);
    private final Map<FileObject, Runnable> subscribedTo = new HashMap<>();

    AR2() {
    }

    static AR2 instance() {
        return INSTANCE_SUPPLIER.get();
    }

    /**
     * The environment is the thing that actually will call code generation,
     * copilation, or whatever it needs.
     *
     * @param <T> A type
     * @param key A key representing the file (which has had its grammar
     * regenerated) and the return type of the "invocation runner" - typically
     * something that can generate and run specific code inside the environment.
     *
     * @param initialState
     * @return
     */
    private <T> InvocationEnvironment<T, ?> environment(EnvironmentKey<T> key, AntlrGenerationEvent initialState) {
        InvocationRunnerLookupKey<T> ik = key.typeKey;
        MapCacheKey<T> mcKey = key.typeKey.mapCacheKey();

        MapCache<Object, InvocationEnvironment<T, ?>> cache = subscriptions.get(mcKey, (K<?> mck) -> {

            SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent> subscriptionBits
                    = subscriptions.get(key.typeKey, kk -> newSubscribable(InvocationRunnerLookupKey.cast(kk)));

            return subscriptionBits.caches.<InvocationEnvironment<T, ?>>createCache(InvocationEnvironment.class,
                    MapFactories.EQUALITY_CONCURRENT, val -> {
                        FileObject fo = (FileObject) val;
                        InvocationRunner<T, ?> runner = find(key.typeKey.invocationResultType);
                        if (runner == null) {
                            return null;
                        }
                        InvocationEnvironment<T, ?> env = createInvocationEnvironment(ik, runner, fo);
                        return env;
                    });
        });
        InvocationEnvironment<T, ?> result = cache.get(key.file);
        result.maybeInitializeFrom(initialState, key.file);
        return result;
    }

    public static <T> void subscribe(FileObject fo, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        instance()._subscribe(fo, type, c);
    }

    public static <T> void subscribe(Document doc, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        instance()._subscribe(doc, type, c);
    }

    public static <T> void subscribe(Path file, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        instance()._subscribe(file, type, c);
    }

    private <T> InvocationRunnerLookupKey<T> keyFor(Class<T> type) {
        InvocationRunnerLookupKey<?> result
                = keyForType.get(type);
        if (result == null) {
            result = new InvocationRunnerLookupKey<>(type);
            keyForType.put(type, result);
        }
        return (InvocationRunnerLookupKey<T>) result;
    }

    private <T> void _subscribe(FileObject fo, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        InvocationRunnerLookupKey<T> key = keyFor(type);
        SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent> obj
                = subscriptions.get(key, kk -> newSubscribable(InvocationRunnerLookupKey.cast(kk)));
        obj.subscribable.subscribe(fo, c);
    }

    private <T> void _subscribe(Document doc, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        InvocationRunnerLookupKey<T> key = keyFor(type);
        SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent> obj
                = subscriptions.get(key, kk -> newSubscribable(InvocationRunnerLookupKey.cast(kk)));
        obj.subscribable.subscribe(doc, c);
    }

    private <T> void _subscribe(Path file, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        InvocationRunnerLookupKey<T> key = keyFor(type);
        SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent> obj
                = subscriptions.get(key, kk -> newSubscribable(InvocationRunnerLookupKey.cast(kk)));
        obj.subscribable.subscribe(file, c);
    }

    private <T> SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent> newSubscribable(InvocationRunnerLookupKey<T> key) {
        SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent> result
                = SubscribableBuilder.withKeys(FileObject.class)
                        .andKeys(StyledDocument.class, doc -> {
                            Object o = doc.getProperty(StreamDescriptionProperty);
                            if (o instanceof DataObject) {
                                return ((DataObject) o).getPrimaryFile();
                            } else if (o instanceof FileObject) {
                                return ((FileObject) o);
                            }
                            throw new IllegalArgumentException("Can't get a FileObject from " + o + " in " + doc);
                        }).andKeys(Path.class, path -> {
                    File f = FileUtil.normalizeFile(path.toFile());
                    return FileUtil.toFileObject(f);
                }).withEventApplier((FileObject fo, AntlrGenerationEvent event,
                        Collection<? extends BiConsumer<Extraction, GrammarRunResult<T>>> listeners) -> {
                    dispatch(key, fo, event, listeners);
                }).withInitialMapSize(32)
                        .withCacheType(MapFactories.EQUALITY_CONCURRENT)
                        .withInitialSubscriberSetSize(5)
                        .withSets(SetFactories.WEAK_HASH).threadSafe()
                        .withSynchronousEventDelivery()
                        .onSubscribe(this::onSubscribe)
                        .onUnsubscribe(this::onUnsubscribe)
                        .build();
        return result;
    }

    private <T> void onSubscribe(Object usedKey, FileObject file, BiConsumer<Extraction, GrammarRunResult<T>> consumer) {
        synchronized (this) {
            if (!subscribedTo.containsKey(file)) {
                subscribedTo.put(file, RebuildSubscriptions.subscribe(file, regenerationSubscriber));
            }
        }
    }

    private <T> void onUnsubscribe(Object usedKey, FileObject file, BiConsumer<Extraction, GrammarRunResult<T>> consumer) {
        Int remaining = Int.create();
        Set<SubscribableContents<Object, FileObject, ?, ?>> all
                = new HashSet<>();
        subscriptions.visitKeys(new KeyVisitor() {
            @Override
            public <V> void accept(K<V> k) {
                InvocationRunnerLookupKey<?> ik = InvocationRunnerLookupKey.cast(k);
                SubscribableContents<Object, FileObject, ?, ?> subs = countSubscribers(ik, file, remaining);
                if (subs != null) {
                    all.add(subs);
                }
            }
        });
        remaining.ifEqual(0, () -> {
            Runnable unsubscribe = subscribedTo.remove(file);
            if (unsubscribe != null) {
                unsubscribe.run();
            }
            for (SubscribableContents<Object, FileObject, ?, ?> s : all) {
                s.subscribable.destroyed(file);
            }
        });
    }

    private <T> SubscribableContents<Object, FileObject, ?, ?> countSubscribers(InvocationRunnerLookupKey<T> ik, FileObject file, Int count) {
        SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent> subs = subscriptions.get(ik, null);
        if (subs != null) {
            count.increment(subs.store.subscribersTo(file).size());
        }
        return subs;
    }

    private <T> void dispatch(InvocationRunnerLookupKey<T> key, FileObject fo, AntlrGenerationEvent event, Collection<? extends BiConsumer<Extraction, GrammarRunResult<T>>> c) {
        if (!c.isEmpty()) {
            GrammarRunResult<T> runResult = run(event, fo, key);
            if (runResult != null) {
                for (BiConsumer<Extraction, GrammarRunResult<T>> consumer : c) {
                    consumer.accept(event.extraction, runResult);
                }
            }
        }
    }

    private <T> void withOneKey(InvocationRunnerLookupKey<T> key, AntlrGenerationEvent info) {
        info.extraction.source().lookup(FileObject.class, fo -> {
            SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent> subs = subscriptions.get(key, null);
            if (subs != null) {
                subs.eventInput.onEvent(fo, info);
            }
        });
    }

    private <T> GrammarRunResult<T> run(AntlrGenerationEvent event,
            FileObject grammarFile, InvocationRunnerLookupKey<T> ik) {
        EnvironmentKey<T> ek = new EnvironmentKey<>(grammarFile, ik);
        InvocationEnvironment<T, ?> env = environment(ek, event);
        try {
            return env.run(event, grammarFile);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    /**
     * This is the thing that listens on subscribed files for events when the
     * Antlr sources have been regenerated and we should recompile and rerun.
     */
    static class SubscriberImpl implements Subscriber {

        private final AR2 ar2;

        public SubscriberImpl(AR2 ar2) {
            this.ar2 = ar2;
        }

        @Override
        public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
                String mimeType, Extraction extraction,
                AntlrGenerationResult res, ParseResultContents populate,
                Fixes fixes) {
            AntlrGenerationEvent reb = new AntlrGenerationEvent(tree, mimeType,
                    extraction, res);
            ar2.subscriptions.visitKeys(new KeyVisitor() {
                @Override
                public <V> void accept(K<V> k) {
                    InvocationRunnerLookupKey<?> ik = InvocationRunnerLookupKey.cast(k);
                    ar2.withOneKey(ik, reb);
                }
            });
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

    static <T, R> InvocationEnvironment<T, R> createInvocationEnvironment(InvocationRunnerLookupKey<T> key,
            InvocationRunner<T, R> runner, FileObject file) {
        return new InvocationEnvironment<>(key, runner, file);
    }

}

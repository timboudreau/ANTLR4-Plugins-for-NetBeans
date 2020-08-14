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
package org.nemesis.antlr.live.execution;

/**
 *
 * @author Tim Boudreau
 */
public class AR2 {
/*
    public static final String BASE_PATH = "antlr/invokers/";
    static final Logger LOG = Logger.getLogger(AntlrRunSubscriptions.class.getName());
    private static final Set<String> WARNED = new HashSet<>(3);
    private static final Supplier<AR2> INSTANCE_SUPPLIER
            = CachingSupplier.of(AR2::new);
    private final TypedCache subscriptions = new TypedCache(MapFactories.EQUALITY_CONCURRENT);
    private final Map<Class<?>, InvocationKey<?>> keyForType = new HashMap<>();
    private final SubscriberImpl regenerationSubscriber = new SubscriberImpl();
    private final Map<FileObject, Runnable> subscribedTo = new HashMap<>();

    AR2() {
    }

    static AR2 instance() {
        return INSTANCE_SUPPLIER.get();
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

    private <T> InvocationKey<T> keyFor(Class<T> type) {
        InvocationKey<?> result
                = keyForType.get(type);
        if (result == null) {
            result = new InvocationKey<>(type);
            keyForType.put(type, result);
        }
        return (InvocationKey<T>) result;
    }

    private <T> void _subscribe(FileObject fo, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        InvocationKey<T> key = keyFor(type);
        SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, RebuildInfo> obj
                = subscriptions.get(key, () -> newSubscribable(type));
        obj.subscribable.subscribe(fo, c);
    }

    private <T> void _subscribe(Document doc, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        InvocationKey<T> key = keyFor(type);
        SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, RebuildInfo> obj
                = subscriptions.get(key, () -> newSubscribable(type));
        obj.subscribable.subscribe(doc, c);
    }

    private <T> void _subscribe(Path file, Class<T> type, BiConsumer<Extraction, GrammarRunResult<T>> c) {
        InvocationKey<T> key = keyFor(type);
        SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, RebuildInfo> obj
                = subscriptions.get(key, () -> newSubscribable(type));
        obj.subscribable.subscribe(file, c);
    }

    private <T> SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, RebuildInfo> newSubscribable(Class<T> type) {
        SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, RebuildInfo> result = SubscribableBuilder.withKeys(FileObject.class)
                .andKeys(StyledDocument.class, doc -> {
                    return NbEditorUtilities.getFileObject(doc);
                }).andKeys(Path.class, path -> {
            File f = FileUtil.normalizeFile(path.toFile());
            return FileUtil.toFileObject(f);
        }).withEventApplier((FileObject fo, RebuildInfo event, Collection<? extends BiConsumer<Extraction, GrammarRunResult<T>>> listeners) -> {
            dispatch(type, fo, event, listeners);
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
                InvocationKey<?> ik = InvocationKey.cast(k);
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

    private <T> SubscribableContents<Object, FileObject, ?, ?> countSubscribers(InvocationKey<T> ik, FileObject file, Int count) {
        SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, RebuildInfo> subs = subscriptions.get(ik, null);
        if (subs != null) {
            count.increment(subs.store.subscribersTo(file).size());
        }
        return subs;
    }

    private <T> void dispatch(Class<T> type, FileObject fo, RebuildInfo event, Collection<? extends BiConsumer<Extraction, GrammarRunResult<T>>> c) {
        if (!c.isEmpty()) {
            GrammarRunResult<T> runResult = event.runResult(type);
            for (BiConsumer<Extraction, GrammarRunResult<T>> consumer : c) {
                consumer.accept(event.extraction, runResult);
            }
        }
    }

    private <T> void withOneKey(InvocationKey<T> key, RebuildInfo info) {
        info.extraction.source().lookup(FileObject.class, fo -> {
            SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, RebuildInfo> subs = subscriptions.get(key, null);
            if (subs != null) {
                subs.eventInput.onEvent(fo, info);
            }
        });
    }

    class SubscriberImpl implements Subscriber {

        @Override
        public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
                String mimeType, Extraction extraction,
                AntlrGenerationResult res, ParseResultContents populate,
                Fixes fixes) {
            RebuildInfo reb = new RebuildInfo(tree, mimeType, extraction, res);
            subscriptions.visitKeys(new KeyVisitor() {
                @Override
                public <V> void accept(K<V> k) {
                    InvocationKey<?> ik = InvocationKey.cast(k);
                    withOneKey(ik, reb);
                }
            });
        }
    }

    final class RebuildInfo {

        final ANTLRv4Parser.GrammarFileContext tree;
        final String mimeType;
        final Extraction extraction;
        final AntlrGenerationResult res;
        final Set<GrammarRunResult<?>> results = new HashSet<>();

        public RebuildInfo(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res) {
            this.tree = tree;
            this.mimeType = mimeType;
            this.extraction = extraction;
            this.res = res;
        }

        <T> GrammarRunResult<T> runResult(Class<T> type) {
            for (GrammarRunResult<?> g : results) {
                if (type.isInstance(g.get())) {
                    return (GrammarRunResult<T>) g;
                }
            }
            GrammarRunResult<T> r = run(type, this);
            if (r != null) {
                results.add(r);
            }
            return r;
        }
    }

    private <T> GrammarRunResult<T> run(Class<T> type, RebuildInfo info) {
        return null;
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

    static class InvocationKey<T> extends K<SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, RebuildInfo>> {

        private final Class<T> invocationResultType;

        public InvocationKey(Class<T> invocationResultType) {
            super(SubscribableContents.class, invocationResultType.getName());
            this.invocationResultType = invocationResultType;
        }

        Class<T> invocationResultType() {
            return invocationResultType;
        }

        static <T> InvocationKey<T> cast(K<?> k) {
            if (k instanceof InvocationKey<?>) {
                return (InvocationKey<T>) k;
            }
            throw new AssertionError(k + "");
        }
    }

    static final class InvocationEnvironment<T, R> {

        private final Class<T> type;
        private final InvocationRunner<T, R> runner;

        InvocationEnvironment(Class<T> type, InvocationRunner<T, R> runner) {
            this.type = type;
            this.runner = runner;
        }
    }
*/
}

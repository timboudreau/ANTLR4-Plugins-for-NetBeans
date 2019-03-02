package org.nemesis.extraction;

import java.util.Collection;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.nemesis.data.Hashable;
import org.nemesis.data.SemanticRegions;
import static org.nemesis.extraction.ExtractionRegistration.BASE_PATH;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.source.api.GrammarSource;
import org.openide.util.lookup.Lookups;

/**
 * An object which extracts information about source regions using strategies
 * provided to its builder, producing an {@link Extraction}, which is
 * essentially a lightweight, serializable, high performance database of
 * information about the file's contents.
 * <p>
 * Usage:
 * <ol>
 * <li>Create ExtractionKey objects such as NamedRegionKey, to identify the
 * things you want to extract and retrieve; typically these are stored as public
 * static fields.</li>
 * <li>Call <code>Extractor.builder(SomeParseTreeType.class)</code> to create a
 * builder, which you will provide strategies to for what to extract and
 * how</li>
 * <li>Run your extraction (you will need an implementation of GrammarSource
 * which can resolve imports if files reference contents from other files).</li>
 * <li>Use your keys to retrieve data structures that describe elements in the
 * sources, such as variable definitions or anything else that could be derived
 * from a ParserRuleContext, and use them for syntax highlighting, semantic
 * error detection, code folding, etc.</li>
 * </ol>
 *
 * @author Tim Boudreau
 */
public final class Extractor<T extends ParserRuleContext> {

    private final Class<T> documentRootType;
    final Set<NamesAndReferencesExtractionStrategy<?>> nameExtractors;
    final Set<RegionExtractionStrategies<?>> regionsInfo;
    private String extractorsHash;
    final Map<SingletonKey<?>, SingletonExtractionStrategies<?>> singles;

    Extractor(Class<T> documentRootType, Set<NamesAndReferencesExtractionStrategy<?>> nameExtractors, Set<RegionExtractionStrategies<?>> regionsInfo2, Map<SingletonKey<?>, SingletonExtractionStrategies<?>> singles) {
        this.documentRootType = documentRootType;
        this.nameExtractors = nameExtractors;
        this.regionsInfo = regionsInfo2;
        this.singles = singles;
    }

    public static Extractor<ParserRuleContext> empty() {
        return new Extractor<>(ParserRuleContext.class, emptySet(), emptySet(), emptyMap());
    }

    public static <T extends ParserRuleContext> Extractor<T> empty(Class<T> type) {
        return new Extractor<>(type, emptySet(), emptySet(), emptyMap());
    }

    public boolean isEmpty() {
        return nameExtractors.isEmpty() && regionsInfo.isEmpty() && singles.isEmpty();
    }

    public static <T extends ParserRuleContext> ExtractorBuilder<T> builder(Class<T> documentRootType) {
        return new ExtractorBuilder<>(documentRootType);
    }

    public Class<T> documentRootType() {
        return documentRootType;
    }

    /**
     * Returns an opaque string representing a unique hash code for all of the
     * strategies employed in creating this extraction. This is useful when you
     * are caching Extractions on disk to avoid re-parses, but do not want to
     * accidentally work against extractions produced by an older version of
     * your module which will not contain what you need or otherwise be
     * incompatible.
     *
     * @return A hash string
     */
    public String extractorsHash() {
        if (extractorsHash == null) {
            Hashable.Hasher hasher = Hashable.newHasher();
            hasher.writeString("eh"); // predictable empty value
            for (NamesAndReferencesExtractionStrategy<?> n : nameExtractors) {
                hasher.hashObject(n);
            }
            for (RegionExtractionStrategies<?> e : regionsInfo) {
                hasher.hashObject(e);
            }
            for (Map.Entry<SingletonKey<?>, SingletonExtractionStrategies<?>> e : singles.entrySet()) {
                hasher.hashObject(e.getValue());
            }
            extractorsHash = hasher.hash();
        }
        return extractorsHash;
    }

    /**
     * Run extraction, passing a rule node and grammar source (which is used to
     * resolve imports).
     *
     * @param ruleNode The root node of the parse tree to walk
     * @param source The source (document, file, whatever) where the content is
     * found, which optionally can resolve references to other sources.
     *
     * @return An extraction - a database of information about the content of
     * this file, whose contents can be retrieved by keys provided to this
     * extractor's builder.
     */
    public Extraction extract(T ruleNode, GrammarSource<?> source, Supplier<? extends TokenStream> streamSupplier) {
        Extraction extraction = new Extraction(extractorsHash(), source);
        for (RegionExtractionStrategies<?> r : regionsInfo) {
            runRegions2(r, ruleNode, extraction, streamSupplier);
        }
        for (NamesAndReferencesExtractionStrategy<?> n : nameExtractors) {
            runNames(ruleNode, n, extraction);
        }
        for (Map.Entry<SingletonKey<?>, SingletonExtractionStrategies<?>> e : singles.entrySet()) {
            runSingles(e.getValue(), ruleNode, extraction);
        }
        return extraction;
    }

    private <K> void runSingles(SingletonExtractionStrategies<K> single, T ruleNode, Extraction extraction) {
        SingletonEncounters<K> encounters = single.extract(ruleNode);
        extraction.addSingle(single.key, encounters);
    }

    private <L extends Enum<L>> void runNames(T ruleNode, NamesAndReferencesExtractionStrategy<L> x, Extraction into) {
        x.invoke(ruleNode, into.store);
    }

    private <K> void runRegions2(RegionExtractionStrategies<K> info, T ruleNode, Extraction extraction, Supplier<? extends TokenStream> streamSupplier) {
        SemanticRegions.SemanticRegionsBuilder<K> bldr = SemanticRegions.builder(info.key.type());
        ParseTreeVisitor<?> v = info.createVisitor((k, bounds) -> {
            bldr.add(k, bounds[0], bounds[1]);
        });
        ruleNode.accept(v);
        RegionsCombiner<K> combiner = new RegionsCombiner<>(bldr.build());
        info.runTokenExtrationStrategies(combiner, streamSupplier);
        extraction.add(info.key, combiner.get());
    }

    static final class RegionsCombiner<T> implements Consumer<SemanticRegions<T>> {

        private SemanticRegions<T> value;

        RegionsCombiner(SemanticRegions<T> initial) {
            this.value = initial;
        }

        SemanticRegions<T> get() {
            return value;
        }

        @Override
        public void accept(SemanticRegions<T> t) {
            if (value == null) {
                value = t;
            } else if (!t.isEmpty()) {
                if (value.isEmpty()) {
                    value = t;
                } else {
                    value = value.combineWith(t);
                }
            }
        }
    }

    private static String registrationPath(String mimeType, Class<?> entryPointType) {
        // Keep BASE_PATH and this method in sync with identical method in
        // ExtractionContributorRegistrationProcessor
        return BASE_PATH + "/" + mimeType + "/" + entryPointType.getName().replace('.', '/').replace('$', '/');
    }

    public static <T extends ParserRuleContext> Extractor<? super T>
            forTypes(String mimeType, Class<T> entryPointType) {
        // Pending: use extrator cache code below
        String path = registrationPath(mimeType, entryPointType);
        Collection<? extends ExtractionContributor> all = Lookups.forPath(path)
                .lookupAll(ExtractionContributor.class);
        System.out.println("Look up extractors in " + path + " getting " + all.size());
        Consumer<ExtractorBuilder<? extends T>> consumer = null;
        for (ExtractionContributor c : all) {
            if (consumer == null) {
                consumer = c;
            } else {
                consumer = consumer.andThen(c);
            }
        }
        if (consumer == null) {
            System.out.println("no extractors, use empty");
            return Extractor.empty(entryPointType);
        }
        ExtractorBuilder<T> eb = new ExtractorBuilder<>(entryPointType);
        consumer.accept(eb);
        return eb.build();
    }

    /*
    static Supplier<ExtractorCache> CACHE_INSTANCE = CachingSupplier.of(ExtractorCache::new);

    private static ExtractorCache cache() { return CACHE_INSTANCE.get(); }

    static final class ExtractorCache implements LookupListener, Runnable {

        private final Map<String, Lookup.Result<ExtractionContributor>> resultMap = new HashMap<>();
        private final Map<Lookup.Result<ExtractionContributor<?>>, String> pathForResult = new IdentityHashMap<>();
        private final Map<String, Extractor<?>> extractorForPath = new HashMap<>();
        private final Map<String, Instant> touched = new HashMap<>();

        private static final ScheduledExecutorService THREAD = Executors.newScheduledThreadPool(1);
        private static final Duration MAX_AGE = Duration.ofMinutes(5);

        @SuppressWarnings("LeakingThisInConstructor")
        ExtractorCache() {
            THREAD.scheduleAtFixedRate(this, 3, 3, TimeUnit.MINUTES);
        }

        @Override
        public void run() {
            Thread.currentThread().setName("antlr-extractor-gc");
            Instant now = Instant.now();
            Set<String> toRemove = new HashSet<>();
            try {
                // Garbage collect extractors unused for more than some number of minutes, and
                // all related objects
                Map<Lookup.Result<ExtractionContributor<?>>, Boolean> resultsToRemove = new IdentityHashMap<>();
                Map<String, Instant> touches;
                synchronized (this) {
                    touches = new HashMap<>(touched);
                }
                for (Map.Entry<String, Instant> touch : touches.entrySet()) {
                    Instant then = touch.getValue();
                    Duration age = Duration.between(then, now);
                    if (age.compareTo(MAX_AGE) > 0) {
                        String remove = touch.getKey();
                        toRemove.add(remove);
                        for (Map.Entry<Lookup.Result<ExtractionContributor<?>>, String> e : pathForResult.entrySet()) {
                            if (remove.equals(e.getValue())) {
                                resultsToRemove.put(e.getKey(), true);
                            }
                        }
                    }
                }
                synchronized (this) {
                    removeAllFromMap(resultMap, toRemove);
                    removeAllFromMap(extractorForPath, toRemove);
                    removeAllFromMap(touched, toRemove);
                    removeAllFromMap(pathForResult, resultsToRemove.keySet());
                }
            } catch (Exception e) {
                Logger.getLogger(ExtractorCache.class.getName()).log(Level.SEVERE, "Exception gc'ing " + toRemove, e);
            }
        }

        public <T extends ParserRuleContext> Extractor<? super T> extractor(String mimeType, Class<T> entryPointType) {
            String path = registrationPath(mimeType, entryPointType);
            touched.put(path, Instant.now());
            return extractorForPath(path, entryPointType);
        }

        private <T> void removeAllFromMap(Map<T, ?> from, Set<T> items) {
            for (T item : items) {
                from.remove(item);
            }
        }

        @SuppressWarnings("unchecked")
        private <T extends ParserRuleContext> Extractor<? super T> extractorForPath(String path, Class<T> entryPointType) {
            Extractor<? super T> result = (Extractor<? super T>) extractorForPath.get(path);
            if (result == null) {
                List<Consumer<ExtractorBuilder<? super T>>> all = extractionContributors(path, entryPointType);
                if (all.isEmpty()) {
                    result = Extractor.empty(entryPointType);
                } else {
                    result = buildExtractor(all, entryPointType);
                    extractorForPath.put(path, result);
                }
            }
            return result;
        }

        private <T extends ParserRuleContext> Extractor<? super T> buildExtractor(List<Consumer<ExtractorBuilder<? super T>>> from, Class<T> entryPointType) {
            Consumer<ExtractorBuilder<? super T>> consumer = null;
            for (Consumer<ExtractorBuilder<? super T>> c : from) {
                if (consumer == null) {
                    consumer = c;
                } else {
                    consumer = consumer.andThen(c);
                }
            }
            if (consumer == null) {
                return Extractor.empty(entryPointType);
            }
            ExtractorBuilder<T> eb = new ExtractorBuilder<>(entryPointType);
            consumer.accept(eb);
            return eb.build();
        }

        private <T extends ParserRuleContext> List<Consumer<ExtractorBuilder<? super T>>> extractionContributors(String path, Class<T> type) {
            Lookup.Result<ExtractionContributor> res = resultForPath(path);
            Collection<? extends ExtractionContributor> c = res.allInstances();
            List<Consumer<ExtractorBuilder<? super T>>> contributors = new ArrayList<>(c.size());
            for (ExtractionContributor<?> e : c) {
                Consumer<ExtractorBuilder<? super T>> oneContributor = e.castIfCompatible(type);
                if (oneContributor != null) {
                    contributors.add(oneContributor);
                }
            }
            return contributors;
        }

        private Lookup.Result<ExtractionContributor> resultForPath(String path) {
            Lookup.Result<ExtractionContributor> res = resultMap.get(path);
            if (res == null) {
                res = lookupResult(path);
            }
            return res;
        }

        private Lookup.Result<ExtractionContributor> lookupResult(String path) {
            Lookup.Result<ExtractionContributor> res = Lookups.forPath(path).lookupResult(ExtractionContributor.class);
            resultMap.put(path, res);
            pathForResult.put(res, path);
            res.addLookupListener(this);
            return res;
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            Lookup.Result<?> res = (Lookup.Result<?>) ev.getSource();
            String path = pathForResult.get(res);
            if (path != null) {
                extractorForPath.remove(path);
            }

        }

        private static final class ExtractorCacheKey<T extends ParserRuleContext> {

            private final String mimeType;
            private final Class<T> entryPoint;

            public ExtractorCacheKey(String mimeType, Class<T> entryPoint) {
                this.mimeType = mimeType;
                this.entryPoint = entryPoint;
            }

            @Override
            public int hashCode() {
                int hash = 3;
                hash = 97 * hash + Objects.hashCode(this.mimeType);
                hash = 97 * hash + Objects.hashCode(this.entryPoint);
                return hash;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final ExtractorCacheKey<?> other = (ExtractorCacheKey<?>) obj;
                if (!Objects.equals(this.mimeType, other.mimeType)) {
                    return false;
                }
                if (!Objects.equals(this.entryPoint, other.entryPoint)) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                return "ExtractorCacheKey{" + "mimeType=" + mimeType + ", entryPoint=" + entryPoint + '}';
            }

        }
    }
     */
}

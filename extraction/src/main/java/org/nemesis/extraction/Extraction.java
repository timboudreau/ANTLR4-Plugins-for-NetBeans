package org.nemesis.extraction;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.IndexAddressable.NamedIndexAddressable;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.graph.StringGraph;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.SerializationContext;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.source.api.GrammarSource;

/**
 * A lightweight, high performance database of information about data extracted
 * by parsing a single file, which may be serialized and cached.
 *
 */
public final class Extraction implements Externalizable {

    private final Map<RegionsKey<?>, SemanticRegions<?>> regions = new HashMap<>();
    private final Map<NamedRegionKey<?>, NamedSemanticRegions<?>> nameds = new HashMap<>();
    private final Map<NamedRegionKey<?>, Map<String, Set<NamedSemanticRegion<?>>>> duplicates = new HashMap<>();
    private final Map<NameReferenceSetKey<?>, NamedRegionReferenceSets<?>> refs = new HashMap<>();
    private final Map<NameReferenceSetKey<?>, StringGraph> graphs = new HashMap<>();
    private final Map<NameReferenceSetKey<?>, SemanticRegions<UnknownNameReference<?>>> unknowns = new HashMap<>();
    private final Map<SingletonKey<?>, SingletonEncounters<?>> singles = new HashMap<>(4);
    private volatile transient Map<NameReferenceSetKey<?>, Map<UnknownNameReference.UnknownNameReferenceResolver<?, ?, ?, ?>, Attributions<?, ?, ?, ?>>> resolutionCache;
    private volatile transient Map<Set<ExtractionKey<?>>, Set<String>> keysCache;
    private transient Map<Extractor<?>, Map<GrammarSource<?>, Extraction>> cachedExtractions;
    private String extractorsHash;
    private GrammarSource<?> source;

    Extraction(String extractorsHash, GrammarSource<?> source) {
        this.extractorsHash = extractorsHash;
        this.source = source;
    }

    public void dispose() {
        regions.clear();
        nameds.clear();
        duplicates.clear();
        refs.clear();
        graphs.clear();
        unknowns.clear();
        singles.clear();
        if (resolutionCache != null) {
            resolutionCache.clear();
            resolutionCache = null;
        }
        if (keysCache != null) {
            keysCache.clear();
            keysCache = null;
        }
        if (cachedExtractions != null) {
            cachedExtractions.clear();
            cachedExtractions = null;
        }
        source = GrammarSource.none();
    }

    private String _logString() {
        int count = regions.size() + nameds.size() + duplicates.size() + refs.size()
                + singles.size();
        StringBuilder sb = new StringBuilder("Extraction{")
                .append(source).append(", size=").append(count);
        Set<ExtractionKey<?>> all = new HashSet<>(regions.keySet());
        all.addAll(nameds.keySet());
        all.addAll(refs.keySet());
        all.addAll(singles.keySet());
        for (Iterator<ExtractionKey<?>> it = all.iterator(); it.hasNext();) {
            ExtractionKey<?> k = it.next();
            String nm = k.getClass().getSimpleName();
            sb.append('(').append(k).append('-').append(nm).append(')');
            if (it.hasNext()) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    /**
     * Provides a less voluminous string for logging purposes.
     *
     * @return A supplier
     */
    public Supplier<String> logString() {
        return new LSP(this);
    }

    static final class LSP implements Supplier<String> {

        private final Extraction extraction;

        LSP(Extraction ext) {
            this.extraction = ext;
        }

        @Override
        public String get() {
            return extraction._logString();
        }

        @Override
        public String toString() {
            return get();
        }
    }

    /**
     * Public constructor for deserialization.
     *
     */
    public Extraction() {
    }

    /**
     * Get the source from whence this data was parsed.
     *
     * @return The source
     */
    public GrammarSource<?> source() {
        return source;
    }

    /**
     * Resolve an "imported" source relative to this one. What exactly is looked
     * up and where is dependent on the GrammarSource implementation.
     *
     * @param name The name
     * @return A source, or null if the grammar source cannot resolve the name
     */
    public GrammarSource<?> resolveRelative(String name) {
        return source.resolveImport(name);
    }

    /**
     * Get a map of any duplicates which were found when searching for names
     * with the given key. The NamedSemanticRegions returned by the
     * namedRegions() method can only contain one element with any given name;
     * if duplicates were encountered, they are preserved here.
     *
     * @param <T> The type
     * @param key The key
     * @return A map of duplicates, or an empty map
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends Enum<T>> Map<String, Set<NamedSemanticRegion<T>>> duplicates(NamedRegionKey<T> key) {
        Map result = duplicates.get(key);
        if (result == null) {
            result = Collections.emptyMap();
        }
        return result;
    }

    /**
     * Fetch a region or reference matching a given key at a given position in
     * the source.
     *
     * @param <T> The key type
     * @param pos The source position
     * @param a A key
     * @return A region, or null
     */
    public <T extends Enum<T>> NamedSemanticRegion<T> regionOrReferenceAt(int pos, NamedExtractionKey<T> a) {
        return regionOrReferenceAt(pos, Collections.singleton(a));
    }

    /**
     * Fetch a region or reference matching either of two keys.
     *
     * @param <T> The common key type
     * @param pos The source positon
     * @param a The first key
     * @param b The second key
     * @param c The third key
     * @return The narrowest matching region, if any
     */
    public <T extends Enum<T>> NamedSemanticRegion<T> regionOrReferenceAt(int pos, NamedExtractionKey<T> a, NamedExtractionKey<T> b) {
        return regionOrReferenceAt(pos, Arrays.asList(a, b));
    }

    /**
     * Fetch a region or reference matching any of three keys.
     *
     * @param <T> The common key type
     * @param pos The source positon
     * @param a The first key
     * @param b The second key
     * @param c The third key
     * @return The narrowest matching region, if any
     */
    public <T extends Enum<T>> NamedSemanticRegion<T> regionOrReferenceAt(int pos, NamedExtractionKey<T> a, NamedExtractionKey<T> b, NamedExtractionKey<T> c) {
        return regionOrReferenceAt(pos, Arrays.asList(a, b, c));
    }

    /**
     * Fetch a region or reference occuring at the given position, under any of
     * the passed keys. When multiple keys match, the most specific (narrowest)
     * region found is returned.
     *
     * @param <T> The type
     * @param pos The position in the source
     * @param keys A collection of keys to look up
     * @return A region from one of the sets matching some key in the
     * collection, or null
     */
    @SuppressWarnings("unchecked")
    public <T extends Enum<T>> NamedSemanticRegion<T> regionOrReferenceAt(int pos, Iterable<? extends NamedExtractionKey<T>> keys) {
        NamedSemanticRegion<T> result = null;
        for (NamedExtractionKey<T> key : keys) {
            NamedSemanticRegion<T> candidate = null;
            if (key instanceof NamedRegionKey<?>) {
                NamedRegionKey<T> k = (NamedRegionKey<T>) key;
                NamedSemanticRegions<T> ns = (NamedSemanticRegions<T>) nameds.get(k);
                if (ns != null) {
                    candidate = ns.at(pos);
                }
            } else if (key instanceof NameReferenceSetKey<?>) {
                NameReferenceSetKey<T> k = (NameReferenceSetKey<T>) key;
                NamedRegionReferenceSets<T> r = (NamedRegionReferenceSets<T>) refs.get(k);
                if (r != null) {
                    candidate = r.at(pos);
                }
            }
            if (candidate != null) {
                if (result == null || candidate.length() < result.length()) {
                    result = candidate;
                }
            }
        }
        return result;
    }

    /**
     * Get an iterable that contains all regions stored for any of the passed
     * keys.
     *
     * @param <T> The type
     * @param all An array of keys for named regions or references
     * @return An iterable
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <T extends Enum<T>> Iterable<NamedSemanticRegion<T>> allRegions(NamedExtractionKey<T>... all) {
        Set<NamedIndexAddressable<? extends NamedSemanticRegion<T>>> collections = new HashSet<>();
        Class<T> type = null;
        for (NamedExtractionKey<T> k : all) {
            if (type != null) {
                if (k.type() != type) {
                    throw new IllegalArgumentException("Heterogenous key types " + type + " and " + k.type());
                }
            } else {
                type = k.type();
            }
            if (k instanceof NamedRegionKey<?>) {
                collections.add(namedRegions((NamedRegionKey<T>) k));
            } else if (k instanceof NameReferenceSetKey<?>) {
                collections.add(nameReferences((NameReferenceSetKey<T>) k));
            }
        }
        if (collections.isEmpty()) {
            return Collections.emptySet();
        }
        return NamedSemanticRegions.combine(collections, true);
    }

    /**
     * Resolve the {@link Extraction} associated with an import name. Note that
     * extractions are cached, keyed by extractor and source, so multiple
     * resolutions of the same name do not run separate parses.
     *
     * @param extractor The extractor to use
     * @param importName The name of the import (language dependent)
     * @param runExtraction A function which will run extraction for this source
     * @return An extraction which is presumably not this one, or an empty
     * extraction if none was found
     */
    public synchronized Extraction resolveExtraction(Extractor<?> extractor, String importName, Function<GrammarSource<?>, Extraction> runExtraction) {
        if (this.source != null && this.source.name().equals(importName)) {
            return this;
        }
        GrammarSource<?> src = resolveRelative(importName);
        if (src == null) {
            return new Extraction();
        }
        return resolveExtraction(extractor, src, runExtraction);
    }

    /**
     * Resolve the {@link Extraction} associated with another source. Note that
     * extractions are cached, keyed by extractor and source, so multiple
     * resolutions of the same name do not run separate parses.
     *
     * @param extractor The extractor to use
     * @param importName The name of the import (language dependent)
     * @param runExtraction A function which will run extraction for this source
     * @return An extraction which is presumably not this one
     */
    public synchronized Extraction resolveExtraction(Extractor<?> extractor, GrammarSource<?> src, Function<GrammarSource<?>, Extraction> runExtraction) {
        if (src.equals(source)) {
            return this;
        }
        if (cachedExtractions != null) {
            Map<GrammarSource<?>, Extraction> forSource = cachedExtractions.get(extractor);
            if (forSource != null) {
                Extraction cached = forSource.get(src);
                if (cached != null) {
                    return cached;
                }
            }
        }
        Extraction nue = runExtraction.apply(src);
        if (cachedExtractions == null) {
            cachedExtractions = new HashMap<>();
        }
        Map<GrammarSource<?>, Extraction> forSource = cachedExtractions.get(extractor);
        if (forSource == null) {
            forSource = new HashMap<>();
            cachedExtractions.put(extractor, forSource);
        }
        forSource.put(src, nue);
        nue.cachedExtractions = cachedExtractions;
        return nue;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("extractor:").append(extractorsHash);
        if (!regions.isEmpty()) {
            sb.append("\n******************* SEMANTIC REGIONS **********************");
            for (Map.Entry<RegionsKey<?>, SemanticRegions<?>> e : regions.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                sb.append("\n ------------- REGIONS ").append(e.getKey()).append(" -----------------");
                for (SemanticRegion<?> reg : e.getValue()) {
                    sb.append("\n  ").append(reg);
                }
            }
        }
        if (!nameds.isEmpty()) {
            sb.append("\n******************* NAMED REGIONS **********************");
            for (Map.Entry<NamedRegionKey<?>, NamedSemanticRegions<?>> e : nameds.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                sb.append("\n ------------- NAMED SET ").append(e.getKey()).append(" -----------------");
                for (NamedSemanticRegion<?> nr : e.getValue().index()) {
                    sb.append("\n  ").append(nr);
                }
            }
        }
        if (!refs.isEmpty()) {
            sb.append("\n******************* REFERENCES TO NAMED REGIONS **********************");
            for (Map.Entry<NameReferenceSetKey<?>, NamedRegionReferenceSets<?>> e : refs.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                sb.append("\n ------------- REFERENCE SETS ").append(e.getKey()).append(" -----------------");
                for (NamedSemanticRegionReference<?> nrrs : e.getValue().asIterable()) {
                    sb.append("\n  ").append(nrrs);
                }
            }
        }
        if (!graphs.isEmpty()) {
            sb.append("\n******************* REFERENCE GRAPHS **********************");
            for (Map.Entry<NameReferenceSetKey<?>, StringGraph> e : graphs.entrySet()) {
                sb.append("\n ------------- GRAPH ").append(e.getKey()).append(" -----------------");
                sb.append("\n  ").append(e.getValue());
            }
        }
        if (!unknowns.isEmpty()) {
            sb.append("\n******************* UNKNOWN REFERENCES **********************");
            for (Map.Entry<NameReferenceSetKey<?>, SemanticRegions<UnknownNameReference<?>>> e : unknowns.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                sb.append("\n ------------- UNKNOWNS ").append(e.getKey()).append(" -----------------");
                for (SemanticRegion<UnknownNameReference<?>> v : e.getValue()) {
                    sb.append("\n  ").append(v);
                }
            }
        }
        if (!singles.isEmpty()) {
            sb.append("\n******************* SINGLETONS **********************");
            for (Map.Entry<SingletonKey<?>, SingletonEncounters<?>> e : singles.entrySet()) {
                sb.append("\n ------------- SINGLETON ").append(e.getKey()).append(" -----------------");
                sb.append(e.getValue());
            }
        }
        return sb.toString();
    }

    public Set<String> allKeys(NamedExtractionKey<?> first, NamedExtractionKey<?>... more) {
        Set<ExtractionKey<?>> keys = new HashSet<>();
        keys.add(first);
        keys.addAll(Arrays.asList(more));
        Set<String> result = keysCache == null ? null : keysCache.get(keys);
        if (result == null) {
            result = new HashSet<>();
            for (ExtractionKey<?> k : keys) {
                if (k instanceof NamedRegionKey<?>) {
                    NamedRegionKey<?> kk = (NamedRegionKey<?>) k;
                    NamedSemanticRegions<?> r = nameds.get(kk);
                    if (r != null) {
                        result.addAll(Arrays.asList(r.nameArray()));
                    }
                } else if (k instanceof NameReferenceSetKey<?>) {
                    NameReferenceSetKey<?> nrk = (NameReferenceSetKey<?>) k;
                    NamedRegionReferenceSets<?> r = refs.get(nrk);
                    if (r != null) {
                        for (NamedRegionReferenceSet<?> i : r) {
                            result.add(i.name());
                        }
                    }
                }
            }
            if (keysCache == null) {
                keysCache = new HashMap<>();
            }
            keysCache.put(keys, result);
        }
        return result;
    }

    /**
     * This string identifies the <i>code</i> which generated an extraction. In
     * the case that these are serialized and cached on disk, this can be used
     * (for example, as a parent directory name) to ensure that a newer version
     * of a module does not load an extraction generated by a previous one
     * unless the code matches. For lambdas, hash generation relies on the
     * consistency of toString() and hashCode() for lambdas across runs - which
     * works fine on JDK 8/9. At worst, you get a false negative and parse.
     *
     * @return A hash value which should be unique to the contents of the
     * Extractor which created this extraction.
     */
    public String creationHash() {
        return extractorsHash;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public void writeExternal(ObjectOutput out) throws IOException {
        Set<NamedSemanticRegions<?>> allNamed = new HashSet<>(nameds.values());
        SerializationContext ctx = SerializationContext.createSerializationContext(allNamed);
        try {
            SerializationContext.withSerializationContext(ctx, () -> {
                out.writeInt(1);
                out.writeObject(ctx);
                out.writeUTF(extractorsHash);
                out.writeObject(regions);
                out.writeObject(nameds);
                out.writeObject(refs);
                out.writeObject(unknowns);
                out.writeObject(singles);
                out.writeInt(graphs.size());
                for (Map.Entry<NameReferenceSetKey<?>, StringGraph> e : graphs.entrySet()) {
                    out.writeObject(e.getKey());
                    e.getValue().save(out);
                }
            });
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Extraction.class.getName())
                    .log(Level.SEVERE, null, ex);
        }
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int v = in.readInt();
        if (v != 1) {
            throw new IOException("Incompatible version " + v);
        }
        SerializationContext ctx = (SerializationContext) in.readObject();
        SerializationContext.withSerializationContext(ctx, () -> {
            extractorsHash = in.readUTF();
            Map<RegionsKey<?>, SemanticRegions<?>> sregions = (Map<RegionsKey<?>, SemanticRegions<?>>) in.readObject();
            Map<NamedRegionKey<?>, NamedSemanticRegions<?>> snameds = (Map<NamedRegionKey<?>, NamedSemanticRegions<?>>) in.readObject();
            Map<NameReferenceSetKey<?>, NamedRegionReferenceSets<?>> srefs = (Map<NameReferenceSetKey<?>, NamedRegionReferenceSets<?>>) in.readObject();
            Map<NameReferenceSetKey<?>, SemanticRegions<UnknownNameReference<?>>> sunknowns = (Map<NameReferenceSetKey<?>, SemanticRegions<UnknownNameReference<?>>>) in.readObject();
            Map<SingletonKey<?>, SingletonEncounters<?>> ssingles = (Map<SingletonKey<?>, SingletonEncounters<?>>) in.readObject();
            int graphCount = in.readInt();
            for (int i = 0; i < graphCount; i++) {
                NameReferenceSetKey<?> k = (NameReferenceSetKey<?>) in.readObject();
                StringGraph graph = StringGraph.load(in);
                graphs.put(k, graph);
            }
            this.regions.putAll(sregions);
            this.nameds.putAll(snameds);
            this.refs.putAll(srefs);
            this.unknowns.putAll(sunknowns);
            this.singles.putAll(ssingles);
        });
    }

    <T> void add(RegionsKey<T> key, SemanticRegions<T> oneRegion) {
        regions.put(key, oneRegion);
    }

    @SuppressWarnings("unchecked")
    public <T extends Enum<T>> NamedRegionReferenceSets<T> references(NameReferenceSetKey<T> key) {
        NamedRegionReferenceSets<?> result = refs.get(key);
        return (NamedRegionReferenceSets<T>) result;
    }

    public StringGraph referenceGraph(NameReferenceSetKey<?> key) {
        return graphs.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T extends Enum<T>> NamedSemanticRegions<T> namedRegions(NamedRegionKey<T> key) {
        NamedSemanticRegions<?> result = nameds.get(key);
        return result == null ? NamedSemanticRegions.empty() : (NamedSemanticRegions<T>) result;
    }

    @SuppressWarnings("unchecked")
    public <T extends Enum<T>> NamedRegionReferenceSets<T> nameReferences(NameReferenceSetKey<T> key) {
        NamedRegionReferenceSets<?> result = refs.get(key);
        return result == null ? NamedRegionReferenceSets.empty() : (NamedRegionReferenceSets<T>) result;
    }

    @SuppressWarnings(value = "unchecked")
    public <T> SemanticRegions<T> regions(RegionsKey<T> key) {
        SemanticRegions<?> result = regions.get(key);
        assert result == null || key.type().equals(result.keyType());
        return result == null ? SemanticRegions.empty() : (SemanticRegions<T>) result;
    }

    @SuppressWarnings("unchecked")
    public <T extends Enum<T>> SemanticRegions<UnknownNameReference<T>> unknowns(NameReferenceSetKey<T> key) {
        SemanticRegions<UnknownNameReference<?>> result = unknowns.get(key);
        if (result == null) {
            return SemanticRegions.empty();
        }
        SemanticRegions x = result; // XXX
        return x;
    }

    public void clearResolutionCache() {
        resolutionCache = null;
    }

    @SuppressWarnings("unchecked")
    public <R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> Attributions<R, I, N, T> resolveUnknowns(NameReferenceSetKey<T> key, UnknownNameReference.UnknownNameReferenceResolver<R, I, N, T> res) throws IOException {
        Map<NameReferenceSetKey<?>, Map<UnknownNameReference.UnknownNameReferenceResolver<?, ?, ?, ?>, Attributions<?, ?, ?, ?>>> rc = this.resolutionCache;
        Map<UnknownNameReference.UnknownNameReferenceResolver<?, ?, ?, ?>, Attributions<?, ?, ?, ?>> perResolverCache = null;
        if (rc != null) {
            perResolverCache = rc.get(key);
            if (perResolverCache != null) {
                Attributions<R, I, N, T> result = (Attributions<R, I, N, T>) perResolverCache.get(res);
                if (result != null) {
                    return result;
                }
            }
        } else {
            rc = new HashMap<>();
            perResolverCache = new WeakHashMap<>();
            rc.put(key, perResolverCache);
        }
        SemanticRegions<UnknownNameReference<T>> u = unknowns(key);
        if (u == null) {
            return null;
        }
        Class c = AttributedForeignNameReference.class;
        SemanticRegions.SemanticRegionsBuilder<AttributedForeignNameReference<R, I, N, T>> bldr = SemanticRegions.builder(c);
        SemanticRegions.SemanticRegionsBuilder<UnknownNameReference> remaining = SemanticRegions.builder(UnknownNameReference.class);
        for (SemanticRegion<UnknownNameReference<T>> reg : u) {
            AttributedForeignNameReference<R, I, N, T> r = reg.key().resolve(this, res);
            if (r != null) {
                bldr.add(r, reg.start(), reg.end());
            } else {
                remaining.add(reg.key(), reg.start(), reg.end());
            }
        }
        Attributions<R, I, N, T> result = new Attributions<>(bldr.build(), remaining.build());
        perResolverCache.put(res, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    public <K> SingletonEncounters<K> encounters(SingletonKey<K> key) {
        SingletonEncounters<K> result = (SingletonEncounters<K>) singles.get(key);
        return result == null ? new SingletonEncounters<>() : result;
    }

    <K> void addSingle(SingletonKey<K> key, SingletonEncounters<K> encounters) {
        singles.put(key, encounters);
    }

    NameInfoStore store = new NameInfoStore() {
        @Override
        public <T extends Enum<T>> void addNamedRegions(NamedRegionKey<T> key, NamedSemanticRegions<T> regions) {
            nameds.put(key, regions);
        }

        @Override
        public <T extends Enum<T>> void addReferences(NameReferenceSetKey<T> key, NamedRegionReferenceSets<T> regions) {
            refs.put(key, regions);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Enum<T>> void addReferenceGraph(NameReferenceSetKey<T> refSetKey, StringGraph stringGraph) {
            graphs.put(refSetKey, stringGraph);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends Enum<T>> void addUnknownReferences(NameReferenceSetKey<T> refSetKey, SemanticRegions<UnknownNameReference<T>> unknownReferences) {
            SemanticRegions s = unknownReferences;
            unknowns.put(refSetKey, s);
        }

        @Override
        public <T extends Enum<T>> void addDuplicateNamedRegions(NamedRegionKey<T> key, String name, Iterable<? extends NamedSemanticRegion<T>> duplicates) {
            Set<NamedSemanticRegion<?>> all = new HashSet<>();
            for (NamedSemanticRegion<T> x : duplicates) {
                all.add(x);
            }
            Map<String, Set<NamedSemanticRegion<?>>> m = Extraction.this.duplicates.get(key);
            if (m == null) {
                m = new HashMap<>();
                Extraction.this.duplicates.put(key, m);
            }
            m.put(name, all);
        }
    };
}

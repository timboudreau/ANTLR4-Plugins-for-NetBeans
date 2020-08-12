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
package org.nemesis.extraction.attribution;

import com.mastfrog.util.cache.TimedCache;
import static com.mastfrog.util.collections.CollectionUtils.immutableSetOf;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ResolutionConsumer;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.UnknownNameReferenceResolver;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Looks up declaratively registered resolvers by mime type. Note that an
 * implementation of Extractions must be registered in the default lookup for
 * the simple subtypes in this package to work.
 *
 * @author Tim Boudreau
 */
public final class ResolverRegistry {

    public static final String RESOLVER_BASE_PATH = "antlr/resolvers";
    private static final TimedCache<String, ResolverRegistry, RuntimeException> REGISTRIES
            = TimedCache.create(60000, ResolverRegistry::findRegistry);
    private final Lookup lkp;
    private static final Logger LOG = Logger.getLogger(ResolverRegistry.class.getName());

    private ResolverRegistry(Lookup lkp) {
        this.lkp = lkp;
    }

    private static ResolverRegistry findRegistry(String mimeType) {
        Lookup lkp = Lookups.forPath(RESOLVER_BASE_PATH + "/" + mimeType);
        return new ResolverRegistry(lkp);
    }

    public static ResolverRegistry forMimeType(String mime) {
        return REGISTRIES.get(mime);
    }

    static ImportFinder importFinder(String mime) {
        ResolverRegistry reg = REGISTRIES.get(mime);
        Collection<? extends ImportFinder> all = reg.lkp.lookupAll(ImportFinder.class);
        switch (all.size()) {
            case 0:
                return ImportFinder.EMPTY;
            case 1:
                return all.iterator().next();
            default:
                return new PolyImportFinder(all);
        }
    }

    public <K extends Enum<K>> UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K> resolver(Extraction ext, Class<K> type) {
        List<RegisterableResolver<K>> toTry = new ArrayList<>();
        for (RegisterableResolver stub : lkp.lookupAll(RegisterableResolver.class)) {
            RegisterableResolver<K> candidate = stub.ifMatches(type);
            if (candidate != null) {
                toTry.add(candidate);
            }
        }
        if (!toTry.isEmpty()) {
            if (toTry.size() == 1) {
                return toTry.get(0);
            }
            return new PolyResolver<>(type, toTry);
        }
        return null;
    }

    private static final class PolyImportFinder implements ImportFinder, ImportKeySupplier {

        private final Collection<? extends ImportFinder> finders;

        public PolyImportFinder(Collection<? extends ImportFinder> finders) {
            this.finders = finders;
        }

        @Override
        public Set<GrammarSource<?>> allImports(Extraction importer, Set<? super NamedSemanticRegion<? extends Enum<?>>> notFound) {
            Set<GrammarSource<?>> result = new HashSet<>();
            for (ImportFinder f : finders) {
                result.addAll(f.allImports(importer, notFound));
            }
            return result;
        }

        @Override
        public NamedRegionKey<?>[] get() {
            Set<NamedRegionKey<?>> set = new LinkedHashSet<>();
            for (ImportFinder i : finders) {
                if (i instanceof ImportKeySupplier) {
                    NamedRegionKey<?>[] keys = ((ImportKeySupplier) i).get();
                    set.addAll(Arrays.asList(keys));
                }
            }
            return set.toArray(new NamedRegionKey<?>[set.size()]);
        }

        @Override
        public <T extends Enum<T>> void importsForKey(Set<? super GrammarSource<?>> result, NamedRegionKey<T> k, Extraction importer, Set<? super NamedSemanticRegion<? extends Enum<?>>> notFound) {
            for (ImportFinder imp : finders) {
                if (imp instanceof ImportKeySupplier) {
                    ImportKeySupplier keyImp = (ImportKeySupplier) imp;
                    Set<NamedRegionKey<?>> keys = immutableSetOf(keyImp.get());
                    if (keys.contains(k)) {
                        keyImp.importsForKey(result, k, importer, notFound);
                    }
                }
            }
        }
    }

    private static final class PolyResolver<K extends Enum<K>> implements UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K> {

        private final Class<K> type;

        private final List<RegisterableResolver<K>> delegates;

        public PolyResolver(Class<K> type, List<RegisterableResolver<K>> stubs) {
            this.type = type;
            this.delegates = stubs;
        }

        @Override
        public Class<K> type() {
            return type;
        }

        @Override
        public <X> Map<UnknownNameReference<K>, X> resolveAll(Extraction extraction, SemanticRegions<UnknownNameReference<K>> refs, ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K, X> c) throws IOException {
            Map<UnknownNameReference<K>, X> result = new HashMap<>();
            for (RegisterableResolver<K> stub : delegates) {
                result.putAll(stub.resolveAll(extraction, refs, c));
            }
            return result;
        }

        @Override
        public <X> X resolve(Extraction extraction, UnknownNameReference<K> ref, ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K, X> c) throws IOException {
            for (RegisterableResolver<K> stub : delegates) {
                try {
                    X result = stub.resolve(extraction, ref, c);
                    if (result != null) {
                        return result;
                    }
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, "Exception resolving " + ref + " with " + stub, ex);
                }
            }
            return null;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + Objects.hashCode(this.type);
            hash = 41 * hash + Objects.hashCode(this.delegates);
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
            final PolyResolver<?> other = (PolyResolver<?>) obj;
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            if (!Objects.equals(this.delegates, other.delegates)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "PolyResolver(" + type.getSimpleName() + ": " + delegates + ")";
        }
    }
}

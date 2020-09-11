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
package org.nemesis.source.spi;

import com.mastfrog.converters.Converters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public abstract class DocumentAdapterRegistry {

    private final Converters converters = new Converters();
    private static DocumentAdapterRegistry INSTANCE;

    protected abstract List<? extends RelativeResolverImplementation<?>> allResolvers(String mimeType);

    protected abstract List<? extends DocumentAdapter<?, ?>> allAdapters();

    Converters converters() {
        if (converters.isEmpty()) {
            allAdapters().forEach((rra) -> {
                addOne(rra);
            });
        }
        return converters;
    }

    private <F, T> void addOne(DocumentAdapter<F, T> rra) {
        converters.register(rra.fromType(), rra.toType(), rra.convertTo());
        converters.register(rra.toType(), rra.fromType(), rra.convertFrom());
    }

    @Override
    public String toString() {
        return converters().toString();
    }

    private final Map<CacheKey, RelativeResolverImplementation<?>> cache = new HashMap<>();

    static <T> Class<? super T> shallowest(Set<Class<? super T>> types) {
        List<Class<? super T>> copy = new ArrayList<>(types);
        Collections.sort(copy, (a, b) -> {
            return Integer.compare(classDepth(a), classDepth(b));
        });
        return copy.isEmpty() ? null : copy.get(0);
    }

    static int classDepth(Class<?> type) {
        int result = 0;
        while (type.getSuperclass() != null) {
            result++;
            type = type.getSuperclass();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public final <T> RelativeResolverImplementation<T> forDocumentAndMimeType(T document, String mimeType) {
        CacheKey key = new CacheKey(mimeType, document);
        RelativeResolverImplementation<?> cached = cache.get(key);
        if (cached != null) {
            return (RelativeResolverImplementation<T>) cached;
        }

        Set<RelativeResolverImplementation<T>> matches = new LinkedHashSet<>();
        Set<Class<? super T>> compatible = converters().compatibleTypes(document);
        if (compatible.isEmpty()) {
            return null;
        }
        for (Class<? super T> type : converters().compatibleTypes(document)) {
            forDocumentAndMimeType(type, document, mimeType, (Set) matches);
        }
        Class<? super T> targetType = shallowest(compatible);
        RelativeResolverImplementation<T> result = matches.isEmpty()
                ? RelativeResolverImplementation.noop((Class<T>) document.getClass())
                : matches.size() == 1 ? matches.iterator().next() : new PolyResolv(targetType, matches);
        if (!matches.isEmpty()) {
            cache.put(key, result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T, ImplT extends T> void forDocumentAndMimeType(Class<T> apiType, ImplT document, String mimeType, Set<RelativeResolverImplementation<T>> addTo) {
        List<? extends RelativeResolverImplementation<T>> matches = allResolversMatching(apiType, document, mimeType);
        RelativeResolverImplementation<T> result = matches == null || matches.isEmpty()
                ? RelativeResolverImplementation.noop((Class<T>) document.getClass())
                : matches.size() == 1 ? matches.get(0) : new PolyResolv<T>(matches.get(0).type(), matches);
        addTo.add(result);
    }

    static final class PolyResolv<T> extends RelativeResolverImplementation<T> {

        private final Collection<? extends RelativeResolverImplementation<T>> origs;

        PolyResolv(Class<T> type, Collection<? extends RelativeResolverImplementation<T>> origs) {
            super(type);
            this.origs = origs;
        }

        @Override
        public Optional<T> resolve(T relativeTo, String name) {
            for (RelativeResolverImplementation<T> rr : origs) {
                Optional<T> resolved = rr.resolve(relativeTo, name);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "PolyResolv(" + origs + ")";
        }

        @Override
        public int hashCode() {
            return origs.hashCode() + (71 * type.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null || !(obj instanceof PolyResolv<?>)) {
                return false;
            }
            final PolyResolv<?> other = (PolyResolv<?>) obj;
            return origs.equals(other.origs);
        }
    }

    private static final class CacheKey {

        private final Class<?> documentType;
        private final String mime;

        CacheKey(String mime, Object doc) {
            this(doc.getClass(), mime);
        }

        CacheKey(Class<?> documentType, String mime) {
            assert documentType != null;
            assert mime != null;
            this.documentType = documentType;
            this.mime = mime;
        }

        @Override
        public int hashCode() {
            return mime.hashCode() + (71 * documentType.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof CacheKey) {
                CacheKey k = (CacheKey) o;
                return documentType == k.documentType && mime.equals(k.mime);
            }
            return false;
        }

        @Override
        public String toString() {
            return documentType.getSimpleName() + "{" + mime + "}";
        }
    }

    private <T, ImplT extends T> List<? extends RelativeResolverImplementation<T>> allResolversMatching(Class<T> apiType,
            ImplT doc, String mimeType) {
        if ("text/plain".equals(mimeType)) {
            new Exception(mimeType).printStackTrace();
        }
        assert apiType.isInstance(doc) : "Incompatible: " + apiType + " and " + doc;
        List<RelativeResolverImplementation<T>> result = new ArrayList<>();
        List<? extends RelativeResolverImplementation<?>> forMimeType = allResolvers(mimeType);
        for (RelativeResolverImplementation rr : forMimeType) {
            if (rr.type().isInstance(doc)) {
                result.add((RelativeResolverImplementation<T>) rr);
            }
        }
        for (RelativeResolverImplementation<?> rr : forMimeType) {
            RelativeResolverImplementation<T> res = tryToCreate(rr, apiType);
            if (res != null) {
                result.add(res);
            }
        }
        return result;
    }

    private <F, T> RelativeResolverImplementation<T> tryToCreate(RelativeResolverImplementation<F> rri, Class<T> type) {
        Function<? super T, ? extends F> cvt = converters().converter(type, rri.type);
        Function<? super F, ? extends T> rev = converters().converter(rri.type, type);
        if (cvt != null && rev != null) {
            return new RRWrap3<>(rri, rev, cvt, type);
        }
        return null;
    }

    static class RRWrap3<F, T> extends RelativeResolverImplementation<T> {

        private final RelativeResolverImplementation<F> orig;
        private final Function<? super F, ? extends T> rev;
        private final Function<? super T, ? extends F> cvt;

        public RRWrap3(RelativeResolverImplementation<F> orig,
                Function<? super F, ? extends T> rev,
                Function<? super T, ? extends F> cvt,
                Class<T> type) {
            super(type);
            this.orig = orig;
            this.rev = rev;
            this.cvt = cvt;
        }

        @Override
        public String toString() {
            return "RRWrap3(" + orig + " -> " + type.getSimpleName() + " {"
                    + cvt + ", " + rev + "})";
        }

        @Override
        public Optional<T> resolve(T relativeTo, String name) {
            F obj = cvt.apply(relativeTo);
            if (obj != null) {
                Optional<F> resolved = orig.resolve(obj, name);
                if (resolved.isPresent()) {
                    T res = rev.apply(resolved.get());
                    return Optional.ofNullable(res);
                }
            }
            return Optional.empty();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.orig);
            hash = 59 * hash + Objects.hashCode(this.rev);
            hash = 59 * hash + Objects.hashCode(this.cvt);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null || !(obj instanceof RRWrap3<?,?>)) {
                return false;
            }
            final RRWrap3<?, ?> other = (RRWrap3<?, ?>) obj;
            return orig.equals(other.orig) &&
                    Objects.equals(cvt, other.cvt)
                    && Objects.equals(rev, other.rev);
        }
    }

    public static DocumentAdapterRegistry getDefault() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        DocumentAdapterRegistry reg = null;
        try {
            reg = Lookup.getDefault().lookup(DocumentAdapterRegistry.class);
        } catch (NoClassDefFoundError notOnClasspath) {
            // ok
            Logger.getLogger(DocumentAdapterRegistry.class.getName())
                    .log(Level.INFO, "Lookup not on classpath", notOnClasspath);
        }
        if (reg == null) {
            reg = getFromServiceLoader();
            if (reg == null) {
                reg = new Dummy();
            }
        }
        return INSTANCE = reg;
    }

    private static DocumentAdapterRegistry getFromServiceLoader() {
        ServiceLoader<DocumentAdapterRegistry> sl = ServiceLoader.load(DocumentAdapterRegistry.class);
        Iterator<DocumentAdapterRegistry> iter = sl.iterator();
        return iter.hasNext() ? iter.next() : null;
    }

    static final class Dummy extends DocumentAdapterRegistry {

        private boolean warned;

        @Override
        protected List<? extends RelativeResolverImplementation<?>> allResolvers(String mimeType) {
            if (!warned) {
                Logger.getLogger(Dummy.class.getName()).log(Level.WARNING, "No implementation of {0} is registered.  " + "Siblings of files will not be resolved.", DocumentAdapterRegistry.class.getName());
                warned = true;
            }
            return Collections.emptyList();
        }

        @Override
        protected List<? extends DocumentAdapter<?, ?>> allAdapters() {
            return Collections.emptyList();
        }
    }
}

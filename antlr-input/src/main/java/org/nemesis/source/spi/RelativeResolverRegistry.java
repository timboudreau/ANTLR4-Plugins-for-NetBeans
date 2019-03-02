/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.source.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public abstract class RelativeResolverRegistry {

    private static RelativeResolverRegistry INSTANCE;

    protected abstract List<? extends RelativeResolverImplementation<?>> allResolvers(String mimeType);

    protected abstract List<? extends RelativeResolverAdapter<?, ?>> allAdapters();

    private final Map<CacheKey, RelativeResolverImplementation<?>> cache = new HashMap<>();
    @SuppressWarnings("unchecked")
    public final <T> RelativeResolverImplementation<T> forDocumentAndMimeType(T document, String mimeType) {
        CacheKey key = new CacheKey(mimeType, document);
        RelativeResolverImplementation<?> cached = cache.get(key);
        if (cached != null) {
            return (RelativeResolverImplementation<T>) cached;
        }
        List<? extends RelativeResolverImplementation<T>> matches = allResolversMatching(document, mimeType);
        RelativeResolverImplementation<T> result = matches == null || matches.isEmpty()
                ? RelativeResolverImplementation.noop((Class<T>) document.getClass())
                : matches.iterator().next();
        if (matches != null) {
            cache.put(key, result);
        }
        return result;
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
            return documentType.getName() + "{" + mime + "}";
        }
    }

    @SuppressWarnings({"rawTypes", "unchecked"})
    private <T> List<? extends RelativeResolverImplementation<T>> allResolversMatching(T doc, String mimeType) {
        List<RelativeResolverImplementation<T>> result = new ArrayList<>();
        List<? extends RelativeResolverImplementation<?>> forMimeType = allResolvers(mimeType);
        for (RelativeResolverImplementation rr : forMimeType) {
            if (rr.type().isInstance(doc)) {
                result.add((RelativeResolverImplementation<T>) rr);
            }
        }
        if (result.isEmpty() && !forMimeType.isEmpty()) {
            List<? extends RelativeResolverAdapter<?, ?>> adapters = allAdapters();
            for (RelativeResolverImplementation<?> rr : forMimeType) {
                for (RelativeResolverAdapter<?, ?> adap : adapters) {
                    RelativeResolverImplementation<T> adapted = adap.match(rr, doc);
                    if (adapted != null) {
                        result.add(adapted);
                    }
                }
            }
        }
        return result;
    }

    public static RelativeResolverRegistry getDefault() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        RelativeResolverRegistry reg = null;
        try {
            reg = Lookup.getDefault().lookup(RelativeResolverRegistry.class);
        } catch (NoClassDefFoundError notOnClasspath) {
            // ok
            Logger.getLogger(RelativeResolverRegistry.class.getName())
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

    private static RelativeResolverRegistry getFromServiceLoader() {
        ServiceLoader<RelativeResolverRegistry> sl = ServiceLoader.load(RelativeResolverRegistry.class);
        Iterator<RelativeResolverRegistry> iter = sl.iterator();
        return iter.hasNext() ? iter.next() : null;
    }

    static final class Dummy extends RelativeResolverRegistry {

        private boolean warned;

        @Override
        protected List<? extends RelativeResolverImplementation<?>> allResolvers(String mimeType) {
            if (!warned) {
                Logger.getLogger(Dummy.class.getName()).log(Level.WARNING, "No implementation of {0} is registered.  " + "Siblings of files will not be resolved.", RelativeResolverRegistry.class.getName());
                warned = true;
            }
            return Collections.emptyList();
        }

        @Override
        protected List<? extends RelativeResolverAdapter<?, ?>> allAdapters() {
            return Collections.emptyList();
        }
    }
}

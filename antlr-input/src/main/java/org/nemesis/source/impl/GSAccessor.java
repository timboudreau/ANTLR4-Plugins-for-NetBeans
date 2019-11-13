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
package org.nemesis.source.impl;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.spi.DocumentAdapterRegistry;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.nemesis.source.spi.GrammarSourceImplementationFactory;
import org.nemesis.source.spi.RelativeResolverImplementation;

/**
 * See http://wiki.netbeans.org/API_Design for what this madness is.
 *
 * @author Tim Boudreau
 */
public abstract class GSAccessor {

    public static GSAccessor DEFAULT;

    public static GSAccessor getDefault() {
        if (DEFAULT != null) {
            return DEFAULT;
        }
        Class<?> type = GrammarSource.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(GSAccessor.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        assert DEFAULT != null : "The DEFAULT field must be initialized";
        return DEFAULT;
    }

    public <T, R> R lookup(GrammarSourceImplementation<T> impl, Class<R> type) {
        return impl.lookup(type);
    }

    public <T> String nameOf(GrammarSourceImplementation<T> impl) {
        return impl.name();
    }

    public <T> long lastModified(GrammarSourceImplementation<T> impl) throws IOException {
        return impl.lastModified();
    }

    public <T> CharStream stream(GrammarSourceImplementation<T> impl) throws IOException {
        return impl.stream();
    }

    public <T> T source(GrammarSourceImplementation<T> impl) throws IOException {
        return impl.source();
    }

    public String computeId(GrammarSourceImplementation<?> impl) {
        return impl.computeId();
    }

    public abstract String hashString(String string);

    private String stripExtension(String name) {
        int ix = name.lastIndexOf('.');
        if (ix > 0) {
            name = name.substring(0, ix);
        }
        return name;
    }

    public <T> GrammarSource<?> resolve(GrammarSourceImplementation<T> impl, String name) {
        GrammarSourceImplementation<?> imported = impl.resolveImport(name);
        if (imported == null) {
            imported = impl.resolveImport(stripExtension(name));
            if (imported != null) {
                new IllegalArgumentException("Name should not be passed with extension: " + name).printStackTrace();
            }
        }
        return imported == null ? null : newGrammarSource(imported);
    }

    public <T> Class<T> typeFor(GrammarSourceImplementation<T> impl) {
        return impl.type();
    }

    public abstract <T> GrammarSource<T> newGrammarSource(GrammarSourceImplementation<T> impl);

    private static String typeOf(Object o) {
        return o == null ? "<null>" : o.getClass().getName();
    }

    public final <T> GrammarSourceImplementationFactory<T> implementationFactoryFor(String mime, T doc) {
        GrammarSourceImplementationFactory<T> gsFactory = GrammarSourceImplementationFactory.find(doc);
        if (gsFactory == null) {
            throw new IllegalArgumentException("No grammar source factory for documents of type " + typeOf(doc));
        }
        return gsFactory;
    }

    public final <T> GrammarSource<T> newGrammarSource(String mime, T doc) {
        assert mime != null : "null mime";
        assert doc != null : "null document";
        RelativeResolverImplementation<T> resolver = DocumentAdapterRegistry.getDefault().forDocumentAndMimeType(doc, mime);
        GrammarSourceImplementationFactory<T> gsFactory = implementationFactoryFor(mime, doc);
        GrammarSourceImplementation<T> impl = gsFactory.create(doc, RRAccessor.getDefault().newResolver(resolver));
        if (impl == null) {
            throw new IllegalStateException(gsFactory + " returned null creating a GrammarSourceImplementation for " + doc);
        }
        return newGrammarSource(impl);
    }
}

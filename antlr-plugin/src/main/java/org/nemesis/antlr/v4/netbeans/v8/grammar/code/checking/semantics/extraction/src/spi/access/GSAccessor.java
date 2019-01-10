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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.access;

import java.io.IOException;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.GrammarSource;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.GrammarSourceImplementationFactory;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.GrammarSourceImplementation;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.RelativeResolverImplementation;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.RelativeResolverRegistry;
import org.openide.util.Exceptions;

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
            Exceptions.printStackTrace(ex);
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

    public <T> GrammarSource<?> resolve(GrammarSourceImplementation<T> impl, String name) {
        GrammarSourceImplementation<?> imported = impl.resolveImport(name);
        if (imported == null) {
            System.out.println("CANNOT RESOLVE " + name + " against " + impl);
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

    public final <T> GrammarSource<T> newGrammarSource(String mime, T doc) {
        assert mime != null : "null mime";
        assert doc != null : "null document";
        RelativeResolverImplementation<T> resolver = RelativeResolverRegistry.getDefault().forDocumentAndMimeType(doc, mime);
        GrammarSourceImplementationFactory<T> gsFactory = GrammarSourceImplementationFactory.find(doc);
        if (gsFactory == null) {
            throw new IllegalArgumentException("No grammar source factory for documents of type " + typeOf(doc));
        }
        GrammarSourceImplementation<T> impl = gsFactory.create(doc, RRAccessor.getDefault().newResolver(resolver));
        if (impl == null) {
            throw new IllegalStateException(gsFactory + " returned null creating a GrammarSourceImplementation for " + doc);
        }
        return newGrammarSource(impl);
    }
}

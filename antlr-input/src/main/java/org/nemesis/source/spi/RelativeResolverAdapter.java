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

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.impl.RRAccessor;

/**
 * Resolves &quot;imported&quot; files when parsing with Antlr.
 *
 * @author Tim Boudreau
 */
public class RelativeResolverAdapter<F, T> {

    private final Class<F> fromType;
    private final Class<T> toType;
    private final Function<F, T> convertTo;
    private final Function<T, F> convertFrom;

    protected RelativeResolverAdapter(Class<F> fromType, Class<T> toType, Function<F, T> convertTo, Function<T, F> convertFrom) {
        this.fromType = fromType;
        this.toType = toType;
        this.convertTo = convertTo;
        this.convertFrom = convertFrom;
    }

    RelativeResolverAdapter<T,F> reverse() {
        return new RelativeResolverAdapter<>(toType, fromType, convertFrom, convertTo);
    }

    Class<F> fromType() {
        return fromType;
    }

    Class<T> toType() {
        return toType;
    }

    Function<T, F> convertFrom() {
        return convertFrom;
    }

    Function<F, T> convertTo() {
        return convertTo;
    }

    @SuppressWarnings("unchecked")
    <I> RelativeResolverImplementation<F> castIfMatches(RelativeResolverImplementation<I> r) {
        if (r.type() == fromType || r.type().isAssignableFrom(fromType)) {
            return (RelativeResolverImplementation<F>) r;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    final <I, J> RelativeResolverImplementation<J> match(RelativeResolverImplementation<I> impl, J doc) {
        if (toType.isInstance(doc)) {
            RelativeResolverImplementation<F> cast = castIfMatches(impl);
            if (cast != null) {
                RelativeResolverAdapter<I, J> castAdapter = (RelativeResolverAdapter<I, J>) this;
                return castAdapter.adapt(impl);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    final RelativeResolverImplementation<T> adapt(RelativeResolverImplementation<?> from) {
        if (fromType.isAssignableFrom(from.type())) {
            return adaptImpl((RelativeResolverImplementation<F>) from);
        }
        return null;
    }

    /**
     * Adapter a RelativeResolverImplementation to a different type (e.g.
     * Document to FileObject).
     *
     * @param from The original adapter
     * @return An adapter which wrappers the original and returns objects of a
     * different type.
     */
    protected final RelativeResolverImplementation<T> adaptImpl(RelativeResolverImplementation<F> from) {
        return simpleAdapter(from);
    }

    @Override
    public String toString() {
        return fromType.getName() + "->" + toType.getName();
    }

    /**
     * Create a simple adapter passing two conversion functions.
     *
     * @param from The original resolver
     * @param convertTo A conversion function
     * @param convertFrom A reverse conversion function
     * @return An implementation of RelativeResolverImplementation which
     * delegates to the passed one and uses the conversion functions to return
     * the expected types
     */
    final RelativeResolverImplementation<T> simpleAdapter(RelativeResolverImplementation<F> from) {
        return adapter(toType, from, convertTo, convertFrom);
    }

    static <T, F> RelativeResolverImplementation<T> adapter(Class<T> toType, RelativeResolverImplementation<F> from, Function<F, T> convertTo, Function<T, F> convertFrom) {
        return new AdaptImpl<>(from, toType, convertTo, convertFrom);
    }

    @Override
    public final int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.fromType);
        hash = 89 * hash + Objects.hashCode(this.toType);
        return hash;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RelativeResolverAdapter<?, ?> other = (RelativeResolverAdapter<?, ?>) obj;
        if (!Objects.equals(this.fromType, other.fromType)) {
            return false;
        }
        return Objects.equals(this.toType, other.toType);
    }

    @SuppressWarnings("unchecked")
    final <X, Y> GrammarSourceImplementationFactory<Y> adaptSourceFactoryIfAble(GrammarSourceImplementationFactory<X> impl, Y doc) {
        if (toType.isInstance(doc) && impl.type() == fromType()) {
            return (GrammarSourceImplementationFactory<Y>) adaptFactoryFrom((GrammarSourceImplementationFactory<F>) impl);
        } else if (fromType.isInstance(doc) && impl.type() == toType()) {
            return (GrammarSourceImplementationFactory<Y>) adaptFactoryTo((GrammarSourceImplementationFactory<T>) impl);
        }
        return null;
    }

    GrammarSourceImplementation<T> adaptTo(GrammarSourceImplementation<F> impl) {
        return new AdaptedGrammarSourceImplementation<>(convertTo, impl, toType());
    }

    GrammarSourceImplementation<F> adaptFrom(GrammarSourceImplementation<T> impl) {
        return new AdaptedGrammarSourceImplementation<>(convertFrom, impl, fromType());
    }

    GrammarSourceImplementationFactory<F> adaptFactoryTo(GrammarSourceImplementationFactory<T> f) {
        return new AdaptedGrammarSourceImplementationFactory<>(fromType, f, this.reverse());
    }

    GrammarSourceImplementationFactory<T> adaptFactoryFrom(GrammarSourceImplementationFactory<F> f) {
        return new AdaptedGrammarSourceImplementationFactory<>(toType, f, this);
    }

    static final class AdaptedGrammarSourceImplementationFactory<F,T> extends GrammarSourceImplementationFactory<T> {

        private final GrammarSourceImplementationFactory<F> delegate;
        private final RelativeResolverAdapter<F,T> adapter;

        AdaptedGrammarSourceImplementationFactory(Class<T> type, GrammarSourceImplementationFactory<F> delegate, RelativeResolverAdapter<F,T> adapter) {
            super(type);
            this.delegate = delegate;
            this.adapter = adapter;
        }

        @Override
        public GrammarSourceImplementation<T> create(T doc, RelativeResolver<T> resolver) {
            F adaptedDoc = adapter.convertFrom.apply(doc);
            if (adaptedDoc != null) {
                RelativeResolverImplementation<F> ar = adapter.reverse().adaptImpl(RRAccessor.getDefault().implementation(resolver));
                GrammarSourceImplementation<F> impl = delegate.create(adaptedDoc, RRAccessor.getDefault().newResolver(ar));
                return adapter.adaptTo(impl);
            }
            return null;
        }

    }

    private static final class AdaptedGrammarSourceImplementation<F, T> extends GrammarSourceImplementation<T> {

        private final Function<F, T> convertTo;
        private final GrammarSourceImplementation<F> toAdapt;

        AdaptedGrammarSourceImplementation(Function<F, T> convertTo, GrammarSourceImplementation<F> toAdapt, Class<T> type) {
            super(type);
            this.convertTo = convertTo;
            this.toAdapt = toAdapt;
        }

        @Override
        protected <R> R lookupImpl(Class<R> type) {
            if (type() == type) {
                F origSrc = toAdapt.source();
                return type.cast(convertTo.apply(origSrc));
            } else if (type() == toAdapt.type()) {
                return type.cast(toAdapt.source());
            }
            return super.lookupImpl(type);
        }

        @Override
        public String toString() {
            return "Adapter<" + type.getSimpleName() + ">{" + toAdapt + "}";
        }

        @Override
        public long lastModified() throws IOException {
            return toAdapt.lastModified();
        }

        @Override
        public String name() {
            return toAdapt.name();
        }

        @Override
        public CharStream stream() throws IOException {
            return toAdapt.stream();
        }

        @Override
        public GrammarSourceImplementation<?> resolveImport(String name) {
            return toAdapt.resolveImport(name);
        }

        @Override
        public T source() {
            return convertTo.apply(toAdapt.source());
        }
    }

    private static final class AdaptImpl<F, T> extends RelativeResolverImplementation<T> {

        private final RelativeResolverImplementation<F> from;
        private final Function<F, T> toTo;
        private final Function<T, F> toFrom;

        private AdaptImpl(RelativeResolverImplementation<F> from, Class<T> type, Function<F, T> toTo, Function<T, F> toFrom) {
            super(type);
            this.from = from;
            this.toTo = toTo;
            this.toFrom = toFrom;
        }

        public String toString() {
            return "adapt " + from + " to " + type().getName();
        }

        private F toFileObject(T doc) {
            return toFrom.apply(doc);
        }

        private T toDocument(F fo) {
            return toTo.apply(fo);
        }

        @Override
        public Optional<T> resolve(T relativeTo, String name) {
            F fo = toFileObject(relativeTo);
            if (fo != null) {
                Optional<F> result = from.resolve(fo, name);
                if (result.isPresent()) {
                    T doc = toDocument(result.get());
                    return Optional.ofNullable(doc);
                }
            }
            return Optional.empty();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.from);
            hash = 29 * hash + Objects.hashCode(this.toTo);
            hash = 29 * hash + Objects.hashCode(this.toFrom);
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
            final AdaptImpl<?, ?> other = (AdaptImpl<?, ?>) obj;
            if (!Objects.equals(this.from, other.from)) {
                return false;
            }
            if (!Objects.equals(this.toTo, other.toTo)) {
                return false;
            }
            return Objects.equals(this.toFrom, other.toFrom);
        }
    }
}

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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves &quot;imported&quot; files when parsing with Antlr.
 *
 * @author Tim Boudreau
 */
public abstract class RelativeResolverAdapter<F, T> {

    private final Class<F> fromType;
    private final Class<T> toType;

    protected RelativeResolverAdapter(Class<F> fromType, Class<T> toType) {
        this.fromType = fromType;
        this.toType = toType;
    }

    @SuppressWarnings("unchecked")
    <I> RelativeResolverImplementation<F> castIfMatches(RelativeResolverImplementation<I> r) {
//        if (r.type() == fromType) {
        System.out.println("\n" + r.type().getName() + " and " + fromType.getName()
                + " eq? " + (r.type() == fromType)
                + " assig1 " + (r.type().isAssignableFrom(fromType))
                + " assig2 " + (fromType.isAssignableFrom(r.type()))
                + "\n");

        if (r.type() == fromType || r.type().isAssignableFrom(fromType)) {
            return (RelativeResolverImplementation<F>) r;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <I, J> RelativeResolverImplementation<J> match(RelativeResolverImplementation<I> impl, J doc) {
        if (toType.isInstance(doc)) {
            RelativeResolverImplementation<F> cast = castIfMatches(impl);
            if (cast != null) {
                RelativeResolverAdapter<I,J> castAdapter = (RelativeResolverAdapter<I,J>) this;
                return castAdapter.adapt(impl);
            }
        } else {
            System.out.println("  no match " + toType.getName() + " and " + doc.getClass().getName());
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
    protected abstract RelativeResolverImplementation<T> adaptImpl(RelativeResolverImplementation<F> from);

    public String toString() {
        return fromType.getName() + "->" + toType.getName();
    }

    /**
     * Create a simple adapter passing two conversion functions.
     *
     * @param from The original resolver
     * @param convertTo A conversion function
     * @param convertFrom A reverse conversion function
     * @return An implementation of RelativeResolverImplementation which delegates to the passed one
     * and uses the conversion functions to return the expected types
     */
    protected final RelativeResolverImplementation<T> adaptSimple(RelativeResolverImplementation<F> from, Function<F, T> convertTo, Function<T,F> convertFrom) {
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
        if (!Objects.equals(this.toType, other.toType)) {
            return false;
        }
        return true;
    }

    private static final class AdaptImpl<F,T> extends RelativeResolverImplementation<T> {
        private final RelativeResolverImplementation<F> from;
        private final Function<F,T> toTo;
        private final Function<T,F> toFrom;

        private AdaptImpl(RelativeResolverImplementation<F> from, Class<T> type, Function<F,T> toTo, Function<T,F> toFrom) {
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
            if (!Objects.equals(this.toFrom, other.toFrom)) {
                return false;
            }
            return true;
        }
    }
}

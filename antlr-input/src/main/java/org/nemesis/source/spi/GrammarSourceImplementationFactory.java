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

import com.mastfrog.converters.Converters;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.impl.RRAccessor;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public abstract class GrammarSourceImplementationFactory<T> {

    private final Class<T> type;

    protected GrammarSourceImplementationFactory(Class<T> type) {
        this.type = type;
    }

    protected final Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" + type.getName() + "}";
    }

    @SuppressWarnings("unchecked")
    <R> GrammarSourceImplementationFactory<R> castThis(R obj) {
        if (type().isInstance(obj)) {
            return (GrammarSourceImplementationFactory<R>) this;
        }
        return null;
    }

    public abstract GrammarSourceImplementation<T> create(T doc, RelativeResolver<T> resolver);

    public static <T> GrammarSourceImplementationFactory<T> find(T obj) {
        GrammarSourceImplementationFactory<T> result = null;
        List<? extends GrammarSourceImplementationFactory<?>> all = findAll();
        for (GrammarSourceImplementationFactory<?> factory : all) {
//            System.out.println("CHECK FACTORY " + factory);
            result = factory.castThis(obj);
            if (result != null) {
                break;
            }
        }
        if (result == null) {
            for (GrammarSourceImplementationFactory<?> factory : all) {
                result = adapt(obj, factory);
                if (result != null) {
                    break;
                }
            }
        }
        return result;
    }

    private static <F, T> GrammarSourceImplementationFactory<T> adapt(T doc, GrammarSourceImplementationFactory<F> fac) {
        Converters converters = DocumentAdapterRegistry.getDefault().converters();
        if (converters.canConvert(doc, fac.type())) {
            return new Adapted((Class<T>) doc.getClass(), fac);
        }
        return null;
    }

    static class Adapted<F, T> extends GrammarSourceImplementationFactory<T> {

        private final GrammarSourceImplementationFactory<F> orig;

        Adapted(Class<T> type, GrammarSourceImplementationFactory<F> orig) {
            super(type);
            this.orig = orig;
        }

        @Override
        public GrammarSourceImplementation<T> create(T doc, RelativeResolver<T> resolver) {
            Converters cvt = DocumentAdapterRegistry.getDefault().converters();
            F origDoc = cvt.convert(doc, orig.type());
            RelativeResolverImplementation<T> rrImpl = RRAccessor.getDefault().implementation(resolver);
            Function<? super T, ? extends F> toF = cvt.converter(type(), orig.type());
            Function<? super F, ? extends T> toT = cvt.converter(orig.type(), type());
            RelativeResolverImplementation<F> rra = DocumentAdapter.adapt(rrImpl, orig.type(), toF, toT);
            RelativeResolver<F> rraApi = RRAccessor.getDefault().newResolver(rra);
            GrammarSourceImplementation<F> res = orig.create(origDoc, rraApi);
            return new AdaptedGrammarSourceImplementation<>(toT, res, type());
        }

        public String toString() {
            return orig + " -> " + type().getSimpleName();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.orig);
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
            final Adapted<?, ?> other = (Adapted<?, ?>) obj;
            if (!Objects.equals(this.orig, other.orig) || (type() == other.type())) {
                return false;
            }
            return true;
        }
    }

    private static List<GrammarSourceImplementationFactory<?>> ALL_FACTORIES = new ArrayList<>();

    static synchronized List<? extends GrammarSourceImplementationFactory<?>> findAll() {
        if (ALL_FACTORIES.isEmpty()) {
            for (GrammarSourceImplementationFactory f : Lookup.getDefault().lookupAll(GrammarSourceImplementationFactory.class)) {
                ALL_FACTORIES.add(f);
            }
        }
        return ALL_FACTORIES;
    }

    static Object defaultLookup = null;
    static Method lookupAllMethod = null;

    @SuppressWarnings("unchecked")
    static List<GrammarSourceImplementationFactory<?>> findAllReflectively() {
        try {
            if (defaultLookup == null) {
                Class<?> Lookup = Class.forName("org.openide.util.Lookup");
                Method getDefault = Lookup.getMethod("getDefault");
                defaultLookup = getDefault.invoke(null);
            }
            assert defaultLookup != null : "Lookup.getDefault() returned null";
            Method lookupAll = lookupAllMethod == null ? defaultLookup.getClass().getMethod("lookupAll", Class.class) : lookupAllMethod;
            return (List<GrammarSourceImplementationFactory<?>>) lookupAll.invoke(defaultLookup, GrammarSourceImplementationFactory.class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(GrammarSourceImplementationFactory.class.getName()).log(Level.WARNING, null, ex);
        }
        return null;
    }
}

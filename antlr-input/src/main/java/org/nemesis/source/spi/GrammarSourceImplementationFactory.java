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

        @Override
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

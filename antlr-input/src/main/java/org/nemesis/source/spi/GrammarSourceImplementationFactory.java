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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.source.api.RelativeResolver;

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
//        System.out.println("TEST " + type().getName() + " is instance " + obj.getClass().getName() + ": " + type().isInstance(obj));
        if (type().isInstance(obj)) {
            return (GrammarSourceImplementationFactory<R>) this;
        }
        return null;
    }

    public abstract GrammarSourceImplementation<T> create(T doc, RelativeResolver<T> resolver);

    public static <T> GrammarSourceImplementationFactory<T> find(T obj) {
        GrammarSourceImplementationFactory<T> result = null;
        for (GrammarSourceImplementationFactory<?> factory : findAll()) {
//            System.out.println("CHECK FACTORY " + factory);
            result = factory.castThis(obj);
            if (result != null) {
                break;
            }
        }
        if (result == null) {
            for (GrammarSourceImplementationFactory<?> factory : findAll()) {
//                System.out.println("CHECK ADAPT " + factory + " WITH " + RelativeResolverRegistry.getDefault().allAdapters().size() + " adapters" );
                for (RelativeResolverAdapter<?, ?> adap : RelativeResolverRegistry.getDefault().allAdapters()) {
//                    System.out.println("CHECK ADAPTER FOR " + factory + " and " + adap);
                    result = adap.adaptSourceFactoryIfAble(factory, obj);
                    if (result != null) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    static List<? extends GrammarSourceImplementationFactory<?>> findAll() {
        List<GrammarSourceImplementationFactory<?>> result = findAllReflectively();
        if (result == null) {
            result = new ArrayList<>(10);
            for (GrammarSourceImplementationFactory<?> l : ServiceLoader.load(GrammarSourceImplementationFactory.class)) {
                result.add(l);
            }
        }
        return result;
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

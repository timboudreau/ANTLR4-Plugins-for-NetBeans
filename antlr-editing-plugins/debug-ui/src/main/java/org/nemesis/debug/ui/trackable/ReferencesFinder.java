/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
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
package org.nemesis.debug.ui.trackable;

import com.mastfrog.util.preconditions.Exceptions;
import java.awt.Color;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.openide.util.Lookup;

/**
 * Insane / assertGc seems to be broken on newer JDKs, so something simpler if
 * less powerful but good enough to solve the problem of leaking references.
 * Traverses the object graph from some set of root objects, notifying on paths
 * which match a predicate (which usually will test if the passed object is the
 * same instance as something which should have been garbage collection).
 * Ignores certain types which will wreak havoc if reflected on post-Java
 * modules.
 *
 * @author Tim Boudreau
 */
public class ReferencesFinder {

    /**
     * Returns a list of all reference paths from the root objects to the passed
     * object.
     *
     * @param obj An object to find references to
     * @param roots A map of names to root objects to traverse from
     * @return A list of string paths that describe what is holding references
     * to the passed object
     */
    public static List<String> detect(Object obj, Map<String, Object> roots, Set<Object> omit) {
        if (omit.contains(obj)) {
            throw new IllegalArgumentException();
        }
        List<String> result = new LinkedList<>();
        detect(new EqPredicate(obj), roots, omit, result::add);
        return result;
    }

    /**
     * Returns a list of all reference paths from the root objects to objects
     * which match the passed predicate.
     *
     * @param Pred A predicate
     * @param roots A map of names to root objects to traverse fields from
     * @return A list of string paths that describe what is holding references
     * to objects which match the predicate
     */
    public static List<String> detect(Predicate<Object> pred, Map<String, Object> roots, Set<Object> omit) {
        List<String> result = new LinkedList<>();
        detect(pred, roots, omit, result::add);
        return result;
    }

    /**
     * Search one or more object graphs for objects which match the passed
     * predicate, calling the passed consumer with each path to an an object
     * which matches the predicate.
     *
     * @param pred A predicate to test if the path to an object should be passed
     * to the consumer
     * @param roots A map of names to root objects to traverse fields from
     * @param c A consumer for paths, e.g. <code>root -&gt; someObject -&gt;
     * someOtherObject -&gt; theThingSearchedFor</code>
     */
    public static void detect(Predicate<Object> pred, Map<String, Object> roots, Set<Object> omit, Consumer<String> c) {
        Detector det = new Detector(pred, c, omit);
        for (Map.Entry<String, Object> e : roots.entrySet()) {
            det.accept(e.getKey(), e.getValue());
        }
    }

    private static final class EqPredicate implements Predicate<Object> {

        private final Object o;

        public EqPredicate(Object o) {
            this.o = o;
        }

        @Override
        public boolean test(Object t) {
            return o == t;
        }
    }

    private static final class Detector implements BiConsumer<String, Object> {

        private final Consumer<String> c;
        private List<DissectionStrategy> strategies = new ArrayList<>();
        private final VisitTracker vt = new VisitTracker();
        private final Predicate<Object> test;
        private final Set<Object> omit;

        @SuppressWarnings("LeakingThisInConstructor")
        Detector(Predicate<Object> test, Consumer<String> c, Set<Object> omit) {
            // There are some objects in the window system which will throw
            // an exception if hashCode() is called when not on the event thread,
            // so make sure membership tests do not do that
            Map<Object, Boolean> omitMap = new IdentityHashMap<>();
            omitMap.put(this, true);
            for (Object o : omit) {
                omitMap.put(o, Boolean.TRUE);
            }
            this.omit = omitMap.keySet();
            this.c = c;
            this.test = test;
            strategies.add(new CollectionDissectionStrategy());
            strategies.add(new ArrayDissectionStrategy());
            strategies.add(new ReferenceDissectionStrategy());
            strategies.add(new AtomicReferenceDissectionStrategy());
            strategies.add(new ThreadLocalDissectionStrategy());
            strategies.add(new VarHandleDissectionStrategy());
            strategies.add(new MapDissectionStrategy());
            strategies.add(new LookupDissectionStrategy());
            strategies.add(new FieldDissectionStrategy());
        }

        @Override
        public void accept(String path, Object obj) {
            if (omit.contains(obj)) {
                return;
            }
            if (obj != null && test.test(obj)) {
                c.accept(path + " -> " + obj);
            } else {
                for (DissectionStrategy d : strategies) {
                    if (d.matches(obj)) {
                        if (d.apply(path, obj, this, vt)) {
                            break;
                        }
                    }
                }
            }
        }
    }

    private interface DissectionStrategy {

        boolean matches(Object o);

        boolean apply(String path, Object o, BiConsumer<String, Object> bi, VisitTracker visits);
    }

    private static abstract class AbstractDissectionStrategy<T> implements DissectionStrategy {

        private final Class<? super T> type;

        public AbstractDissectionStrategy(Class<? super T> type) {
            this.type = type;
        }

        public boolean matches(Object o) {
            return type.isInstance(o);
        }

        public boolean apply(String path, Object o, BiConsumer<String, Object> bi, VisitTracker visits) {
            if (o != null && type.isInstance(o)) {
                doApply((T) type.cast(o), path, bi, visits);
                return true;
            }
            return false;
        }

        protected abstract void doApply(T o, String path, BiConsumer<String, Object> bi, VisitTracker visits);
    }

    private static class CollectionDissectionStrategy extends AbstractDissectionStrategy<Collection<?>> {

        CollectionDissectionStrategy() {
            super(Collection.class);
        }

        @Override
        protected void doApply(Collection<?> o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            int ix = 0;
            List<Object> l;
            for (;;) {
                try {
                    l = new ArrayList<>(o);
                    break;
                } catch (ConcurrentModificationException ex) {

                }
            }
            for (Object item : l) {
                if (item != o && visits.shouldVisit(item)) {
                    bi.accept(path + " -> [c-" + ix + "]", item);
                }
                ix++;
            }
        }
    }

    private static class ArrayDissectionStrategy implements DissectionStrategy {

        @Override
        public boolean matches(Object o) {
            boolean result = o != null && o.getClass().isArray();
            if (result) {
                Class<?> comp = o.getClass().getComponentType();
                if (comp == Integer.class || comp == int.class || comp == byte.class || comp == Byte.class
                        || comp == Long.class || comp == long.class || comp == Short.class || comp == short.class
                        || comp == boolean.class || comp == Boolean.class) {
                    return false;
                }
                if (Array.getLength(o) == 0) {
                    return false;
                }
            }
            return result;
        }

        @Override
        public boolean apply(String path, Object o, BiConsumer<String, Object> bi, VisitTracker visits) {
            if (matches(o)) {
                int max = Array.getLength(o);
                for (int i = 0; i < max; i++) {
                    Object item = Array.get(o, i);
                    if (visits.shouldVisit(item)) {
                        bi.accept(path + "[" + i + "]", item);
                    }
                }
                return true;
            }
            return false;
        }
    }

    private static final class MapDissectionStrategy extends AbstractDissectionStrategy<Map<?, ?>> {

        MapDissectionStrategy() {
            super(Map.class);
        }

        @Override
        protected void doApply(Map<?, ?> o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            if (!visits.shouldVisit(o)) {
                return;
            }
            Map<Object, Object> m;
            for (;;) {
                try {
                    m = new HashMap<>(o);
                    break;
                } catch (ConcurrentModificationException cme) {

                }
            }
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                if (visits.shouldVisit(k)) {
                    String kn = path + "[key=" + k + "]";
                    bi.accept(kn, k);
                }
                if (visits.shouldVisit(v)) {
                    String vn = path + "[" + k + "]";
                    bi.accept(vn, v);
                }
            }
            visits.shouldVisit(o);
        }
    }

    private static final class ReferenceDissectionStrategy extends AbstractDissectionStrategy<Reference<?>> {

        ReferenceDissectionStrategy() {
            super(Reference.class);
        }

        @Override
        protected void doApply(Reference<?> o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            try {
                Object refd = o.get();
                if (refd != null && visits.shouldVisit(refd)) {
                    bi.accept(path, refd);
                }
            } catch (UnsupportedOperationException ex) {
                // Phantom reference
            }
        }
    }

    private static final class AtomicReferenceDissectionStrategy extends AbstractDissectionStrategy<AtomicReference<?>> {

        AtomicReferenceDissectionStrategy() {
            super(AtomicReference.class);
        }

        @Override
        protected void doApply(AtomicReference<?> o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            Object refd = o.get();
            if (refd != null && visits.shouldVisit(refd)) {
                bi.accept(path, refd);
            }
        }
    }

    private static final class VarHandleDissectionStrategy extends AbstractDissectionStrategy<VarHandle> {

        VarHandleDissectionStrategy() {
            super(VarHandle.class);
        }

        @Override
        protected void doApply(VarHandle o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            try {
                Object refd = o.get();
                if (refd != null && visits.shouldVisit(refd)) {
                    bi.accept(path, refd);
                }
            } catch (Exception ex) {
                if (!"java.lang.invoke.WrongMethodTypeException".equals(ex.getClass().getName())) {
                    Exceptions.chuck(ex);
                }
            }
        }
    }

    private static final class ThreadLocalDissectionStrategy extends AbstractDissectionStrategy<ThreadLocal<?>> {

        ThreadLocalDissectionStrategy() {
            super(ThreadLocal.class);
        }

        @Override
        protected void doApply(ThreadLocal<?> o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            Object refd = o.get();
            if (refd != null && visits.shouldVisit(refd)) {
                bi.accept(path, refd);
            }
        }
    }

    private static final class FieldDissectionStrategy extends AbstractDissectionStrategy<Object> {

        static Set<String> IGNORE = new HashSet<>(Arrays.asList("jdk.internal.misc.Unsafe",
                "java.lang.module.ModuleDescriptor", "sun.misc.Unsafe"));

        FieldDissectionStrategy() {
            super(Object.class);
        }

        @Override
        protected void doApply(Object o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
//            System.out.println(path);
            Class<?> c = o.getClass();
            if (c == Class.class) {
                return;
            }
            while (c != Object.class) {
                if ("java.lang.module.ModuleDescriptor".equals(c.getName())) {
                    return;
                }
                Field[] flds = c.getDeclaredFields();
                for (int i = 0; i < flds.length; i++) {
                    Object value = null;
                    Field f = flds[i];
                    String tn = f.getType().getName();
                    if (IGNORE.contains(tn)) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        if ((f.getModifiers() & Modifier.STATIC) != 0) {
                            value = f.get(null);
                        } else {
                            value = f.get(o);
                        }
                    } catch (Exception ex) {
                        if (!ex.getClass().getName().equals("InaccessibleObjectException")) {
//                            ex.printStackTrace();
                        }
                    }
                    if (visits.shouldVisit(value)) {
                        String nm = path + " -> " + f.getName() + "{" + f.getType().getSimpleName() + "}";
                        bi.accept(nm, value);
                    }
                }
                c = c.getSuperclass();
            }
        }
    }

    static String className(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof Class<?>) {
            return "Class<?>";
        }
        if (o.getClass().isArray()) {
            return o.getClass().getComponentType().getSimpleName() + "[]";
        }
        return o.getClass().getSimpleName();
    }

    static final class LookupDissectionStrategy extends AbstractDissectionStrategy<Lookup> {

        LookupDissectionStrategy() {
            super(Lookup.class);
        }

        @Override
        protected void doApply(Lookup o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            for (Object item : o.lookupAll(Object.class)) {
                if (item == null) {
                    continue;
                }
                if (item != o && visits.shouldVisit(item)) {
                    bi.accept(path + "[" + className(item) + "]", item);
                }
            }
        }
    }

    static boolean isIgnored(Object o) {
        return o == null || o instanceof String || o instanceof CharSequence
                || o.getClass() == Object.class
                || o instanceof Logger || o instanceof LogManager
                || o instanceof String
                || o instanceof MethodHandle
                || o instanceof Throwable
                || o instanceof StackTraceElement
                || o instanceof Color
                //                || o instanceof VarHandle
                || o instanceof Long || o instanceof Integer || o instanceof Boolean
                || o instanceof Float || o instanceof Double || o instanceof Short
                || o instanceof Byte || o instanceof int[] || o instanceof long[]
                || o instanceof char[] || o instanceof Character
                || o instanceof byte[] || o instanceof short[] || o instanceof double[]
                || o instanceof boolean[] || o.getClass().getSimpleName().equals("ModuleDescriptor")
                || o instanceof ClassLoader;
    }

    private static final class VisitTracker {

        private final Set<Integer> idHashes = new HashSet<>();

        boolean shouldVisit(Object o) {
            if (isIgnored(o)) {
                return false;
            }
            int ic = System.identityHashCode(o);
            if (!idHashes.contains(ic)) {
                idHashes.add(ic);
                return true;
            }
            return false;
        }
    }
}

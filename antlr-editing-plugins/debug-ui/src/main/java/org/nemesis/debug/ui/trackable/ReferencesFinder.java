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
package org.nemesis.debug.ui.trackable;

import java.awt.Color;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.openide.util.Lookup;
import org.openide.util.WeakSet;

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
    public static List<String> detect(Object obj, Map<String, Object> roots, Set<Object> omit, Predicate<? super Object> ignore) {
        if (omit.contains(obj)) {
            throw new IllegalArgumentException();
        }
        List<String> result = new LinkedList<>();
        detect(new EqPredicate(obj), roots, omit, result::add, ignore);
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
    public static List<String> detect(Predicate<Object> pred, Map<String, Object> roots, Set<Object> omit, Predicate<? super Object> ignore) {
        List<String> result = new LinkedList<>();
        detect(pred, roots, omit, result::add, ignore);
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
     * someOtherObject -&gt; theThingSearchedFor and other output</code>
     */
    public static int detect(Predicate<Object> pred, Map<String, Object> roots, Set<Object> omit, Consumer<String> c, Predicate<? super Object> ignore) {
        List<Map.Entry<String, Object>> l = new ArrayList<>(roots.entrySet());
        int detections = 0;
        Detector det = new Detector(true, pred, c, omit, roots, ignore);
        for (Map.Entry<String, Object> e : l) {
            c.accept("Scanning " + e.getKey());
            Thread.yield();
            det.accept(e.getKey(), e.getValue());
        }
        detections = det.detections;
        if (detections == 0) {
            c.accept("\nNo detections with weak references unsearched, try with them on. The result may not be a strong reference.\n");
            det = new Detector(false, pred, c, omit, roots, ignore);
            for (Map.Entry<String, Object> e : l) {
                c.accept("Scanning " + e.getKey() + " including weak sets, maps and references.");
                det.accept(e.getKey(), e.getValue());
            }
            detections = det.detections;
        }
        if (detections == 0) {
            c.accept("\nFailed.  No references found.\n");
        }
        return detections;
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
        private final List<DissectionStrategy> strategies = new ArrayList<>();
        private final VisitTracker vt;
        private final Predicate<Object> test;
        private final Map<Object, String> rootsBidi = new IdentityHashMap<>();
        // There are some objects in the window system which will throw
        // an exception if hashCode() is called when not on the event thread,
        // so make sure membership tests do not do that
        private final IdentityHashMap<Object, Boolean> omitMap = new IdentityHashMap<>();
        private volatile int detections;
        private final Predicate<? super Object> tester;

        @SuppressWarnings("LeakingThisInConstructor")
        Detector(boolean ignoreWeak, Predicate<Object> test, Consumer<String> c, Set<Object> omit, Map<String, Object> roots, Predicate<? super Object> tester) {
            vt = new VisitTracker(ignoreWeak, tester);
            for (Map.Entry<String, Object> e : roots.entrySet()) {
                rootsBidi.put(e.getValue(), e.getKey());
            }
            omitMap.put(this, true);
            omitMap.put(test, true);
            omitMap.put(c, true);
            omitMap.put(omit, true);
            for (Object o : omit) {
                omitMap.put(o, Boolean.TRUE);
            }
            this.c = c;
            this.test = test;
            strategies.add(new CollectionDissectionStrategy());
            strategies.add(new ArrayDissectionStrategy());
            strategies.add(new ReferenceDissectionStrategy());
            strategies.add(new AtomicReferenceDissectionStrategy());
            strategies.add(new ThreadLocalDissectionStrategy());
            strategies.add(new OptionalDissectionStrategy());
            maybeAddVarHandleStrategy(strategies);
            strategies.add(new MapDissectionStrategy());
            strategies.add(new LookupDissectionStrategy());
            strategies.add(new FieldDissectionStrategy());
            strategies.add(new StaticFieldsDissectionStrategy());
            this.tester = tester;
        }

        @Override
        public void accept(String path, Object obj) {
            if (obj != null && test.test(obj)) {
                detections++;
                c.accept(path + " ==> \n" + obj + "\n\n");
            } else if (obj != null) {
                if (omitMap.containsKey(obj)) {
                    return;
                }
                boolean addToOmit = false;
                try {
                    String name = rootsBidi.get(obj);
                    if (name != null) {
                        path = name + " -> ";
                        addToOmit = true;
                    }
                } catch (IllegalStateException | NullPointerException ex) {
                    ex.printStackTrace();
                    // will be this, called via hashCode() on something else
                    /*
java.lang.IllegalStateException: Problem in some module which uses Window System: Window System API is required to be called from AWT thread only, see http://core.netbeans.org/proposals/threading/
	at org.netbeans.core.windows.WindowManagerImpl.warnIfNotInEDT(WindowManagerImpl.java:1816)
	at org.netbeans.core.windows.WindowManagerImpl.topComponentID(WindowManagerImpl.java:1458)
	at org.openide.windows.WindowManager.findTopComponentID(WindowManager.java:526)

                    or it will be

java.lang.NullPointerException
	at java.xml/com.sun.org.apache.xerces.internal.impl.dtd.XMLContentSpec.hashCode(XMLContentSpec.java:245)
	at java.base/java.util.HashMap.hash(HashMap.java:340)
	at java.base/java.util.HashMap.get(HashMap.java:558)
	at org.nemesis.debug.ui.trackable.ReferencesFinder$Detector.accept(ReferencesFinder.java:187)

                     */
                }
                for (DissectionStrategy d : strategies) {
                    if (d.matches(obj)) {
                        if (d.apply(path, obj, this, vt)) {
                            break;
                        }
                    }
                }
                if (addToOmit) {
                    omitMap.put(obj, Boolean.TRUE);
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

        protected AbstractDissectionStrategy(Class<? super T> type) {
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
            List<Object> l = null;
            for (int i = 0; i < 20; i++) {
                try {
                    l = new ArrayList<>(o);
                    break;
                } catch (ConcurrentModificationException ex) {
                    if (i < 19) {
                        Thread.yield();
                    } else {
                        ex.printStackTrace();
                        bi.accept(path + "<unreadable>", "Could not iterate collection w/o CME");
                        return;
                    }
                }
            }
            if (l == null) {
                return;
            }
            String commonType = "<" + leastCommonTypeName(o) + ">";
            for (Object item : l) {
                if (item != o && visits.shouldVisit(item)) {
                    bi.accept(path + commonType + "[" + ix + "] -> ", item);
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
                        String typeName = typeName(item);
                        bi.accept(path + "[" + i + "](" + typeName + ")", item);
                    }
                }
                return true;
            }
            return false;
        }
    }

    private static String leastCommonTypeName(Collection<?> c) {
        if (c.isEmpty()) {
            return "?";
        }
        List<Set<Class<?>>> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            try {
                for (Object o : c) {
                    all.add(typeNames(o));
                }
                break;
            } catch (ConcurrentModificationException cme) {
                all.clear();
            }
        }
        return commonDenominatorTypeName(all);
    }

    private static String commonDenominatorTypeName(Collection<? extends Set<Class<?>>> types) {
        if (types.isEmpty()) {
            return "?";
        }
        Set<Class<?>> curr = null;
        for (Set<Class<?>> oneSet : types) {
            if (curr == null) {
                curr = oneSet;
            } else {
                curr.retainAll(oneSet);
            }
        }
        if (curr == null || curr.isEmpty()) {
            return "?";
        }
        List<Class<?>> l = new ArrayList<>(curr);
        Collections.sort(l, (a, b) -> {
            return -Integer.compare(depth(a), depth(b));
        });
        return l.get(0).getSimpleName();
    }

    private static int depth(Class<?> type) {
        int result = 0;
        while (type != null) {
            result++;
            type = type.getSuperclass();
        }
        return result;
    }

    private static Set<Class<?>> typeNames(Object o) {
        if (o == null) {
            return new HashSet<>(1);
        }
        Set<Class<?>> types = new HashSet<>();
        Class<?> curr = o.getClass();
        while (curr != null) {
            types.add(curr);
            for (Class<?> iface : curr.getInterfaces()) {
                types.add(iface);
            }
            curr = curr.getSuperclass();
        }
        return types;
    }

    private static String typeName(Object o) {
        if (o == null) {
            return "null";
        }
        if (o.getClass().isArray()) {
            return o.getClass().getComponentType().getSimpleName() + "[]";
        }
        if (o.getClass().getName().contains("$$Lambda")) {
            return o.getClass().getName();
        }
        return o.getClass().getSimpleName();
    }

    private static final class MapDissectionStrategy extends AbstractDissectionStrategy<Map<?, ?>> {

        MapDissectionStrategy() {
            super(Map.class);
        }

        @Override
        @SuppressWarnings("CallToThreadYield")
        protected void doApply(Map<?, ?> o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            if (!visits.shouldVisit(o)) {
                return;
            }
            Map<Object, Object> m = null;
            for (int i = 0; i < 12; i++) {
                try {
                    m = new HashMap<>(o);
                    break;
                } catch (ConcurrentModificationException cme) {
                    Thread.yield();
                }
            }
            if (m == null) {
                return;
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

    /*
    // XXX JDK9
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
     */
    static void maybeAddVarHandleStrategy(Collection<? super AbstractDissectionStrategy<?>> addTo) {
        try {
            Class<?> type = Class.forName("java.lang.invoke.VarHandle");
            Method getMethod = type.getMethod("get");
            addTo.add(new ReflectiveVarHandleDissectionStrategy<>(type, getMethod));
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | UnsupportedOperationException ex) {
            // do nothing
        }
    }

    // On JDK 8, actually referening VarHandle will break badly, so as long as we
    // compile on that, we cannot use the explicit version of it
    private static final class ReflectiveVarHandleDissectionStrategy<T> extends AbstractDissectionStrategy<T> {

        private final Method getMethod;

        ReflectiveVarHandleDissectionStrategy(Class<T> varHandleType, Method getMethod) {
            super(varHandleType);
            this.getMethod = getMethod;
        }

        @Override
        protected void doApply(T o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            try {
                Object refd = getMethod.invoke(o);
                if (refd != null && visits.shouldVisit(refd)) {
                    bi.accept(path, refd);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
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

    private static final class OptionalDissectionStrategy extends AbstractDissectionStrategy<Optional<?>> {

        OptionalDissectionStrategy() {
            super(Optional.class);
        }

        @Override
        protected void doApply(Optional<?> o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            if (o.isPresent()) {
                Object refd = o.get();
                if (refd != null && visits.shouldVisit(refd)) {
                    bi.accept(path + " -> Optional<" + typeName(refd) + ">", refd);
                }
            }
        }
    }

    private static final class FieldDissectionStrategy extends AbstractDissectionStrategy<Object> {

        static Set<String> IGNORE = new HashSet<>(Arrays.asList("jdk.internal.misc.Unsafe",
                "java.lang.module.ModuleDescriptor", "sun.misc.Unsafe"));
        static int counter = 0;
        StaticFieldsDissectionStrategy strat = new StaticFieldsDissectionStrategy();

        FieldDissectionStrategy() {
            super(Object.class);
        }

        @Override
        @SuppressWarnings("CallToThreadYield")
        protected void doApply(Object o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            Class<?> c = o.getClass();
            if (c == Class.class) {
                return;
            }
            while (c != Object.class) {
                strat.apply(path + ".class", c, bi, visits);
//                if (visits.shouldVisit(c)) {
//                    bi.accept(path + ".class -> ", c);
//                }
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
                        if (!ex.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
                            ex.printStackTrace();
                        }
                    }
                    if (visits.shouldVisit(value)) {
                        String nm = path + " -> " + f.getName() + "{" + f.getType().getSimpleName() + "}";
                        bi.accept(nm, value);
                    }
                    if (value instanceof Thread) {
                        Thread t = (Thread) value;
                        ClassLoader ldr = t.getContextClassLoader();
                        if (visits.shouldVisit(ldr)) {
                            String nm = path + " -> " + "Thread-" + t.getName() + "context-classloader -> ";
                            bi.accept(nm, ldr);
                        }
                    }
                }
                c = c.getSuperclass();
            }
            // periodically give the UI a chance to paint
            if (++counter % 100 == 0) {
                Thread.yield();
            }
        }
    }

    static final class StaticFieldsDissectionStrategy extends AbstractDissectionStrategy<Class<?>> {

        private static final Set<Integer> seen = new HashSet<>();

        StaticFieldsDissectionStrategy() {
            super(Class.class);
        }

        @Override
        public boolean matches(Object o) {
            return o instanceof Class<?> && !seen.contains(System.identityHashCode(o));
        }

        @Override
        protected void doApply(Class<?> o, String path, BiConsumer<String, Object> bi, VisitTracker visits) {
            if (seen.contains(System.identityHashCode(o))) {
                return;
            }
            Class<?> type = o;
            while (type != null && type != Object.class) {
                Field[] fields = o.getDeclaredFields();
                for (Field f : fields) {
                    if ((f.getModifiers() & Modifier.STATIC) != 0 && !f.getType().isPrimitive()) {
                        try {
                            f.setAccessible(true);
                            Object val = f.get(null);
                            if (val != null) {
                                if (visits.shouldVisit(val)) {
                                    bi.accept(path + "->" + type.getSimpleName() + "." + f.getName(), val);
                                }
                            }
                        } catch (Exception ex) {
                            if (!ex.getClass().getSimpleName().equals("InaccessibleObjectException")) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
                if (visits.shouldVisit(type.getClassLoader())) {
                    bi.accept(path + "->" + type.getSimpleName() + ".classloader", type.getClassLoader());
                }
                type = type.getSuperclass();
                if (seen.contains(System.identityHashCode(type))) {
                    return;
                }
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
                    if (visits.shouldVisit(item.getClass())) {
                        bi.accept(path + ".class", item.getClass());
                    }
                }
            }
        }
    }

    static boolean isIgnored(Object o) {
        boolean result = o == null || o instanceof String || o instanceof CharSequence
                || o.getClass() == Object.class
                || o instanceof Logger || o instanceof LogManager
                || o instanceof String
                || o instanceof MethodHandle
                || o instanceof Throwable
                || o instanceof StackTraceElement
                || o instanceof Color
                || o instanceof File
                || o instanceof Path
                || o instanceof Method
                || o instanceof Field
                //                || o instanceof VarHandle
                || o instanceof Long || o instanceof Integer || o instanceof Boolean
                || o instanceof Float || o instanceof Double || o instanceof Short
                || o instanceof Byte || o instanceof int[] || o instanceof long[]
                || o instanceof char[] || o instanceof Character
                || o instanceof byte[] || o instanceof short[] || o instanceof double[]
                || o instanceof boolean[] || o.getClass().getSimpleName().equals("ModuleDescriptor")
                || o instanceof Locale || o instanceof Charset || o instanceof InputStream
                || o instanceof OutputStream || o instanceof ByteBuffer || o instanceof Channel
                || o instanceof Random || o instanceof ResourceBundle;
        if (!result) {
            String nm = o.getClass().getName();
            result = nm.startsWith("sun.util") || nm.startsWith("sun.reflect");
        }
        return result;
    }

    private static final class VisitTracker {

        private final Set<Integer> idHashes = new HashSet<>();
        private final boolean ignoreWeakReferences;
        private final Predicate<? super Object> tester;

        public VisitTracker(boolean ignoreWeakReferences, Predicate<? super Object> tester) {
            this.ignoreWeakReferences = ignoreWeakReferences;
            this.tester = tester;
        }

        boolean shouldVisit(Object o) {
            if (isIgnored(o)) {
                return false;
            }
            if (o != null && tester.test(o)) {
                idHashes.add(System.identityHashCode(o));
                return false;
            }
            if (ignoreWeakReferences) {
                if (o instanceof WeakReference<?> || o instanceof WeakSet<?> || o instanceof WeakHashMap<?, ?>) {
                    return false;
                }
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

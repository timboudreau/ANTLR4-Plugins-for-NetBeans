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
package com.mastfrog.mock.named.services;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.netbeans.junit.MockServices;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.PathInLookupTest;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.implspi.NamedServicesProvider;

/**
 * Equivalent of MockServices for named lookups - used <code>builder()</code> to
 * initialize. Can also populate the default lookup.
 * <p>
 * Note: Due to Lookup's reliance on global state and no useful way to hook into
 * all ways a thread can be created (though perhaps with a SecurityManager it
 * could be done?), tests that use this cannot be run concurrently in the same
 * JVM without interfering with each other - either set it up for an entire
 * <i>test class</i> run, or set your unit tests to run serially or fork new
 * per-test VMs.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class MockNamedServices {

    private static final Map<String, Set<Object>> CONTENTS_FOR_NAMED_LOOKUP_PATH
            = CollectionUtils.supplierMap(LinkedHashSet::new);
    private static final Map<String, Lookup> NAMED_LOOKUPS = new HashMap<>();

    private static Set<String> debug = ConcurrentHashMap.newKeySet();
    private static volatile boolean debugAll = Boolean.getBoolean("log.lookups");

    /**
     * Log lookups from all created lookups.
     */
    public static void debugAll() {
        debugAll = true;
    }

    /**
     * Clear all debugging flags.
     */
    public static void undebugAll() {
        debugAll = false;
        debug.clear();
    }

    /**
     * Set just one path to log.
     *
     * @param path The path
     */
    public static void debug(String path) {
        debug.add(path);
    }

    /**
     * Clear logging for one path.
     *
     * @param name
     */
    public static void undebug(String name) {
        debug.remove(name);
    }

    /**
     * Test if a path is to-be-logged.
     *
     * @param path The path
     * @return True if logging is enabled
     */
    public static boolean isDebug(String path) {
        return debugAll || debug.contains(path);
    }

    /**
     * Implemented detail needed from the package we are forced to use to hack
     * in our own implementation of named lookups.
     *
     * @return The type of the NamedServicesProvider
     */
    public static Class<? extends NamedServicesProvider> type() {
        return PathInLookupTest.P.class;
    }

    /**
     * Log one line for the path.
     *
     * @param path
     * @param msg
     */
    public static void log(String path, String msg) {
        if (isDebug(path)) {
            System.out.println("  * " + path + ":" + msg);
        }
    }

    /**
     * Fetch the lookup for the given path.
     *
     * @param path The path
     * @return A lookup
     */
    public static synchronized Lookup lookupFor(String path) {
        if (!path.isEmpty() && path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }
        if (NAMED_LOOKUPS.containsKey(path)) {
            log(path, "return existing lookup");
            return NAMED_LOOKUPS.get(path);
        }
        ClassLoader l = Lookup.getDefault().lookup(ClassLoader.class);
        if (l == null) {
            l = Thread.currentThread().getContextClassLoader();
            if (l == null) {
                l = NamedServicesProvider.class.getClassLoader();
            }
        }
        if (CONTENTS_FOR_NAMED_LOOKUP_PATH.containsKey(path)) {
            Lookup result = NAMED_LOOKUPS.get(path);
            if (result == null) {
                result = Lookups.fixed(CONTENTS_FOR_NAMED_LOOKUP_PATH.get(path).toArray(new Object[0]));
                Lookup combineWith = Lookups.metaInfServices(l, "META-INF/namedservices/" + path);
                result = new ProxyLookup(result, combineWith);
                NAMED_LOOKUPS.put(path, result);
            }
            log(path, "Create a lookup for " + path);
            return new LoggingLookup(result, path);
        } else {
            log(path, "No contents for " + path);
        }
        Lookup result = Lookups.metaInfServices(l, "META-INF/namedservices/" + path);
        log(path, "create a plain meta-inf-services lookup");
        result = new LoggingLookup(result, "named:" + path);
        return result;
    }

    /**
     * Create a builder to initialize MockNamedServices; the build method
     * returns a ThrowingRunnable that undoes everything the builder did
     * (additional shutdown tasks can be added with
     * ThrowingRunnable.andAlways(), which will be run exactly once, regardless
     * of the state of any other task throwing an exception).
     *
     * @return A builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, Set<Object>> cfp
                = CollectionUtils.supplierMap(HashSet::new);

        private final Set<Class<?>> defaultLookupAdd = new LinkedHashSet<>(16);

        /**
         * Add some types to the default lookup.
         *
         * @param a The first type
         * @param more More types
         * @return this
         */
        public Builder addToDefaultLookup(Class<?> a, Class<?>... more) {
            defaultLookupAdd.add(a);
            defaultLookupAdd.addAll(Arrays.asList(more));
            return this;
        }

        /**
         * Add objects or classes to the named path; the passed objects can
         * either be class objects, which will be instantiated per the usual
         * rules, or live objects to be included in the resulting lookup
         * returned by Lookups.forPath().
         *
         * @param path A path
         * @param first The first object
         * @param more More objects
         * @return this
         */
        public Builder add(String path, Object first, Object... more) {
            Set<Object> s = cfp.get(path);
            if (first instanceof Class<?>) {
                try {
                    first = ((Class<?>) first).newInstance();
                } catch (InstantiationException | IllegalAccessException ex) {
                    return Exceptions.chuck(ex);
                }
            }
            s.add(first);
            for (Object m : more) {
                if (m instanceof Class<?>) {
                    try {
                        m = ((Class<?>) m).newInstance();
                    } catch (InstantiationException | IllegalAccessException ex) {
                        return Exceptions.chuck(ex);
                    }
                }
                s.add(m);
            }
            return this;
        }

        /**
         * Add types on the passed named lookup path.
         *
         * @param path The path
         * @param type The type to instantiate (must have a public no-arg
         * constructor and be public)
         * @return this
         */
        public Builder add(String path, Class<?> type) {
            log(path, "add " + type.getSimpleName());
            try {
                return add(path, type.newInstance());
            } catch (InstantiationException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        static Class<?>[] BASE_LOOKUP_CONTENTS = new Class<?>[]{
            MockNamedServices.type()
        };

        /**
         * Initialize named lookups, and return a runnable which will clear
         * them.
         *
         * @param defaultLookupContents Additional contents for the
         * <i>default lookup</i>
         * @return this
         */
        public ThrowingRunnable build(Class<?>... defaultLookupContents) {
            for (Map.Entry<String, Set<Object>> e : cfp.entrySet()) {
                CONTENTS_FOR_NAMED_LOOKUP_PATH.get(e.getKey()).addAll(e.getValue());
            }
            Class<?>[] allDefaultLookup = ArrayUtils.concatenate(
                    defaultLookupAdd.toArray(new Class[defaultLookupAdd.size()]),
                    defaultLookupContents);
            allDefaultLookup = ArrayUtils.concatenate(allDefaultLookup,
                    BASE_LOOKUP_CONTENTS);

            log("DefaultLookup", "Init default lookup " + Strings.join(',',
                    Arrays.asList(allDefaultLookup),
                    Class::getSimpleName));
            MockServices.setServices(allDefaultLookup);
            return () -> {
                try {
                    MockServices.setServices(new Class<?>[0]);
                } finally {
                    for (Map.Entry<String, Set<Object>> e : cfp.entrySet()) {
                        CONTENTS_FOR_NAMED_LOOKUP_PATH.get(e.getKey()).removeAll(e.getValue());
                    }
                    cfp.clear();
                    defaultLookupAdd.clear();
                    debugAll = Boolean.getBoolean("log.lookups");
                    debug.clear();
                    NAMED_LOOKUPS.clear();
                    CONTENTS_FOR_NAMED_LOOKUP_PATH.clear();
                }
            };
        }
    }

    static final class LoggingLookup extends Lookup {

        private final Lookup delegate;
        private final String name;

        public LoggingLookup(Lookup delegate, String name) {
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public <T> T lookup(Class<T> clazz) {
            T result = delegate.lookup(clazz);
            log(name, "lookup " + clazz + " -> " + result);
            return result;
        }

        @Override
        public <T> Lookup.Result<T> lookup(Lookup.Template<T> template) {
            Lookup.Result<T> result = delegate.lookup(template);
            log(name, "lookup-template " + name + " with "
                    + template.getType().getSimpleName() + " on " + name + " -> "
                    + result
                    + " with " + result.allItems().size());
            return new DelegatingResult<>(name, result);
        }

        @Override
        public <T> Lookup.Item<T> lookupItem(Lookup.Template<T> template) {
            Lookup.Item<T> item = delegate.lookupItem(template);
            log(name, "lookupItem with " + template.getType().getName()
                    + " -> " + item);
            return new DelegatingItem<>(name, item);
        }

        @Override
        public <T> Lookup.Result<T> lookupResult(Class<T> clazz) {
            Result<T> res = delegate.lookupResult(clazz);
            log(name, "lookupResult " + clazz.getSimpleName()
                    + " -> " + res + " current size " + res.allItems().size());
            return new DelegatingResult<>(name, res);
        }

        @Override
        public <T> Collection<? extends T> lookupAll(Class<T> clazz) {
            Collection<? extends T> all = delegate.lookupAll(clazz);
            log(name, "lookupAll " + clazz.getName() + " -> " + all);
            return all;
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        public boolean equals(Object obj) {
            if (obj instanceof LoggingLookup) {
                obj = ((LoggingLookup) obj).delegate;
            }
            return delegate.equals(obj);
        }

        @Override
        public String toString() {
            return "LoggingLookup(" + delegate.toString() + ")";
        }

        static final class DelegatingResult<T> extends Lookup.Result<T>
                implements LookupListener {

            private final Lookup.Result<T> delegate;
            private final Set<LookupListener> listeners = new HashSet<>();
            private final String name;

            public DelegatingResult(String name, Result<T> delegate) {
                this.name = name;
                this.delegate = delegate;
            }

            private void doLog(String op, String msg) {
                String types = Strings.join(',', delegate.allClasses(),
                        Class::getSimpleName);
                log(name, op + " " + types + " " + (msg != null && !msg.isEmpty()
                        ? msg
                        : ""));
            }

            @Override
            public void addLookupListener(LookupListener ll) {
                listeners.add(ll);
                if (listeners.size() == 1) {
                    delegate.addLookupListener(this);
                    delegate.allInstances();
                }
            }

            @Override
            public void removeLookupListener(LookupListener ll) {
                listeners.remove(ll);
                if (listeners.isEmpty()) {
                    delegate.removeLookupListener(this);
                }
            }

            @Override
            public Collection<? extends T> allInstances() {
                Collection<? extends T> result = delegate.allInstances();
                doLog("allInstances", result.isEmpty() ? "<empty>"
                        : Strings.join(',', result));
                return result;
            }

            @Override
            public void resultChanged(LookupEvent le) {
                doLog("resultChanged", null);
                for (LookupListener ll : listeners) {
                    ll.resultChanged(new LookupEvent(this));
                }
            }

            @Override
            public Collection<? extends Item<T>> allItems() {
                List<DelegatingItem<T>> result = new ArrayList<>();
                Collection<? extends Item<T>> items = delegate.allItems();
                doLog(name, "allItems -> " + Strings.join(',', items));
                for (Lookup.Item<T> item : items) {
                    result.add(new DelegatingItem<>(name, item));
                }
                return result;
            }

            @Override
            public Set<Class<? extends T>> allClasses() {
                Set<Class<? extends T>> types = delegate.allClasses();
                doLog("allClasses", null);
                return types;
            }

            @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
            @Override
            public boolean equals(Object o) {
                if (o instanceof DelegatingResult<?>) {
                    o = ((DelegatingResult<?>) o).delegate;
                }
                return delegate.equals(o);
            }

            @Override
            public int hashCode() {
                return delegate.hashCode();
            }

            @Override
            public String toString() {
                return "DelegatingResult(" + name + " " + delegate + ")";
            }
        }

        static class DelegatingItem<T> extends Lookup.Item<T> {

            private final String name;

            private final Lookup.Item<T> delegate;

            public DelegatingItem(String name, Item<T> delegate) {
                this.name = name;
                this.delegate = delegate;
            }

            private void doLog(String op, String msg) {
                String types = delegate.getType().getSimpleName();
                log(name, op + " " + types + " " + (msg != null && !msg.isEmpty()
                        ? msg
                        : ""));
            }

            @Override
            public T getInstance() {
                T instance = delegate.getInstance();
                doLog("getInstance", Objects.toString(instance));
                return instance;
            }

            @Override
            public Class<? extends T> getType() {
                return delegate.getType();
            }

            @Override
            public String getId() {
                String result = delegate.getId();
                doLog("getId", result);
                return result;
            }

            @Override
            public String getDisplayName() {
                String result = delegate.getDisplayName();
                doLog("getDisplayName", result);
                return result;
            }

            @Override
            @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
            public boolean equals(Object o) {
                if (o instanceof DelegatingItem<?>) {
                    o = ((DelegatingItem<?>) o).delegate;
                }
                return delegate.equals(o);
            }

            @Override
            public int hashCode() {
                return delegate.hashCode();
            }

            @Override
            public String toString() {
                return "DelegatingItem(" + name + " " + delegate.toString()
                        + ")";
            }
        }
    }
}

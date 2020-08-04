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
package org.nemesis.test.fixtures.support;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.core.NbLoaderPool;
import org.netbeans.core.startup.MainLookup;
import org.netbeans.core.startup.NbRepository;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.editor.document.StubImpl;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.editor.lib.DocumentServices;
import org.netbeans.modules.editor.settings.storage.EditorLocatorFactory;
import org.netbeans.modules.editor.settings.storage.NbUtils;
import org.netbeans.modules.editor.settings.storage.StorageImpl;
import org.netbeans.modules.lexer.nbbridge.MimeLookupLanguageProvider;
import org.netbeans.modules.openide.filesystems.DefaultURLMapperProxy;
import org.netbeans.modules.openide.util.ProxyURLStreamHandlerFactory;
import org.netbeans.modules.parsing.impl.indexing.implspi.ActiveDocumentProvider;
import org.netbeans.modules.parsing.nb.CurrentDocumentScheduler;
import org.netbeans.modules.parsing.nb.EditorMimeTypesImpl;
import org.netbeans.modules.parsing.nb.SelectedNodesScheduler;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.openide.ErrorManager;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.PathInLookupTest;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.implspi.NamedServicesProvider;

/**
 * A way to go beyond MockServices, and provide custom contents for mime lookups
 * and path-based lookups and more.
 *
 * @author Tim Boudreau
 */
public class TestFixtures {

    private final Set<Class<?>> defaultLookupContents = new LinkedHashSet<>(64);
    private ThrowingRunnable onShutdown = ThrowingRunnable.oneShot(true);
    private static final Map<String, Set<Object>> contentsForNamedLookupPath
            = CollectionUtils.supplierMap(LinkedHashSet::new);

    private static Map<String, Set<Object>> contentsByMimePath
            = CollectionUtils.supplierMap(LinkedHashSet::new);

    private static final Map<String, Lookup> namedLookups = new HashMap<>();
    private static Map<String, Lookup> mimeLookupsByPath = new HashMap<>();
    private boolean logging = false;
    private boolean avoidStartingModuleSystem = true;
    private final Set<String> classNamesToInitializeLoggingOn = new HashSet<>();
    private final Set<Class<?>> classesToInitializeLoggingOn = new HashSet<>();
    private final Set<Logger> loggersToInitialize = new HashSet<>();

    /**
     * Replace the global logging config with one which is simple and readable.
     *
     * @classesOrClassNamesOrLoggersToPreconfigure A set of class objects,
     * String class names, or Logger instances which should be pre-initialized
     * before the logging configuration is replaced and set to Level.ALL. If any
     * are not found, a stack trace will be printed but the test will proceed.
     *
     * @return this
     */
    public TestFixtures verboseGlobalLogging(Object... classesOrClassNamesOrLoggersToPreconfigure) {
        addLoggingClassesLoggersOrClassNames(classesOrClassNamesOrLoggersToPreconfigure);
        logging = true;
        return this;
    }

    private static final Class<?>[] DEFAULT_LOOKUP_CONTENTS = new Class<?>[]{
        ActiveDocument.class,
        MockErrorManager.class,
        MockNamedServicesImpl.type(),
        NbLoaderPool.class,
        DocumentServices.class,
        MimeLookupLanguageProvider.class,
        StorageImpl.StorageCacheImpl.class,
        EditorLocatorFactory.class,
        NbUtils.class,
        StubImpl.F.class,
        EditorMimeTypesImpl.class,
        MockMimeDataProvider.class,
        NbRepository.class,
        MockModuleSystem.class,
        ProxyURLStreamHandlerFactory.class,
        DefaultURLMapperProxy.class,
        CurrentDocumentScheduler.class,
        SelectedNodesScheduler.class,};

    static void hackModuleSystemAlreadyStarted(boolean val) {
        try {
            Field f = MainLookup.class.getDeclaredField("started");
            f.setAccessible(true);
            f.set(null, val);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * If you call something that directly or indirectly calls
     * Lookup.getDefault().lookup(ModuleInfo.class), or ModuleSystem.class or
     * Module.class, that will trigger the entire NetBeans module system trying
     * to bootstrap itself inside a unit test, which is almost always a recipe
     * for disaster. This hacks the "started" field in MainLookup to make it
     * think the module system is already loaded, and ensures that a single fake
     * ModuleInfo will be returned from all lookups it can satisfy. Note a
     * lookup of Module or ModuleSystem will still return null.
     *
     * @return this
     */
    public TestFixtures avoidStartingModuleSystem() {
        this.avoidStartingModuleSystem = true;
        return this;
    }

    /**
     * Turns off the hack to avoid initializing the module system.
     *
     * @return this
     */
    public TestFixtures dontAvoidStartingModuleSystem() {
        this.avoidStartingModuleSystem = false;
        return this;
    }

    public TestFixtures() {
        MockNamedServicesImpl.builder()
                .add("URLStreamHandler/nbresloc", new org.netbeans.core.startup.NbResourceStreamHandler())
                .add("URLStreamHandler/nbres", new org.netbeans.core.startup.NbResourceStreamHandler())
                .add("URLStreamHandler/m2", new org.netbeans.core.startup.MavenRepoURLHandler())
                .build();
    }

    @SuppressWarnings("deprecation")
    public ThrowingRunnable build() {
        DocumentFactory fact = new DocumentFactoryImpl();
        includedLogs.addAll(includeLogs);
        excludedLogs.addAll(excludeLogs);
        addToMimeLookup("", fact);
        addToMimeLookup("text/plain", new org.netbeans.modules.editor.plain.PlainKit());
        Set<Class<?>> set = new LinkedHashSet<>(DEFAULT_LOOKUP_CONTENTS.length + defaultLookupContents.size());
        if (avoidStartingModuleSystem) {
            set.add(MockModuleSystem.class);
        }
        set.addAll(Arrays.asList(DEFAULT_LOOKUP_CONTENTS));
        set.addAll(defaultLookupContents);
        MockServices.setServices(set.toArray(new Class[set.size()]));
        // XXX this is right, but can cause weird deadlocks.
        // better to just run tests in their own isolated JVM and use
        // test-class level initialization of services - too much of the
        // NetBeans plumbing simply isn't prepared to be de-initialized cleanly
//        onShutdown.andAlwaysRun(() -> MockServices.setServices(new Class[0]));
        if (avoidStartingModuleSystem) {
            hackModuleSystemAlreadyStarted(avoidStartingModuleSystem);
        }
        // Force init
        assertNotNull(Lookup.getDefault().lookup(ActiveDocumentProvider.class));
        if (logging) {
            Set<String> loggerNames = preinitializeLogging();
            initLogging(loggerNames, insanelyVerboseLogging);
//            preinitializeLogging();
        }

        onShutdown.andAlwaysFirst(() -> {
            try {
                MockErrorManager.onTestCompleted();
                if (avoidStartingModuleSystem) {
                    hackModuleSystemAlreadyStarted(false);
                }
            } catch (Throwable ex) {
                Exceptions.chuck(ex);
            } finally {
                mimeLookupsByPath.clear();
                namedLookups.clear();
                excludedLogs.clear();
                includedLogs.clear();
            }
        });
        return onShutdown;
    }

    static void assertNotNull(Object o) {
        if (o == null) {
            throw new AssertionError("Object should not be null");
        }
    }

    static void assertNotNull(Object o, String msg) {
        if (o == null) {
            throw new AssertionError(msg);
        }
    }

    public TestFixtures addToDefaultLookup(Class<?> first, Class<?>... more) {
        defaultLookupContents.add(first);
        defaultLookupContents.addAll(Arrays.asList(more));
        return this;
    }

    public TestFixtures addToNamedLookup(String name, Class<?> type) {
        onShutdown.andAlwaysRun(MockNamedServicesImpl.builder().add(name, type).build());
        return this;
    }

    public TestFixtures addToNamedLookup(String name, Object first, Object... more) {
        onShutdown.andAlwaysRun(MockNamedServicesImpl.builder().add(name, first, more).build());
        return this;
    }

    public TestFixtures addToMimeLookup(String mimeType, Object first, Object... more) {
        onShutdown.andAlwaysRun(MockMimeDataProvider.builder(mimeType).add(first, more).build());
        return this;
    }

    public void onShutdown() throws Throwable {
        onShutdown.run();
    }

    Set<String> excludeLogs = new HashSet<>();
    Set<String> includeLogs = new HashSet<>();

    public TestFixtures excludeLogs(String... names) {
        excludeLogs.addAll(Arrays.asList(names));
        return this;
    }

    public TestFixtures includeLogs(String... names) {
        includeLogs.addAll(Arrays.asList(names));
        return this;
    }

    public static void setActiveDocument(Document document) {
        ActiveDocument.INSTANCE.setActiveDocument(document);
    }

    static Set<String> excludedLogs = new HashSet<>();
    static Set<String> includedLogs = new HashSet<>();

    public static final class ActiveDocument implements ActiveDocumentProvider {

        private final List<ActiveDocumentProvider.ActiveDocumentListener> listeners = new ArrayList<>();
        private Document active;

        static ActiveDocument INSTANCE;

        public ActiveDocument() {
            INSTANCE = this;
        }

        public void setActiveDocument(Document document) {
            if (active != document) {
                Document old = active;
                active = document;
                ActiveDocumentProvider.ActiveDocumentEvent e = new ActiveDocumentProvider.ActiveDocumentEvent(this, old, active, Collections.emptySet());
                for (ActiveDocumentProvider.ActiveDocumentListener l : listeners) {
                    l.activeDocumentChanged(e);
                }
            }
        }

        void clear() {
            active = null;
            listeners.clear();
        }

        @Override
        public Document getActiveDocument() {
            return active;
        }

        @Override
        public Set<? extends Document> getActiveDocuments() {
            return active == null ? Collections.emptySet()
                    : Collections.singleton(active);
        }

        @Override
        public void addActiveDocumentListener(ActiveDocumentProvider.ActiveDocumentListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeActiveDocumentListener(ActiveDocumentProvider.ActiveDocumentListener listener) {
            listeners.remove(listener);
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
            System.out.println("LOOKUP " + clazz + " on " + name + " has result? " + (result != null));
            return result;
        }

        @Override
        public <T> Result<T> lookup(Template<T> template) {
            return delegate.lookup(template);
        }

        @Override
        public <T> Item<T> lookupItem(Template<T> template) {
            return delegate.lookupItem(template);
        }

        @Override
        public <T> Result<T> lookupResult(Class<T> clazz) {
            return delegate.lookupResult(clazz);
        }

        @Override
        public <T> Collection<? extends T> lookupAll(Class<T> clazz) {
            return delegate.lookupAll(clazz);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    public static class MockMimeDataProvider implements MimeDataProvider {

        @Override
        public synchronized Lookup getLookup(MimePath mimePath) {
            String path = mimePath.getPath();
            Lookup result = mimeLookupsByPath.get(path);
            if (result == null) {
                result = Lookups.fixed(contentsByMimePath.get(path).toArray(new Object[0]));
                if (Boolean.getBoolean("log.lookups")) {
                    result = new LoggingLookup(result, "mime:'" + path + "'");
                }
                mimeLookupsByPath.put(path, result);
            }
            return result;
        }

        private static MimeDataBuilder builder(String mimeType) {
            assert mimeType != null;
            return new MimeDataBuilder(mimeType);
        }

        private static final class MimeDataBuilder {

            private final String mimeType;
            private final List<Object> items = new ArrayList<>();

            private MimeDataBuilder(String mimeType) {
                this.mimeType = mimeType;
            }

            @SuppressWarnings("deprecation")
            private MimeDataBuilder add(Object obj, Object... more) {
                if (obj instanceof Class<?>) {
                    try {
                        obj = ((Class<?>) obj).newInstance();
                    } catch (InstantiationException | IllegalAccessException ex) {
                        return Exceptions.chuck(ex);
                    }
                }
                add(obj);
                for (Object o : more) {
                    if (o instanceof Class<?>) {
                        try {
                            o = ((Class<?>) o).newInstance();
                        } catch (InstantiationException | IllegalAccessException ex) {
                            return Exceptions.chuck(ex);
                        }
                    }
                    items.add(o);
                }
                return this;
            }

            private MimeDataBuilder add(Object obj) {
                items.add(obj);
                return this;
            }

            private Runnable build() {
                contentsByMimePath.get(mimeType).addAll(items);
                return () -> {
                    contentsByMimePath.get(mimeType).removeAll(items);
                    mimeLookupsByPath.remove(mimeType);
                };
            }

        }
    }

    public static final class MockNamedServicesImpl {

        public static Class<? extends NamedServicesProvider> type() {
            return PathInLookupTest.P.class;
        }

        public static synchronized Lookup lookupFor(String path) {
            if (!path.isEmpty() && path.charAt(path.length() - 1) == '/') {
                path = path.substring(0, path.length() - 1);
            }
            if (namedLookups.containsKey(path)) {
                return namedLookups.get(path);
            }
            ClassLoader l = Lookup.getDefault().lookup(ClassLoader.class);
            if (l == null) {
                l = Thread.currentThread().getContextClassLoader();
                if (l == null) {
                    l = NamedServicesProvider.class.getClassLoader();
                }
            }
            if (contentsForNamedLookupPath.containsKey(path)) {
                Lookup result = namedLookups.get(path);
                if (result == null) {
                    result = Lookups.fixed(contentsForNamedLookupPath.get(path).toArray(new Object[0]));
                    Lookup combineWith = Lookups.metaInfServices(l, "META-INF/namedservices/" + path);
                    namedLookups.put(path, new ProxyLookup(result, combineWith));
                }
                if (Boolean.getBoolean("log.lookups")) {
                    result = new LoggingLookup(result, "named:" + path);
                }
                return result;
            }
            Lookup result = Lookups.metaInfServices(l, "META-INF/namedservices/" + path);
            if (Boolean.getBoolean("log.lookups")) {
                result = new LoggingLookup(result, "named:" + path);
            }
            return result;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private final Map<String, Set<Object>> cfp
                    = CollectionUtils.supplierMap(HashSet::new);

            @SuppressWarnings("deprecation")
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

            @SuppressWarnings("deprecation")
            public Builder add(String path, Class<?> type) {
                try {
                    return add(path, type.newInstance());
                } catch (InstantiationException | IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }

            public Runnable build() {
                for (Map.Entry<String, Set<Object>> e : cfp.entrySet()) {
                    contentsForNamedLookupPath.get(e.getKey()).addAll(e.getValue());
                }
                return () -> {
                    for (Map.Entry<String, Set<Object>> e : cfp.entrySet()) {
                        contentsForNamedLookupPath.get(e.getKey()).removeAll(e.getValue());
                    }
                };
            }
        }
    }

    private static final Set<MockErrorManager> ERROR_MANAGERS = new HashSet<>();

    public static final class MockErrorManager extends ErrorManager {

        private Throwable failure;

        @SuppressWarnings("LeakingThisInConstructor")
        public MockErrorManager() {
            ERROR_MANAGERS.add(this);
        }

        @Override
        public Throwable attachAnnotations(Throwable t, ErrorManager.Annotation[] arr) {
            return t;
        }

        @Override
        public ErrorManager.Annotation[] findAnnotations(Throwable t) {
            return new ErrorManager.Annotation[0];
        }

        @Override
        public Throwable annotate(Throwable t, int severity, String message, String localizedMessage, Throwable stackTrace, Date date) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            return t;
        }

        @Override
        public synchronized void notify(int severity, Throwable t) {
            if (failure == null) {
                failure = t;
            } else {
                if (t instanceof NullPointerException) {
                    // cannot suppress NullPointerException
                    t = new RuntimeException(t);
                }
                failure.addSuppressed(t);
            }
            t.printStackTrace();
        }

        @Override
        public void log(int severity, String s) {
            System.out.println(s);
        }

        @Override
        public ErrorManager getInstance(String name) {
            return this;
        }

        public synchronized void _rethrow() throws Throwable {
            if (failure != null) {
                throw failure;
            }
        }

        public static void rethrow() throws Throwable {
            for (MockErrorManager t : ERROR_MANAGERS) {
                t._rethrow();
            }
        }

        public static void onTestCompleted() throws Throwable {
            try {
                rethrow();
            } finally {
                ERROR_MANAGERS.clear();
            }
        }
    }

    private void addLoggingClassesLoggersOrClassNames(Object[] all) {
        addLoggingClassesLoggersOrClassNames(all, classNamesToInitializeLoggingOn, classesToInitializeLoggingOn, loggersToInitialize);
    }

    private static void addLoggingClassesLoggersOrClassNames(Object[] all, Set<String> classNamesToInitializeLoggingOn, Set<Class<?>> classesToInitializeLoggingOn, Set<Logger> loggersToInitialize) {
        for (Object o : all) {
            if (o == null) {
                continue;
            } else if (o instanceof String) {
                classNamesToInitializeLoggingOn.add((String) o);
            } else if (o instanceof Logger) {
                loggersToInitialize.add((Logger) o);
            } else if (o instanceof Class<?>) {
                classesToInitializeLoggingOn.add((Class<?>) o);
            } else {
                throw new AssertionError("Don't know how to "
                        + "initialize logging on a " + o.getClass().getName() + ": " + o);
            }
        }
    }

    private static Class<?> preinitOneClassForLogging(Class<?> type) {
        try {
            return Class.forName(type.getName(), true, type.getClassLoader());
        } catch (Exception | Error ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static Logger findLogger(Class<?> type) {
        try {
            for (Field f : type.getDeclaredFields()) {
                if (f.getType() == Logger.class && (f.getModifiers() & Modifier.STATIC) != 0) {
                    f.setAccessible(true);
                    Logger logger = (Logger) f.get(null);
                    System.out.println("verbose-log-on: " + type.getName() + "." + f.getName());
                    return logger;
                }
            }

        } catch (Exception | Error ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private Set<String> preinitializeLogging() {
        return preinitializeLogging(insanelyVerboseLogging, classesToInitializeLoggingOn, classNamesToInitializeLoggingOn, loggersToInitialize);
    }

    private boolean insanelyVerboseLogging = false;

    /**
     * If called, and you also set verbose logging, this will walk the parents
     * of each logger down to the root and turn on verbose logging for ALL of
     * them, effectively putting EVERY SINGLE LOGGER into the most verbose mode
     * possible. It will definitely tell you what's happening, if it is logged,
     * and if you can actually find it.
     *
     * @return this
     */
    public TestFixtures insanelyVerboseLogging() {
        insanelyVerboseLogging = true;
        return this;
    }

    private static final IntSet alreadyInitialized = IntSet.create(Integer.MIN_VALUE, Integer.MAX_VALUE);

    private static Set<String> preinitializeLogging(boolean parents, Set<Class<?>> classesToInitializeLoggingOn, Set<String> classNamesToInitializeLoggingOn, Set<Logger> loggersToInitialize) {
        Set<String> loggerNames = new HashSet<>();
        try {
            Set<Class<?>> toInitialize = new HashSet<>();
            Set<String> namesInitialized = new HashSet<>();
            for (Class<?> type : classesToInitializeLoggingOn) {
                Class<?> res = preinitOneClassForLogging(type);
                if (res != null) {
                    toInitialize.add(res);
                    namesInitialized.add(res.getName());
                }
            }
            for (String name : classNamesToInitializeLoggingOn) {
                if (!namesInitialized.contains(name)) {
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    try {
                        Class<?> c = Class.forName(name, true, cl);
                        namesInitialized.add(c.getName());
                        toInitialize.add(c);
                    } catch (Exception | Error ex1) {
                        ex1.printStackTrace();
                    }
                }
            }
            Set<Logger> loggers = new HashSet<>(loggersToInitialize);
            for (Class<?> toInit : toInitialize) {
                Logger logger = findLogger(toInit);
                if (logger != null) {
                    if (parents) {
                        while (logger != null && !loggers.contains(logger)) {
                            loggers.add(logger);
                            logger = logger.getParent();
                        }
                    } else {
                        loggers.add(logger);
                    }
                }
            }
            for (Logger lg : loggers) {
                int code = System.identityHashCode(lg);
                loggerNames.add(lg.getName());
                if (!alreadyInitialized.contains(code)) {
                    lg.setLevel(Level.ALL);
                    alreadyInitialized.add(code);
                }
            }

        } catch (Exception | Error ex) {
            ex.printStackTrace();
        }
        return loggerNames;
    }

    public static void initLoggingFrom(Object... classNamesLoggersOrClassesToScanForLoggerFields) {
        initLogging(new HashSet<>(), false, classNamesLoggersOrClassesToScanForLoggerFields);
    }

    static void initLogging(Set<String> loggerNames, boolean insanelyVerbose, Object... init) {
        if (init != null && init.length > 0) {
            Set<Class<?>> classesToInitializeLoggingOn = new HashSet<>();
            Set<String> classNamesToInitializeLoggingOn = new HashSet<>();
            Set<Logger> loggersToInitialize = new HashSet<>();
            addLoggingClassesLoggersOrClassNames(init, classNamesToInitializeLoggingOn, classesToInitializeLoggingOn, loggersToInitialize);
            Set<String> more = preinitializeLogging(insanelyVerbose, classesToInitializeLoggingOn, classNamesToInitializeLoggingOn, loggersToInitialize);
            loggerNames.addAll(more);
        }
        LogManager logManager = LogManager.getLogManager();
        configureLogManager(loggerNames, logManager);
//        logManager.addConfigurationListener(() -> {
//            new Exception("Log manager reconfigured: ").printStackTrace();
//        });
    }

    private static boolean loggingConfigured;

    private static void configureLogManager(Set<String> loggerNames, LogManager logManager) {
        loggingConfigured = true;
        Properties loggingProps = new Properties();
//        String consoleHandler = ListenableConsoleHandler.class.getName();
        String consoleHandler = ConsoleHandler.class.getName();
        loggingProps.setProperty("handlers", consoleHandler);
        loggingProps.setProperty(consoleHandler + ".level", "ALL");
        loggingProps.setProperty(consoleHandler + ".formatter", Fmt.class.getName());
        loggingProps.setProperty(".formatter", Fmt.class.getName());
        loggingProps.setProperty(".useParentHandlers", "true");
        for (String name : loggerNames) {
            loggingProps.setProperty(name + ".level", "ALL");
            loggingProps.setProperty(name + ".handlers", consoleHandler);
            loggingProps.setProperty(name + ".formatter", Fmt.class.getName());
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            loggingProps.store(out, "Generated");
            try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray())) {
                logManager.readConfiguration(in);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Wait for a line to be logged that contains the passed string; will throw
     * an assertion error if none such is logged before the timeout, or if
     * verbose logging is not enabled. Depends on the specific logger having a
     * loggable level to work.
     *
     * @param dur
     * @param units
     * @param test
     * @throws InterruptedException
     */
    public static void awaitLogLine(long dur, TimeUnit units, String containing) throws InterruptedException {
        awaitLogLine(dur, units, ln -> ln.contains(containing));
    }

    /**
     * Wait for a line to be logged that matches the passed predicate; will
     * throw an assertion error if none such is logged before the timeout, or if
     * verbose logging is not enabled. Depends on the specific logger having a
     * loggable level to work..
     *
     * @param dur
     * @param units
     * @param test
     * @throws InterruptedException
     */
    public static void awaitLogLine(long dur, TimeUnit units, Predicate<String> test) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean();
        onLineLogged(ln -> {
            boolean result = test.test(ln);
            if (result) {
                success.set(true);
            }
            return result;
        }, latch::countDown);
        latch.await(dur, units);
        if (!success.get()) {
            throw new AssertionError("Line matching test " + test + " not logged within " + dur + " " + units);
        }
    }

    /**
     * Run some code when a line is logged that contains the passed text (note
     * this is WITHOUT derferencing arguments - just the message template. Will
     * throw an assertion error if none such is logged before the timeout, or if
     * verbose logging is not enabled. Depends on the specific logger having a
     * loggable level to work.
     *
     * @param rawLogLineText The text
     * @param invoke What to do
     */
    public static void onLineLoggedContaining(String rawLogLineText, Runnable invoke) {
        onLineLogged((str -> {
            return str.contains(rawLogLineText);
        }), invoke);
    }

    /**
     * Run some code when a line is logged that contains the passed text (note
     * this is WITHOUT derferencing arguments - just the message template. Will
     * throw an assertion error if none such is logged before the timeout, or if
     * verbose logging is not enabled. Depends on the specific logger having a
     * loggable level to work.
     *
     * @param rawLogLineTest The test
     * @param invoke What to do
     */
    public static void onLineLogged(Predicate<String> rawLogLineTest, Runnable invoke) {
        assert loggingConfigured : "Verbose logging not enabled";
        triggers.add(new ConsoleListenEntry(rawLogLineTest, invoke));
    }


    static List<ConsoleListenEntry> triggers = new CopyOnWriteArrayList<>();


}

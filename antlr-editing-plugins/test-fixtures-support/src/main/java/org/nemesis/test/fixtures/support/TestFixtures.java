package org.nemesis.test.fixtures.support;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.core.NbLoaderPool;
import org.netbeans.core.startup.NbRepository;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.editor.document.StubImpl;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.editor.lib.DocumentServices;
import org.netbeans.modules.editor.plain.PlainKit;
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

    public TestFixtures verboseGlobalLogging() {
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
        ProxyURLStreamHandlerFactory.class,
        DefaultURLMapperProxy.class,
        CurrentDocumentScheduler.class,
        SelectedNodesScheduler.class,};

    public TestFixtures() {
        MockNamedServicesImpl.builder()
                .add("URLStreamHandler/nbresloc", new org.netbeans.core.startup.NbResourceStreamHandler())
                .add("URLStreamHandler/nbres", new org.netbeans.core.startup.NbResourceStreamHandler())
                .add("URLStreamHandler/m2", new org.netbeans.core.startup.MavenRepoURLHandler())
                .build();
    }

    public ThrowingRunnable build() {
        if (logging) {
            initLogging();
        }
        DocumentFactory fact = new DocumentFactoryImpl();
        includedLogs.addAll(includeLogs);
        excludedLogs.addAll(excludeLogs);
        addToMimeLookup("", fact);
        addToMimeLookup("text/plain", new PlainKit());
        Set<Class<?>> set = new LinkedHashSet<>(DEFAULT_LOOKUP_CONTENTS.length + defaultLookupContents.size());
        set.addAll(Arrays.asList(DEFAULT_LOOKUP_CONTENTS));
        set.addAll(defaultLookupContents);
        MockServices.setServices(set.toArray(new Class[set.size()]));
//        onShutdown.andAlwaysRun(() -> MockServices.setServices(new Class[0]));
        // Force init
        assertNotNull(Lookup.getDefault().lookup(ActiveDocumentProvider.class));
        onShutdown.andAlwaysFirst(() -> {
            try {
                MockErrorManager.onTestCompleted();
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

    static void initLogging() {
        LogManager logManager = LogManager.getLogManager();
        Properties loggingProps = new Properties();
        String consoleHandler = ConsoleHandler.class.getName();
        loggingProps.setProperty("handlers", consoleHandler);
        loggingProps.setProperty(consoleHandler + ".level", "ALL");
        loggingProps.setProperty(consoleHandler + ".formatter", Fmt.class.getName());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            loggingProps.store(out, "Generated");
            try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray())) {
                logManager.readConfiguration(in);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
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

    public static final class Fmt extends java.util.logging.Formatter {

        String pad(long val) {
            StringBuilder sb = new StringBuilder();
            sb.append(Long.toString(val));
            while (sb.length() < 4) {
                sb.insert(0, '0');
            }
            return sb.toString();
        }

        private boolean nbeventsLogged;

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder()
                    .append(pad(record.getSequenceNumber())).append(":");
            String nm = record.getLoggerName();
            if (!nbeventsLogged && "NbEvents".equals(nm)) {
                nbeventsLogged = true;
                new Exception("Got message from " + nm + " - "
                        + " something is attempting to start the full IDE.").printStackTrace();
            }
            if (nm.indexOf('.') > 0 && nm.indexOf('.') < nm.length() - 1) {
                nm = nm.substring(nm.lastIndexOf('.') + 1);
            }
            if (!excludedLogs.isEmpty() && excludedLogs.contains(nm)) {
                return "";
            }
            if (!includedLogs.isEmpty() && !includedLogs.contains(nm)) {
                return "";
            }
            sb.append(nm).append(": ");
            String msg = record.getMessage();
            if (msg != null && record.getParameters() != null && record.getParameters().length > 0) {
                msg = MessageFormat.format(msg, record.getParameters());
            }
            if (msg != null) {
                sb.append(msg);
            }
            if (record.getThrown() != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (PrintStream ps = new PrintStream(out, true, "UTF-8")) {
                    record.getThrown().printStackTrace(ps);
                } catch (UnsupportedEncodingException ex) {
                    ex.printStackTrace();
                }
                sb.append(new String(out.toByteArray(), UTF_8));
            }
            return sb.append('\n').toString();
        }

    }

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

    public static class MockMimeDataProvider implements MimeDataProvider {

        @Override
        public synchronized Lookup getLookup(MimePath mimePath) {
            String path = mimePath.getPath();
            Lookup result = mimeLookupsByPath.get(path);
            if (result == null) {
                result = Lookups.fixed(contentsByMimePath.get(path).toArray(new Object[0]));
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
                return result;
            }
            return Lookups.metaInfServices(l, "META-INF/namedservices/" + path);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private final Map<String, Set<Object>> cfp
                    = CollectionUtils.supplierMap(HashSet::new);

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
}

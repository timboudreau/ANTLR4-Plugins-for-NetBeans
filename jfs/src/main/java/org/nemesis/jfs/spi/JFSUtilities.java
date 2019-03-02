package org.nemesis.jfs.spi;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import org.nemesis.jfs.JFSUrlStreamHandlerFactory;

/**
 * A little abstraction which allows this library not to directly depend on any
 * NetBeans classes. Implement and put in the default lookup or make available
 * to ServiceLoader.
 * <p>
 * The library will work without an implementation present, but for example,
 * timestamps for documents will be bogus, and the system default charset will
 * be used for all files.
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class JFSUtilities {

    protected JFSUtilities() {
    }

    private static JFSUtilities INSTANCE;

    public static JFSUtilities getDefault() {
        return INSTANCE == null ? INSTANCE = loadDefaultInstance() : INSTANCE;
    }

    private static JFSUtilities loadDefaultInstance() {
        JFSUtilities result = getViaLookupReflectively(JFSUtilities.class);
        if (result != null) {
            return result;
        }
        ServiceLoader<JFSUtilities> ldr = ServiceLoader.load(JFSUtilities.class);
        return ldr.iterator().hasNext() ? ldr.iterator().next()
                : new DummyJFSUtilities();
    }

    static <T> T getViaLookupReflectively(Class<T> type) {
        try {
            Class<?> Lookup = Class.forName("org.openide.util.Lookup");
            Method getDefault = Lookup.getMethod("getDefault");
            Object defaultLookup = getDefault.invoke(null);
            assert defaultLookup != null : "Lookup.getDefault() returned null";
            Method lookup = defaultLookup.getClass().getMethod("lookup", Class.class);
            return (T) lookup.invoke(defaultLookup, type);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(JFSUrlStreamHandlerFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static Charset encodingFor(Path file) {
        return getDefault().getEncodingFor(file);
    }

    public static long lastModifiedFor(Document doc) {
        return getDefault().getLastModifiedFor(doc);
    }

    public static void attachWeakListener(Document doc, DocumentListener listener) {
        getDefault().weakListen(doc, listener);
    }

    public static <T> Set<T> newWeakSet() {
        return getDefault().createWeakSet();
    }

    protected abstract Charset getEncodingFor(Path file);

    protected abstract long getLastModifiedFor(Document document);

    /**
     * Attach the passed listener to the document without creating a strong
     * reference in either direction.
     *
     * @param document The document
     * @param listener The listener
     */
    protected void weakListen(Document document, DocumentListener listener) {
        document.addDocumentListener(new WL(document, listener));
    }

    /**
     * Create a set which weakly references its content.
     *
     * @param <T> The type
     * @return A set
     */
    protected <T> Set<T> createWeakSet() {
        return new SimpleWeakSet<>();
    }

    private static final class WL implements DocumentListener {

        private final Reference<Document> docRef;
        private final Reference<DocumentListener> delegateRef;

        WL(Document doc, DocumentListener delegate) {
            docRef = new WeakReference<Document>(doc);
            delegateRef = new WeakReference<DocumentListener>(delegate);
        }

        private DocumentListener delegate() {
            DocumentListener result = delegateRef.get();
            return result;
        }

        private void withDelegate(Consumer<DocumentListener> c) {
            DocumentListener delegate = delegate();
            if (delegate == null) {
                Document doc = docRef.get();
                if (doc != null) {
                    doc.removeDocumentListener(this);;
                } else {
                    c.accept(delegate);
                }
            }
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            withDelegate(delegate -> {
                delegate.insertUpdate(e);
            });
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            withDelegate(delegate -> {
                delegate.removeUpdate(e);
            });
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            withDelegate(delegate -> {
                delegate.changedUpdate(e);
            });
        }
    }

    private static final class SimpleWeakSet<T> extends AbstractSet<T> {

        private final Map<T, Boolean> backingStore = new WeakHashMap<>();

        @Override
        public Iterator<T> iterator() {
            return backingStore.keySet().iterator();
        }

        @Override
        public void clear() {
            backingStore.clear();
        }

        @Override
        public int size() {
            return backingStore.size();
        }

        @Override
        public boolean add(T e) {
            if (e == null) {
                throw new IllegalArgumentException("Null argument to add");
            }
            Boolean result = backingStore.put(e, Boolean.TRUE);
            return result == null ? false : result;
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            int oldSize = size();
            for (T t : c) {
                backingStore.put(t, Boolean.TRUE);
            }
            return size() != oldSize;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean changed = false;
            for (Object o : c) {
                changed |= backingStore.remove(o);
            }
            return changed;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Set<T> toRemove = new HashSet<>();
            for (Map.Entry<T, ?> e : backingStore.entrySet()) {
                if (!c.contains(e.getKey())) {
                    toRemove.add(e.getKey());
                }
            }
            return removeAll(toRemove);
        }

        @Override
        public boolean remove(Object o) {
            return backingStore.remove(o);
        }
    }

    private static final class DummyJFSUtilities extends JFSUtilities {

        private static final long DUMMY_TIMESTAMP = System.currentTimeMillis();

        @Override
        protected Charset getEncodingFor(Path file) {
            return Charset.defaultCharset();
        }

        @Override
        protected long getLastModifiedFor(Document document) {
            return DUMMY_TIMESTAMP;
        }
    }
}

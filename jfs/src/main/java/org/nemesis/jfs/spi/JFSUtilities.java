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
package org.nemesis.jfs.spi;

import com.mastfrog.util.collections.CollectionUtils;
import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import org.nemesis.jfs.JFSFileObject;
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

    private final boolean priorityListeners;
    protected JFSUtilities() {
        priorityListeners = doDocumentListenersHavePriority();
    }

    private static JFSUtilities INSTANCE;

    public static JFSUtilities getDefault() {
        return INSTANCE == null ? INSTANCE = loadDefaultInstance() : INSTANCE;
    }

    public static boolean documentListenersHavePriority() {
        return getDefault().priorityListeners;
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

    /**
     * Get the encoding for a file which is being masqueraded or copied, and may
     * differ from the default system encoding, or may be a file format which
     * specifies its encoding internally.
     *
     * @param file A file
     * @return A character set or null
     */
    public static Charset encodingFor(Path file) {
        return getDefault().getEncodingFor(file);
    }

    public static long lastModifiedFor(Document doc) {
        return getDefault().getLastModifiedFor(doc);
    }

    /**
     * Weakly listen on a document, so the document does not hold a strong
     * reference to the listener.
     *
     * @param doc A document
     * @param listener A listener
     */
    public static void attachWeakListener(Document doc, DocumentListener listener) {
        getDefault().weakListen(doc, listener);
    }

    /**
     * Create a weak set.
     *
     * @param <T> The set contents type
     * @return A weak set
     */
    public static <T> Set<T> newWeakSet() {
        return getDefault().createWeakSet();
    }

    /**
     * For the case of JFS files which are masqueraded documents or files,
     * return the original masqueraded item, possibly (as in NetBeans, where a
     * FileObject can be looked up from a Document) performing some conversion
     * or lookup along the way.
     *
     * @param <T> The desired typ
     * @param file The file that is masqueraded
     * @param type The type that is desired
     * @param obj The masqueraded object
     * @return An object of the correct type, or null
     */
    public static <T> T convertOrigin(JFSFileObject file, Class<T> type, Object obj) {
        return getDefault().convert(file, type, obj);
    }

    /**
     * For the case of JFS files which are masqueraded documents or files,
     * return the original masqueraded item, possibly (as in NetBeans, where a
     * FileObject can be looked up from a Document) performing some conversion
     * or lookup along the way.
     * <p>
     * The default implementation will return the object if it is an instance of
     * T, and will perform Path to File conversion if the object is a path and
     * the type requested is File.
     *
     * @param <T> The desired typ
     * @param file The file that is masqueraded
     * @param type The type that is desired
     * @param obj The masqueraded object
     * @return An object of the correct type, or null
     */
    protected <T> T convert(JFSFileObject file, Class<T> type, Object obj) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }
        if (type == File.class && obj instanceof Path) {
            return type.cast(((Path) obj).toFile());
        }
        return null;
    }

    /**
     * Get the encoding for a file on disk, which may be project-specific or (in
     * cases like XML), file-specific.
     *
     * @param file A file
     * @return The encoding
     */
    protected abstract Charset getEncodingFor(Path file);

    protected boolean doDocumentListenersHavePriority() {
        return false;
    }

    protected long getLastModifiedFor(Document document) {
        return DummyJFSUtilities.DummyTimestamps.getTimestamp(document);
    }

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
        return CollectionUtils.weakSet();
    }

    private static final class WL implements DocumentListener {

        private final Reference<Document> docRef;
        private final Reference<DocumentListener> delegateRef;

        WL(Document doc, DocumentListener delegate) {
            docRef = new WeakReference<>(doc);
            delegateRef = new WeakReference<>(delegate);
        }

        private DocumentListener delegate() {
            DocumentListener result = delegateRef.get();
            return result;
        }

        private void withDelegate(Consumer<DocumentListener> c) {
            DocumentListener delegate = delegate();
            if (delegate != null) {
                Document doc = docRef.get();
                if (doc == null) {
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

    private static final class DummyJFSUtilities extends JFSUtilities {

        @Override
        protected Charset getEncodingFor(Path file) {
            return Charset.defaultCharset();
        }

        @Override
        protected long getLastModifiedFor(Document document) {
            // The NbJFSUtilities implementation uses priority
            // document listeners to ensure we are notified first
            return DummyTimestamps.getTimestamp(document);
        }

        static final class DummyTimestamps implements DocumentListener {

            private final AtomicLong timestamp;

            DummyTimestamps(long initialValue) {
                timestamp = new AtomicLong(initialValue);
            }

            static long getTimestamp(Document doc) {
                return get(doc).get();
            }

            static DummyTimestamps get(Document doc) {
                DummyTimestamps result
                        = (DummyTimestamps) doc.getProperty(DummyTimestamps.class);
                if (result == null) {
                    long initial = 0;
                    Object o = doc.getProperty("last-modification-timestamp");
                    if (o instanceof AtomicLong) {
                        initial = ((AtomicLong) o).get();
                    }
                    result = new DummyTimestamps(initial == 0
                            ? System.currentTimeMillis()
                            : initial);
                    // Object is self-contained, not a leak
                    doc.putProperty(DummyTimestamps.class, result);
                    doc.addDocumentListener(result);
                }
                return result;
            }

            long get() {
                return timestamp.get();
            }

            private void touch() {
                timestamp.getAndUpdate((old) -> {
                    return System.currentTimeMillis();
                });
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                touch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                touch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                touch();
            }
        }
    }
}

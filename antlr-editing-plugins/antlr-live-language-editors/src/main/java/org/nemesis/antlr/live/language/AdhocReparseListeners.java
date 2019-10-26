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
package org.nemesis.antlr.live.language;

import com.mastfrog.util.collections.CollectionUtils;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.debug.api.Debug;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.lexer.Language;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.Source;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;

/**
 * Allows for listening for reparses of a specific adhoc file or document, for
 * things like printing error output.
 *
 * @author Tim Boudreau
 */
public final class AdhocReparseListeners {

    private static final Logger LOG = Logger.getLogger(AdhocReparseListeners.class.getName());
    private final Map<Document, Set<BiConsumer<? super Document, ? super EmbeddedAntlrParserResult>>> documentListeners
            = CollectionUtils.weakSupplierMap(WeakSet::new);
    private final Map<FileObject, Set<BiConsumer<? super FileObject, ? super EmbeddedAntlrParserResult>>> fileListeners
            = CollectionUtils.weakSupplierMap(WeakSet::new);
    private final String mimeType;

    public AdhocReparseListeners(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Listen for reparses of a document of a particular (adhoc) mime type. The
     * listener is weakly referenced.
     *
     * @param mimeType A mime type
     * @param doc A document of that type
     * @param listener A listener callback
     */
    public static void listen(String mimeType, Document doc, BiConsumer<? super Document, ? super EmbeddedAntlrParserResult> listener) {
        String actualMimeType = NbEditorUtilities.getMimeType(doc);
        if (!mimeType.equals(actualMimeType)) {
            IllegalArgumentException iae = new IllegalArgumentException("Attempting to listen for reparses "
                    + " of " + mimeType + " for a document of type " + actualMimeType + " which will "
                    + "never fire a change");
            LOG.log(Level.WARNING, null, iae);
        }

        DynamicLanguages.ensureRegistered(mimeType);
        boolean found = withListeners(mimeType, true, arl -> {
            arl.documentListeners.get(doc).add(listener);
        });
        if (!found) {
            LOG.log(Level.WARNING, "Dynamic language not registered for mime type " + mimeType, new Exception(
                    "Dynamic language not registered for mime type " + mimeType));
        }
    }

    public static void unlisten(String mimeType, FileObject fo, BiConsumer<? super FileObject, ? super EmbeddedAntlrParserResult> listener) {
        withListeners(mimeType, false, arl -> {
            Set<BiConsumer<? super FileObject, ? super EmbeddedAntlrParserResult>> set
                    = arl.fileListeners.get(fo);
            set.remove(listener);
            if (set.isEmpty()) {
                arl.fileListeners.remove(fo);
            }
        });
    }

    public static void unlisten(String mimeType, Document doc, BiConsumer<? super Document, ? super EmbeddedAntlrParserResult> listener) {
        DynamicLanguages.ensureRegistered(mimeType);
        String realMimeType = NbEditorUtilities.getMimeType(doc);
        if (!realMimeType.equals(mimeType)) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            StringBuilder sb = new StringBuilder()
                    .append("Unsubscribing from reparses of ")
                    .append(fo.getNameExt())
                    .append(" which is of mime type ")
                    .append(fo.getMIMEType())
                    .append(" as the mime type ")
                    .append(mimeType)
                    .append("(").append(AdhocMimeTypes.loggableMimeType(mimeType))
                    .append(") is surely a bug.  Callback is: ")
                    .append(listener).append(", document is ")
                    .append(doc);
            LOG.log(Level.SEVERE, sb.toString(), new Exception(sb.toString()));
        }

        withListeners(mimeType, false, arl -> {
            Set<BiConsumer<? super Document, ? super EmbeddedAntlrParserResult>> set
                    = arl.documentListeners.get(doc);
            set.remove(listener);
            if (set.isEmpty()) {
                arl.documentListeners.remove(doc);
            }
        });
    }

    /**
     * Listen for reparses of a file object of a particular (adhoc) mime type.
     *
     * @param mimeType A mime type
     * @param fo A file object of that type
     * @param listener A listener callback
     */
    public static boolean listen(String mimeType, FileObject fo, BiConsumer<? super FileObject, ? super EmbeddedAntlrParserResult> listener) {
        boolean wasRegistered = DynamicLanguages.ensureRegistered(mimeType);
        if (!wasRegistered) {
            Language l = Language.find(mimeType);
            System.out.println("looked up " + l);
        }
        final File file = FileUtil.toFile(fo);

        Runnable r = new Runnable() {
            int runCount;

            @Override
            public void run() {
                FileObject theFileObject = FileUtil.toFileObject(file);
                System.out.println("RUN " + runCount + " " + mimeType);
                if (!theFileObject.getMIMEType().equals(mimeType)) {
                    if (runCount++ < 6) {
                        System.out.println("not yet, reschedule");
                        PROC.schedule(this, 1, TimeUnit.SECONDS);
                        return;
                    }
                }
                if (!theFileObject.getMIMEType().equals(mimeType)) {
                    StringBuilder sb = new StringBuilder(512)
                            .append("Subscribing to reparses of ")
                            .append(theFileObject.getNameExt())
                            .append(" which is  of mime type ")
                            .append(theFileObject.getMIMEType())
                            .append(" as the mime type ")
                            .append(mimeType)
                            .append("(").append(AdhocMimeTypes.loggableMimeType(mimeType))
                            .append(") is surely a bug.  Callback is: ")
                            .append(listener);
                    LOG.log(Level.SEVERE, sb.toString(), new Exception(sb.toString()));
                }
                boolean found = withListeners(mimeType, true, arl -> {
                    arl.fileListeners.get(theFileObject).add(listener);
                });
                if (!found) {
                    LOG.log(Level.WARNING, "Dynamic language not registered for mime type " + mimeType,
                            new Exception("Dynamic language not registered for mime type " + mimeType));
                }
            }
        };
        if (wasRegistered) {
            System.out.println("was registered - run immediate");
            r.run();
        } else {
            System.out.println("not registered - async run");
//            PROC.schedule(r, 500, TimeUnit.MILLISECONDS);
            r.run();
        }
        return wasRegistered;
    }

    static Document documentFor(Source src) {
        Document d = src.getDocument(false);
        if (d != null) {
            return d;
        }
        FileObject fo = src.getFileObject();
        if (fo == null) {
            return null;
        }
        try {
            DataObject dob = DataObject.find(fo);
            EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
            if (ck != null) {
                return ck.getDocument();
            }
        } catch (DataObjectNotFoundException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    static FileObject fileObjectFor(Source src) {
        FileObject result = src.getFileObject();
        if (result == null) {
            Document d = src.getDocument(false);
            if (d != null) {
                result = NbEditorUtilities.getFileObject(d);
            } else {
                Object o = d.getProperty(StreamDescriptionProperty);
                if (o instanceof FileObject) {
                    result = (FileObject) o;
                } else if (o instanceof DataObject) {
                    result = ((DataObject) o).getPrimaryFile();
                }
            }
        }
        return result;
    }

    void onReparse(Source src, EmbeddedAntlrParserResult res) {
        System.out.println("ON REPARSE " + src.getFileObject());
        Debug.run(this, "on-reparse " + AdhocMimeTypes.loggableMimeType(src.getMimeType()) + " " + AdhocMimeTypes.loggableMimeType(res.proxy().mimeType()), () -> {
            StringBuilder sb = new StringBuilder("Source mime type: ").append(src.getMimeType()).append('\n');
            sb.append("Source: ").append(src.getFileObject()).append('\n');
            sb.append("Proxy mime type: ").append(res.proxy().mimeType()).append('\n');
            sb.append("Proxy grammar file: ").append(res.proxy().grammarPath()).append('\n');
            sb.append("\nPROXY:\n").append(res.proxy());
            sb.append("\nTEXT:\n").append(res.proxy().text());
            return sb.toString();
        }, () -> {
            FileObject fo = fileObjectFor(src);
            if (fo != null && fileListeners.containsKey(fo)) {
                Set<BiConsumer<? super FileObject, ? super EmbeddedAntlrParserResult>> listeners = fileListeners.get(fo);
                LOG.log(Level.FINER, "Call {0} file listeners for {1}: {2}",
                        new Object[]{listeners.size(), fo, listeners});
                Debug.message(listeners.size() + " file listeners", listeners::toString);
                for (BiConsumer<? super FileObject, ? super EmbeddedAntlrParserResult> listener : listeners) {
                    try {
                        Debug.message("Call " + listener);
                        listener.accept(fo, res);
                    } catch (Exception ex) {
                        LOG.log(Level.SEVERE, "Notifying " + fo, ex);
                    }
                }
            } else {
                LOG.log(Level.FINEST, "No file listeners for {0}", fo);
            }
            Document doc = documentFor(src);
            if (doc != null && documentListeners.containsKey(doc)) {
                Set<BiConsumer<? super Document, ? super EmbeddedAntlrParserResult>> listeners = documentListeners.get(doc);
                LOG.log(Level.FINER, "Call {0} document listeners for {1}: {2}",
                        new Object[]{listeners.size(), doc, listeners});
                Debug.message(listeners.size() + " doc listeners", listeners::toString);
                for (BiConsumer<? super Document, ? super EmbeddedAntlrParserResult> listener : listeners) {
                    try {
                        Debug.message("Call " + listener);
                        listener.accept(doc, res);
                    } catch (Exception ex) {
                        LOG.log(Level.SEVERE, "Notifying " + doc, ex);
                    }
                }
            } else {
                LOG.log(Level.FINEST, "No doc listeners for {0}", doc);
            }
        });
    }

    void onReparse(Document doc, EmbeddedAntlrParserResult res) {
        onReparse(Source.create(doc), res);
    }

    static boolean reparsed(String mimeType, Source src, EmbeddedAntlrParserResult res) {
        System.out.println("REPARSED " + src.getFileObject());
        if (true) {
            return withListeners(mimeType, false, arl -> {
                arl.onReparse(src, res);
            });
        }

        String foundMime = src.getMimeType();
        String proxyMime = res.proxy().mimeType();
        if (!foundMime.equals(mimeType) || !foundMime.equals(proxyMime)) {
            FileObject fo = fileObjectFor(src);
            StringBuilder sb = new StringBuilder(512)
                    .append("Reparse listeners for ")
                    .append(mimeType)
                    .append(" notified for wrong mime typed file or proxy ")
                    .append(fo == null ? "no-file" : fo.getNameExt())
                    .append(" src ")
                    .append(src)
                    .append("\n")
                    .append("Ours: ").append(mimeType).append('\n')
                    .append("Prox: ").append(proxyMime).append('\n')
                    .append("Sorc: ").append(foundMime).append('\n')
                    .append("File: ").append(fo == null ? "<no file>" : fo.getMIMEType()).append('\n')
                    .append("(").append(AdhocMimeTypes.loggableMimeType(mimeType))
                    .append(") is surely a bug.");
            LOG.log(Level.SEVERE, sb.toString(), new Exception(sb.toString()));
        }
        // Coalesce reparses since there are frequently many after a rebuild
        boolean result = withListeners(mimeType, false, arl -> {
            System.out.println("In with Listeners");
            synchronized (PENDING) {
                SourceKey k = new SourceKey(mimeType, src, res);
                SourceKey real = PENDING.get(k);
                if (real == null) {
                    k.touch();
                } else if (real != k) {
                    if (real.maybeUpdate(res)) {
                        real.touch();
                    } else {
                        PENDING.put(k, k);
                    }
                } else {
                    real.touch();
                }
            }
        });
        return result;
    }

    public static boolean reparsed(String mimeType, Document src, EmbeddedAntlrParserResult embp) {
        return withListeners(mimeType, false, arl -> {
            FileObject fo = NbEditorUtilities.getFileObject(src);
            if (fo != null) {
                System.out.println("  not calling on reparse on " + arl.fileListeners.get(fo));
            }
            arl.onReparse(src, embp);
        });
    }

    static boolean withListeners(String mimeType, boolean ifNoListeners, Consumer<AdhocReparseListeners> c) {
        Lookup lkp = MimeLookup.getLookup(mimeType);
        AdhocReparseListeners l = lkp.lookup(AdhocReparseListeners.class);
        if (l != null && (ifNoListeners || !l.documentListeners.isEmpty() || !l.fileListeners.isEmpty())) {
            c.accept(l);
            return true;
        }
        return false;
    }

    static final RequestProcessor PROC = new RequestProcessor("adhoc-reparse", 5);
    static Map<SourceKey, SourceKey> PENDING = new HashMap<>();

    static final class SourceKey implements Runnable {

        private final String mimeType;
        private final Source src;
        private final AtomicReference<KeyInfo> ref = new AtomicReference<>();
        private volatile boolean running;
        private final RequestProcessor.Task task = PROC.create(this, false);

        public SourceKey(String mimeType, Source src, EmbeddedAntlrParserResult res) {
            this.mimeType = mimeType;
            this.src = src;
            ref.set(new KeyInfo(res));
        }

        Source src() {
            return src;
        }

        @Override
        public String toString() {
            return "SourceKey(" + src + ", " + ref.get() + " running "
                    + running + ")";
        }

        SourceKey touch() {
            synchronized (PENDING) {
                PENDING.put(this, this);
                task.schedule(250);
            }
            return this;
        }

        public boolean maybeUpdate(EmbeddedAntlrParserResult res) {
            if (running) {
                LOG.log(Level.FINEST, "Update parser result while running {0}", res);
                return false;
            }
            ref.set(new KeyInfo(res));
            return true;
        }

        @Override
        public void run() {
            running = true;
            Debug.run(this, this.toString(), () -> {
                synchronized (PENDING) {
                    PENDING.remove(this);
                }
                KeyInfo info = ref.get();
                boolean res = withListeners(mimeType, false, arl -> {
                    arl.onReparse(src, info.res);
                });
                Debug.message("WithListeners returned " + res);
                LOG.log(Level.FINEST, "WithListeners result {0} for {1}",
                        new Object[]{res, this});
            });
        }

        static class KeyInfo {

            private final EmbeddedAntlrParserResult res;

            public KeyInfo(EmbeddedAntlrParserResult res) {
                this.res = res;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof SourceKey)) {
                return false;
            } else if (o == this) {
                return true;
            } else {
                SourceKey sk = (SourceKey) o;
                if (!mimeType.equals(sk.mimeType)) {
                    return false;
                }
                if (sk.src.equals(src)) {
                    return true;
                } else {
                    FileObject theirs = sk.src.getFileObject();
                    FileObject mine = src.getFileObject();
                    if (theirs != null && mine != null && theirs.equals(mine)) {
                        return true;
                    }
                    Document dTheirs = sk.src.getDocument(false);
                    Document dMine = sk.src.getDocument(false);
                    if (dTheirs == dMine) {
                        return true;
                    }
                    if (dTheirs != null && dMine != null) {
                        FileObject foTheirs = NbEditorUtilities.getFileObject(dTheirs);
                        FileObject foMine = NbEditorUtilities.getFileObject(dMine);
                        if (foTheirs.equals(foMine)) {
                            return true;
                        }
                    }
                    if (dMine != null && dTheirs != null) {
                        Object myProp = dMine.getProperty(StreamDescriptionProperty);
                        Object theirProp = dTheirs.getProperty(StreamDescriptionProperty);
                        if (myProp != null && theirProp != null && myProp.equals(theirProp)) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        }

        @Override
        public int hashCode() {
            FileObject fo = src.getFileObject();
            if (fo != null) {
                return fo.hashCode();
            }
            Document doc = src.getDocument(false);
            if (doc != null) {
                fo = NbEditorUtilities.getFileObject(doc);
                if (fo != null) {
                    return fo.hashCode();
                }
            }
            Object prop = doc.getProperty(StreamDescriptionProperty);
            if (prop != null) {
                return prop.hashCode();
            }
            return doc.hashCode();
        }
    }
}

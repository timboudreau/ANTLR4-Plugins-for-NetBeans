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
package org.nemesis.antlr.live.language;

import com.mastfrog.function.TriConsumer;
import com.mastfrog.util.collections.CollectionUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.debug.api.Debug;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.Source;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
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
    private final Map<Document, Set<TriConsumer<? super Document, ? super GrammarRunResult<?>, ? super ParseTreeProxy>>> documentListeners
            = CollectionUtils.weakSupplierMap(WeakSet::new);
    private final Map<FileObject, Set<TriConsumer<? super FileObject, ? super GrammarRunResult<?>, ? super ParseTreeProxy>>> fileListeners
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
    public static void listen(String mimeType, Document doc, TriConsumer<? super Document, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener) {
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

    public static void unlisten(String mimeType, FileObject fo, TriConsumer<? super FileObject, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener) {
        withListeners(mimeType, false, arl -> {
            Set<TriConsumer<? super FileObject, ? super GrammarRunResult<?>, ? super ParseTreeProxy>> set
                    = arl.fileListeners.get(fo);
            set.remove(listener);
            if (set.isEmpty()) {
                arl.fileListeners.remove(fo);
            }
        });
    }

    public static void unlisten(String mimeType, Document doc, TriConsumer<? super Document, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener) {
        DynamicLanguages.ensureRegistered(mimeType);
        String realMimeType = NbEditorUtilities.getMimeType(doc);
        if (!realMimeType.equals(mimeType)) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            StringBuilder sb = new StringBuilder()
                    .append("Subscribing to reparses of ")
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
            Set<TriConsumer<? super Document, ? super GrammarRunResult<?>, ? super ParseTreeProxy>> set
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
    public static void listen(String mimeType, FileObject fo, TriConsumer<? super FileObject, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener) {
        DynamicLanguages.ensureRegistered(mimeType);
        if (!fo.getMIMEType().equals(mimeType)) {
            StringBuilder sb = new StringBuilder(512)
                    .append("Subscribing to reparses of ")
                    .append(fo.getNameExt())
                    .append(" which is  of mime type ")
                    .append(fo.getMIMEType())
                    .append(" as the mime type ")
                    .append(mimeType)
                    .append("(").append(AdhocMimeTypes.loggableMimeType(mimeType))
                    .append(") is surely a bug.  Callback is: ")
                    .append(listener);
            LOG.log(Level.SEVERE, sb.toString(), new Exception(sb.toString()));
        }
        boolean found = withListeners(mimeType, true, arl -> {
            arl.fileListeners.get(fo).add(listener);
        });
        if (!found) {
            LOG.log(Level.WARNING, "Dynamic language not registered for mime type " + mimeType, new Exception());
        }
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

    void onReparse(Source src, GrammarRunResult<?> gbrg, ParseTreeProxy proxy) {
        Debug.run(this, "on-reparse " + AdhocMimeTypes.loggableMimeType(src.getMimeType()) + " " + AdhocMimeTypes.loggableMimeType(proxy.mimeType()), () -> {
            StringBuilder sb = new StringBuilder("Source mime type: ").append(src.getMimeType()).append('\n');
            sb.append("Source: ").append(src.getFileObject()).append('\n');
            sb.append("Proxy mime type: ").append(proxy.mimeType()).append('\n');
            sb.append("Proxy grammar file: ").append(proxy.grammarPath()).append('\n');
            sb.append("\nPROXY:\n").append(proxy);
            sb.append("\nTEXT:\n").append(proxy.text());
            return sb.toString();
        }, () -> {
            FileObject fo = fileObjectFor(src);
            if (fo != null && fileListeners.containsKey(fo)) {
                for (TriConsumer<? super FileObject, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener : fileListeners.get(fo)) {
                    try {
                        Debug.message("Call " + listener);
                        listener.apply(fo, gbrg, proxy);
                    } catch (Exception ex) {
                        LOG.log(Level.SEVERE, "Notifying " + fo, ex);
                    }
                }
            }
            Document doc = documentFor(src);
            if (doc != null && documentListeners.containsKey(doc)) {
                for (TriConsumer<? super Document, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener : documentListeners.get(doc)) {
                    try {
                        Debug.message("Call " + listener);
                        listener.apply(doc, gbrg, proxy);
                    } catch (Exception ex) {
                        LOG.log(Level.SEVERE, "Notifying " + doc, ex);
                    }
                }
            }
        });
    }

    void onReparse(Document doc, GrammarRunResult<?> gbrg, ParseTreeProxy proxy) {
        onReparse(Source.create(doc), gbrg, proxy);
    }

    static boolean reparsed(String mimeType, Source src, GrammarRunResult<?> gbrg, ParseTreeProxy proxy) {
//        return withListeners(mimeType, false, arl -> {
//            arl.onReparse(src, gbrg, proxy);
//        });
        String foundMime = src.getMimeType();
        String proxyMime = proxy.mimeType();
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
        return withListeners(mimeType, false, arl -> {
            synchronized (PENDING) {
                SourceKey k = new SourceKey(mimeType, src, gbrg, proxy);
                SourceKey real = PENDING.get(k);
                if (real == null) {
                    PENDING.put(k, k);
                    real = k;
                } else if (real != k) {
                    if (real.maybeUpdate(gbrg, proxy)) {
                        real.touch();
                    } else {
                        PENDING.put(k, k);
                    }
                } else {
                    real.touch();
                }
            }
        });
    }

    static boolean reparsed(String mimeType, Document src, GrammarRunResult<?> gbrg, ParseTreeProxy proxy) {
        return withListeners(mimeType, false, arl -> {
            arl.onReparse(src, gbrg, proxy);
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

        public SourceKey(String mimeType, Source src, GrammarRunResult<?> res, ParseTreeProxy proxy) {
            this.mimeType = mimeType;
            this.src = src;
            ref.set(new KeyInfo(res, proxy));
        }

        Source src() {
            return src;
        }

        SourceKey touch() {
            synchronized (PENDING) {
                PENDING.put(this, this);
                task.schedule(250);
            }
            return this;
        }

        public boolean maybeUpdate(GrammarRunResult<?> res, ParseTreeProxy prox) {
            if (running) {
                return false;
            }
            ref.set(new KeyInfo(res, prox));
            return true;
        }

        @Override
        public void run() {
            running = true;
            synchronized (PENDING) {
                PENDING.remove(this);
            }
            KeyInfo info = ref.get();
            withListeners(mimeType, false, arl -> {
                arl.onReparse(src, info.res, info.proxy);
            });
        }

        static class KeyInfo {

            private final GrammarRunResult<?> res;
            private final ParseTreeProxy proxy;

            public KeyInfo(GrammarRunResult<?> res, ParseTreeProxy proxy) {
                this.res = res;
                this.proxy = proxy;
            }
        }

        @Override
        public String toString() {
            return src + " / " + mimeType;
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

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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.debug.api.Debug;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.modules.parsing.api.Source;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
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

    /**
     * Listen for reparses of a file object of a particular (adhoc) mime type.
     *
     * @param mimeType A mime type
     * @param doc A document of that type
     * @param listener A listener callback
     */
    public static void listen(String mimeType, Document doc, TriConsumer<? super Document, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener) {
        DynamicLanguages.ensureRegistered(mimeType);
        boolean found = withListeners(mimeType, true, arl -> {
            arl.documentListeners.get(doc).add(listener);
        });
        if (!found) {
            LOG.log(Level.WARNING, "Dynamic language not registered for mime type " + mimeType, new Exception(
                    "Dynamic language not registered for mime type " + mimeType));
        }
    }

    public static void unlisten(String mimeType, Document doc, TriConsumer<? super Document, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener) {
        DynamicLanguages.ensureRegistered(mimeType);
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
        boolean found = withListeners(mimeType, true, arl -> {
            arl.fileListeners.get(fo).add(listener);
        });
        if (!found) {
            LOG.log(Level.WARNING, "Dynamic language not registered for mime type " + mimeType, new Exception());
        }
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
            FileObject fo = src.getFileObject();
            if (fo != null && fileListeners.containsKey(fo)) {
                for (TriConsumer<? super FileObject, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener : fileListeners.get(fo)) {
                    try {
                        listener.apply(fo, gbrg, proxy);
                    } catch (Exception ex) {
                        LOG.log(Level.SEVERE, "Notifying " + fo, ex);
                    }
                }
            }
            Document doc = src.getDocument(false);
            if (doc != null && documentListeners.containsKey(doc)) {
                for (TriConsumer<? super Document, ? super GrammarRunResult<?>, ? super ParseTreeProxy> listener : documentListeners.get(doc)) {
                    try {
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
        return withListeners(mimeType, false, arl -> {
            arl.onReparse(src, gbrg, proxy);
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
}

/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.highlighting;

import com.mastfrog.util.collections.CollectionUtils;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.text.Document;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 * There may be any number of registered highlighters for a given mime type; we
 * do not really want every single one of them to grab the parser manager lock
 * and pound on the parser infrastructure on every file change (yes, Source
 * caches results, but it is still not cheap) - so this allows all of the highlighters
 * for a particular document to react to a document change by enqueueing themselves
 * to receive the result of the next parse and ensuring that parse happens.
 *
 * @author Tim Boudreau
 */
final class ParseCoalescer {

    private static final int HIGHLIGHTING_PER_MIME_TYPE_THREADS = 5;
    private static final int DELAY = 370;
    private final Map<Document, Set<GeneralHighlighter<?>>> pending
            = CollectionUtils.concurrentSupplierMap(() -> {
                return ConcurrentHashMap.newKeySet(5);
            });
    private final Map<Document, RequestProcessor.Task> tasks = new WeakHashMap<>();
    private final Map<String, RequestProcessor> threadPoolForMimeType
            = new ConcurrentHashMap<>();

    private static final ParseCoalescer INSTANCE = new ParseCoalescer();

    static ParseCoalescer getDefault() {
        return INSTANCE;
    }

    void enqueueParse(Document doc, GeneralHighlighter<?> hl) {
        Set<GeneralHighlighter<?>> awaiting = pending.get(doc);
        awaiting.add(hl);
        RequestProcessor.Task delayedTask = tasks.computeIfAbsent(doc, d -> {
            return threadPoolFor(doc).create(new UTask(doc));
        });
        delayedTask.schedule(DELAY);
    }

    RequestProcessor threadPoolFor(Document doc) {
        String mimeType = NbEditorUtilities.getMimeType(doc);
        return threadPoolFor(mimeType);
    }

    RequestProcessor threadPoolFor(String mimeType) {
        return threadPoolForMimeType.computeIfAbsent(mimeType, mt -> new RequestProcessor(mt + "-highlight",
                HIGHLIGHTING_PER_MIME_TYPE_THREADS, false));
    }

    class UTask extends UserTask implements Runnable {

        private final Reference<Document> docRef;
        private final AtomicBoolean enqueued = new AtomicBoolean();
        private final int documentIdHash;

        UTask(Document doc) {
            this.docRef = new WeakReference<>(doc);
            documentIdHash = System.identityHashCode(doc);
        }

        @Override
        public void run(ResultIterator ri) throws Exception {
            try {
                Document doc = ri.getSnapshot().getSource().getDocument(false);
                Parser.Result result = ri.getParserResult();
                Extraction ext = ExtractionParserResult.extraction(result);
                if (ext != null) {
                    Set<GeneralHighlighter<?>> hlsx = pending.get(doc);
                    for (Set<GeneralHighlighter<?>> hls = pending.get(doc); !hls.isEmpty(); hls = pending.get(doc)) {
                        Set<GeneralHighlighter<?>> toNotify;
                        synchronized (hls) {
                            toNotify = new HashSet<>(hls);
                            hls.clear();
                        }
                        for (GeneralHighlighter<?> hl : toNotify) {
                            hl.refresh(doc, ext);
                        }
                    }
                }
            } finally {
                enqueued.set(false);
            }
        }

        volatile Future<?> lastTask;

        @Override
        public void run() {
            Document doc = docRef.get();
            if (doc != null && enqueued.compareAndSet(false, true)) {
                Source src = Source.create(doc);
                Future<?> lt = lastTask;
                try {
                    if (lt != null) {
//                        lt.cancel(false);
                    }
                    lastTask = ParserManager.parseWhenScanFinished(Collections.singleton(src), this);
                } catch (ParseException ex) {
                    Exceptions.printStackTrace(ex);
                }
            } else if (doc != null && enqueued.get()) {
                Future<?> lt = lastTask;
                if (lt != null && lt.isCancelled()) {
                    lastTask = null;
                    enqueued.set(false);
                    run();
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || !(o instanceof UTask)) {
                return false;
            } else {
                UTask ut = (UTask) o;
                return ut.documentIdHash == documentIdHash;
            }
        }

        @Override
        public int hashCode() {
            return documentIdHash;
        }

        public String toString() {
            return "UTask(" + docRef.get() + ")";
        }
    }
}

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

import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.collections.MapFactories;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.text.Document;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 * There may be any number of registered highlighters for a given mime type; we
 * do not really want every single one of them to grab the parser manager lock
 * and pound on the parser infrastructure on every file change (yes, Source
 * caches results, but it is still not cheap) - so this allows all of the
 * highlighters for a particular document to react to a document change by
 * enqueueing themselves to receive the result of the next parse and ensuring
 * that parse happens.
 *
 * @author Tim Boudreau
 */
final class ParseCoalescer {

    private static final int HIGHLIGHTING_PER_MIME_TYPE_THREADS = 1;
    private final Map<Document, RunReparse> tasks = MapFactories.WEAK.createMap(12, true);
    private final Map<String, RequestProcessor> threadPoolForMimeType
            = new ConcurrentHashMap<>();

    private static final ParseCoalescer INSTANCE = new ParseCoalescer();

    static ParseCoalescer getDefault() {
        return INSTANCE;
    }

    void enqueueParse(Document doc, GeneralHighlighter<?> hl) {
        // Need to make sure we get out of the way of
        // the current document event
        RunReparse runner = tasks.computeIfAbsent(doc, d -> {
            RequestProcessor proc = threadPoolFor(doc);
            return new RunReparse(d, proc);
        });
        runner.add(hl);
    }

    private static final class RunReparse implements Runnable {

        private final AtomicLinkedQueue<GeneralHighlighter<?>> queue = new AtomicLinkedQueue<>();
        private final WeakReference<Document> docRef;
        private final RequestProcessor.Task task;
        private static final int DELAY = 200;

        @SuppressWarnings("LeakingThisInConstructor")
        RunReparse(Document doc, RequestProcessor proc) {
            docRef = new WeakReference<>(doc);
            task = proc.create(this);
        }

        void add(GeneralHighlighter<?> hl) {
            boolean empty = queue.isEmpty();
            queue.add(hl);
            task.schedule(DELAY);
        }

        @Override
        public void run() {
            Document doc = docRef.get();
            if (doc == null) {
                return;
            }
            try {
                Extraction ext = NbAntlrUtils.parseImmediately(doc);
                Set<GeneralHighlighter<?>> enqueued;
                do {
                    enqueued = new LinkedHashSet<>();
                    queue.drain(enqueued::add);
                    if (!enqueued.isEmpty()) {
                        int ct = 0;
                        while (ext == null || ext.isSourceProbablyModifiedSinceCreation()) {
                            Extraction nue = NbAntlrUtils.extractionFor(doc);
                            if (nue == ext) {
                                break;
                            }
                            if ((ext == null && nue != null) || (nue.age() < ext.age())) {
                                ext = nue;
                                break;
                            }
                            if (nue != null) {
                                break;
                            }
                            if (ct > 20) {
                                return;
                            }
                        }
                        if (ext == null) {
                            queue.addAll(enqueued);
                            task.schedule(DELAY * 5);
                            return;
                        }
                        for (GeneralHighlighter<?> hl : enqueued) {
                            hl.refresh(doc, ext);
                        }
                    }
                } while (!enqueued.isEmpty());
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    RequestProcessor threadPoolFor(Document doc) {
        String mimeType = NbEditorUtilities.getMimeType(doc);
        return threadPoolFor(mimeType);
    }

    RequestProcessor threadPoolFor(String mimeType) {
        return threadPoolForMimeType.computeIfAbsent(mimeType, mt -> new RequestProcessor(mt + "-highlight",
                HIGHLIGHTING_PER_MIME_TYPE_THREADS, false));
    }
}

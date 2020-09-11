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
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.text.Document;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.editor.NbEditorUtilities;
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

    private static final int HIGHLIGHTING_PER_MIME_TYPE_THREADS = 5;
    private final Map<Document, RunReparse> tasks = new WeakHashMap<>();
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
        private final AtomicBoolean pending = new AtomicBoolean();
        private final WeakReference<Document> docRef;
        private final RequestProcessor.Task task;
        private static final int DELAY = 100;

        @SuppressWarnings("LeakingThisInConstructor")
        RunReparse(Document doc, RequestProcessor proc) {
            docRef = new WeakReference<>(doc);
            task = proc.create(this);
        }

        void add(GeneralHighlighter<?> hl) {
            synchronized (this) {
                queue.add(hl);
            }
            if (pending.compareAndSet(false, true)) {
                task.schedule(DELAY);
            }
        }

        @Override
        public void run() {
            try {
                Set<GeneralHighlighter<?>> enqueued;
                do {
                    enqueued = new LinkedHashSet<>();
                    queue.drain(enqueued::add);
                    if (!enqueued.isEmpty()) {
                        Document doc = docRef.get();
                        if (doc != null) {
                            Extraction ext = NbAntlrUtils.extractionFor(doc);
                            for (GeneralHighlighter<?> hl : enqueued) {
                                hl.refresh(doc, ext);
                            }
                        }
                    }
                } while (!enqueued.isEmpty());
            } finally {
                pending.set(false);
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

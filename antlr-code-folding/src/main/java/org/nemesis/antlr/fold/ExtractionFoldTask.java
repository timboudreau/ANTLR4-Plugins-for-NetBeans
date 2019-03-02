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
package org.nemesis.antlr.fold;

import java.awt.EventQueue;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserResultTask;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.spi.editor.fold.FoldInfo;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class ExtractionFoldTask<T> extends UserTask {

    private final AtomicLong version = new AtomicLong(0);
    private final RegionsKey<T> key;
    private final SemanticRegionToFoldConverter<T> converter;
    private static final Collection<Reference<ExtractionElementsFoldManager>> managers = new ArrayList<>(2);
    private static final Logger LOG = Logger.getLogger(ExtractionFoldTask.class.getName());
    static {
        LOG.setLevel(Level.ALL);
    }

    ExtractionFoldTask(RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
        this.key = key;
        this.converter = converter;
        LOG.log(Level.FINE, "Create an ExtractionFoldTask for {0}", key);
    }

    @Override
    public String toString() {
        return "ExtractionFoldTask{" + key + ", " + version.get() + "}";
    }

    @Override
    public void run(ResultIterator resultIterator) throws Exception {
        try {
            if (Thread.interrupted()) {
                return;
            }
            // This is where the parse is triggered, so test again after
            Parser.Result res = resultIterator.getParserResult();
            if (Thread.interrupted()) {
                return;
            }
            if (res instanceof ExtractionParserResult) {
                Extraction extraction = ((ExtractionParserResult) res).extraction();
                run(extraction);
            }
        } catch (ParseException e) {
            throw new IllegalStateException("Parse failed for folds", e);
        }
    }

    void invalidate() {
        version.incrementAndGet();
    }


    synchronized void setElementFoldManager(ExtractionElementsFoldManager manager, FileObject file) {
        LOG.log(Level.FINE, "set element fold manager for {0} to {1}", new Object[] {file, manager});
        if (file == null) {
            for (Iterator<Reference<ExtractionElementsFoldManager>> it = managers.iterator(); it.hasNext();) {
                Reference<ExtractionElementsFoldManager> ref = it.next();
                ExtractionElementsFoldManager fm = ref.get();
                if (fm == null || fm == manager) {
                    LOG.log(Level.FINER, "Encountered gc'd existing manager {0} while looking for manager for {1}",
                            new Object[] {ref, file.getPath()});
                    it.remove();
                    break;
                }
            }
        } else {
            LoggableWeakReference<ExtractionElementsFoldManager> ref = new LoggableWeakReference<>(manager, file);
            LOG.log(Level.FINEST, "Add manager {0}", ref);
            managers.add(ref);
            ExtractionFoldRefresher.getDefault().scheduleRefresh(file, key, converter);
        }
    }

    SchedulerTask toSchedulerTask() {
        return new PRT();
    }

    class PRT extends ParserResultTask {

        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void run(Parser.Result result, SchedulerEvent event) {
            if (cancelled.get()) {
                LOG.log(Level.FINER, "Not runing folds as ParserResultTask for {0} due to cancellation", result);
                return;
            }
            if (result instanceof ExtractionParserResult) {
                LOG.log(Level.FINEST, "Run folds as ParserResultTask {0} for {1}", new Object[] {result, event});
                ExtractionParserResult epr = (ExtractionParserResult) result;
                ExtractionFoldTask.this.run(epr.extraction());
            } else {
                LOG.log(Level.FINE, "Did not get an ExtractionParserResult for {0}, got {1} ({2})",
                        new Object[] {key, result, result.getClass().getName()});
            }
        }

        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public Class<? extends Scheduler> getSchedulerClass() {
            return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }


    void reparse(FileObject fo) {
        if (!fo.isValid() || Thread.interrupted()) {
            return;
        }
        String mimeType = fo.getMIMEType();
        if (ParserManager.canBeParsed(mimeType)) {
            try {
                ParserManager.parse(Collections.singleton(Source.create(fo)), this);
            } catch (ParseException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private synchronized Object findLiveManagers() {
        ExtractionElementsFoldManager oneMgr = null;
        List<ExtractionElementsFoldManager> result = null;
        for (Iterator<Reference<ExtractionElementsFoldManager>> it = managers.iterator(); it.hasNext();) {
            Reference<ExtractionElementsFoldManager> ref = it.next();
            ExtractionElementsFoldManager fm = ref.get();
            if (fm == null) {
                it.remove();
                continue;
            }
            if (result != null) {
                result.add(fm);
            } else if (oneMgr != null) {
                result = new ArrayList<>(2);
                result.add(oneMgr);
                result.add(fm);
            } else {
                oneMgr = fm;
            }
        }
//        System.out.println("LIVE MANAGERS " + result + " oneMgr " + oneMgr);
        return result != null ? result : oneMgr;
    }

    boolean isRequeued(long stamp) {
        return version.get() != stamp;
    }

    private void createFolds(Extraction extraction, List<? super FoldInfo> folds, List<? super Integer> anchors) {
        SemanticRegions<T> regions = extraction.regions(key);
        LOG.log(Level.FINE, "Create folds for {0} with {1} regions", new Object[]{extraction.source(), regions.size()});
//        System.out.println("CREATE FOLDS with " + regions.size() + regions);
        for (SemanticRegion<T> region : regions) {
//            System.out.println("FOLD REGION " + region + " for " + region.key());
//            FoldInfo nue = FoldInfo.range(region.start(), region.end(), GrammarFoldType.forFoldableRegion(region.key()));
            FoldInfo nue = converter.apply(region);
            if (nue != null) {
                folds.add(nue);
            }
            anchors.add(region.start());
        }
    }

    @SuppressWarnings(value = "unchecked")
    public void run(final Extraction info) {
        LOG.log(Level.FINE, "Run folds for extraction of {0}", info.source());
        Object mgrs = findLiveManagers();
        if (mgrs == null) {
            LOG.log(Level.WARNING, "No live managers for {0} from {1}", new Object[] {info.source(), this});
            // WTF?
//            ExtractionElementsFoldManager mgr = new ExtractionElementsFoldManager(this.key, this.converter);
//            managers.add(new WeakReference<>(mgr));
//            mgrs = mgr;
            return;
        }
        long startTime = System.currentTimeMillis();
        final Optional<Document> docOpt = info.source().lookup(Document.class);
        if (!docOpt.isPresent() || Thread.interrupted()) {
            LOG.log(Level.WARNING, "No document available for {0} from {1}", new Object[] {info.source(), this});
            return;
        }
        Document doc = docOpt.get();
        List<FoldInfo> folds = new ArrayList<>();
        List<Integer> anchors = new ArrayList<>();
        createFolds(info, folds, anchors);
        final long stamp = version.get();
        final Object x = mgrs;
        if (mgrs instanceof ExtractionElementsFoldManager) {
            EventQueue.invokeLater(new CommitFolds(doc, folds, anchors, version, stamp, (ExtractionElementsFoldManager) mgrs));
        } else {
            EventQueue.invokeLater(new Runnable() {
                Collection<ExtractionElementsFoldManager> jefms = (Collection<ExtractionElementsFoldManager>) x;

                public void run() {
                    LOG.log(Level.FINER, "Commit {0} folds for {1}", new Object[] {jefms.size(), info.source()});
                    for (ExtractionElementsFoldManager jefm : jefms) {
                        new CommitFolds(doc, folds, anchors, version, stamp, jefm).run();
                    }
                }
            });
        }
        long endTime = System.currentTimeMillis();
        Logger.getLogger("TIMER").log(Level.FINE, "AFolds - 1",
                new Object[]{
                    info.source(),
                    endTime - startTime
                });
    }
}

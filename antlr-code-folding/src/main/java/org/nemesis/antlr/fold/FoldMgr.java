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
package org.nemesis.antlr.fold;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import static org.nemesis.antlr.fold.FoldUtils.opStringSupplier;
import static org.nemesis.antlr.fold.FoldUtils.opToString;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.ExtractionKey;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.fold.FoldHierarchyTransaction;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldOperation;

/**
 *
 * @author Tim Boudreau
 */
final class FoldMgr<P extends Parser.Result & ExtractionParserResult> implements FoldManager {

    private static final Logger LOG = Logger.getLogger(FoldMgr.class.getName());
    private FoldOperation operation;
    private final FoldUpdater<?, ?, ?, ?> updater;
    private FoldTask task;
    private final AtomicBoolean first = new AtomicBoolean(true);

    <K extends ExtractionKey<T>, T, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<I>> FoldMgr(K key, KeyToFoldConverter<? super I> converter,
            Function<Extraction, C> extractionFetcher) {
        updater = new FoldUpdater<>(key, converter, extractionFetcher);
    }

    @Override
    public String toString() {
        return "FoldMgr{" + updater.toString() + ", first=" + first.get() + ", op=" + opToString(operation) + "}";
    }

    @Override
    public void init(FoldOperation operation) {
        this.operation = operation;
        LOG.log(Level.FINE, "Initialize {0} with {1}", new Object[]{this, opStringSupplier(operation)});
    }

    @Override
    public synchronized void initFolds(FoldHierarchyTransaction transaction) {
        task = FoldTasks.getDefault().forOperation(operation, true);
        LOG.log(Level.FINE, "initFolds on {0} with {1} got task {2}", new Object[]{this, transaction, task});
        task.add(this);
    }

    Runnable withNewExtraction(Extraction extraction) {
        FoldTask currTask;
        synchronized (this) {
            currTask = this.task;
        }
        if (operation != null && currTask != null) {
            try {
                return updater.updateFolds(operation, extraction, first.compareAndSet(false, true), currTask::version);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Updater threw exception: " + updater, ex);
            }
        } else {
            LOG.log(Level.FINE, "Tried to run {0} with extraction {1}, but "
                    + "operation or task is null",
                    new Object[]{this, extraction.logString()});
        }
        return null;
    }

    private synchronized void invalidate() {
        if (task != null) {
            LOG.log(Level.FINEST, "Invalidate folds on {0}", this);
            task.invalidate();
        }
    }

    @Override
    public synchronized void release() {
        if (task != null) {
            LOG.log(Level.FINEST, "Release {0}", this);
            task.remove(this);
        }
    }

    @Override
    public void insertUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
        invalidate();
    }

    @Override
    public void removeUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
        invalidate();
    }

    @Override
    public void changedUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
    }

    @Override
    public void removeEmptyNotify(Fold emptyFold) {
        removeDamagedNotify(emptyFold);
    }

    @Override
    public void removeDamagedNotify(Fold damagedFold) {
    }

    @Override
    public void expandNotify(Fold expandedFold) {
    }

}

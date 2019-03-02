package org.nemesis.antlr.fold.revised;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
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
public class FoldMgr<P extends Parser.Result & ExtractionParserResult> implements FoldManager {

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
    public void init(FoldOperation operation) {
        this.operation = operation;
        LOG.log(Level.FINE, "Initialize {0} with {1}", new Object[]{this, operation});
    }

    @Override
    public synchronized void initFolds(FoldHierarchyTransaction transaction) {
        task = FoldTasks.getDefault().forOperation(operation, true);
        task.add(this);
    }

    Runnable withNewExtraction(Extraction extraction) {
        FoldTask task;
        synchronized(this) {
            task = this.task;
        }
        if (operation != null && task != null) {
            try {
                return updater.updateFolds(operation, extraction, first.compareAndSet(false, true), task::version);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Updater threw exception: " + updater, ex);
            }
        }
        return null;
    }

    private synchronized void invalidate() {
        if (task != null) {
            task.invalidate();
        }
    }

    @Override
    public synchronized void release() {
        if (task != null) {
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

    public void removeEmptyNotify(Fold emptyFold) {
        removeDamagedNotify(emptyFold);
    }

    public void removeDamagedNotify(Fold damagedFold) {
    }

    public void expandNotify(Fold expandedFold) {
    }

}

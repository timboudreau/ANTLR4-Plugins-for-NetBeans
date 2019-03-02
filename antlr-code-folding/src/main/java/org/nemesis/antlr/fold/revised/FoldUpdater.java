/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.fold.revised;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import static org.nemesis.antlr.fold.revised.FoldTasks.documentFor;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;
import org.netbeans.spi.editor.fold.FoldInfo;
import org.netbeans.spi.editor.fold.FoldOperation;

/**
 *
 * @author Tim Boudreau
 */
public class FoldUpdater<K extends ExtractionKey<T>, T, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<I>> {

    private final K key;
    private final KeyToFoldConverter<? super I> converter;
    private final Function<Extraction, C> extractionFetcher;
    private static final Logger LOG = Logger.getLogger(FoldUpdater.class.getName());

    FoldUpdater(K key, KeyToFoldConverter<? super I> converter,
            Function<Extraction, C> extractionFetcher) {
        this.key = key;
        this.converter = converter;
        this.extractionFetcher = extractionFetcher;
    }

    @Override
    public String toString() {
        return key.toString();
    }

    Runnable updateFolds(FoldOperation operation, Extraction extraction, boolean first, LongSupplier version) {
        LOG.log(Level.FINE, "Run folds for extraction of {0}", extraction.source());
        C collection = extractionFetcher.apply(extraction);
        long startTime = System.currentTimeMillis();

        Document doc = documentFor(operation);
        if (doc == null) {
            return null;
        }

        List<FoldInfo> folds = new ArrayList<>();
        List<Integer> anchors = new ArrayList<>();
        createFolds(extraction, collection, folds, anchors);
        final long stamp = version.getAsLong();
        Runnable result = new FoldCommitter(doc, folds, anchors, version, stamp, operation, first);
        
        long endTime = System.currentTimeMillis();
        Logger.getLogger("TIMER").log(Level.FINE, "AntlrFolds - 1",
                new Object[]{
                    extraction.source(),
                    endTime - startTime
                });
        return result;
    }

    private void createFolds(Extraction extraction, C regions, List<? super FoldInfo> folds, List<? super Integer> anchors) {
        LOG.log(Level.FINE, "Create folds for {0} with {1} regions", new Object[]{extraction.source(), regions.size()});
        for (I region : regions.asIterable()) {
            FoldInfo nue = converter.apply(region);
            if (nue != null) {
                folds.add(nue);
            }
            anchors.add(region.start());
        }
    }
}

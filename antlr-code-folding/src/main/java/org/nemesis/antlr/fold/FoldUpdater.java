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

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.util.collections.IntList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import static org.nemesis.antlr.fold.FoldUtils.documentFor;
import static org.nemesis.antlr.fold.FoldUtils.opStringSupplier;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;
import org.netbeans.spi.editor.fold.FoldInfo;
import org.netbeans.spi.editor.fold.FoldOperation;

/**
 *
 * @author Tim Boudreau
 */
class FoldUpdater<K extends ExtractionKey<T>, T, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<I>> {

    private final K key;
    private final KeyToFoldConverter<? super I> converter;
    private final Function<Extraction, C> extractionFetcher;
    private static final Logger LOG = Logger.getLogger(FoldUpdater.class.getName());
    private volatile String lastTokensHash;

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
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "Run folds {0} first={1} on {2} for extraction of {3} version {4}", new Object[]{
                key,
                first,
                opStringSupplier(operation),
                extraction.source(),
                version.getAsLong()
            });
        }
        String hash = extraction.tokensHash();
        if (Objects.equals(lastTokensHash, hash)) {
            LOG.log(Level.FINER, "Folds already computed for {0} with hash {1}. Skipping.",
                    new Object[]{extraction.source(), hash});
            return null;
        }
        lastTokensHash = hash;
        C collection = extractionFetcher.apply(extraction);
        long startTime = System.currentTimeMillis();

        Document doc = documentFor(operation);
        if (doc == null) {
            return null;
        }

        List<FoldInfo> folds = new ArrayList<>();
        IntList anchors = IntList.create(64);
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

    private void createFolds(Extraction extraction, C regions, List<? super FoldInfo> folds, IntList anchors) {
        LOG.log(Level.FINE, "Create folds for {0} with {1} regions", new Object[]{extraction.source(), regions.size()});
        Set<IntRange<? extends IntRange>> ranges = new HashSet<>();
        for (I region : regions.asIterable()) {
            IntRange<? extends IntRange<?>> plainRange = Range.of(region.start(), region.size());
            if (ranges.contains(plainRange)) {
                continue;
            }
            ranges.add(plainRange);
            FoldInfo nue = converter.apply(region);
            if (nue != null) {
                folds.add(nue);
            }
            anchors.add(region.start());
        }
    }
}

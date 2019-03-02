/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.fold.revised;

import java.util.function.Function;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.IndexAddressable.IndexAddressableItem;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.fold.FoldInfo;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldManagerFactory;

/**
 *
 * @author Tim Boudreau
 */
public class ManagerFactory<K extends ExtractionKey<T>, T, I extends IndexAddressableItem, C extends IndexAddressable<I>, P extends Parser.Result & ExtractionParserResult> implements FoldManagerFactory {

    private final K key;
    private final KeyToFoldConverter<? super I> converter;
    private final Function<Extraction, C> extractionFetcher;

    ManagerFactory(K key, KeyToFoldConverter<? super I> converter,
            Function<Extraction, C> extractionFetcher) {
        this.key = key;
        this.converter = converter;
        this.extractionFetcher = extractionFetcher;
    }

    @Override
    public FoldManager createFoldManager() {
        return new FoldMgr<>(key, converter, extractionFetcher);
    }

    public static TaskFactory taskFactory() {
        return FoldTaskFactory.INSTANCE;
    }

    public static <T, P extends Parser.Result & ExtractionParserResult> ManagerFactory<RegionsKey<T>, T, SemanticRegion<T>, SemanticRegions<T>, P> create(RegionsKey<T> key, KeyToFoldConverter<SemanticRegion<T>> converter) {
        if (converter == null) {
            converter = new DefaultKeyToFoldConverter<>(FoldType.CODE_BLOCK);
        }
        Function<Extraction, SemanticRegions<T>> fetcher = (extraction) -> {
            return extraction.regions(key);
        };

        return new ManagerFactory<>(key, converter, fetcher);
    }

    public static <T extends Enum<T>, P extends Parser.Result & ExtractionParserResult> ManagerFactory<NamedRegionKey<T>, T, NamedSemanticRegion<T>, NamedSemanticRegions<T>, P> create(NamedRegionKey<T> key, KeyToFoldConverter<NamedSemanticRegion<T>> converter) {
        if (converter == null) {
            converter = new DefaultKeyToFoldConverter<>(FoldType.CODE_BLOCK);
        }
        Function<Extraction, NamedSemanticRegions<T>> fetcher = (extraction) -> {
            return extraction.namedRegions(key);
        };

        return new ManagerFactory<>(key, converter, fetcher);
    }

    public static <T, P extends Parser.Result & ExtractionParserResult> ManagerFactory<RegionsKey<T>, T, SemanticRegion<T>, SemanticRegions<T>, P> create(RegionsKey<T> key) {
        return create(key, FoldType.CODE_BLOCK);
    }

    public static <T, P extends Parser.Result & ExtractionParserResult> ManagerFactory<RegionsKey<T>, T, SemanticRegion<T>, SemanticRegions<T>, P> create(RegionsKey<T> key, FoldType foldType) {
        return create(key, new DefaultKeyToFoldConverter<>(foldType));
    }

    public static <T extends Enum<T>, P extends Parser.Result & ExtractionParserResult> ManagerFactory<NamedRegionKey<T>, T, NamedSemanticRegion<T>, NamedSemanticRegions<T>, P> create(NamedRegionKey<T> key) {
        return create(key, FoldType.CODE_BLOCK);
    }

    public static <T extends Enum<T>, P extends Parser.Result & ExtractionParserResult> ManagerFactory<NamedRegionKey<T>, T, NamedSemanticRegion<T>, NamedSemanticRegions<T>, P> create(NamedRegionKey<T> key, FoldType foldType) {
        return create(key, new DefaultKeyToFoldConverter<>(foldType));
    }

    private static final class DefaultKeyToFoldConverter<T extends IndexAddressableItem> implements KeyToFoldConverter<T> {

        private final FoldType foldType;

        DefaultKeyToFoldConverter(FoldType foldType) {
            this.foldType = foldType;
        }

        @Override
        public FoldInfo apply(T t) {
            return FoldInfo.range(t.start(), t.end(), foldType);
        }

        public String toString() {
            return "DefaultKeyToFoldConverter{" + foldType.toString() + '}';
        }

    }
}

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

import java.util.function.Function;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.IndexAddressable.IndexAddressableItem;
import com.mastfrog.abstractions.Named;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.api.editor.fold.FoldTemplate;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.fold.FoldInfo;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldManagerFactory;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
class ManagerFactory<K extends ExtractionKey<T>, T, I extends IndexAddressableItem, C extends IndexAddressable<I>, P extends Parser.Result & ExtractionParserResult> implements FoldManagerFactory {

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

    static TaskFactory taskFactory() {
        return FoldTaskFactory.INSTANCE;
    }

    static <T, P extends Parser.Result & ExtractionParserResult> ManagerFactory<RegionsKey<T>, T, SemanticRegion<T>, SemanticRegions<T>, P> create(RegionsKey<T> key, KeyToFoldConverter<SemanticRegion<T>> converter) {
        if (converter == null) {
            converter = new DefaultKeyToFoldConverter<>(FoldType.CODE_BLOCK);
        }
        Function<Extraction, SemanticRegions<T>> fetcher = (extraction) -> {
            return extraction.regions(key);
        };
        return new ManagerFactory<>(key, converter, fetcher);
    }

    static <T extends Enum<T>, P extends Parser.Result & ExtractionParserResult> ManagerFactory<NamedRegionKey<T>, T, NamedSemanticRegion<T>, NamedSemanticRegions<T>, P> create(NamedRegionKey<T> key, KeyToFoldConverter<NamedSemanticRegion<T>> converter) {
        if (converter == null) {
            converter = new DefaultKeyToFoldConverter<>(FoldType.MEMBER);
        }
        Function<Extraction, NamedSemanticRegions<T>> fetcher = (extraction) -> {
            return extraction.namedRegions(key);
        };
        return new ManagerFactory<>(key, converter, fetcher);
    }

    static <T, P extends Parser.Result & ExtractionParserResult> ManagerFactory<RegionsKey<T>, T, SemanticRegion<T>, SemanticRegions<T>, P> create(RegionsKey<T> key) {
        return ManagerFactory.create(key, FoldType.CODE_BLOCK);
    }

    static <T, P extends Parser.Result & ExtractionParserResult> ManagerFactory<RegionsKey<T>, T, SemanticRegion<T>, SemanticRegions<T>, P> create(RegionsKey<T> key, FoldType foldType) {
        return ManagerFactory.create(key, new DefaultKeyToFoldConverter<>(foldType));
    }

    static <T extends Enum<T>, P extends Parser.Result & ExtractionParserResult> ManagerFactory<NamedRegionKey<T>, T, NamedSemanticRegion<T>, NamedSemanticRegions<T>, P> create(NamedRegionKey<T> key) {
        return ManagerFactory.create(key, FoldType.MEMBER);
    }

    static <T extends Enum<T>, P extends Parser.Result & ExtractionParserResult> ManagerFactory<NamedRegionKey<T>, T, NamedSemanticRegion<T>, NamedSemanticRegions<T>, P> create(NamedRegionKey<T> key, FoldType foldType) {
        return ManagerFactory.create(key, new DefaultKeyToFoldConverter<>(foldType));
    }

    static final class DefaultKeyToFoldConverter<T extends IndexAddressableItem> implements KeyToFoldConverter<T> {

        private final FoldType foldType;

        DefaultKeyToFoldConverter(FoldType foldType) {
            this.foldType = foldType;
        }

        @Override
        @Messages({"licenseComment=License Comment"})
        public FoldInfo apply(T t) {
            FoldType actualType = foldType;
            if (foldType == FoldType.COMMENT) {
                if (t.start() < 5) {
                    actualType = FoldType.INITIAL_COMMENT;
                    FoldTemplate ft = new FoldTemplate(actualType.getTemplate().getGuardedStart(), actualType.getTemplate().getGuardedEnd(),
                            Bundle.licenseComment());
                    return FoldInfo.range(t.start(), t.end(), actualType.override(Bundle.licenseComment(), ft));
                }
            }
            if (t instanceof Named) {
                String name = ((Named) t).name();
                FoldTemplate ft = new FoldTemplate(foldType.getTemplate().getGuardedStart(), foldType.getTemplate().getGuardedEnd(), name);
                actualType = foldType.derive(name, name, ft);
            } else if (t instanceof SemanticRegion<?>) {
                Object key = ((SemanticRegion<?>) t).key();
                if (key instanceof String) {
                    String name = ((Named) t).name();
                    FoldTemplate ft = new FoldTemplate(foldType.getTemplate().getGuardedStart(), foldType.getTemplate().getGuardedEnd(), name);
                    actualType = foldType.override(name, ft);
                } else if (key instanceof Named) {
                    String name = ((Named) t).name();
                    FoldTemplate ft = new FoldTemplate(foldType.getTemplate().getGuardedStart(), foldType.getTemplate().getGuardedEnd(), name);
//                    actualType = foldType.derive(name, name, ft);
                    actualType = foldType.override(name, ft);
                } else if (key instanceof FoldableRegion) {
                    FoldableRegion reg = (FoldableRegion) key;
                    switch (reg.kind) {
                        case COMMENT:
                            if (((SemanticRegion<?>) t).start() < 50) {
                                actualType = FoldType.INITIAL_COMMENT;
                                FoldTemplate ft = new FoldTemplate(actualType.getTemplate().getGuardedStart(), actualType.getTemplate().getGuardedEnd(),
                                        Bundle.licenseComment());
                                return FoldInfo.range(t.start(), t.end(), actualType.override(Bundle.licenseComment(), ft));
                            } else {
                                actualType = FoldType.COMMENT;
                            }
                            break;
                        default:
                            String text = reg.text;
                            if (text != null) {
                                FoldTemplate ft = new FoldTemplate(foldType.getTemplate().getGuardedStart(), foldType.getTemplate().getGuardedEnd(), text);
                                actualType = foldType.derive(text, text, ft);
                            }
                    }
                }
            }
            return FoldInfo.range(t.start(), t.end(), actualType);
        }

        @Override
        public String toString() {
            return "DefaultKeyToFoldConverter{" + foldType.toString() + '}';
        }
    }
}

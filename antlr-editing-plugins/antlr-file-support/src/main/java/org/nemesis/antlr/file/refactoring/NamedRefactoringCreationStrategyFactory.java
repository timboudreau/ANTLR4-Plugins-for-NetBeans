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
package org.nemesis.antlr.file.refactoring;

import com.mastfrog.function.TriFunction;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Optional;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;

/**
 *
 * @author Tim Boudreau
 */
abstract class NamedRefactoringCreationStrategyFactory<R extends AbstractRefactoring, T extends Enum<T>> implements TriFunction<R, Extraction, PositionBounds, RefactoringPlugin> {

    final Class<R> type;
    final NamedExtractionKey<T> key;
    final CharFilter filter;

    NamedRefactoringCreationStrategyFactory(Class<R> type, NamedExtractionKey<T> key, CharFilter filter) {
        this.type = type;
        this.key = key;
        this.filter = filter == null ? CharFilter.ALL : filter;
    }

    @SuppressWarnings("unchecked")
    static <T extends Enum<T>> NamedSemanticRegion<T> find(NamedExtractionKey<T> key, Extraction extraction, PositionBounds bounds) {
        NamedRegionKey<T> namedKey = null;
        NameReferenceSetKey<T> refKey = null;
        if (notNull("key", key) instanceof NameReferenceSetKey<?>) {
            refKey = (NameReferenceSetKey<T>) key;
            namedKey = refKey.referencing();
        } else if (key instanceof NamedRegionKey<?>) {
            namedKey = (NamedRegionKey<T>) key;
        }
        assert namedKey != null : "Unknown named key type: " + key;
        NamedSemanticRegions<T> regions = extraction.namedRegions(namedKey);
        int pos = bounds.getBegin().getOffset();
        NamedSemanticRegion region = regions.at(pos);
        if (region == null && refKey != null) {
            NamedRegionReferenceSets<T> sets = extraction.nameReferences(refKey);
            NamedSemanticRegionReference<T> ref = sets.at(pos);
            if (ref != null) {
                region = ref.referencing();
            }
        }
        return region;
    }

    @Override
    public RefactoringPlugin apply(R refactoring, Extraction extraction, PositionBounds bounds) {
        Optional<FileObject> fileOpt = extraction.source().lookup(FileObject.class);
        if (!fileOpt.isPresent()) {
            return null;
        }
        FileObject file = fileOpt.get();
        NamedSemanticRegion<T> item = find(key, extraction, bounds);
        if (item != null) {
            return createRefactoringPlugin(key, refactoring, extraction,
                    file, item, filter);
        }
        return null;
    }

    protected abstract RefactoringPlugin createRefactoringPlugin(NamedExtractionKey<T> key,
            R refactoring, Extraction extraction, FileObject file,
            NamedSemanticRegion<T> item, CharFilter filter);

    public static <R extends AbstractRefactoring, T extends Enum<T>>
            RefactoringPluginGenerator<R> create(Class<R> refactoringType,
                    NamedCreationStrategy<R, T> strategy,
                    NamedExtractionKey<T> key, CharFilter filter) {
        return new StrategyNamedRefactoringCreationStrategyFactory<>(
                refactoringType, strategy, key, filter).toPluginGenerator();
    }

    RefactoringPluginGenerator<R> toPluginGenerator() {
        return new RefactoringPluginGenerator<>(type, this);
    }

    private static class StrategyNamedRefactoringCreationStrategyFactory<R extends AbstractRefactoring, T extends Enum<T>>
            extends NamedRefactoringCreationStrategyFactory<R, T> {

        private final NamedCreationStrategy<R, T> strategy;

        public StrategyNamedRefactoringCreationStrategyFactory(Class<R> type,
                NamedCreationStrategy<R, T> strategy,
                NamedExtractionKey<T> key, CharFilter filter) {
            super(type, key, filter);
            this.strategy = strategy;
        }

        @Override
        protected RefactoringPlugin createRefactoringPlugin(NamedExtractionKey<T> key, R refactoring, Extraction extraction, FileObject file, NamedSemanticRegion<T> item, CharFilter filter) {
            return strategy.createRefactoringPlugin(key, refactoring, extraction, file, item, filter);
        }

        public String toString() {
            return getClass().getSimpleName() + "(" 
                    + type.getSimpleName() + " "
                    + key + " "
                    + filter + " "
                    + strategy + ")";
        }

    }

}

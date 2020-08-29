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
package org.nemesis.antlr.refactoring;

import com.mastfrog.function.TriFunction;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Optional;
import javax.swing.text.Document;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NamedExtractionKey;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;

/**
 *
 * @author Tim Boudreau
 */
abstract class NamedRefactoringCreationStrategyFactory<R extends AbstractRefactoring, T extends Enum<T>>
        extends AbstractRefactoringContext implements TriFunction<R, Extraction, PositionBounds, RefactoringPlugin> {

    final Class<R> type;
    final NamedExtractionKey<T> key;
    final CharFilter filter;

    NamedRefactoringCreationStrategyFactory(Class<R> type, NamedExtractionKey<T> key, CharFilter filter) {
        this.type = notNull("type", type);
        this.key = notNull("key", key);
        this.filter = filter == null ? CharFilter.ALL : filter;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + type.getSimpleName()
                + " for " + key + " with " + filter + ")";
    }

    @Override
    public RefactoringPlugin apply(R refactoring, Extraction extraction, PositionBounds bounds) {
        FileObject file = extraction.source().lookupOrDefault(FileObject.class, null);
        if (file == null) {
            Optional<Document> docOpt = extraction.source().lookup(Document.class);
            if (docOpt.isPresent()) {
                file = NbEditorUtilities.getFileObject(docOpt.get());
            } else {
                return null;
            }
        }
        if (file == null) {
            return null;
        }
        FindResult<T> item = findWithAttribution(file, key, extraction, bounds);
        if (item != null) {
            RefactoringPlugin result = createRefactoringPlugin(item.key(), refactoring, item.extraction(),
                    item.file(), item.region(), filter);
            logFine("{0} creates plugin {1} for {2}", this, result, item);
            return result;
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

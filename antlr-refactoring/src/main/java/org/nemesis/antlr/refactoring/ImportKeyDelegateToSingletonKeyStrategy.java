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
import com.mastfrog.util.collections.CollectionUtils;
import java.util.Optional;
import java.util.Set;
import javax.swing.text.Document;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.api.WhereUsedQuery;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;

/**
 * Does a reverse lookup from an import key to a singleton key, for applying
 * rename or where used to an import and having it get applied, under the hood,
 * to the referent of the import.
 *
 * @author Tim Boudreau
 */
final class ImportKeyDelegateToSingletonKeyStrategy<T extends Enum<T>, K, R extends AbstractRefactoring> extends AbstractRefactoringContext implements TriFunction<R, Extraction, PositionBounds, RefactoringPlugin> {

    private final SingletonKey<K> targetKey;
    private final NamedRegionKey<T> sourceKey;
    private final CharFilter filter;
    private final DelegatingRefactoringPluginCreator<K, R> delegate;

    ImportKeyDelegateToSingletonKeyStrategy(SingletonKey<K> targetKey, NamedRegionKey<T> sourceKey, CharFilter filter, DelegatingRefactoringPluginCreator<K, R> delegate) {
        this.targetKey = targetKey;
        this.sourceKey = sourceKey;
        this.filter = filter;
        this.delegate = delegate;
        System.out.println("Create impdel " + this);
    }

    static <T extends Enum<T>, K> ImportKeyDelegateToSingletonKeyStrategy<T, K, WhereUsedQuery> forWhereUsed(SingletonKey<K> targetKey, NamedRegionKey<T> sourceKey, CharFilter filter) {
        return new ImportKeyDelegateToSingletonKeyStrategy<>(targetKey, sourceKey, filter, new WhereUsedDelegatingCreator<>());
    }

    static <T extends Enum<T>, K> ImportKeyDelegateToSingletonKeyStrategy<T, K, RenameRefactoring> forRename(SingletonKey<K> targetKey, NamedRegionKey<T> sourceKey, CharFilter filter) {
        return new ImportKeyDelegateToSingletonKeyStrategy<>(targetKey, sourceKey, filter, new RenameDelegatingCreator<>());
    }

    @Override
    public String toString() {
        return "ImportKeyDelegateToSingletonKeyStrategy(" + targetKey + " -> " + sourceKey + " -> " + filter + " with " + delegate + ")";
    }

    @Override
    public RefactoringPlugin apply(R query, Extraction extraction, PositionBounds bounds) {
        Optional<FileObject> foOpt = extraction.source().lookup(FileObject.class);
        System.out.println("Apply " + query.getClass().getName() + " to " + extraction.source() + " at " + bounds);
        if (foOpt.isPresent()) {
            FindResult<T> region = super.find(foOpt.get(), sourceKey, extraction, bounds);
            System.out.println("FOUND REGION for " + sourceKey + "? " + region);
            if (region != null && region.region() != null) {
                ImportFinder impFinder = ImportFinder.forKeys(sourceKey);
                System.out.println("  have import finder " + impFinder);
                Set<GrammarSource<?>> thingsWeImport = impFinder.allImports(extraction, CollectionUtils.blackHoleSet());
                System.out.println("   we import " + thingsWeImport);
                for (GrammarSource<?> src : thingsWeImport) {
                    System.out.println("   Check '" + src.name() + "' and '" + region.region().name() + "'");
                    if (region.region().name().equals(src.name())) {
                        Optional<Document> docOpt = src.lookup(Document.class);
                        Extraction targetExtraction = null;
                        try {
                            if (docOpt.isPresent()) {
                                targetExtraction = parse(docOpt.get());
                                System.out.println("  parse by doc " + targetExtraction.source());
                            } else {
                                Optional<FileObject> targetFoOpt = src.lookup(FileObject.class);
                                if (targetFoOpt.isPresent()) {
                                    targetExtraction = parse(targetFoOpt.get());
                                    System.out.println("  parse by file " + targetExtraction.source());
                                }
                            }
                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        if (targetExtraction != null) {
                            System.out.println("  have a target extraction " + targetExtraction.source());
                            SingletonEncounters<K> singletons = targetExtraction.singletons(targetKey);
                            if (singletons.hasEncounter()) {
                                SingletonEncounters.SingletonEncounter<K> singleton = singletons.first();
                                System.out.println("    have singleton");
                                Optional<FileObject> tfo = src.lookup(FileObject.class);
                                if (tfo.isPresent()) {
                                    FileObject originalFile = tfo.get();
                                    System.out.println("    original file " + originalFile);
                                    return delegate.create(targetKey, query, extraction, originalFile, singleton, filter);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    static class WhereUsedDelegatingCreator<K> implements DelegatingRefactoringPluginCreator<K, WhereUsedQuery> {

        @Override
        public RefactoringPlugin create(SingletonKey<K> targetKey, WhereUsedQuery query, Extraction extraction, FileObject originalFile, SingletonEncounters.SingletonEncounter<K> singleton, CharFilter filter) {
            return new SingletonWhereUsedCreationStrategy(targetKey).createRefactoringPlugin(targetKey, query, extraction, originalFile, singleton, filter);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    static class RenameDelegatingCreator<K> implements DelegatingRefactoringPluginCreator<K, RenameRefactoring> {

        @Override
        public RefactoringPlugin create(SingletonKey<K> targetKey, RenameRefactoring query, Extraction extraction, FileObject originalFile, SingletonEncounters.SingletonEncounter<K> singleton, CharFilter filter) {
            return new RenameFileAndReferencesFromSingletonPlugin<>(targetKey, query, extraction, originalFile, singleton, filter);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    interface DelegatingRefactoringPluginCreator<K, R extends AbstractRefactoring> {

        RefactoringPlugin create(SingletonKey<K> targetKey, R query, Extraction extraction, FileObject originalFile, SingletonEncounters.SingletonEncounter<K> singleton, CharFilter filter);
    }

}

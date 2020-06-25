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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.charfilter.CharFilterBuilder;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.api.WhereUsedQuery;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;

/**
 * Builder for Antlr-based refactorings - call <code>forMimeType()</code> to get
 * an instance. The usage pattern is to wrap and delegate.
 *
 * @author Tim Boudreau
 */
public final class RefactoringsBuilder {

    private final String mimeType;
    private final Set<RefactoringPluginGenerator<?>> generators
            = new LinkedHashSet<>();

    RefactoringsBuilder(String mimeType) {
        this.mimeType = mimeType;
        for (CustomRefactoring<?> c : CustomRefactoring.registeredRefactorings(mimeType)) {
            System.out.println("FOUND CUSTOM REFACTORING FOR " + mimeType + ": " + c);
            generators.add(c.toGenerator());
        }
    }

    Set<RefactoringPluginGenerator<?>> generators() {
        return generators;
    }

    /**
     * Create a new RefactoringsBuilder for a given MIME type.
     *
     * @param mimeType The mime type
     * @return A builder
     */
    public static RefactoringsBuilder forMimeType(String mimeType) {
        return new RefactoringsBuilder(mimeType);
    }

    /**
     * Add a factory for a refactoring of any (or a custom) type.
     *
     * @param <T>
     * @param type The type
     * @param factory The factory method - this should test if it will really
     * match something it understands from the caret position or selection, and
     * if so, return a refactoring plugin.
     *
     * @return this
     */
    public <T extends AbstractRefactoring> RefactoringsBuilder add(Class<T> type, TriFunction<? super T, ? super Extraction, ? super PositionBounds, ? extends RefactoringPlugin> factory) {
        generators.add(new RefactoringPluginGenerator<>(notNull("type", type), notNull("factory", factory)));
        return this;
    }

    /**
     * Create a builder to answer rename refactorings.
     *
     * @return A builder
     */
    public RenameRefactoringBuilder rename() {
        return new RenameRefactoringBuilder(this);
    }

    /**
     * Create a builder to answer find-usages queries.
     *
     * @return
     */
    public WhereUsedRefactoringBuilder usages() {
        return new WhereUsedRefactoringBuilder(this);
    }

    /**
     * Currently does nothing, but simplifies code generation.
     *
     * @return this
     */
    public RefactoringsBuilder finished() {
        return this;
    }

    public static class RefactoringItemBuilder<T, R extends AbstractRefactoring, B extends RefactoringItemBuilder<T, R, B>>
            extends AbstractRefactoringBuilder<T, B> {

        final Class<R> type;

        RefactoringItemBuilder(Class<R> type, Function<? super B, T> converter) {
            super(converter);
            this.type = notNull("type", type);
        }
    }

    public static class KeyedRefactoringItemBuilder<X, K extends ExtractionKey<X>, T, R extends AbstractRefactoring, B extends KeyedRefactoringItemBuilder<X, K, T, R, B>>
            extends RefactoringItemBuilder<T, R, B> {

        final Class<R> type;
        final K key;

        KeyedRefactoringItemBuilder(K key, Class<R> type, Function<? super B, T> converter) {
            super(type, converter);
            this.type = notNull("type", type);
            this.key = notNull("key", key);
        }
    }

    public static final class WhereUsedRefactoringBuilder {

        private final RefactoringsBuilder builder;

        WhereUsedRefactoringBuilder(RefactoringsBuilder builder) {
            this.builder = builder;
        }

        /**
         * Find uses a region key representing an <i>import of another file</i>,
         * matching the name of the queried region against names of files this
         * file imports, then resolving the passed singleton key within that
         * file, and running the requested refactoring against that.
         *
         * @param <K>
         * @param <T>
         * @param key
         * @param asFileReferenceTo
         * @return
         */
        public <K, T extends Enum<T>> RefactoringsBuilder finding(NamedRegionKey<T> key, SingletonKey<K> asFileReferenceTo) {
            ImportKeyDelegateToSingletonKeyStrategy<T, K, WhereUsedQuery> strategy
                    = ImportKeyDelegateToSingletonKeyStrategy.forWhereUsed(notNull("asFileReferenceTo", asFileReferenceTo),
                            notNull("key", key), CharFilter.ALL /* filter does not matter for where used */);
            return builder.add(WhereUsedQuery.class, strategy);
        }

        /**
         * Find usages of a singleton key, <i>treating it as a reference to the file
         * the query was initiated in.
         *
         * @param <T>
         * @param k The key
         * @return This builder
         */
        public <T> RefactoringsBuilder finding(SingletonKey<T> k) {
            SingletonWhereUsedCreationStrategy<T> strat = new SingletonWhereUsedCreationStrategy<>(notNull("k", k));
            SingletonRefactoringFactory<WhereUsedQuery, T> f = new StrategyRefactoringFactory<>(k, null, strat);
            return builder.add(WhereUsedQuery.class, f);
        }

        public <T extends Enum<T>> RefactoringsBuilder finding(NamedRegionKey<T> k) {
            NamedCreationStrategy<WhereUsedQuery, T> c = new NamedCreationStrategyImpl<>(notNull("k", k));
            builder.generators.add(
                    NamedRefactoringCreationStrategyFactory.create(WhereUsedQuery.class, c, k, CharFilter.ALL));
            return builder;
        }

        public <T extends Enum<T>> RefactoringsBuilder finding(NameReferenceSetKey<T> k) {
            NamedCreationStrategy<WhereUsedQuery, T> c = new NamedCreationStrategyImpl<>(notNull("k", k));
            builder.generators.add(
                    NamedRefactoringCreationStrategyFactory.create(WhereUsedQuery.class, c, k, CharFilter.ALL));
            return finding(k.referencing());
        }
    }

    private static class NamedCreationStrategyImpl<T extends Enum<T>> implements NamedCreationStrategy<WhereUsedQuery, T> {

        private final NamedExtractionKey<T> k;

        public NamedCreationStrategyImpl(NamedExtractionKey<T> k) {
            this.k = k;
        }

        @Override
        public RefactoringPlugin createRefactoringPlugin(NamedExtractionKey<T> key,
                WhereUsedQuery refactoring, Extraction extraction, FileObject file,
                NamedSemanticRegion<T> item, CharFilter filter) {
            return new NamedFindUsagesPlugin<>(refactoring, extraction, file, k, item);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + k + ")";
        }
    }

    public static final class RenameRefactoringBuilder {

        private final RefactoringsBuilder builder;

        RenameRefactoringBuilder(RefactoringsBuilder builder) {
            this.builder = builder;
        }

        public <T extends Enum<T>, K> RenameViaImportBuilder<T, K> renaming(NamedRegionKey<T> key, SingletonKey<K> asFileReferenceTo) {
            return new RenameViaImportBuilder<>(builder, key, asFileReferenceTo);
        }

        public <X, B extends SingletonFileNameRefactoringItemBuilder<RefactoringsBuilder, X, B>> SingletonFileNameRefactoringItemBuilder<RefactoringsBuilder, X, B>
                renaming(SingletonKey<X> key) {
            SingletonFileNameRefactoringItemBuilder<RefactoringsBuilder, X, B> result = new SingletonFileNameRefactoringItemBuilder<>(notNull("key", key), sfnrib -> {
                return builder.add(RenameRefactoring.class,
                        new StrategyRefactoringFactory<>(
                                key,
                                sfnrib.nameFilter,
                                new RenameFileFromSingletonCreationStrategy<>()));
            });
            return result;
        }

        public <T extends Enum<T>> RenameNamedReferencesBuilder renaming(NameReferenceSetKey<T> key) {
            return new RenameNamedReferencesBuilder<>(builder, notNull("key", key));
        }
    }

    public static final class RenameViaImportBuilder<T extends Enum<T>, K> {

        private final RefactoringsBuilder builder;
        private final NamedRegionKey<T> key;
        private CharFilter filter;
        private final SingletonKey<K> targetKey;

        RenameViaImportBuilder(RefactoringsBuilder builder, NamedRegionKey<T> key, SingletonKey<K> targetKey) {
            this.builder = builder;
            this.key = key;
            this.targetKey = targetKey;
        }

        public RefactoringsBuilder withCharFilter(CharFilter filter) {
            this.filter = filter;
            return build();
        }

        public CharFilterBuilder<RefactoringsBuilder> withCharFilter() {
            return CharFilter.builder(cf -> {
                return withCharFilter(cf);
            });
        }

        public RefactoringsBuilder build() {
            ImportKeyDelegateToSingletonKeyStrategy<T, K, RenameRefactoring> strategy = ImportKeyDelegateToSingletonKeyStrategy.forRename(targetKey, key, filter == null ? CharFilter.ALL : filter);
            return builder.add(RenameRefactoring.class, strategy);
        }
    }

    public static final class RenameNamedReferencesBuilder<T extends Enum<T>> {

        private final RefactoringsBuilder builder;
        private final NameReferenceSetKey<T> key;
        private CharFilter filter;

        RenameNamedReferencesBuilder(RefactoringsBuilder builder, NameReferenceSetKey<T> key) {
            this.builder = builder;
            this.key = key;
        }

        public RefactoringsBuilder withCharFilter(CharFilter filter) {
            this.filter = filter;
            return build();
        }

        public CharFilterBuilder<RefactoringsBuilder> withCharFilter() {
            return CharFilter.builder(cf -> {
                return withCharFilter(cf);
            });
        }

        public RefactoringsBuilder build() {
            return builder.add(RenameRefactoring.class,
                    RenameNamedPlugin.generator(key.referencing(), key, filter));
        }
    }

    public static class SingletonFileNameRefactoringItemBuilder<T, X, B extends SingletonFileNameRefactoringItemBuilder<T, X, B>>
            extends KeyedRefactoringItemBuilder<X, SingletonKey<X>, T, RenameRefactoring, B> {

        CharFilter nameFilter = CharFilter.ALL;

        SingletonFileNameRefactoringItemBuilder(SingletonKey<X> key, Function<? super B, T> converter) {
            super(key, RenameRefactoring.class, converter);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "(" + super.type.getSimpleName()
                    + " " + key + " " + nameFilter + " "
                    + ")";
        }

        public CharFilterBuilder<T> withCharFilter() {
            return CharFilter.builder(cf -> {
                return withCharFilter(cf);
            });
        }

        public T withCharFilter(CharFilter filter) {
            nameFilter = filter;
            return done();
        }

        public T build() {
            return done();
        }
    }
}

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

import com.mastfrog.abstractions.Stringifier;
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
import org.netbeans.modules.refactoring.spi.RefactoringPluginFactory;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;

/**
 * Builder for Antlr-based refactorings - call <code>forMimeType()</code>
 * to get an instance.  The usage pattern is to wrap and delegate.
 *
 * @author Tim Boudreau
 */
public final class RefactoringsBuilder {

    private final String mimeType;
    private final Set<RefactoringPluginGenerator<?>> generators
            = new LinkedHashSet<>();

    RefactoringsBuilder(String mimeType) {
        this.mimeType = mimeType;
    }

    public static RefactoringsBuilder forMimeType(String mimeType) {
        return new RefactoringsBuilder(mimeType);
    }

    public <T extends AbstractRefactoring> RefactoringsBuilder add(Class<T> type, TriFunction<? super T, ? super Extraction, ? super PositionBounds, ? extends RefactoringPlugin> factory) {
        generators.add(new RefactoringPluginGenerator<>(notNull("type", type), notNull("factory", factory)));
        return this;
    }

    public RenameRefactoringBuilder rename() {
        return new RenameRefactoringBuilder(this);
    }

    public WhereUsedRefactoringBuilder usages() {
        return new WhereUsedRefactoringBuilder(this);
    }

    /**
     * Create a new refactoring plugin factory suitable for registering
     * in the default lookup.
     *
     * @return A plugin factory
     */
    public RefactoringPluginFactory build() {
        return new AntlrRefactoringPluginFactory(mimeType, generators);
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

        KeyedRefactoringItemBuilder(K key, Class<R> type, Function<? super B, T> converter) {
            super(type, converter);
            this.type = notNull("type", type);
        }
    }

    public static final class WhereUsedRefactoringBuilder {

        private final RefactoringsBuilder builder;

        WhereUsedRefactoringBuilder(RefactoringsBuilder builder) {
            this.builder = builder;
        }

        public <T> RefactoringsBuilder finding(SingletonKey<T> k) {
            return finding(k, null);
        }

        public <T> RefactoringsBuilder finding(SingletonKey<T> k, Stringifier<? super T> stringifier) {
            SingletonWhereUsedCreationStrategy<T> strat = new SingletonWhereUsedCreationStrategy<>(k);
            SingletonRefactoringFactory<WhereUsedQuery, T> f = new StrategyRefactoringFactory<>(k, null, stringifier, strat);
            return builder.add(WhereUsedQuery.class, f);
        }

        public <T extends Enum<T>> RefactoringsBuilder finding(NamedRegionKey<T> k) {
            NamedCreationStrategy<WhereUsedQuery, T> c
                    = (NamedExtractionKey<T> key, WhereUsedQuery refactoring,
                            Extraction extraction, FileObject file, NamedSemanticRegion<T> item, CharFilter filter)
                    -> new NamedFindUsagesPlugin<>(refactoring, extraction, file, k, item);

            builder.generators.add(
                    NamedRefactoringCreationStrategyFactory.create(WhereUsedQuery.class, c, k, CharFilter.ALL));
            return builder;
        }

        public <T extends Enum<T>> RefactoringsBuilder finding(NameReferenceSetKey<T> k) {
            NamedCreationStrategy<WhereUsedQuery, T> c = new NamedCreationStrategy<WhereUsedQuery, T>() {
                @Override
                public RefactoringPlugin createRefactoringPlugin(NamedExtractionKey<T> key,
                        WhereUsedQuery refactoring, Extraction extraction, FileObject file,
                        NamedSemanticRegion<T> item, CharFilter filter) {
                    return new NamedFindUsagesPlugin<>(refactoring, extraction, file, key, item);
                }
            };
            builder.generators.add(
                    NamedRefactoringCreationStrategyFactory.create(WhereUsedQuery.class, c, k, CharFilter.ALL));
            return builder;
        }
    }

    public static final class RenameRefactoringBuilder {

        private final RefactoringsBuilder builder;

        RenameRefactoringBuilder(RefactoringsBuilder builder) {
            this.builder = builder;
        }

        public <X, B extends SingletonFileNameRefactoringItemBuilder<RefactoringsBuilder, X, B>> SingletonFileNameRefactoringItemBuilder<RefactoringsBuilder, X, B>
                renaming(SingletonKey<X> key) {
            SingletonFileNameRefactoringItemBuilder<RefactoringsBuilder, X, B> result = new SingletonFileNameRefactoringItemBuilder<>(notNull("key", key), sfnrib -> {
                return builder.add(RenameRefactoring.class,
                        new StrategyRefactoringFactory<>(
                                key,
                                sfnrib.nameFilter,
                                sfnrib.stringifier,
                                new RenameFileFromSingletonCreationStrategy<>()));
            });
            return result;
        }

    }

    public static class SingletonFileNameRefactoringItemBuilder<T, X, B extends SingletonFileNameRefactoringItemBuilder<T, X, B>> extends KeyedRefactoringItemBuilder<X, SingletonKey<X>, T, RenameRefactoring, B> {

        CharFilter nameFilter = CharFilter.ALL;
        Stringifier<? super X> stringifier;

        SingletonFileNameRefactoringItemBuilder(SingletonKey<X> key, Function<? super B, T> converter) {
            super(key, RenameRefactoring.class, converter);
        }

        public CharFilterBuilder<B> withCharFilter() {
            return CharFilter.builder(cf -> {
                return withNameFilter(cf);
            });
        }

        public B withNameFilter(CharFilter filter) {
            nameFilter = filter;
            return cast();
        }

        public B withStringifier(Stringifier<? super X> stringifier) {
            this.stringifier = stringifier;
            return cast();
        }

        public T build() {
            return done();
        }

    }
}

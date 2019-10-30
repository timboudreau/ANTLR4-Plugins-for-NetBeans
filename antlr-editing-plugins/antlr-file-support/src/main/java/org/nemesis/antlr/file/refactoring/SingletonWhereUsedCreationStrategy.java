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
import org.nemesis.charfilter.CharFilter;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.modules.refactoring.api.WhereUsedQuery;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
final class SingletonWhereUsedCreationStrategy<T> implements SingletonRefactoringCreationStrategy<WhereUsedQuery, T> {

    private final SingletonKey<T> key;
    private final Stringifier<T> stringifier;

    public SingletonWhereUsedCreationStrategy(SingletonKey<T> key) {
        this(key, null);
    }

    public SingletonWhereUsedCreationStrategy(SingletonKey<T> key, Stringifier<T> stringifier) {
        this.key = key;
        this.stringifier = stringifier;
    }

    @Override
    public RefactoringPlugin createRefactoringPlugin(SingletonKey<T> key, WhereUsedQuery refactoring, Extraction extraction, FileObject file, SingletonEncounters.SingletonEncounter<T> item, Stringifier<? super T> optionalStringifier, CharFilter filter) {
        return new SingletonFindUsagesPlugin<>(refactoring, extraction, file, key, item, stringifier);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + key + " "
                + stringifier + ")";
    }
}

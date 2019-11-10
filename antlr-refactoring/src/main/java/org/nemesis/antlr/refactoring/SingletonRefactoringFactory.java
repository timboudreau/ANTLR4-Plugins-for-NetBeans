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
import org.nemesis.charfilter.CharFilter;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;

/**
 *
 * @author Tim Boudreau
 */
abstract class SingletonRefactoringFactory<R extends AbstractRefactoring, K> extends AbstractRefactoringContext implements TriFunction<R, Extraction, PositionBounds, RefactoringPlugin> {

    private final SingletonKey<K> key;
    private final CharFilter filter;

    public SingletonRefactoringFactory(SingletonKey<K> key, CharFilter filter) {
        this.key = notNull("key", key);
        this.filter = filter;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "("
                + key + " "
                + filter + ")";
    }

    <K> SingletonEncounter<K> find(SingletonKey<K> key, Extraction extraction, PositionBounds bounds) {
        SingletonEncounters<K> gtEncounters = extraction.singletons(key);
        if (gtEncounters.isEmpty()) {
            return null;
        }
        return AbstractRefactoringContext.find(bounds, gtEncounters);
    }

    @Override
    public RefactoringPlugin apply(R refactoring, Extraction extraction, PositionBounds bounds) {
        Optional<FileObject> fileOpt = extraction.source().lookup(FileObject.class);
        if (!fileOpt.isPresent()) {
            return null;
        }
        FileObject file = fileOpt.get();
        SingletonEncounter<K> item = find(key, extraction, bounds);
        if (item != null) {
            logFine("{0} creates a refactoring plugin for {1}", this, item);
            return createRefactoringPlugin(key, refactoring, extraction,
                    file, item, filter);
        }
        return null;
    }

    protected abstract RefactoringPlugin createRefactoringPlugin(SingletonKey<K> key, R refactoring, Extraction extraction, FileObject file, SingletonEncounters.SingletonEncounter<K> item, CharFilter filter);
}

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
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.range.RangeRelation;
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
abstract class SingletonRefactoringFactory<R extends AbstractRefactoring, K> implements TriFunction<R, Extraction, PositionBounds, RefactoringPlugin> {

    private final SingletonKey<K> key;
    private final CharFilter filter;
    private final Stringifier<? super K> stringifier;

    public SingletonRefactoringFactory(SingletonKey<K> key, CharFilter filter, Stringifier<? super K> stringifier) {
        this.key = notNull("key", key);
        this.filter = filter;
        this.stringifier = stringifier;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "("
                + key + " filter="
                + filter + " stringifier=" + stringifier + ")";
    }

    static <K> SingletonEncounter<K> find(SingletonKey<K> key, Extraction extraction, PositionBounds bounds) {
        SingletonEncounters<K> gtEncounters = extraction.singletons(key);
        if (gtEncounters.isEmpty()) {
            return null;
        }
        int start = bounds.getBegin().getOffset();
        int end = bounds.getEnd().getOffset();
        IntRange range = Range.ofCoordinates(start, end);
        SingletonEncounters.SingletonEncounter<K> item = null;
        loop:
        for (SingletonEncounters.SingletonEncounter<K> s : gtEncounters) {
            RangeRelation rel = s.relationTo(range);
            switch (rel) {
                case CONTAINED:
                case CONTAINS:
                case EQUAL:
                    item = s;
                    break loop;
            }
        }
        if (item == null) {
            item = gtEncounters.at(range.start());
        }
        return item;
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
            return createRefactoringPlugin(key, refactoring, extraction,
                    file, item, stringifier, filter);
        }
        return null;
    }

    protected abstract RefactoringPlugin createRefactoringPlugin(SingletonKey<K> key, R refactoring, Extraction extraction, FileObject file, SingletonEncounters.SingletonEncounter<K> item, Stringifier<? super K> optionalStringifier, CharFilter filter);

}

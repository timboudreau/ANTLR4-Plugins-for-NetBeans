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
package org.nemesis.antlr.instantrename;

import com.mastfrog.range.IntRange;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.instantrename.impl.RenameActionType;
import org.nemesis.antlr.instantrename.impl.RenameQueryResultTrampoline;
import org.nemesis.antlr.instantrename.spi.RenameQueryResult;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.key.ExtractionKey;

/**
 *
 * @author Tim Boudreau
 */
final class FindItemsResult<T, X extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<? extends I>> implements Iterable<IntRange> {

    private final Set<? extends IntRange> ranges;
    private final RenameQueryResult queryResult;

    FindItemsResult(Set<? extends IntRange> ranges, RenameQueryResult queryResult) {
        this.ranges = ranges;
        this.queryResult = queryResult;
    }

    boolean isNotFound() {
        return type() == RenameActionType.NOTHING_FOUND;
    }

    boolean isUseRefactoring() {
        return type().isRefactor();
    }

    boolean isInplaceProceed() {
        return type().isInplaceProceed();
    }

    boolean isPassChanges() {
        return type().isPassChanges();
    }

    RenameActionType type() {
        return RenameQueryResultTrampoline.typeOf(queryResult);
    }

    Set<? extends IntRange> ranges() {
        return ranges == null ? Collections.emptySet() : ranges;
    }

    boolean test(boolean initial, char c) {
        return RenameQueryResultTrampoline.testChar(queryResult, initial, c);
    }

    void onCancelled() {
        RenameQueryResultTrampoline.onCancelled(queryResult);
    }

    void onNameUpdated(String orig, StyledDocument doc, String newName) {
        RenameQueryResultTrampoline.onNameUpdated(queryResult, orig, newName, doc);
    }

    void onRename(String original, String nue, Runnable undo) {
        RenameQueryResultTrampoline.onRename(queryResult, original, nue, undo);
    }

    @Override
    public Iterator<IntRange> iterator() {
        return (Iterator<IntRange>) ranges().iterator();
    }
}

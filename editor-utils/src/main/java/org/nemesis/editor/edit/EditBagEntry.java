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
package org.nemesis.editor.edit;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import java.util.function.Consumer;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.function.DocumentConsumer;

/**
 *
 * @author Tim Boudreau
 */
interface EditBagEntry extends DocumentConsumer<StyledDocument>, IntRange<EditBagEntry> {

    // These methods are used in range coalescing - we don't need them here
    @Override
    default EditBagEntry newRange(int start, int size) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    default EditBagEntry newRange(long start, long size) {
        return newRange(Math.max(0, (int) start), Math.max(0, (int) size));
    }

    Position startPosition();

    Position endPosition();

    ChangeKind kind();

    default boolean isDeletion() {
        return false;
    }

    default void positions(Consumer<Position> c) {
        Position a = startPosition();
        c.accept(a);
        Position b = endPosition();
        if (a != b) {
            c.accept(b);
        }
    }

    // For tests
    void apply(StringBuilder sb) throws Exception;

    int sequence();

    @Override
    default int compareTo(Range<?> o) {
        //            if (this instanceof DeletionEntry && !(o instanceof DeletionEntry)) {
        //                return 1;
        //            } else if (o instanceof DeletionEntry && !(this instanceof DeletionEntry)){
        //                return -1;
        //            }
        int result = -IntRange.super.compareTo(o);
        if (result == 0) {
            result = Integer.compare(sequence(), ((EditBagEntry) o).sequence());
        }
        return result;
        //            return entrySort(this, (EditBagEntry) o);
    }

}

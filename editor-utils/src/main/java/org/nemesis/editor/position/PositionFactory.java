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
package org.nemesis.editor.position;

import com.mastfrog.range.IntRange;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;

/**
 * Abstracts away whether we're dealing with PositionRefs from
 * CloneableEditorSupport or Positions from NbDocument or document; PositionRef
 * preferred when available.
 *
 * @author Tim Boudreau
 */
public interface PositionFactory {

    public static <T> T sorted(T result, Position a, Position b, BiConsumer<Position, Position> firstLast) {
        return sortedGet(a, b, (start, end) -> {
            firstLast.accept(start, end);
            return result;
        });
    }

    public static <T> T sortedGet(Position a, Position b, BiFunction<Position, Position, T> firstLast) {
        int oa = a.getOffset();
        int ob = b.getOffset();
        if (oa == ob) {
            return firstLast.apply(a, b);
        }
        if (ob > oa) {
            return firstLast.apply(a, b);
        } else {
            return firstLast.apply(b, a);
        }
    }

    /**
     * Create a position in the document this factory operates on.
     *
     * @param position The position
     * @param bias The bias
     * @return a position
     * @throws BadLocationException
     */
    Position createPosition(int position, Position.Bias bias) throws BadLocationException;

    /**
     * Get or create a position factory for the document.
     *
     * @param doc The document
     * @return A position factory
     */
    static PositionFactory forDocument(Document doc) {
        PositionFactory existing = (PositionFactory) doc.getProperty(PositionFactory.class);
        if (existing != null) {
            return existing;
        }

        DataObject dob = NbEditorUtilities.getDataObject(doc);
        PositionFactory result = null;
        if (dob != null) {
            CloneableEditorSupport ed = dob.getLookup().lookup(CloneableEditorSupport.class);
            if (ed != null) {
                result = new PositionRefFactory(ed);
            }
        }
        if (result == null) {
            if (doc instanceof StyledDocument) {
                result = new DefaultPositionFactory((StyledDocument) doc);
            } else {
                result = new DumbPositionFactory(doc);
            }
        }
        doc.putProperty(PositionFactory.class, result);
        return result;
    }

    PositionRange range(int start, Position.Bias startBias, int end, Position.Bias endBias) throws BadLocationException;

    default PositionRange range(int start, int end) throws BadLocationException {
        return range(start, Position.Bias.Forward, end, Position.Bias.Backward);
    }

    PositionRange range(Position.Bias startBias, IntRange<? extends IntRange> orig, Position.Bias endBias) throws BadLocationException;

    default PositionRange range(IntRange<? extends IntRange<?>> orig) throws BadLocationException {
        return range(Position.Bias.Forward, orig, Position.Bias.Backward);
    }

    public static PositionBounds toPositionBounds(PositionRange range) {
        if (range instanceof RefPositionIntRange) {
            return ((RefPositionIntRange) range).positionBounds();
        }
        return null;
    }

    public static PositionRange toPositionRange(PositionBounds bounds) {
        return new RefPositionIntRange(bounds.getBegin(), bounds.getEnd());
    }
}

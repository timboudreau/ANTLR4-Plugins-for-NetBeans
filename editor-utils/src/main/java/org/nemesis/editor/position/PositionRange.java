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
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.openide.text.PositionRef;

/**
 * Implmentation of IntRange over Position instances;  this is useful both because
 * positions survive edits and will stay accurate in hints still displayed after
 * an edit, and while openide.text has PositionBounds, which is almost the same
 * thing, you can't rely on every document in the universe being backed by a
 * CloneableEditorSupport;  and a number of the methods built into Range and
 * IntRange are very useful.
 *
 * @author Tim Boudreau
 */
public abstract class PositionRange implements IntRange<PositionRange> {

    public abstract Position startPosition();

    public abstract Position endPosition();

    public abstract Position.Bias startBias();

    public abstract Position.Bias endBias();

    public abstract Document document();

    public static PositionRange create(Position a, Position b, Document doc) {
        return PositionFactory.sortedGet(a, b, (first, last) -> {
            if (first instanceof PositionRef && last instanceof PositionRef) {
                return new RefPositionIntRange((PositionRef) first, (PositionRef) last);
            }
            if (doc instanceof StyledDocument) {
                return new DefaultPositionIntRange(a, Position.Bias.Forward, b,
                        Position.Bias.Backward, (StyledDocument) doc);
            }
            // For completeness
            return new DumbPositionFactory.DumbIntRange(first, Position.Bias.Forward,
                    last, Position.Bias.Backward, doc);
        });
    }

    @Override
    public final int start() {
        return startPosition().getOffset();
    }

    @Override
    public final int size() {
        return end() - start();
    }

    @Override
    public final int end() {
        return endPosition().getOffset();
    }

    @Override
    public final int stop() {
        return end() - 1;
    }

    @Override
    public final int hashCode() {
        int hash = 5;
        hash = 73 * hash + startPosition().getOffset();
        hash = 73 * hash + endPosition().getOffset();
        hash = 73 * hash + startBias().hashCode();
        hash = 73 * hash + endBias().hashCode();
        hash = 73 * hash + document().hashCode();
        return hash;
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof PositionRange) {
            PositionRange other = (PositionRange) o;
            return other.startBias() == startBias()
                    && other.endBias() == endBias()
                    && other.document() == document()
                    && other.startPosition().getOffset() == startPosition().getOffset()
                    && other.endPosition().getOffset() == endPosition().getOffset();
        }
        return false;
    }

    @Override
    public final PositionRange newRange(long start, long size) {
        return newRange(Math.max(0, (int) start), Math.max(0, (int) size));
    }

    @Override
    public String toString() {
        int start = start();
        int end = end();
        return start + "(" + startBias() + "):" + end
                + "(" + endBias() + ")[" + (end - start) + "]";
    }

    public String id() {
        int idHash;
        Object str = document().getProperty(StreamDescriptionProperty);
        if (str != null) {
            idHash = System.identityHashCode(str);
        } else {
            idHash = System.identityHashCode(document());
        }
        return start() + ":"+end() + ":" + idHash;
    }
}

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

import com.mastfrog.util.preconditions.Exceptions;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.ops.DocumentOperator;
import org.nemesis.editor.function.DocumentSupplier;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;

/**
 *
 * @author Tim Boudreau
 */
final class RefPositionIntRange extends PositionRange {

    private final PositionBounds pb;

    RefPositionIntRange(PositionRef start, PositionRef end) {
        pb = new PositionBounds(start, end);
    }

    PositionBounds positionBounds() {
        return pb;
    }

    @Override
    public PositionRef startPosition() {
        return pb.getBegin();
    }

    @Override
    public PositionRef endPosition() {
        return pb.getEnd();
    }

    @Override
    public Position.Bias startBias() {
        return startPosition().getPositionBias();
    }

    @Override
    public Position.Bias endBias() {
        return endPosition().getPositionBias();
    }

    @Override
    public StyledDocument document() {
        return pb.getBegin().getCloneableEditorSupport().getDocument();
    }

    @Override
    public PositionRange newRange(int start, int size) {
        CloneableEditorSupport supp = pb.getBegin().getCloneableEditorSupport();
        try {
            return DocumentOperator.runAtomic(supp.getDocument(),
                    (DocumentSupplier<PositionRange, RuntimeException>) () -> {
                        PositionRef begin = supp.createPositionRef(start,
                                startBias());
                        PositionRef end = supp.createPositionRef(start + size,
                                endBias());
                        return new RefPositionIntRange(begin, end);
                    });
        } catch (BadLocationException ex) {
            return Exceptions.chuck(ex);
        }
    }
}

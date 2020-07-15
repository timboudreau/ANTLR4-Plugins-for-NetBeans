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

/**
 *
 * @author Tim Boudreau
 */
final class DefaultPositionIntRange extends PositionRange {

    private final Position start;
    private final Position.Bias startBias;
    private final Position end;
    private final Position.Bias endBias;
    private final StyledDocument doc;

    public DefaultPositionIntRange(Position start, Position.Bias startBias,
            Position end, Position.Bias endBias, StyledDocument doc) {
        this.start = start;
        this.startBias = startBias;
        this.end = end;
        this.endBias = endBias;
        this.doc = doc;
    }

    @Override
    public StyledDocument document() {
        return doc;
    }

    @Override
    public Position startPosition() {
        return start;
    }

    @Override
    public Position endPosition() {
        return end;
    }

    @Override
    public Position.Bias startBias() {
        return startBias;
    }

    @Override
    public Position.Bias endBias() {
        return endBias;
    }

    @Override
    public PositionRange newRange(int start, int size) {
        try {
            return PositionFactory.forDocument(doc).range(start, startBias, start + size, endBias);
        } catch (BadLocationException ex) {
            return Exceptions.chuck(ex);
        }
    }

}

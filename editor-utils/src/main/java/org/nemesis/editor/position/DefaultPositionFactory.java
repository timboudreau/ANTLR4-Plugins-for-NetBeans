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
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.ops.DocumentOperator;
import org.openide.text.NbDocument;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultPositionFactory implements PositionFactory {

    final StyledDocument doc;

    DefaultPositionFactory(StyledDocument doc) {
        this.doc = doc;
    }

    @Override
    public Position createPosition(int position, Position.Bias bias) throws BadLocationException {
        return NbDocument.createPosition(doc, position, bias);
    }

    @Override
    public PositionRange range(int start, Position.Bias startBias, int end, Position.Bias endBias) throws BadLocationException {
        return DocumentOperator.<PositionRange,RuntimeException>runAtomic(doc, () -> {
            Position startPosition = createPosition(start, startBias);
            Position endPosition = createPosition(end, endBias);
            return new DefaultPositionIntRange(startPosition, startBias, endPosition, endBias, doc);
        });
    }

    @Override
    public PositionRange range(Position.Bias startBias, IntRange<? extends IntRange> orig, Position.Bias endBias) throws BadLocationException {
        return range(orig.start(), startBias, orig.end(), endBias);
    }

}

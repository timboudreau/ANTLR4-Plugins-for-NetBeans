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
import static com.mastfrog.util.preconditions.Checks.notNull;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionRef;

/**
 *
 * @author Tim Boudreau
 */
final class PositionRefFactory implements PositionFactory {

    final CloneableEditorSupport supp;

    PositionRefFactory(CloneableEditorSupport supp) {
        this.supp = supp;
    }

    @Override
    public PositionRef createPosition(int position, Position.Bias bias) throws BadLocationException {
        return supp.createPositionRef(position, bias);
    }

    @Override
    public PositionRange range(int start, Position.Bias startBias, int end, Position.Bias endBias) throws BadLocationException {
        return new RefPositionIntRange(createPosition(start, startBias),
                createPosition(end, endBias));
    }

    @Override
    public PositionRange range(Position.Bias startBias, IntRange<? extends IntRange> orig, Position.Bias endBias) throws BadLocationException {
        return new RefPositionIntRange(createPosition(notNull("orig", orig).start(), startBias),
                createPosition(orig.end(), endBias));
    }
}

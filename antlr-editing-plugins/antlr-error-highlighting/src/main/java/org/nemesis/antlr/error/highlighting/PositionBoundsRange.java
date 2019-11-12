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
package org.nemesis.antlr.error.highlighting;

import com.mastfrog.range.IntRange;
import java.util.Optional;
import javax.swing.text.Position;
import org.nemesis.data.SemanticRegion;
import org.nemesis.source.api.GrammarSource;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class PositionBoundsRange implements IntRange<PositionBoundsRange> {

    private final SemanticRegion<?> range;
    private final PositionBounds bounds;

    public PositionBoundsRange(SemanticRegion<?> range, PositionBounds bounds) {
        this.range = range;
        this.bounds = bounds;
    }

    public PositionBounds bounds() {
        return bounds;
    }

    SemanticRegion<?> original() {
        return range;
    }

    static PositionBounds createBounds(GrammarSource<?> src, int start, int end) {
        Optional<FileObject> fo = src.lookup(FileObject.class);
        if (fo.isPresent()) {
            try {
                DataObject dob = DataObject.find(fo.get());
                CloneableEditorSupport supp = dob.getLookup().lookup(CloneableEditorSupport.class);
                if (supp != null) {
                    PositionRef startPos = supp.createPositionRef(start, Position.Bias.Backward);
                    PositionRef endPos = supp.createPositionRef(end, Position.Bias.Forward);
                    return new PositionBounds(startPos, endPos);
                }
            } catch (DataObjectNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return null;

    }

    static PositionBoundsRange create(GrammarSource<?> src, SemanticRegion<?> range) {
        PositionBounds bds = createBounds(src, range.start(), range.end());
        return bds == null ? null : new PositionBoundsRange(range, bds);
    }

    @Override
    public int start() {
        return bounds.getBegin().getOffset();
    }

    @Override
    public int size() {
        return bounds.getEnd().getOffset() - start();
    }

    @Override
    public PositionBoundsRange newRange(int start, int size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PositionBoundsRange newRange(long start, long size) {
        throw new UnsupportedOperationException();
    }

}

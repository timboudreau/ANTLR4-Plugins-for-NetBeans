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
package org.nemesis.antlr.live.language.coloring;

import com.mastfrog.range.DataIntRange;
import java.awt.EventQueue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.ReleasableHighlightsContainer;
import org.netbeans.spi.editor.highlighting.support.AbstractHighlightsContainer;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocHighlightsContainer extends AbstractHighlightsContainer implements ReleasableHighlightsContainer {

    private final AtomicReference<List<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>>> mostRecent
            = new AtomicReference<>();
    private int lastColoringsRev = -1;

    public void update(AdhocColorings colorings, AntlrProxies.ParseTreeProxy semantics, int length) {

        List<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> nue
                = AdhocHighlightsSequence.computeRanges(colorings, semantics, length);

        List<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> old
                = this.mostRecent.getAndSet(nue);

        int lcr = lastColoringsRev;
        lastColoringsRev = colorings.rev();
        if (old == null || lcr != lastColoringsRev) {
            fire(0, length);
        } else {
            int max = Math.min(old.size(), nue.size());
            int diffStart = 0;
            for (int i = 0; i < max; i++) {
                DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> oldItem = old.get(i);
                DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> newItem = nue.get(i);
                if (!oldItem.equals(newItem)) {
                    diffStart = Math.min(oldItem.start(), newItem.start());
                    break;
                }
            }
            if (diffStart == max && old.size() == nue.size()) {
                return;
            }
            int min = Math.max(old.size(), nue.size()) - max;
            int diffEnd = -1;
            for (int i = 0; i < min; i++) {
                int ix = old.size() - (i + 1);
                if (ix < 0) {
                    break;
                }
                int mrix = nue.size() - (i + 1);
                if (mrix == -1) {
                    break;
                }
                DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> oldItem = old.get(ix);
                DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> newItem = nue.get(nue.size() - (i + 1));
                if (!oldItem.equals(newItem)) {
                    diffEnd = Math.max(oldItem.end(), newItem.end());
                }
            }
            if (diffEnd == -1) {
                fire(diffStart, length);
            } else {
                fire(diffStart, diffEnd);
            }
        }
    }

    private void fire(int start, int end) {
        if (EventQueue.isDispatchThread()) {
            fireHighlightsChange(start, end);
        } else {
            EventQueue.invokeLater(() -> {
                fireHighlightsChange(start, end);
            });
        }
    }

    @Override
    public HighlightsSequence getHighlights(int startOffset, int endOffset) {
        if (endOffset == startOffset + 1) {
            Thread.dumpStack();
        }
        List<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> currentHighlights
                = mostRecent.get();

        if (currentHighlights == null || currentHighlights.isEmpty()) {
            return HighlightsSequence.EMPTY;
        }
        if (startOffset <= 0 && endOffset == Integer.MAX_VALUE) {
            return new AdhocHighlightsSequence(currentHighlights.iterator());
        }
        DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> first = currentHighlights.get(0);
        if (endOffset <= first.start()) {
            return HighlightsSequence.EMPTY;
        }
        DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> last = currentHighlights.get(currentHighlights.size() - 1);
        if (startOffset >= last.end()) {
            return HighlightsSequence.EMPTY;
        }
        if (startOffset <= first.start() && endOffset >= last.end()) {
            return new AdhocHighlightsSequence(currentHighlights.iterator());
        }
        int start = -1;
        int end = -1;
        for (int i = 0; i < currentHighlights.size(); i++) {
            DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> item = currentHighlights.get(i);
            if (item.end() > startOffset || item.start() >= startOffset) {
                start = i;
                break;
            }
        }
        if (start == -1) {
            return HighlightsSequence.EMPTY;
        }
        for (int i = currentHighlights.size() - 1; i >= 0; i--) {
            DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> item = currentHighlights.get(i);
            if (item.start() <= endOffset || item.end() <= endOffset) {
                end = i;
                break;
            }
        }
        if (end == -1 || end <= start) {
            return HighlightsSequence.EMPTY;
        }
        return new AdhocHighlightsSequence(currentHighlights.subList(start, end + 1).iterator());
    }

    @Override
    public void released() {
        mostRecent.set(null);
    }
}

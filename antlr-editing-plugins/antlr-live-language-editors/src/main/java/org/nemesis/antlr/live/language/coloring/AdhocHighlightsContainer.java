/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.language.coloring;

import com.mastfrog.range.DataIntRange;
import java.awt.EventQueue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.nemesis.antlr.live.language.AdhocColorings;
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

    public void update(AdhocColorings colorings, AntlrProxies.ParseTreeProxy semantics, int length) {

        List<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> nue
                = AdhocHighlightsSequence.computeRanges(colorings, semantics, length);

        List<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> old
                = this.mostRecent.getAndSet(nue);

        nue = AdhocHighlightsSequence.computeRanges(colorings, semantics, length);
        if (old == null) {
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
            if (item.start() >= startOffset) {
                start = i;
                break;
            }
        }
        if (start == -1) {
            return HighlightsSequence.EMPTY;
        }
        for (int i = currentHighlights.size() - 1; i >= 0; i--) {
            DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> item = currentHighlights.get(i);
            if (item.end() <= endOffset) {
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

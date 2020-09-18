/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.spi.language.highlighting;

import javax.swing.text.AttributeSet;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 *
 * @author Tim Boudreau
 */
public interface HighlightConsumer {

    public void addHighlight(int startOffset, int endOffset, AttributeSet attributes);

    public void clear();

    public void setHighlights(HighlightsContainer other);

    static HighlightConsumer wrap(OffsetsBag bag) {
        return new BagWrapper(bag);
    }

    static final class BagWrapper implements HighlightConsumer {

        private final OffsetsBag bag;

        public BagWrapper(OffsetsBag bag) {
            this.bag = bag;
        }

        @Override
        public void addHighlight(int startOffset, int endOffset, AttributeSet attributes) {
            bag.addHighlight(startOffset, endOffset, attributes);
        }

        @Override
        public void clear() {
            bag.clear();
        }

        @Override
        public void setHighlights(HighlightsContainer other) {
            if (other instanceof OffsetsBag) {
                OffsetsBag off = (OffsetsBag) other;
                bag.setHighlights(bag);
            } else {
                bag.setHighlights(other.getHighlights(0, Integer.MAX_VALUE));
            }
        }
    }
}

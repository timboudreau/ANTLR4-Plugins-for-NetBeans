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

import java.util.function.BooleanSupplier;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 * Wrapper interface for the input side of OffsetsBag.
 *
 * @author Tim Boudreau
 */
public interface HighlightConsumer {

    void addHighlight(int startOffset, int endOffset, AttributeSet attributes);

    void clear();

    void setHighlights(HighlightsContainer other);

    static HighlightConsumer wrap(OffsetsBag bag, Document doc) {
        return new BagWrapper(bag, doc);
    }

    void update( BooleanSupplier run );

    HighlightsContainer getHighlightsContainer();
}

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
package org.nemesis.antlr.highlighting;

import java.util.function.Function;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 *
 * @author Tim Boudreau
 */
interface AntlrHighlighter {

    void refresh(Document doc, Extraction ext, OffsetsBag bag, Integer caret);

    default boolean mergeHighlights() {
        return false;
    }

    static AntlrHighlighter create(String mimeType, RegionsKey<?> key, String coloring) {
        return create(mimeType, key, ignored -> coloring);
    }

    static <T> AntlrHighlighter create(String mimeType, RegionsKey<T> key, Function<SemanticRegion<T>, String> coloringNameProvider) {
        return SimpleSemanticRegionAntlrHighlighter.create(key, mimeType, coloringNameProvider);
    }

    static AntlrHighlighter create(String mimeType, NameReferenceSetKey<?> key, String coloringName) {
        return create(mimeType, key, ignored -> coloringName);
    }

    static <T extends Enum<T>> AntlrHighlighter create(String mimeType, NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, String> coloringNameProvider) {
        return SimpleNamedRegionReferenceAntlrHighlighter.create(mimeType, key, coloringNameProvider);
    }

    static AntlrHighlighter create(NamedRegionKey<?> key, String mimeType, String coloringName) {
        return create(key, mimeType, ignored -> coloringName);
    }

    static <T extends Enum<T>> AntlrHighlighter create(NamedRegionKey<T> key, String mimeType, Function<NamedSemanticRegion<T>, String> coloringNameProvider) {
        return SimpleNamedRegionAntlrHighlighter.create(key, mimeType, coloringNameProvider);
    }

    static <T extends Enum<T>> AntlrHighlighter create(NamedRegionKey<T> key, Function<NamedSemanticRegion<T>, AttributeSet> coloringLookup) {
        return new SimpleNamedRegionAntlrHighlighter<>(key, coloringLookup);
    }

    static <T extends Enum<T>> AntlrHighlighter create(NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, AttributeSet> coloringLookup) {
        return new SimpleNamedRegionReferenceAntlrHighlighter<>(key, coloringLookup);
    }

    static <T> AntlrHighlighter create(RegionsKey<T> key, Function<SemanticRegion<T>, AttributeSet> coloringLookup) {
        return new SimpleSemanticRegionAntlrHighlighter<>(key, coloringLookup);
    }
}

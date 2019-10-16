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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import static org.nemesis.antlr.highlighting.SimpleSemanticRegionAntlrHighlighter.coloringForMimeType;
import static org.nemesis.antlr.highlighting.SimpleSemanticRegionAntlrHighlighter.coloringLookup;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NamedRegionKey;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleNamedRegionAntlrHighlighter<T extends Enum<T>> implements AntlrHighlighter {

    private final NamedRegionKey<T> key;
    private final Function<NamedSemanticRegion<T>, AttributeSet> coloringLookup;
    private final int cacheSize;

    private static final Logger LOG = Logger.getLogger(SimpleNamedRegionAntlrHighlighter.class.getName());

    private static void log(String msg, Object... args) {
        LOG.log(Level.FINER, msg, args);
    }

    SimpleNamedRegionAntlrHighlighter(NamedRegionKey<T> key, Function<NamedSemanticRegion<T>, AttributeSet> coloringLookup) {
        this.key = key;
        this.coloringLookup = coloringLookup;
        cacheSize = key.type().getEnumConstants().length;
    }

    @Override
    public String toString() {
        return "SimpleNamedRegionAntlrHighlighter{" + key + '}';
    }

    static <T extends Enum<T>> SimpleNamedRegionAntlrHighlighter<T> fixed(NamedRegionKey<T> key, Supplier<AttributeSet> lookup) {
        return new SimpleNamedRegionAntlrHighlighter<>(key, t -> {
            return lookup.get();
        });
    }

    static <T extends Enum<T>> SimpleNamedRegionAntlrHighlighter<T> fixed(NamedRegionKey<T> key, String mimeType, String coloring) {
        return fixed(key, coloringLookup(mimeType, coloring));
    }

    static <T extends Enum<T>> SimpleNamedRegionAntlrHighlighter<T> create(NamedRegionKey<T> key, String mimeType, Function<NamedSemanticRegion<T>, String> coloringNameProvider) {
        Function<String, AttributeSet> coloringFinder = coloringForMimeType(mimeType);
        Function<NamedSemanticRegion<T>, AttributeSet> xformed = coloringNameProvider.andThen(coloringFinder);
        return new SimpleNamedRegionAntlrHighlighter<>(key, xformed);
    }

    @Override
    public void refresh(Document doc, Extraction ext, Parser.Result result, OffsetsBag bag, Integer ignored) {
        NamedSemanticRegions<T> regions = ext.namedRegions(key);
        log("{0} refresh {1} NamedSemanticRegions for {2}", key, regions.size(), doc);
        if (!regions.isEmpty()) {
            Map<T, AttributeSet> cache = new HashMap<>(cacheSize);
            for (NamedSemanticRegion<T> region : regions) {
                T kind = region.kind();
                AttributeSet coloring = cache.get(kind);
                if (coloring == null) {
                    coloring = coloringLookup.apply(region);
                    if (coloring != null) {
                        cache.put(kind, coloring);
                    } else {
                        log("no color for {0}", kind);
                    }
                }
                if (coloring != null) {
                    bag.addHighlight(region.start(), region.end(), coloring);
                }
            }
        }
    }
}

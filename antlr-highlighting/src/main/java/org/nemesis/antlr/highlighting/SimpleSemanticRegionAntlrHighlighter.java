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

import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleSemanticRegionAntlrHighlighter<T> implements AntlrHighlighter {

    private final RegionsKey<T> key;
    private final Function<SemanticRegion<T>, AttributeSet> coloringLookup;
    private static final Logger LOG = Logger.getLogger(SimpleNamedRegionAntlrHighlighter.class.getName());

    private static void log(String msg, Object... args) {
        LOG.log(Level.FINER, msg, args);
    }

    SimpleSemanticRegionAntlrHighlighter(RegionsKey<T> key, Function<SemanticRegion<T>, AttributeSet> coloringLookup) {
        this.key = key;
        this.coloringLookup = coloringLookup;
    }

    @Override
    public String toString() {
        return "SSRAH{" + key + '}';
    }

    @Override
    public boolean mergeHighlights() {
        return true;
    }

    static <T> SimpleSemanticRegionAntlrHighlighter<T> fixed(RegionsKey<T> key, Supplier<AttributeSet> lookup) {
        return new SimpleSemanticRegionAntlrHighlighter<>(key, t -> {
            return lookup.get();
        });
    }

    static Supplier<AttributeSet> coloringLookup(String mimeType, String coloring) {
        return new ColoringSupplier(coloring, mimeType);
    }

    // Don't use lambdas, for loggability
    private static final class ColoringSupplier implements Supplier<AttributeSet> {

        private final String coloringName;
        private final ColoringLookup lkp;

        public ColoringSupplier(String coloringName, String mimeType) {
            this.coloringName = coloringName;
            lkp = new ColoringLookup(mimeType);
        }

        @Override
        public AttributeSet get() {
            return lkp.apply(coloringName);
        }

        public String toString() {
            return lkp + " supplier for '" + coloringName + "'";
        }
    }

    private static final class ColoringLookup implements Function<String, AttributeSet> {

        private final String mimeType;
        private static final Set<String> missing = new HashSet<>();

        public ColoringLookup(String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public AttributeSet apply(String coloringName) {
            MimePath mimePath = MimePath.parse(mimeType);
            FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
            if (fcs != null) {
                AttributeSet set = fcs.getFontColors(coloringName);
                if (set == null || set.getAttributeCount() == 0) {
                    AttributeSet alt = fcs.getTokenFontColors(coloringName);
                    if (alt != null && alt.getAttributeCount() > 0) {
                        set = alt;
                    }
                }
                if (set == null) {
                    String key = mimeType + "." + coloringName;
                    if (!missing.contains(key)) {
                        log("No coloring for '" + coloringName + "' for " + mimeType);
                        missing.add(key);
                    }
                }
                return set;
            }
            return null;
        }

        public String toString() {
            return "ColoringLookup for " + mimeType;
        }
    }

    static <T> Function<String, AttributeSet> coloringForMimeType(String mimeType) {
        return new ColoringLookup(mimeType);
    }

    static <T> SimpleSemanticRegionAntlrHighlighter<T> fixed(RegionsKey<T> key, String mimeType, String coloring) {
        return fixed(key, coloringLookup(mimeType, coloring));
    }

    static <T> SimpleSemanticRegionAntlrHighlighter<T> create(RegionsKey<T> key, String mimeType, Function<SemanticRegion<T>, String> coloringNameProvider) {
        Function<String, AttributeSet> coloringFinder = coloringForMimeType(mimeType);
        Function<SemanticRegion<T>, AttributeSet> xformed = coloringNameProvider.andThen(coloringFinder);
        return new SimpleSemanticRegionAntlrHighlighter<>(key, xformed);
    }

    @Override
    public void refresh(Document doc, Extraction ext, HighlightConsumer bag, Integer ignored) {
        SemanticRegions<T> regions = ext.regions(key);
        log("refresh {0} NamedRegionReferenceSets for {1}", regions.size(), doc);
        if (!regions.isEmpty()) {
            Map<T, AttributeSet> cache = new HashMap<>();
            for (SemanticRegion<T> region : regions) {
                T kind = region.key();
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

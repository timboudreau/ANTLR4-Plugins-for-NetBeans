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
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, msg, args);
        }
    }

    SimpleNamedRegionAntlrHighlighter(NamedRegionKey<T> key, Function<NamedSemanticRegion<T>, AttributeSet> coloringLookup) {
        this.key = key;
        this.coloringLookup = coloringLookup;
        cacheSize = key.type().getEnumConstants().length;
        LOG.log(Level.FINE, "Create SimpleNamedRegionAntlrHighlighter for {0} with {1}",
                new Object[]{key, coloringLookup});
    }

    @Override
    public String toString() {
        return "SimpleNamedRegionAntlrHighlighter{" + key + " with " + coloringLookup + '}';
    }

    static <T extends Enum<T>> SimpleNamedRegionAntlrHighlighter<T> fixed(NamedRegionKey<T> key, Supplier<AttributeSet> lookup) {
        return new SimpleNamedRegionAntlrHighlighter<>(key, new FixedLookup<>(lookup));
    }

    static <T extends Enum<T>> SimpleNamedRegionAntlrHighlighter<T> fixed(
            NamedRegionKey<T> key, String mimeType, String coloring) {
        if (Supplier.class.isAssignableFrom(key.type())) {
            return create(key, mimeType, reg -> coloring);
        }
        return fixed(key, coloringLookup(mimeType, coloring));
    }

    static <T extends Enum<T>> Function<NamedSemanticRegion<T>, AttributeSet> wrapColoringLookupWithNamedRegionKeyCheck(
            NamedRegionKey<T> key, String mimeType, Function<NamedSemanticRegion<T>, AttributeSet> xformed) {
        Function<String, AttributeSet> coloringFinder = coloringForMimeType(mimeType);
        if (Supplier.class.isAssignableFrom(key.type())) {
            xformed = new DelegateToColoringNameSupplier(coloringFinder, xformed);
        }
        return xformed;
    }

    static <T extends Enum<T>> SimpleNamedRegionAntlrHighlighter<T> create(NamedRegionKey<T> key, String mimeType, Function<NamedSemanticRegion<T>, String> coloringNameProvider) {
        Function<String, AttributeSet> coloringFinder = coloringForMimeType(mimeType);
        Function<NamedSemanticRegion<T>, AttributeSet> xformed = coloringNameProvider.andThen(coloringFinder);
        xformed = wrapColoringLookupWithNamedRegionKeyCheck(key, mimeType, xformed);
        return new SimpleNamedRegionAntlrHighlighter<>(key, xformed);
    }

    @Override
    public void refresh(Document doc, Extraction ext, OffsetsBag bag, Integer ignored) {
        NamedSemanticRegions<T> regions = ext.namedRegions(key);
        log("{0} refresh {1} NamedSemanticRegions for {2}", key, regions.size(), doc);
        if (!regions.isEmpty()) {
            Map<T, AttributeSet> cache = new HashMap<>(cacheSize);
            for (NamedSemanticRegion<T> region : regions.index()) {
                T kind = region.kind();
                AttributeSet coloring = cache.get(kind);
                if (coloring == null) {
                    coloring = coloringLookup.apply(region);
                    log("Coloring {0} for {1} from {2}",
                            new Object[]{coloring, kind, this});
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

    /**
     * Wraps the default coloring supplier with one which can use a value from
     * the region's key as a Supplier&lt;Color&gt; if it is one.
     *
     * @param <T> The key type
     */
    private static class DelegateToColoringNameSupplier<T extends Enum<T>> implements Function<NamedSemanticRegion<T>, AttributeSet> {

        private final Function<String, AttributeSet> coloringFinder;
        private final Function<NamedSemanticRegion<T>, AttributeSet> oldxformed;

        public DelegateToColoringNameSupplier(Function<String, AttributeSet> coloringFinder, Function<NamedSemanticRegion<T>, AttributeSet> oldxformed) {
            this.coloringFinder = coloringFinder;
            this.oldxformed = oldxformed;
        }

        public String toString() {
            return "DelegateToColoringNameSupplier(wrapping " + oldxformed + " using " + coloringFinder + ")";
        }

        @Override
        public AttributeSet apply(NamedSemanticRegion<T> region) {
            if (region.kind() instanceof Supplier<?>) {
                Supplier<?> cns = (Supplier<?>) region.kind();
                if (cns != null && cns.get() != null) {
                    Object o = cns.get();
                    if (o instanceof String) {
                        AttributeSet result = coloringFinder.apply((String) o);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
            AttributeSet result = oldxformed.apply(region);
            return result;
        }
    }

    static final class FixedLookup<T extends Enum<T>> implements Function<NamedSemanticRegion<T>, AttributeSet> {

        private final Supplier<AttributeSet> lookup;

        public FixedLookup(Supplier<AttributeSet> lookup) {
            this.lookup = lookup;
        }

        @Override
        public String toString() {
            return "FixedLookup(" + lookup + ")";
        }

        @Override
        public AttributeSet apply(NamedSemanticRegion<T> t) {
            return lookup.get();
        }
    }
}

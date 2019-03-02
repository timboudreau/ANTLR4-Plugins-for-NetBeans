package org.nemesis.antlr.highlighting;

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
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleSemanticRegionAntlrHighlighter<T> implements AntlrHighlighter {

    private final RegionsKey<T> key;
    private final Function<SemanticRegion<T>, AttributeSet> coloringLookup;
    private static final Logger LOG = Logger.getLogger(SimpleNamedRegionAntlrHighlighter.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    private static void log(String msg, Object... args) {
        LOG.log(Level.FINER, msg, args);
    }

    SimpleSemanticRegionAntlrHighlighter(RegionsKey<T> key, Function<SemanticRegion<T>, AttributeSet> coloringLookup) {
        this.key = key;
        this.coloringLookup = coloringLookup;
    }

    @Override
    public String toString() {
        return "SimpleSemanticRegionAntlrHighlighter{" + key + '}';
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

    private static final class ColoringLookup implements Function<String,AttributeSet> {
        private final String mimeType;
        private static final Set<String> missing = new HashSet<>();

        public ColoringLookup(String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public AttributeSet apply(String coloringName) {
            MimePath mimePath = MimePath.parse(mimeType);
            FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
            AttributeSet result = fcs == null ? null : fcs.getTokenFontColors(coloringName);
            if (result == null) {
                String key = mimeType + "." + coloringName;
                if (!missing.contains(key)) {
                    log("No coloring for '" + coloringName + "' for " + mimeType);
                    missing.add(key);
                }
            }
            return result;
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
    public void refresh(Document doc, Extraction ext, Parser.Result result, OffsetsBag bag) {
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

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
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleNamedRegionReferenceAntlrHighlighter<T extends Enum<T>> implements AntlrHighlighter {

    private final NameReferenceSetKey<T> key;
    private final Function<NamedSemanticRegionReference<T>, AttributeSet> coloringLookup;
    private final int cacheSize;
    private static final Logger LOG = Logger.getLogger(SimpleNamedRegionAntlrHighlighter.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    private static void log(String msg, Object... args) {
        LOG.log(Level.FINER, msg, args);
    }

    SimpleNamedRegionReferenceAntlrHighlighter(NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, AttributeSet> coloringLookup) {
        this.key = key;
        this.coloringLookup = coloringLookup;
        cacheSize = key.type().getEnumConstants().length;
    }

    @Override
    public String toString() {
        return "SimpleNamedRegionReferenceAntlrHighlighter{" + key + '}';
    }

    static <T extends Enum<T>> SimpleNamedRegionReferenceAntlrHighlighter<T> fixed(NameReferenceSetKey<T> key, Supplier<AttributeSet> lookup) {
        return new SimpleNamedRegionReferenceAntlrHighlighter<>(key, t -> {
            return lookup.get();
        });
    }

    static <T extends Enum<T>> SimpleNamedRegionReferenceAntlrHighlighter<T> fixed(String mimeType, NameReferenceSetKey<T> key, String coloring) {
        return fixed(key, coloringLookup(mimeType, coloring));
    }

    static <T extends Enum<T>> SimpleNamedRegionReferenceAntlrHighlighter<T> create(String mimeType, NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, String> coloringNameProvider) {
        Function<String, AttributeSet> coloringFinder = coloringForMimeType(mimeType);
        Function<NamedSemanticRegionReference<T>, AttributeSet> xformed = coloringNameProvider.andThen(coloringFinder);
        return new SimpleNamedRegionReferenceAntlrHighlighter<>(key, xformed);
    }

    @Override
    public void refresh(Document doc, Extraction ext, Parser.Result result, OffsetsBag bag) {
        NamedRegionReferenceSets<T> regions = ext.references(key);
        log("refresh {0} NamedRegionReferenceSets for {1}", regions.size(), doc);
        if (!regions.isEmpty()) {
            Map<T, AttributeSet> cache = new HashMap<>(cacheSize);
            for (NamedSemanticRegionReference<T> region : regions.asIterable()) {
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

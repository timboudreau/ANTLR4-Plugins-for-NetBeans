package org.nemesis.antlr.highlighting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.text.AttributeSet;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightRefreshTrigger;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;

/**
 * Base class for Antlr-based highlights layer factory. To use, create one with
 * a builder, and then delegate to it from one you register in mime lookup for
 * your mime type.
 *
 * @author Tim Boudreau
 */
public abstract class AntlrHighlightingLayerFactory implements HighlightsLayerFactory {

    private final List<? extends HighlighterFactory> all;

    AntlrHighlightingLayerFactory(List<HighlighterFactory> all) {
        System.out.println("AntlrHighlightingLayerFactory with " + all.size() + " layers");
        this.all = Collections.unmodifiableList(all);
    }

    @Override
    public HighlightsLayer[] createLayers(Context context) {
        List<HighlightsLayer> layers = new ArrayList<>(this.all.size());
        all.stream().map((f) -> f.createLayer(context)).filter((layer) -> (layer != null)).forEach((layer) -> {
            layers.add(layer);
        });
//        for (HighlighterFactory f : all) {
//            HighlightsLayer layer = f.createLayer(context);
//            if (layer != null) {
//                layers.add(layer);
//            }
//        }
        return layers.toArray(new HighlightsLayer[layers.size()]);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<HighlighterFactory> factories = new ArrayList<>(30);

        private Builder() {
        }

        Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, String id, Supplier<AntlrHighlighter> supp) {
            factories.add(HighlighterFactory.forRefreshTrigger(trigger, zorder, fixedSize, positionInZOrder, id, supp));
            return this;
        }

        public <T> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, String mimeType, RegionsKey<T> key, Function<SemanticRegion<T>, String> coloringNameProvider) {
            Supplier<AntlrHighlighter> supp = () -> {
                return AntlrHighlighter.create(mimeType, key, coloringNameProvider);
            };
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, String mimeType, NameReferenceSetKey<?> key, String coloringName) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(mimeType, key, coloringName);
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public <T extends Enum<T>> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, String mimeType, NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, String> coloringNameProvider) {
            Supplier<AntlrHighlighter> supp = ()
                    -> AntlrHighlighter.create(mimeType, key, coloringNameProvider);
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NameReferenceSetKey<?> key, String mimeType, String coloringName) {
            Supplier<AntlrHighlighter> supp = ()
                    -> AntlrHighlighter.create(mimeType, key, coloringName);
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NamedRegionKey<?> key, String mimeType, String coloringName) {
            Supplier<AntlrHighlighter> supp = ()
                    -> AntlrHighlighter.create(key, mimeType, coloringName);
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public <T extends Enum<T>> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NamedRegionKey<T> key, String mimeType, Function<NamedSemanticRegion<T>, String> coloringNameProvider) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, mimeType, coloringNameProvider);
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public <T extends Enum<T>> Builder create(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NamedRegionKey<T> key, Function<NamedSemanticRegion<T>, AttributeSet> coloringLookup) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, coloringLookup);
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public <T extends Enum<T>> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, AttributeSet> coloringLookup) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, coloringLookup);
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public <T> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, RegionsKey<T> key, Function<SemanticRegion<T>, AttributeSet> coloringLookup) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, coloringLookup);
            return add(trigger, zorder, fixedSize, positionInZOrder, key.toString(), supp);
        }

        public HighlightsLayerFactory build() {
            return new AntlrHighlightingLayerFactory.Impl(factories);
        }
    }

    static final class Impl extends AntlrHighlightingLayerFactory {

        Impl(List<HighlighterFactory> all) {
            super(all);
        }
    }
}

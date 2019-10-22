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

import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private static final Logger LOG = Logger.getLogger(
            AntlrHighlightingLayerFactory.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    AntlrHighlightingLayerFactory(List<HighlighterFactory> all) {
        Collections.sort(all);
        this.all = Collections.unmodifiableList(all);
        LOG.log(Level.FINE, "Created an AntlrHighlightingLayerFactory with {0}",
                all);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AntlrHighlightingLayerFactory(");
        sb.append(Strings.join(", ", all));
        return sb.append(')').toString();
    }

    @Override
    public HighlightsLayer[] createLayers(Context context) {
        List<HighlightsLayer> layers = new ArrayList<>(this.all.size());
        for (HighlighterFactory f : all) {
            HighlightsLayer layer = f.createLayer(context);
            if (layer != null) {
                LOG.log(Level.FINEST, "AntlrHighlighterFactory {0} created layer {1} for {2}",
                        new Object[]{f, layer, context});
                layers.add(layer);
            } else {
                LOG.log(Level.WARNING, "AntlrHighlighterFactory {0} returned null "
                        + "creating layer for {1}", new Object[]{f, context});
            }
        }
        return layers.toArray(new HighlightsLayer[layers.size()]);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<HighlighterFactory> factories = new ArrayList<>(30);
        private static final AtomicInteger ids = new AtomicInteger();

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
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public <K extends Enum<K>> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NameReferenceSetKey<K> key, String mimeType, Function<NamedSemanticRegionReference<K>, String> coloringFinder) {
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            Supplier<AntlrHighlighter> supp = () -> {
                return AntlrHighlighter.create(mimeType, key, coloringFinder);
            };
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString,
                    supp);
        }

        public Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, String mimeType, NameReferenceSetKey<?> key, String coloringName) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(mimeType, key, coloringName);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public <T extends Enum<T>> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, String mimeType, NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, String> coloringNameProvider) {
            Supplier<AntlrHighlighter> supp = ()
                    -> AntlrHighlighter.create(mimeType, key, coloringNameProvider);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NameReferenceSetKey<?> key, String mimeType, String coloringName) {
            Supplier<AntlrHighlighter> supp = ()
                    -> AntlrHighlighter.create(mimeType, key, coloringName);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public <T> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, RegionsKey<T> key, String mimeType, String coloringName) {
            Supplier<AntlrHighlighter> supp = ()
                    -> AntlrHighlighter.create(mimeType, key, coloringName);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NamedRegionKey<?> key, String mimeType, String coloringName) {
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString,
                    new FixedMimeHighlighterSupplier(key, mimeType, coloringName));
        }

        public <T extends Enum<T>> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NamedRegionKey<T> key, Function<NamedSemanticRegion<T>, AttributeSet> coloringLookup, String mimeType) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, coloringLookup);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public <T extends Enum<T>> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NamedRegionKey<T> key, String mimeType, Function<NamedSemanticRegion<T>, String> coloringNameProvider) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, mimeType, coloringNameProvider);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public <T extends Enum<T>> Builder create(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NamedRegionKey<T> key, String mimeType, Function<NamedSemanticRegion<T>, AttributeSet> coloringLookup) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, coloringLookup);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public <T extends Enum<T>> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, AttributeSet> coloringLookup) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, coloringLookup);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
        }

        public <T> Builder add(HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, RegionsKey<T> key, Function<SemanticRegion<T>, AttributeSet> coloringLookup) {
            Supplier<AntlrHighlighter> supp = () -> AntlrHighlighter.create(key, coloringLookup);
            String keyString = key.toString() + "-" + ids.incrementAndGet();
            return add(trigger, zorder, fixedSize, positionInZOrder, keyString, supp);
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

    // For loggability
    static final class FixedMimeHighlighterSupplier implements Supplier<AntlrHighlighter> {

        private final NamedRegionKey<?> key;
        private final String mimeType;
        private final String defaultColoringName;

        public FixedMimeHighlighterSupplier(NamedRegionKey<?> key, String mimeType, String defaultColoringName) {
            this.key = key;
            this.mimeType = mimeType;
            this.defaultColoringName = defaultColoringName;
        }

        @Override
        public AntlrHighlighter get() {
            return AntlrHighlighter.create(key, mimeType, defaultColoringName);
        }

        @Override
        public String toString() {
            return "FixedMimeHighlighterSupplier(" + key + " for "
                    + mimeType + " coloring " + defaultColoringName;
        }
    }
}

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
package org.nemesis.antlr.navigator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JPopupMenu;
import org.nemesis.data.SemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.spi.navigator.NavigatorPanel;
import org.openide.awt.HtmlRenderer;

/**
 * Configuration object which aggregates the various bits of code that go into
 * creating the behavior of a navigator panel over an Extraction.
 *
 * @author Tim Boudreau
 */
public final class SemanticRegionPanelConfig<K> {

    private final Appearance<? super SemanticRegion<K>> appearance;
    private final ListModelPopulator<K, SemanticRegion<K>> populator;
    private final Consumer<JPopupMenu> popupMenuPopulator;
    private final BiConsumer<Extraction, List<? super SemanticRegion<K>>> elementFetcher;
    private final boolean sortable;
    private final String displayName;
    private final String hint;
    private final boolean trackCaret;

    private SemanticRegionPanelConfig(Appearance<SemanticRegion<K>> appearance,
            ListModelPopulator<K, SemanticRegion<K>> populator, Consumer<JPopupMenu> popupMenuPopulator,
            BiConsumer<Extraction, List<? super SemanticRegion<K>>> elementFetcher,
            String displayName, boolean sortable, String hint, boolean trackCaret) {
        this.appearance = appearance == null ? new DefaultAppearance() : appearance;
        this.populator = populator;
        this.popupMenuPopulator = popupMenuPopulator;
        this.hint = hint;
        this.displayName = displayName;
        this.sortable = sortable;
        this.elementFetcher = elementFetcher;
        this.trackCaret = trackCaret;
    }

    boolean isTrackCaret() {
        return trackCaret;
    }

    RegionsKey<K> key() {
        if (elementFetcher instanceof SemanticRegionPanelConfig.Builder.FetchByKey<?>) {
            return ((SemanticRegionPanelConfig.Builder.FetchByKey<K>) elementFetcher).key();
        }
        return null;
    }

    /**
     * Builder for navigator panel configurations - supply at least a display
     * name and a function to extract elements from a parse; return the
     * resulting NavigatorPanelConfig of this builder from a static method, and
     * register it with &064;AntlrNavigatorPanelRegistration to add a navigator
     * panel.
     *
     * @param <K>
     */
    public static final class Builder<K> {

        private boolean sortable;
        private Consumer<JPopupMenu> popupMenuPopulator;
        private BiConsumer<Extraction, List<? super SemanticRegion<K>>> elementFetcher;
        private ListModelPopulator<K, SemanticRegion<K>> populator;
        private String displayName;
        private String hint;
        private Appearance<SemanticRegion<K>> appearance;
        private boolean trackCaret;

        private Builder() {

        }

        public Builder<K> trackCaret() {
            trackCaret = true;
            return this;
        }

        public SemanticRegionPanelConfig<K> build() {
            if (elementFetcher == null) {
                throw new IllegalStateException("Element fetcher must be set");
            }
            if (displayName == null) {
                throw new IllegalStateException("Display name must be set");
            }
            return new SemanticRegionPanelConfig<>(appearance, populator == null ? new DefaultPopulator<K>() : populator,
                    popupMenuPopulator, elementFetcher, displayName, sortable, hint, trackCaret);
        }

        static final class DefaultPopulator<K> implements ListModelPopulator<K, SemanticRegion<K>> {

            @Override
            public int populateListModel(Extraction extraction, List<? extends SemanticRegion<K>> fetched, Collection<? super SemanticRegion<K>> model, SemanticRegion<K> oldSelection, SortTypes sort) {
                int sel = -1;
                for (SemanticRegion<K> region : fetched) {
                    if (sel == -1 && oldSelection != null && Objects.equals(region.key(), oldSelection.key())) {
                        sel = model.size();
                    }
                    model.add(region);
                }
                return sel;
            }

        }

        /**
         * Supply an object which will copy items from the fetched list of items
         * and put them into a new empty list model which is passed in. May be
         * called once per builder. Optional.
         *
         * @param populator The populator
         * @return this
         */
        public Builder<K> withListModelPopulator(ListModelPopulator<K, SemanticRegion<K>> populator) {
            if (this.populator != null) {
                throw new IllegalStateException("Populator already set to " + this.populator);
            }
            this.populator = populator;
            return this;
        }

        /**
         * Set the display name of this navigator panel. Required.
         *
         * @param displayName The display name (should be localized)
         * @return this
         */
        public Builder<K> setDisplayName(String displayName) {
            if (this.displayName != null) {
                throw new IllegalStateException("Display name already set");
            }
            this.displayName = displayName;
            return this;
        }

        /**
         * Set the tooltip for this navigator panel.
         *
         * @param hint The hint
         * @return this
         */
        public Builder<K> setHint(String hint) {
            if (this.hint != null) {
                throw new IllegalStateException("Hint already set");
            }
            this.hint = hint;
            return this;
        }

        Builder<K> fetchingWith(RegionsKey<K> key) {
            return fetchingWith(new FetchByKey<>(key));
        }

        public Builder<K> setSingleIcon(String icon) {
            return withAppearance(new IconAppearance<>(icon));
        }

        /**
         * In addition to the fetcher or key supplied when creating this object,
         * also use the passed key to look up additional regions.
         *
         * @param key The key
         * @return this
         */
        public Builder<K> alsoFetchingWith(RegionsKey<K> key) {
            return fetchingWith(new FetchByKey<>(key));
        }

        private static final class FetchByKey<K> implements BiConsumer<Extraction, List<? super SemanticRegion<K>>> {

            private final RegionsKey<K> key;

            public FetchByKey(RegionsKey<K> key) {
                this.key = key;
            }

            public RegionsKey<K> key() {
                return key;
            }

            @Override
            public void accept(Extraction t, List<? super SemanticRegion<K>> u) {
                for (SemanticRegion<K> region : t.regions(key)) {
                    u.add(region);
                }
            }
        }

        /**
         * In addition to the fetcher or key supplied when creating this object,
         * also use the passed key to look up additional regions.
         *
         * @param key The key
         * @return this
         */
        public Builder<K> alsoFetchingWith(BiConsumer<Extraction, List<? super SemanticRegion<K>>> fetcher) {
            return fetchingWith(fetcher);
        }

        Builder<K> fetchingWith(BiConsumer<Extraction, List<? super SemanticRegion<K>>> fetcher) {
            if (this.elementFetcher != null) {
                this.elementFetcher = this.elementFetcher.andThen(fetcher);
            } else {
                this.elementFetcher = fetcher;
            }
            return this;
        }

        /**
         * Use the passed Appearance to configure the rendering of each row with
         * color, html text, icon, indentaton and more.
         *
         * @param appearance An appearance configurer
         * @return
         */
        public Builder<K> withAppearance(Appearance<SemanticRegion<K>> appearance) {
            if (this.appearance != null) {
                this.appearance = this.appearance.and(appearance);
            } else {
                this.appearance = appearance;
            }
            return this;
        }

        /**
         * If called, the popup menu for this item will include menu items for
         * setting how the elements are sorted in the view.
         *
         * @return this
         */
        public Builder<K> sortable() {
            sortable = true;
            return this;
        }

        /**
         * If you want to add items to the navigator panel's popup menu, add
         * them here.
         *
         * @param populator
         * @return
         */
        public Builder<K> popupMenuPopulator(Consumer<JPopupMenu> populator) {
            if (popupMenuPopulator != null) {
                this.popupMenuPopulator = popupMenuPopulator.andThen(populator);
            } else {
                this.popupMenuPopulator = populator;
            }
            return this;
        }
    }

    String hint() {
        return hint;
    }

    String displayName() {
        return displayName;
    }

    boolean sortable() {
        return sortable;
    }

    public static <K> Builder<K> builder(RegionsKey<K> key) {
        return new Builder<K>().fetchingWith(key);
    }

    public static <K> Builder<K> builder(BiConsumer<Extraction, List<? super SemanticRegion<K>>> elementFetcher) {
        return new Builder<K>().fetchingWith(elementFetcher);
    }

    void configureAppearance(HtmlRenderer.Renderer on, SemanticRegion<K> region, boolean componentActive, SortTypes sort) {
        appearance.configureAppearance(on, region, componentActive, null, sort);
    }

    void onPopulatePopupMenu(JPopupMenu menu) {
        if (popupMenuPopulator != null) {
            popupMenuPopulator.accept(menu);
        }
    }

    int populateListModel(Extraction extraction, List<? super SemanticRegion<K>> newListModel, SemanticRegion<K> oldSelection, SortTypes requestedSort) {
        List<SemanticRegion<K>> items = new ArrayList<>(100);
        elementFetcher.accept(extraction, items);
        return populator.populateListModel(extraction, items, newListModel, oldSelection, requestedSort);
    }

    public static <T> Appearance<T> simpleAppearance(String icon) {
        return new IconAppearance<>(icon);
    }

    public NavigatorPanel toNavigatorPanel(String mimeType) {
        return new GenericSemanticRegionNavigatorPanel<>(mimeType, this, appearance);
    }
}

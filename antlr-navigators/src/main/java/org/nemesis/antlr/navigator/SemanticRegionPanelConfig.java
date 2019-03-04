/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.navigator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
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

    private final Appearance<SemanticRegion<K>> appearance;
    private final ListModelPopulator<K> populator;
    private final Consumer<JPopupMenu> popupMenuPopulator;
    private final BiConsumer<Extraction, List<? super SemanticRegion<K>>> elementFetcher;
    private final boolean sortable;
    private final String displayName;
    private final String hint;

    private SemanticRegionPanelConfig(Appearance<SemanticRegion<K>> appearance,
            ListModelPopulator<K> populator, Consumer<JPopupMenu> popupMenuPopulator,
            BiConsumer<Extraction, List<? super SemanticRegion<K>>> elementFetcher,
            String displayName, boolean sortable, String hint) {
        this.appearance = appearance;
        this.populator = populator;
        this.popupMenuPopulator = popupMenuPopulator;
        this.hint = hint;
        this.displayName = displayName;
        this.sortable = sortable;
        this.elementFetcher = elementFetcher;
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
        private ListModelPopulator<K> populator;
        private String displayName;
        private String hint;
        private Appearance<SemanticRegion<K>> appearance;

        private Builder() {

        }

        public SemanticRegionPanelConfig<K> build() {
            if (elementFetcher == null) {
                throw new IllegalStateException("Element fetcher must be set");
            }
            if (displayName == null) {
                throw new IllegalStateException("Display name must be set");
            }
            return new SemanticRegionPanelConfig<>(appearance, populator == null ? new DefaultPopulator<K>() : populator,
                    popupMenuPopulator, elementFetcher, displayName, sortable, hint);
        }

        static final class DefaultPopulator<K> implements ListModelPopulator<K> {

            @Override
            public int populateListModel(Extraction extraction, List<SemanticRegion<K>> fetched, DefaultListModel<SemanticRegion<K>> model, SemanticRegion<K> oldSelection, SortTypes sort) {
                int sel = -1;
                for (SemanticRegion<K> region : fetched) {
                    if (sel == -1 && oldSelection != null && Objects.equals(region.key(), oldSelection.key())) {
                        sel = model.size();
                    }
                    model.addElement(region);
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
        public Builder<K> withListModelPopulator(ListModelPopulator<K> populator) {
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
            return withAppearance(new IconAppearance<SemanticRegion<K>> (icon));
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
        if (appearance != null) {
            appearance.configureAppearance(on, region, componentActive, sort);
        } else {
            NoAppearance.defaultConfigure(on, region, componentActive);
        }
    }

    void onPopulatePopupMenu(JPopupMenu menu) {
        if (popupMenuPopulator != null) {
            popupMenuPopulator.accept(menu);
        }
    }

    /**
     * Updates a list model and sorts it.
     *
     * @param <K> The enum type
     */
    public interface ListModelPopulator<K> {

        /**
         * Populate the list model with whatever objects this panel should find
         * in the extraction.
         *
         * @param extraction The extraction
         * @param model A new, empty model
         * @param oldSelection The selection in the panel at this time
         * @param requestedSort The sort order that should be used
         * @return The index of the old selection (if not null) in the new set
         * of model elements, or -1 if not found
         */
        int populateListModel(Extraction extraction, List<SemanticRegion<K>> fetched, DefaultListModel<SemanticRegion<K>> model, SemanticRegion<K> oldSelection, SortTypes sort);
    }

    int populateListModel(Extraction extraction, DefaultListModel<SemanticRegion<K>> newListModel, SemanticRegion<K> oldSelection, SortTypes requestedSort) {
        List<SemanticRegion<K>> items = new ArrayList<>(100);
        elementFetcher.accept(extraction, items);
        return populator.populateListModel(extraction, items, newListModel, oldSelection, requestedSort);
    }

    public static <T> Appearance<T> simpleAppearance(String icon) {
        return new IconAppearance<>(icon);
    }

    public NavigatorPanel toNavigatorPanel(String mimeType) {
        return new GenericSemanticRegionNavigatorPanel<>(mimeType, this);
    }
}

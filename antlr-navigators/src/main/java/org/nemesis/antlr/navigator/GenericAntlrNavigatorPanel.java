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

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.nemesis.extraction.Extraction;
import org.nemesis.data.named.NamedSemanticRegion;
import static org.nemesis.antlr.navigator.SortTypes.NATURAL;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.swing.html.HtmlRenderer;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.cookies.EditorCookie;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
final class GenericAntlrNavigatorPanel<K extends Enum<K>> extends AbstractAntlrListNavigatorPanel<NamedSemanticRegion<K>, ActivatedTcPreCheckJList<NamedSemanticRegion<K>>> {

    private static final String PREFERENCES_KEY_NAVIGATOR_SORT = "navigator-sort";
    static final boolean DUPLICATE_DEBUG = Boolean.getBoolean("antlr.navigator.debug.duplicates");

    private SortTypes sort = SortTypes.NATURAL;
    private final String mimeType;
    private final NavigatorPanelConfig<K> config;
    private final Appearance<? super NamedSemanticRegion<K>> appearance;
    private CaretTracker tracker;

    GenericAntlrNavigatorPanel(String mimeType, NavigatorPanelConfig<K> config, Appearance<? super NamedSemanticRegion<K>> appearance) {
        this.mimeType = mimeType;
        this.config = config;
        this.appearance = appearance;
    }

    protected JList list() {
        return list;
    }

    @Override
    public String getDisplayHint() {
        return config.hint();
    }

    @Override
    public String getDisplayName() {
        return config.displayName();
    }

    private String sortKey() {
        String mt = mimeType.replace('/', '-').replace('+', '_');
        return mt + "-" + PREFERENCES_KEY_NAVIGATOR_SORT;
    }

    @Override
    protected void onBeforeCreateComponent() {
        Preferences prefs = NbPreferences.forModule(GenericAntlrNavigatorPanel.class);
        String savedSort = prefs.get(sortKey(), NATURAL.name());
        if (sort.name().equals(savedSort)) {
            this.sort = SortTypes.valueOf(savedSort);
        }
        trackingCaret = prefs.getBoolean(trackingKey(), true);
    }

    private String trackingKey() {
        return "caretTrack_" + mimeType.replace('/', '_').replace('+', '_');
    }

    @Override
    public void panelDeactivated() {
        super.panelDeactivated();
        if (tracker != null) {
            tracker.detach();
        }
    }

    @Override
    protected void onAfterCreateComponent(ActivatedTcPreCheckJList<NamedSemanticRegion<K>> component) {
        if (config.isTrackCaret() && config.key() != null) {
            this.tracker = new CaretTracker(list, config.key());
        } else {
            this.tracker = null;
        }
    }

    SortTypes getSort() {
        return sort;
    }

    private void rebuildModel(EditorAndChangeAwareListModel<NamedSemanticRegion<K>> oldModel, SortTypes modelSortedAs) {
        if (oldModel.change != changeCount.get()) {
            return;
        }
        withNewModel(oldModel.semantics, oldModel.cookie, oldModel.change);
    }

    private Set<String> scopingDelimiters;

    Set<String> getDelimiters() {
        return scopingDelimiters;
    }

    @Override
    protected void withNewModel(Extraction extraction, EditorCookie ck, int forChange) {
        if (scopingDelimiters == null && !extraction.isPlaceholder()) {
            scopingDelimiters = config.delimiters(extraction);
        }
        NamedSemanticRegion<K> oldSelection = null;
        if (list.getModel() instanceof EditorAndChangeAwareListModel<?>) {
            oldSelection = list.getSelectedValue();
        }
        List<NamedSemanticRegion<K>> newItems = new ArrayList<>(120);
        List<NamedSemanticRegion<K>> debugItems = DUPLICATE_DEBUG ? new DuplicateCheckingList<>(newItems)
                : newItems;

        EditorAndChangeAwareListModel<NamedSemanticRegion<K>> newModel
                = new EditorAndChangeAwareListModel<>(newItems, ck, forChange, extraction);

        int newSelectedIndex = config.populateListModel(extraction, debugItems, oldSelection, sort);
        setNewModel(newModel, forChange, newSelectedIndex);
        updateCaretTracking(extraction, ck);
    }

    @SuppressWarnings("unchecked")
    void setSort(SortTypes sort) {
        SortTypes old = this.sort;
        if (old != sort) {
            ListModel<NamedSemanticRegion<K>> mdl = list.getModel();
            this.sort = sort;
            if (mdl instanceof EditorAndChangeAwareListModel<?> && mdl.getSize() > 0) {
                rebuildModel((EditorAndChangeAwareListModel<NamedSemanticRegion<K>>) mdl, old);
            }
            NbPreferences.forModule(GenericAntlrNavigatorPanel.class).put(
                    sortKey(), sort.name());
        }
    }

    private boolean trackingCaret;

    private boolean isTrackCaret() {
        return trackingCaret;
    }

    private void setTrackingCaret(boolean val) {
        if (trackingCaret != val) {
            trackingCaret = val;
            Preferences prefs = NbPreferences.forModule(getClass());
            prefs.putBoolean(trackingKey(), val);
            if (!trackingCaret) {
                if (tracker != null) {
                    tracker.detach();
                }
            } else {
                if (tracker != null) {
                    ListModel<?> lm = list().getModel();
                    if (lm instanceof EditorAndChangeAwareListModel<?>) {
                        EditorAndChangeAwareListModel<?> em = (EditorAndChangeAwareListModel<?>) lm;
                        tracker.track(em.semantics, em.cookie);
                    }
                }
            }
        }
    }

    private void toggleTrackingCaret() {
        setTrackingCaret(!isTrackCaret());
    }

    @Messages({
        "track-caret=Track Caret",
        "the-list-tootip=Click to navigate; right click to show popup"})
    @SuppressWarnings("unchecked")
    @Override
    protected ActivatedTcPreCheckJList<NamedSemanticRegion<K>> createComponent() {
        final ActivatedTcPreCheckJList<NamedSemanticRegion<K>> result = new ActivatedTcPreCheckJList<>();
        result.setToolTipText(Bundle.the_list_tootip());
        // Listen for clicks, not selection events
        result.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() != MouseEvent.BUTTON1) {
                    // Dynamic popup menu to set sort order and showing labels
                    JPopupMenu menu = new JPopupMenu();
                    for (SortTypes sort : SortTypes.values()) {
                        if (config.isSortTypeEnabled(sort)) {
                            menu.add(sort.toMenuItem(GenericAntlrNavigatorPanel.this::getSort,
                                    GenericAntlrNavigatorPanel.this::setSort));
                        }
                    }
                    config.onPopulatePopupMenu(menu);
                    if (tracker != null) {
                        JCheckBoxMenuItem item = new JCheckBoxMenuItem(Bundle.track_caret());
                        item.setSelected(isTrackCaret());
                        item.addActionListener(ae -> {
                            toggleTrackingCaret();
                        });
                        if (menu.getComponentCount() > 0) {
                            menu.add(new JSeparator());
                        }
                        menu.add(item);
                    }
                    menu.show(list, e.getX(), e.getY());
                } else {
                    EditorAndChangeAwareListModel<NamedSemanticRegion<K>> mdl
                            = result.getModel() instanceof EditorAndChangeAwareListModel<?>
                            ? (EditorAndChangeAwareListModel<NamedSemanticRegion<K>>) result.getModel() : null;
                    if (mdl != null) {
                        int loc = result.locationToIndex(e.getPoint());
                        if (loc >= 0 && loc < result.getModel().getSize()) {
                            NamedSemanticRegion<K> el = result.getModel().getElementAt(loc);
                            moveTo(mdl.cookie, el);
                        }
                    }
                }
            }
        });
        // enter should navigate and send focus
        result.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "nav");
        result.getActionMap().put("nav", new AbstractAction() {
            @Override
            @SuppressWarnings("unchecked")
            public void actionPerformed(ActionEvent e) {
                if (list != null && list.getModel() instanceof EditorAndChangeAwareListModel<?>) {
                    EditorAndChangeAwareListModel<NamedSemanticRegion<K>> mdl = (EditorAndChangeAwareListModel<NamedSemanticRegion<K>>) list.getModel();
                    int selected = list.getSelectedIndex();
                    if (selected >= 0 && selected < mdl.getSize()) {
                        moveTo(mdl.cookie, mdl.getElementAt(selected));
                    }
                }
            }
        });
        // Use the fast, lightweight HtmlRenderer I wrote in 2002 for the actual rendering
        result.setCellRenderer(new Ren(appearance, this::getSort, this::getDelimiters));
        return result;
    }

    private void updateCaretTracking(Extraction extraction, EditorCookie ck) {
        if (tracker != null && trackingCaret) {
            tracker.track(extraction, ck);
        }
    }

    private static final class Ren<K extends Enum<K>> implements ListCellRenderer<NamedSemanticRegion<K>> {

        // Use the fast, lightweight HtmlRenderer I wrote in 2002 for the actual rendering
        private final HtmlRenderer.Renderer renderer = HtmlRenderer.createRenderer();
        private final Appearance<? super NamedSemanticRegion<K>> config;
        private final Supplier<SortTypes> sortSupplier;
        private final Supplier<Set<String>> delimiterSupplier;

        public Ren(Appearance<? super NamedSemanticRegion<K>> config, Supplier<SortTypes> sortSupplier, Supplier<Set<String>> delimiterSupplier) {
            this.config = config;
            this.sortSupplier = sortSupplier;
            this.delimiterSupplier = delimiterSupplier;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(JList<? extends NamedSemanticRegion<K>> list, NamedSemanticRegion<K> value, int index, boolean isSelected, boolean cellHasFocus) {
            String txt = value.name();
            Component render = renderer.getListCellRendererComponent(list, txt, index, isSelected, cellHasFocus);
            renderer.setRenderStyle(HtmlRenderer.STYLE_CLIP);
            boolean active = list != null && list instanceof ActivatedTcPreCheckJList<?> && ((ActivatedTcPreCheckJList) list).isActive();
            config.configureAppearance(renderer, value, active, delimiterSupplier.get(), sortSupplier.get());
            return render;
        }
    }

    private void moveTo(EditorCookie cookie, NamedSemanticRegion<K> el) {
        try {
            StyledDocument doc = cookie.openDocument();
            // Prefer EditorRegistry - allows editor the embedded editor
            // in the Antlr preview (or any other component hacked into
            // EditorRegistry) to be preferred
            JTextComponent comp = EditorRegistry.findComponent(doc);
            if (comp != null) {
                moveTo(comp, el);
                return;
            }
            
            JEditorPane pane = NbDocument.findRecentEditorPane(cookie);
            if (pane != null) {
                moveTo(pane, el);
                return;
            }
            JEditorPane[] panes = cookie.getOpenedPanes();
            if (panes.length > 0) {
                moveTo(panes[0], el);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @SuppressWarnings("deprecation")
    private void moveTo(JTextComponent pane, NamedSemanticRegion<K> el) {
        assert EventQueue.isDispatchThread();
        // References are associated with extracted *bounds*, but usually
        // two sets of NamedSemanticRegions are extracted, one for the container
        // element and one for the bounds of the name itself, and we can
        // ask the extraction for the corresponding name key, and fetch
        // the corresponding name element - which is a better thing to
        // select, as selecting the entire text of a Java method or similar
        // on click is just annoying.  So, if we were built with a single
        // NamedRegionKey, the list model has the current extraction we're
        // working from, so it can fetch the actual name item to choose
        // selection coordinates
        if (list().getModel() instanceof EditorAndChangeAwareListModel<?>) {
            EditorAndChangeAwareListModel<?> mdl = (EditorAndChangeAwareListModel<?>) list.getModel();
            NamedRegionKey<K> key = config.key();
            if (key != null) {
                el = mdl.nameRegionFor(key, el);
            }
        }
        int len = pane.getDocument().getLength();
        if (el.start() < len && el.end() <= len) {
            moveTo(pane, el.start(), el.end());
        }
    }

    void setNewModel(EditorAndChangeAwareListModel<NamedSemanticRegion<K>> mdl, int expectedChange, int selectedIndex) {
        Mutex.EVENT.readAccess(() -> {
            if (list == null) {
                return;
            }
            if (changeCount.get() != expectedChange) {
                return;
            }
            if (mdl == null) {
                ListModel<NamedSemanticRegion<K>> old = list.getModel();
                if (old.getSize() > 0) {
                    list.setModel(EmptyListModel.emptyModel());
                }
            } else {
                list.setModel(mdl);
                if (selectedIndex >= 0 && selectedIndex < mdl.getSize()) {
                    list.getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);
                    Rectangle2D rect = list.getCellBounds(selectedIndex, selectedIndex);
                    list.scrollRectToVisible(rect.getBounds());
                }
            }
            list.invalidate();
            list.revalidate();
            list.repaint();
        });
    }

    @Override
    protected void setNoModel(int forChange) {
        setNewModel(null, forChange, -1);
    }
}

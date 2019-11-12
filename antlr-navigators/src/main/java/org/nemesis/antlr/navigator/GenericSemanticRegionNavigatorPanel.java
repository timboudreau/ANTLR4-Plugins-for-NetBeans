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
import java.util.Objects;
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
import org.nemesis.extraction.Extraction;
import org.nemesis.data.SemanticRegion;
import org.openide.awt.HtmlRenderer;
import org.openide.cookies.EditorCookie;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
final class GenericSemanticRegionNavigatorPanel<K> extends AbstractAntlrListNavigatorPanel<SemanticRegion<K>, ActivatedTcPreCheckJList<SemanticRegion<K>>> {

    private final String mimeType;
    private final SemanticRegionPanelConfig<K> config;
    private final Appearance<? super SemanticRegion<K>> appearance;
    private CaretTracker tracker;
    private boolean trackingCaret;

    GenericSemanticRegionNavigatorPanel(String mimeType, SemanticRegionPanelConfig<K> config, Appearance<? super SemanticRegion<K>> appearance) {
        this.mimeType = mimeType;
        this.config = config;
        this.appearance = appearance;
    }

    @Override
    public String getDisplayHint() {
        return config.hint();
    }

    @Override
    public String getDisplayName() {
        return config.displayName();
    }

    @Override
    public void panelDeactivated() {
        super.panelDeactivated();
        if (tracker != null) {
            tracker.detach();
        }
    }

    @Override
    protected void onBeforeCreateComponent() {
        super.onBeforeCreateComponent();
        Preferences prefs = NbPreferences.forModule(GenericAntlrNavigatorPanel.class);
        trackingCaret = prefs.getBoolean(trackingKey(), true);
    }

    @Override
    protected void onAfterCreateComponent(ActivatedTcPreCheckJList<SemanticRegion<K>> component) {
        if (config.isTrackCaret() && config.key() != null) {
            this.tracker = new CaretTracker(list, config.key());
        } else {
            this.tracker = null;
        }
    }

    @Override
    protected void withNewModel(Extraction extraction, EditorCookie ck, int forChange) {
        SemanticRegion<K> oldSelection = null;
        if (list.getModel() instanceof EditorAndChangeAwareListModel<?>) {
            oldSelection = list.getSelectedValue();
        }
        List<SemanticRegion<K>> nue = new ArrayList<>(120);
        EditorAndChangeAwareListModel<SemanticRegion<K>> newModel
                = new EditorAndChangeAwareListModel<>(nue, ck, forChange, extraction);

        int newSelectedIndex = config.populateListModel(extraction, nue, oldSelection, SortTypes.NATURAL);
        setNewModel(newModel, forChange, newSelectedIndex);
        updateCaretTracking(extraction, ck);
    }

    protected JList list() {
        return list;
    }

    private boolean isTrackCaret() {
        return trackingCaret;
    }

    private String trackingKey() {
        return "caretTrack_" + mimeType.replace('/', '_').replace('+', '_');
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

    @SuppressWarnings("unchecked")
    protected ActivatedTcPreCheckJList<SemanticRegion<K>> createComponent() {
        final ActivatedTcPreCheckJList<SemanticRegion<K>> result = new ActivatedTcPreCheckJList<>();
        result.setToolTipText(Bundle.the_list_tootip());
        // Listen for clicks, not selection events
        result.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() != MouseEvent.BUTTON1) {
                    // Dynamic popup menu to set sort order and showing labels
                    JPopupMenu menu = new JPopupMenu();
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
                    EditorAndChangeAwareListModel<SemanticRegion<K>> mdl
                            = result.getModel() instanceof EditorAndChangeAwareListModel<?>
                            ? (EditorAndChangeAwareListModel<SemanticRegion<K>>) result.getModel() : null;
                    if (mdl != null) {
                        int loc = result.locationToIndex(e.getPoint());
                        if (loc >= 0 && loc < result.getModel().getSize()) {
                            SemanticRegion<K> el = result.getModel().getElementAt(loc);
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
                    EditorAndChangeAwareListModel<SemanticRegion<K>> mdl = (EditorAndChangeAwareListModel<SemanticRegion<K>>) list.getModel();
                    int selected = list.getSelectedIndex();
                    if (selected >= 0 && selected < mdl.getSize()) {
                        moveTo(mdl.cookie, mdl.getElementAt(selected));
                    }
                }
            }
        });
        // Use the fast, lightweight HtmlRenderer I wrote in 2002 for the actual rendering
        result.setCellRenderer(new Ren(appearance, () -> SortTypes.NATURAL));
        return result;
    }

    private static final class Ren<K> implements ListCellRenderer<SemanticRegion<K>> {

        // Use the fast, lightweight HtmlRenderer I wrote in 2002 for the actual rendering
        private final HtmlRenderer.Renderer renderer = HtmlRenderer.createRenderer();
        private final Appearance<? super SemanticRegion<K>> config;
        private final Supplier<SortTypes> sortSupplier;

        public Ren(Appearance<SemanticRegion<K>> config, Supplier<SortTypes> sortSupplier) {
            this.config = config;
            this.sortSupplier = sortSupplier;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(JList<? extends SemanticRegion<K>> list, SemanticRegion<K> value, int index, boolean isSelected, boolean cellHasFocus) {
            String txt = Objects.toString(value.key());
            Component render = renderer.getListCellRendererComponent(list, txt, index, isSelected, cellHasFocus);
            renderer.setRenderStyle(HtmlRenderer.STYLE_CLIP);
            boolean active = list != null && list instanceof ActivatedTcPreCheckJList<?> && ((ActivatedTcPreCheckJList) list).isActive();
            config.configureAppearance(renderer, value, active, null, sortSupplier.get());
            return render;
        }
    }

    private void moveTo(EditorCookie cookie, SemanticRegion<K> el) {
        try {
            cookie.openDocument();
            JEditorPane[] panes = cookie.getOpenedPanes();
            if (panes.length > 0) {
                moveTo(panes[0], el);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @SuppressWarnings("deprecation")
    private void moveTo(JEditorPane pane, SemanticRegion<K> el) {
        assert EventQueue.isDispatchThread();
        int len = pane.getDocument().getLength();
        if (el.start() < len && el.end() <= len) {
            moveTo(pane, el.start(), el.end());
        }
    }

    void setNewModel(EditorAndChangeAwareListModel<SemanticRegion<K>> mdl, int expectedChange, int selectedIndex) {
        Mutex.EVENT.readAccess(() -> {
            if (list == null) {
                return;
            }
            if (changeCount.get() != expectedChange) {
                return;
            }
            if (mdl == null) {
                ListModel<SemanticRegion<K>> old = list.getModel();
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

    private void updateCaretTracking(Extraction extraction, EditorCookie ck) {
        if (tracker != null && trackingCaret) {
            tracker.track(extraction, ck);
        }
    }
}

package org.nemesis.antlr.navigator;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import org.nemesis.extraction.Extraction;
import org.nemesis.data.named.NamedSemanticRegion;
import static org.nemesis.antlr.navigator.SortTypes.NATURAL;
import org.openide.awt.HtmlRenderer;
import org.openide.cookies.EditorCookie;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
final class GenericAntlrNavigatorPanel<K extends Enum<K>> extends AbstractAntlrNavigatorPanel<NamedSemanticRegion<K>> {

    private static final String PREFERENCES_KEY_NAVIGATOR_SORT = "navigator-sort";

    private SortTypes sort = SortTypes.NATURAL;
    private final String mimeType;
    private final NavigatorPanelConfig<K> config;

    public GenericAntlrNavigatorPanel(String mimeType, NavigatorPanelConfig<K> config) {
        this.mimeType = mimeType;
        this.config = config;
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
        String mt = mimeType.replace('/', '-');
        return mt + "-" + PREFERENCES_KEY_NAVIGATOR_SORT;
    }

    @Override
    protected void onBeforeCreateComponent() {
        String savedSort = NbPreferences.forModule(GenericAntlrNavigatorPanel.class)
                .get(sortKey(), NATURAL.name());
        if (sort.name().equals(savedSort)) {
            this.sort = SortTypes.valueOf(savedSort);
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

    @Override
    protected void withNewModel(Extraction extraction, EditorCookie ck, int forChange) {
        NamedSemanticRegion<K> oldSelection = null;
        if (list.getModel() instanceof EditorAndChangeAwareListModel<?>) {
            oldSelection = list.getSelectedValue();
        }
        EditorAndChangeAwareListModel<NamedSemanticRegion<K>> newModel
                = new EditorAndChangeAwareListModel<>(ck, forChange, extraction);

        int newSelectedIndex = config.populateListModel(extraction, newModel, oldSelection, sort);
        setNewModel(newModel, forChange, newSelectedIndex);
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

    @Messages({
        "the-list-tootip=Click to navigate; right click to show popup"})
    @SuppressWarnings("unchecked")
    protected ActivatedTcPreCheckJList<NamedSemanticRegion<K>> createList() {
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
                        moveTo(mdl.cookie, mdl.elementAt(selected));
                    }
                }
            }
        });
        // Use the fast, lightweight HtmlRenderer I wrote in 2002 for the actual rendering
        result.setCellRenderer(new Ren(config, this::getSort));
        return result;
    }

    private static final class Ren<K extends Enum<K>> implements ListCellRenderer<NamedSemanticRegion<K>> {

        // Use the fast, lightweight HtmlRenderer I wrote in 2002 for the actual rendering
        private final HtmlRenderer.Renderer renderer = HtmlRenderer.createRenderer();
        private final NavigatorPanelConfig config;
        private final Supplier<SortTypes> sortSupplier;

        public Ren(NavigatorPanelConfig config, Supplier<SortTypes> sortSupplier) {
            this.config = config;
            this.sortSupplier = sortSupplier;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(JList<? extends NamedSemanticRegion<K>> list, NamedSemanticRegion<K> value, int index, boolean isSelected, boolean cellHasFocus) {
            String txt = value.name();
            Component render = renderer.getListCellRendererComponent(list, txt, index, isSelected, cellHasFocus);
            renderer.setRenderStyle(HtmlRenderer.STYLE_CLIP);
            boolean active = list != null && list instanceof ActivatedTcPreCheckJList<?> && ((ActivatedTcPreCheckJList) list).isActive();
            config.configureAppearance(renderer, value, active, sortSupplier.get());
            return render;
        }
    }

    private void moveTo(EditorCookie cookie, NamedSemanticRegion<K> el) {
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
    private void moveTo(JEditorPane pane, NamedSemanticRegion<K> el) {
        assert EventQueue.isDispatchThread();
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

    protected void setNoModel(int forChange) {
        setNewModel(null, forChange, -1);
    }
}

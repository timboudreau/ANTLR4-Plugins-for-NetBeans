package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.ListModel;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.data.graph.BitSetHeteroObjectGraph;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator.SortTypes.NATURAL;
import org.netbeans.spi.navigator.NavigatorPanel;
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
@NavigatorPanel.Registration(mimeType = "text/x-g4", displayName = "Rules", position = 0)
@Messages({"navigator-panel-name=ANTLR Rules",
    "navigator-panel-hint=Lists rules for ANTLR 4 grammar (.g4) files"})
public class AntlrNavigatorPanel extends AbstractAntlrNavigatorPanel<NamedSemanticRegion<RuleTypes>> {

    private static final String PREFERENCES_KEY_NAVIGATOR_ALTERNATIVES = "navigator-alternatives";
    private static final String PREFERENCES_KEY_NAVIGATOR_SORT = "navigator-sort";

    private boolean showAlternatives = true;
    private SortTypes sort = SortTypes.NATURAL;

    public AntlrNavigatorPanel() {
    }

    public String getDisplayHint() {
        return Bundle.navigator_panel_hint();
    }

    public String getDisplayName() {
        return Bundle.navigator_panel_name();
    }

    protected void onBeforeCreateComponent() {
        String savedSort = NbPreferences.forModule(AntlrNavigatorPanel.class)
                .get(PREFERENCES_KEY_NAVIGATOR_SORT, NATURAL.name());
        showAlternatives = NbPreferences.forModule(AntlrNavigatorPanel.class)
                .getBoolean(PREFERENCES_KEY_NAVIGATOR_ALTERNATIVES, true);
        if (sort.name().equals(savedSort)) {
            this.sort = SortTypes.valueOf(savedSort);
        }
    }

    SortTypes getSort() {
        return sort;
    }

    private void rebuildModel(EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>> oldModel, SortTypes modelSortedAs) {
        if (oldModel.change != changeCount.get()) {
            return;
        }
        int size = oldModel.getSize();
        List<NamedSemanticRegion<RuleTypes>> els = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            NamedSemanticRegion<RuleTypes> el = oldModel.getElementAt(i);
            if (el.kind() != RuleTypes.NAMED_ALTERNATIVES) {
                els.add(oldModel.getElementAt(i));
            }
        }
        replaceModelFromList(oldModel.cookie, els, modelSortedAs, oldModel.change, oldModel.semantics);
    }

    private static List<NamedSemanticRegion<RuleTypes>> toList(Iterable<NamedSemanticRegion<RuleTypes>> it) {
        List<NamedSemanticRegion<RuleTypes>> result = new ArrayList<>();
        for (NamedSemanticRegion<RuleTypes> ns : it) {
            result.add(ns);
        }
        return result;
    }

    private void replaceModelFromList(EditorCookie ck, List<NamedSemanticRegion<RuleTypes>> rules, SortTypes listSortedBy, int change, Extraction extraction) {
        EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>> nue
                = new EditorAndChangeAwareListModel<>(ck, change, extraction);
        NamedSemanticRegion<RuleTypes> oldSelection = null;
        if (list.getModel() instanceof EditorAndChangeAwareListModel) {
            oldSelection = list.getSelectedValue();
        }
        if (sort != listSortedBy) {
            sort.sort(rules, extraction, AntlrExtractor.RULE_NAME_REFERENCES);
        }
        int newSelectedIndex = -1;

        NamedSemanticRegions<RuleTypes> namedAlts = extraction.namedRegions(AntlrExtractor.NAMED_ALTERNATIVES);
        NamedSemanticRegions<RuleTypes> ruleBounds = extraction.namedRegions(AntlrExtractor.RULE_BOUNDS);

        BitSetHeteroObjectGraph<NamedSemanticRegion<RuleTypes>, NamedSemanticRegion<RuleTypes>, ?, NamedSemanticRegions<RuleTypes>> graph = ruleBounds.crossReference(namedAlts);

        for (int i = 0; i < rules.size(); i++) {
            NamedSemanticRegion<RuleTypes> rule = rules.get(i);
            if (oldSelection != null && rule.name().equals(oldSelection.name())) {
                newSelectedIndex = nue.getSize();
            }
            nue.addElement(rule);

            if (showAlternatives && graph.leftSlice().childCount(rule) > 0) {
                List<NamedSemanticRegion<RuleTypes>> alts = new ArrayList<>(graph.leftSlice().children(rule));
                switch (sort) {
                    case ALPHA:
                    case ALPHA_TYPE:
                        sort.sort(alts, extraction, AntlrExtractor.RULE_NAME_REFERENCES);
                        break;
                    case NATURAL:
                        // These will always be natural sort, as originally created
                        break;
                    default:
                        // Eigenvector and pagerank are meaningless here since
                        // these are labels for rules, not rules - they do
                        // not have a ranking score
                        SortTypes.ALPHA.sort(alts, extraction, AntlrExtractor.RULE_NAME_REFERENCES);
                }
                for (NamedSemanticRegion<RuleTypes> alt : alts) {
                    nue.addElement(alt);
                }
            }
        }
        setNewModel(nue, change, newSelectedIndex);
    }

    @SuppressWarnings("unchecked")
    void setShowAlternatives(boolean showAlternatives) {
        ListModel<NamedSemanticRegion<RuleTypes>> mdl = list.getModel();
        if (this.showAlternatives != showAlternatives) {
            this.showAlternatives = showAlternatives;
            if (mdl instanceof EditorAndChangeAwareListModel<?> && mdl.getSize() > 0) {
                rebuildModel((EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>>) mdl, sort);
            }
            NbPreferences.forModule(AntlrNavigatorPanel.class).putBoolean(
                    PREFERENCES_KEY_NAVIGATOR_ALTERNATIVES, showAlternatives);
        }
    }

    @SuppressWarnings("unchecked")
    void setSort(SortTypes sort) {
        SortTypes old = this.sort;
        if (old != sort) {
            ListModel<NamedSemanticRegion<RuleTypes>> mdl = list.getModel();
            this.sort = sort;
            if (mdl instanceof EditorAndChangeAwareListModel<?> && mdl.getSize() > 0) {
                rebuildModel((EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>>) mdl, old);
            }
            NbPreferences.forModule(AntlrNavigatorPanel.class).put(
                    PREFERENCES_KEY_NAVIGATOR_SORT, sort.name());
        }
    }

    @Messages({"show-alternatives=Show Named Alternatives",
        "list-tootip=Click to navigate; right click to show popup"})
    @SuppressWarnings("unchecked")
    protected ActivatedTcPreCheckJList<NamedSemanticRegion<RuleTypes>> createList() {
        final ActivatedTcPreCheckJList<NamedSemanticRegion<RuleTypes>> result = new ActivatedTcPreCheckJList<>();
        result.setToolTipText(Bundle.list_tootip());
        // Listen for clicks, not selection events
        result.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() != MouseEvent.BUTTON1) {
                    // Dynamic popup menu to set sort order and showing labels
                    JPopupMenu menu = new JPopupMenu();
                    for (SortTypes sort : SortTypes.values()) {
                        menu.add(sort.toMenuItem(AntlrNavigatorPanel.this::getSort, AntlrNavigatorPanel.this::setSort));
                    }
                    menu.add(new JSeparator());
                    final JCheckBoxMenuItem show = new JCheckBoxMenuItem();
                    show.setSelected(showAlternatives);
                    show.addActionListener(evt -> {
                        setShowAlternatives(!showAlternatives);
                    });
                    show.setText(Bundle.show_alternatives());
                    menu.add(show);
                    menu.show(list, e.getX(), e.getY());
                } else {
                    EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>> mdl
                            = result.getModel() instanceof EditorAndChangeAwareListModel<?>
                            ? (EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>>) result.getModel() : null;
                    if (mdl != null) {
                        int loc = result.locationToIndex(e.getPoint());
                        if (loc >= 0 && loc < result.getModel().getSize()) {
                            NamedSemanticRegion<RuleTypes> el = result.getModel().getElementAt(loc);
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
                    EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>> mdl = (EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>>) list.getModel();
                    int selected = list.getSelectedIndex();
                    if (selected >= 0 && selected < mdl.getSize()) {
                        moveTo(mdl.cookie, mdl.elementAt(selected));
                    }
                }
            }
        });
        // Use the fast, lightweight HtmlRenderer I wrote in 2002 for the actual rendering
        final HtmlRenderer.Renderer renderer = HtmlRenderer.createRenderer();
        renderer.setRenderStyle(HtmlRenderer.STYLE_CLIP);
        final EnumMap<RuleTypes, ? extends Icon> iconForType = iconForTypeMap();
        final ImageIcon alternativeIcon = alternativeIcon();
        assert alternativeIcon != null : "alternative.png is missing";
        result.setCellRenderer((JList<? extends NamedSemanticRegion<RuleTypes>> list1, NamedSemanticRegion<RuleTypes> value, int index, boolean isSelected, boolean cellHasFocus) -> {
            String txt = value.name();
            Component render = renderer.getListCellRendererComponent(list1, txt, index, isSelected, cellHasFocus);
            RuleTypes tgt = value.kind();
            switch (tgt) {
                case FRAGMENT:
                    txt = "<i>" + txt;
                    renderer.setHtml(true);
                    break;
                case LEXER:
                    renderer.setHtml(false);
                    break;
                case PARSER:
                    if (value.kind() != RuleTypes.NAMED_ALTERNATIVES) {
                        renderer.setHtml(true);
                        txt = "<b>" + txt;
                    }
                    break;
            }
            renderer.setText(txt);
            renderer.setParentFocused(isTopComponentActive());
            if (value.kind() == RuleTypes.NAMED_ALTERNATIVES) {
                // Subrules are indented for a tree-like display
                renderer.setIcon(alternativeIcon);
                renderer.setIndent(alternativeIcon.getIconWidth() + 5 + 3);
            } else {
                Icon icon = iconForType.get(tgt);
                assert icon != null : "null icon for " + tgt + " in " + iconForType;
                renderer.setIcon(icon);
                renderer.setIndent(5);
            }

            renderer.setIconTextGap(5);
            return render;
        });
        return result;
    }

    private boolean isTopComponentActive() {
        return list != null && list.isActive();
    }

    private void moveTo(EditorCookie cookie, NamedSemanticRegion<RuleTypes> el) {
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
    private void moveTo(JEditorPane pane, NamedSemanticRegion<RuleTypes> el) {
        assert EventQueue.isDispatchThread();
        int len = pane.getDocument().getLength();
        if (el.start() < len && el.end() <= len) {
            moveTo(pane, el.start(), el.end());
        }
    }

    void setNewModel(EditorAndChangeAwareListModel<NamedSemanticRegion<RuleTypes>> mdl, int expectedChange, int selectedIndex) {
        Mutex.EVENT.readAccess(() -> {
            if (list == null) {
                return;
            }
            if (changeCount.get() != expectedChange) {
                return;
            }
            if (mdl == null) {
                ListModel<NamedSemanticRegion<RuleTypes>> old = list.getModel();
                if (old.getSize() > 0) {
                    list.setModel(EmptyListModel.EMPTY_MODEL);
                }
            } else {
                list.setModel(mdl);
                if (selectedIndex != -1 && selectedIndex < mdl.getSize()) {
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

    protected void withNewModel(Extraction extraction, EditorCookie ck, int forChange) {
        NamedSemanticRegions<RuleTypes> rules = extraction.namedRegions(AntlrExtractor.RULE_BOUNDS);
        NamedSemanticRegions<RuleTypes> alts = extraction.namedRegions(AntlrExtractor.NAMED_ALTERNATIVES);
        Iterable<NamedSemanticRegion<RuleTypes>> it = rules.combinedIterable(alts, true);
        replaceModelFromList(ck, toList(it), SortTypes.NATURAL, forChange, extraction);
    }
}

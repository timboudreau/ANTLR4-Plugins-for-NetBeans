package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JEditorPane;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegion;
import org.netbeans.spi.navigator.NavigatorPanel;
import org.openide.cookies.EditorCookie;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.graph.StringGraphVisitor;

/**
 * Displays the parser rule tree.
 *
 * @author Tim Boudreau
 */
@NavigatorPanel.Registration(mimeType = "text/x-g4", displayName = "Parser Rule Tree", position = 2)
public class AntlrRuleTreeNavigatorPanel extends AbstractAntlrNavigatorPanel<String> {

    private final EmptyListModel<String> EMPTY = new EmptyListModel<>();
    private final Map<String, int[]> offsetsForRules = new HashMap<>();

    @Override
    protected ActivatedTcPreCheckJList<String> createList() {
        ActivatedTcPreCheckJList<String> result = new ActivatedTcPreCheckJList<String>();
        result.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                EditorAndChangeAwareListModel<String> mdl
                        = result.getModel() instanceof EditorAndChangeAwareListModel<?>
                        ? (EditorAndChangeAwareListModel<String>) result.getModel() : null;
                if (mdl != null) {
                    int loc = result.locationToIndex(e.getPoint());
                    if (loc >= 0 && loc < result.getModel().getSize()) {
                        String el = result.getModel().getElementAt(loc);
                        if (el != null) {
                            moveTo(mdl.cookie, el.trim());
                        }
                    }
                }
            }
        });
        return result;
    }

    private void moveTo(EditorCookie ck, String el) {
        int[] offsets = offsetsForRules.get(el);
        if (offsets != null) {
            JEditorPane[] panes = ck.getOpenedPanes();
            if (panes != null && panes.length > 0) {
                moveTo(panes[0], offsets[0], offsets[1]);
            }
        }
    }

    @Override
    protected void setNoModel(int forChange) {
        Mutex.EVENT.readAccess((Runnable) () -> {
            if (isCurrent(forChange)) {
                list.setModel(EMPTY);
            }
        });
    }

    @Override
    protected void withNewModel(Extraction extraction, EditorCookie ck, int forChange) {
        Mutex.EVENT.readAccess((Runnable) () -> {
            if (isCurrent(forChange)) {
                offsetsForRules.clear();
                String s = list.getSelectedValue();
                if (s != null) {
                    s = s.trim();
                } else {
                    s = "";
                }
                final String prevSelection = s;
                EditorAndChangeAwareListModel<String> mdl 
                        = new EditorAndChangeAwareListModel<String>(ck, forChange, extraction);
                int[] newSelIndex = new int[]{-1};
                NamedSemanticRegions<RuleTypes> decls = extraction.namedRegions(AntlrExtractor.RULE_NAMES);
                extraction.referenceGraph(AntlrExtractor.RULE_NAME_REFERENCES).walk(new StringGraphVisitor() {

                    private List<String> depthStrings = new ArrayList<>(10);

                    int index = 0;

                    private String depthString(int val) {
                        if (val < depthStrings.size()) {
                            return depthStrings.get(val);
                        }
                        char[] chars = new char[val];
                        Arrays.fill(chars, ' ');
                        String result = new String(chars);
                        depthStrings.add(result);
                        return result;
                    }

                    @Override
                    public void enterRule(String rule, int depth) {
                        mdl.addElement(depthString(depth) + rule);
                        if (prevSelection.equals(rule)) {
                            newSelIndex[0] = index;
                        }
                        NamedSemanticRegion<?> rd = decls.regionFor(rule);
                        if (rd != null) {
                            int[] offsets = new int[]{rd.start(), rd.end()};
                            offsetsForRules.put(rule, offsets);
                        }
                        index++;
                    }

                    @Override
                    public void exitRule(String rule, int depth) {
                    }
                });
                list.setModel(mdl);
                if (newSelIndex[0] != -1) {
                    list.setSelectionInterval(newSelIndex[0], newSelIndex[0]);
                    list.scrollRectToVisible(list.getCellBounds(newSelIndex[0], newSelIndex[0]));
                }
            }
        });
    }

    @Override
    @Messages("TREE_DISPLAY_NAME=Parser Rule Tree")
    public String getDisplayName() {
        return Bundle.TREE_DISPLAY_NAME();
    }

    @Override
    @Messages("TREE_HINT=Shows the parser rule tree of this grammar")
    public String getDisplayHint() {
        return Bundle.TREE_HINT();
    }
}

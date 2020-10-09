/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language.ambig;

import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.antlr.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.GrammarParserInterpreter;
import org.nemesis.antlr.live.language.ambig.AmbiguityAnalyzer.STV;
import org.nemesis.antlr.live.language.ambig.AmbiguityAnalyzer.STVProvider;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Tim Boudreau
 */
public class AmbiguityUI implements STVProvider {

    private JPanel panel;
    GridBagConstraints ambigConstraints = new GridBagConstraints();
    private final AtomicBoolean vis = new AtomicBoolean();
    private TC tc;

    AmbiguityUI() {
        ambigConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        ambigConstraints.fill = GridBagConstraints.BOTH;
        ambigConstraints.gridwidth = 1;
        ambigConstraints.gridheight = 1;
        ambigConstraints.gridx = 0;
        ambigConstraints.gridy = 0;
        ambigConstraints.weightx = 1;
        ambigConstraints.weighty = 1;
//        EventQueue.invokeLater(() -> {
//            ensureVisible("Ambiguities...");
//        });
    }

    @Override
    public AmbiguityAnalyzer.STV stv(FileObject grammarFile, Grammar grammar, int startIndex, int stopIndex,
            BitSet bits, int ruleIndex, String ruleName, CharSequence offendingText, IntMap<PositionRange> editSafeRegions) {
        PanelProvider pp = new PanelProvider(grammarFile, grammar, pnl -> {
            if (panel == null) {
                panel = new JPanel(new GridBagLayout());
            }
            panel.add(pnl, ambigConstraints);
            if (panel.isShowing()) {
                panel.invalidate();
                panel.revalidate();
                panel.repaint();
            } else {
                ensureVisible("Ambiguities - " + grammar.name);
            }
        }, ruleName, offendingText, bits, startIndex, stopIndex, editSafeRegions);
        return pp;
    }

    private void ensureVisible(String displayName) {
        if (vis.compareAndSet(false, true)) {
            System.out.println("OPEN A TC " + displayName);
            TC tc = new TC(displayName, panel);
            tc.open();
            tc.requestVisible();
        } else if (tc != null) {
            tc.setDisplayName(displayName);
        }
    }

    static final class TC extends TopComponent {

        TC(String title, Component inner) {
            setDisplayName(title);
            setLayout(new BorderLayout());
            add(new JScrollPane(inner), BorderLayout.CENTER);
        }

        @Override
        public void open() {
            Mode mode = WindowManager.getDefault().findMode("output");
            if (mode != null) {
                mode.dockInto(this);
            }
            super.open();
        }

        @Override
        public int getPersistenceType() {
            return PERSISTENCE_NEVER;
        }

        @Override
        protected void componentClosed() {
            super.componentClosed();
            removeAll();
        }
    }

    private static final class PanelProvider implements STV, BiConsumer<MouseEvent, PT> {

        private final Doc doc = new Doc();
        private final FileObject grammarFile;
        private final Grammar grammar;
        private final Consumer<? super Container> onInit;
        private final String titleText;
        private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("root", true);
        private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
        private Components components;
        private final int startToken;
        private final int stopToken;
        private final String targetRule;
        private final IntMap<PositionRange> editSafeRegions;

        PanelProvider(FileObject grammarFile, Grammar grammar, Consumer<? super Container> onInit, String ruleName, CharSequence offendingText,
                BitSet alternatives, int startToken, int stopToken, IntMap<PositionRange> editSafeRegions) {
            titleText = "<html>Ambiguity in <b>" + ruleName + "</b>  in <i>"
                    + Strings.elide(offendingText, 40) + "</i>";
            this.targetRule = ruleName;
            doc.accept("\nOffending text:" + offendingText + "\n----------------\n");
            this.grammarFile = grammarFile;
            this.grammar = grammar;
            this.onInit = onInit;
            rootNode.setUserObject(ruleName);
            this.startToken = startToken;
            this.stopToken = stopToken;
            this.editSafeRegions = editSafeRegions;
            EventQueue.invokeLater(this::init);
        }

        static class Components {

            private final GridBagConstraints c = new GridBagConstraints();

            private final JPanel outerPanel = new JPanel(new GridBagLayout());
            private final JPanel panel = new JPanel(new GridBagLayout());
//            private final JEditorPane pane = new JEditorPane();
//            private final JTree tree;
            private final JLabel title = new JLabel();
            private final JLabel status = new JLabel();
            private final JProgressBar bar = new JProgressBar();

            Components(PanelProvider pp, Consumer<? super Container> onInit) {
//                tree = new JTree(pp.treeModel);
//                tree.setRootVisible(false);
                title.setText(pp.titleText);
//                pane.setDocument(pp.doc);
//                pane.setEditable(false);
                title.setFont(title.getFont().deriveFont(Font.BOLD));
                title.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Tree.line")));
//                pane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, UIManager.getColor("Tree.line")));
                bar.setIndeterminate(true);
                c.anchor = GridBagConstraints.FIRST_LINE_START;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.weightx = 0.75;
                c.weighty = 1;
                c.gridwidth = 1;
                c.gridheight = 1;
                c.gridx = 0;
                c.gridy = 0;
                outerPanel.add(title, c);
                c.weightx = 0.25;
                c.gridx++;
                c.anchor = GridBagConstraints.FIRST_LINE_END;
                outerPanel.add(bar, c);
                c.fill = GridBagConstraints.BOTH;
                c.gridx = 0;
                c.gridy++;
                c.weightx = 1;
                c.weighty = 1;
                outerPanel.add(panel, c);
                c.fill = GridBagConstraints.HORIZONTAL;
                c.weighty = 0;
                c.weightx = 1;
                c.gridx = 0;
                c.gridy++;
                c.gridwidth = 2;
                c.anchor = GridBagConstraints.LAST_LINE_END;
                status.setText("Initializing....");
                outerPanel.add(status, c);

//                panel.add(new JScrollPane(tree), c);
                c.gridx = 0;
                c.gridy = 0;
//                panel.add(pane, c);
                c.weighty = 0;
                c.gridy++;
                c.fill = GridBagConstraints.HORIZONTAL;
                onInit.accept(panel);
            }

            void add(JComponent comp) {
                comp.setBorder(BorderFactory.createEmptyBorder(12, 5, 5, 5));
//                pane.setVisible(false);
                c.gridy++;
                c.fill = GridBagConstraints.HORIZONTAL;
                panel.add(comp, c);
                panel.invalidate();
                panel.revalidate();
                panel.repaint();
            }
        }

        @Override
        public void accept(MouseEvent t, PT clicked) {
            if (!t.isPopupTrigger() && t.getClickCount() == 1) {
                ParseTree tree = clicked.tree();
                if (tree instanceof ParserRuleContext) {
                    PositionRange range = rangefor(clicked);
                    if (range != null) {
                        openRange(range);
                    }
                }
            }
        }

        private PositionRange rangefor(PT pt) {
            ParseTree tree = pt.tree();
            if (tree instanceof ParserRuleContext) {
                int invokingState = ((ParserRuleContext) tree).invokingState;
                PositionRange range = editSafeRegions.get(invokingState);
                if (range != null) {
                    return range;
                } else {
                    // WTF
                    System.out.println("NO RANGE FOR INVOKING STATE " + invokingState + " for " + tree);
                    Interval ival = grammar.getStateToGrammarRegion(invokingState);
                    if (ival != null) {
                        try {
                            DataObject dob = DataObject.find(grammarFile);
                            EditorCookie ec = dob.getLookup().lookup(EditorCookie.class);
                            if (ec != null) {
                                StyledDocument doc = ec.openDocument();
                                PositionFactory pf = PositionFactory.forDocument(doc);
                                CommonToken first = (CommonToken) grammar.originalTokenStream.get(ival.a);
                                CommonToken second = ival.a == ival.b ? first : (CommonToken) grammar.originalTokenStream.get(ival.a);
                                int start = first.getStartIndex();
                                int end = second.getStopIndex() + 1;
                                PositionRange rng = pf.range(start, Position.Bias.Forward, end, Position.Bias.Forward);
                                editSafeRegions.put(invokingState, rng);
                                return rng;
                            }
                        } catch (IOException | BadLocationException | Error ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            }
            return null;
        }

        private void openRange(PositionRange range) {
            int startOffset = range.start();
            int endOffset = range.end();
            Document doc = range.document();
            JTextComponent jtc = EditorRegistry.findComponent(doc);
            if (jtc != null && jtc.isShowing()) {
                jtc.setSelectionStart(startOffset);
                jtc.setSelectionEnd(endOffset);
                TopComponent tc = (TopComponent) NbEditorUtilities.getOuterTopComponent(jtc);
                if (tc != null) {
                    tc.requestActive();
                    jtc.requestFocus();
                    return;
                }
            }
            // No editor for the grammar opened?  Ensure it is opened.
            // Should only happen when a file extension has been associated
            // and a file is being edited for it
            Line ln = NbEditorUtilities.getLine(doc, startOffset, false);
            ln.show(Line.ShowOpenType.REUSE_NEW, Line.ShowVisibilityType.FOCUS);
        }

        private void init() {
            components = new Components(this, onInit);
        }

        @Override
        public void status(String msg, int step, int of) {
            doc.accept(msg);
            EventQueue.invokeLater(() -> {
                components.status.setText(msg);
                if (step >= 0 && of >= 0) {
                    if (step == of) {
                    } else {
                        components.bar.setIndeterminate(false);
                        components.bar.setString(step + " / " + of);
                        BoundedRangeModel mdl = components.bar.getModel();
                        mdl.setRangeProperties(step, of, 0, of, false);
                    }
                } else {
                    components.bar.setIndeterminate(true);
                    components.bar.setString("Running");
                }
            });
        }

        static class DMT extends DefaultMutableTreeNode {

            DMT(ParseTree pt, String[] ruleNames, Vocabulary vocab, int startToken, int stopToken) {
                super(stringify(pt, ruleNames, vocab), pt.getChildCount() > 0);
                for (int i = 0; i < pt.getChildCount(); i++) {
                    ParseTree kid = pt.getChild(i);

//                    Interval ival = kid.getSourceInterval();
//                    if (Interval.of(startToken, stopToken).properlyContains(ival)) {
                    super.add(new DMT(kid, ruleNames, vocab, startToken, stopToken));
//                    }
                }
            }

            private static String stringify(ParseTree pt, String[] ruleNames, Vocabulary vocab) {
                StringBuilder sb = new StringBuilder("<html>");
                String text = Escaper.CONTROL_CHARACTERS.escape(pt.getText());
                if (pt instanceof ParserRuleContext) {
                    ParserRuleContext pr = (ParserRuleContext) pt;
                    sb.append("<b>").append(ruleNames[pr.getRuleIndex()]);
                    sb.append("</b> <i>");
                    sb.append(Strings.elide(text));
                    sb.append("</i>");
                } else if (pt instanceof TerminalNode) {
                    TerminalNode tn = (TerminalNode) pt;
                    Token tok = tn.getSymbol();
                    String name = vocab.getSymbolicName(tok.getType());
                    if (name == null) {
                        name = vocab.getDisplayName(tok.getType());
                    }
                    sb.append("<i><b>").append(name).append(" </b>").append(text).append("</i>");
                } else {
                    sb.append(pt.getClass().getSimpleName()).append(" <i>").append(text).append("</i>");
                }
                return sb.toString();
            }
        }

        private int lastDepth = -1;

        @Override
        public boolean visit(GrammarParserInterpreter gpi, Grammar grammar, int depth, int ruleIndex,
                String ruleName, int childIndex, Interval invokingStateGrammarRegions, ParseTree tree) {

            StringBuilder info = new StringBuilder();

            if (depth == 0) {
                String[] ruleNames = gpi.getRuleNames();
                Vocabulary vocab = gpi.getVocabulary();
                EventQueue.invokeLater(() -> {
                    DMT dmt = new DMT(tree, ruleNames, vocab, startToken, stopToken);
                    rootNode.add(dmt);
                    treeModel.nodeChanged(rootNode);
//                    components.tree.expandPath(new TreePath(new Object[]{rootNode, dmt}));
//                    components.tree.invalidate();
//                    components.tree.revalidate();
//                    components.tree.repaint();
                });
            }
            if (depth < lastDepth) {
                info.append('\n');
            }
            lastDepth = depth;
            info.append('\n');
            char[] c = new char[depth * 3];
            Arrays.fill(c, ' ');
            info.append(c);
            info.append('\n').append(childIndex + 1).append(". ");

            info.append(ruleName).append(": ");
            if (invokingStateGrammarRegions != null) {
                for (int i = invokingStateGrammarRegions.a; i <= invokingStateGrammarRegions.b; i++) {
                    org.antlr.runtime.Token tok = grammar.originalTokenStream.get(i);
                    info.append(tok.getText().trim()).append(' ');
                }
            }
            doc.accept(info.toString());
            return true;
        }

        @Override
        public void done() {
            EventQueue.invokeLater(() -> {
                BoundedRangeModel mdl = components.bar.getModel();
                mdl.setRangeProperties(100, 100, 0, 100, false);
//                components.bar.setVisible(false);
                components.bar.setString("Done");
            });
        }

        @Override
        public void visitPaths(GrammarParserInterpreter gpi, Grammar grammar, Map<PT, List<List<PT>>> pathsByTail,
                Vocabulary vocab, String[] ruleNames) {
            doc.accept("\nPaths:\n");
            doc.accept(pathsByTail.toString());
            doc.accept("\nDone.");
            EventQueue.invokeLater(() -> {
                for (Map.Entry<PT, List<List<PT>>> e : pathsByTail.entrySet()) {
                    CellsPanel cp = new CellsPanel(targetRule, e.getKey(), e.getValue(), this, this::tooltipForPT);
                    components.add(cp);
                }
                components.bar.setVisible(false);
                components.status.setText("Done.");
            });
        }

        String tooltipForPT(PT pt) {
            if (pt.isRuleTree()) {
                int state = pt.invokingState();
                if (state >= 0) {
                    PositionRange rng = rangefor(pt);
                    if (rng != null) {
                        PositionBounds bds = PositionFactory.toPositionBounds(rng);
                        if (bds != null) {
                            try {
                                return bds.getText();
                            } catch (BadLocationException | IOException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    }
                }
            }
            return null;
        }
    }

    static class Doc extends DefaultStyledDocument implements Runnable, Consumer<String> {

        private final AtomicLinkedQueue<String> pending = new AtomicLinkedQueue<>();
        private final AtomicBoolean enqueued = new AtomicBoolean();

        public void append(String txt) {
            pending.add(txt);
            if (enqueued.compareAndSet(false, true)) {
                EventQueue.invokeLater(this);
            }
        }

        @Override
        public void accept(String t) {
            append(t);
        }

        @Override
        public void run() {
            try {
                while (!pending.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    pending.drain(line -> {
                        sb.insert(0, line);
                    });
                    writeLock();
                    try {
                        int len = getLength();
                        insertString(len, sb.toString(), null);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    } finally {
                        writeUnlock();
                    }
                }
            } finally {
                enqueued.set(false);
            }
        }
    }
}

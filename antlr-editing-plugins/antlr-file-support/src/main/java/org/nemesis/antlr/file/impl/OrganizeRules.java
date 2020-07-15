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
package org.nemesis.antlr.file.impl;

import com.mastfrog.antlr.utils.CharSequenceCharStream;
import com.mastfrog.antlr.utils.HeuristicRuleNameComparator;
import com.mastfrog.antlr.utils.TreeUtils;
import com.mastfrog.graph.StringGraph;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.strings.Strings;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;
import javax.swing.text.StyledDocument;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.EbnfSuffixContext;
import org.nemesis.antlr.ANTLRv4Parser.LexerRuleElementBlockContext;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import static org.nemesis.antlr.file.AntlrKeys.RULE_BOUNDS;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAME_REFERENCES;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.ops.CaretInformation;
import org.nemesis.editor.ops.DocumentOperator;
import org.nemesis.extraction.Extraction;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.Mnemonics;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.windows.WindowManager;

@ActionID(
        category = "Source",
        id = "org.nemesis.antlr.file.impl.OrganizeRules"
)
@ActionRegistration(
        displayName = "#CTL_OrganizeRules",
        asynchronous = false
)
@ActionReferences({
    @ActionReference(path = "Editors/text/x-g4/Popup", position = 300)
})
@Messages({"CTL_OrganizeRules=Organize Rules",
    "ttlWarning=Organize Rules Caveats",
    "warning=<html><body><h2>Organize Rules</h2><p>"
    + "Organize rules makes a best-effort attempt to organize and group "
    + "rules in your grammar, taking into account rules that can match "
    + "the same thing and need to be ordered accordingly, complexity and"
    + "interdependencies, and name prefix- and suffix-matching heuristics.</p><p>"
    + "It is important to test your grammar before and after organizing "
    + "rules, to ensure the semantics of your grammar have not changed "
    + "by reordering the rules.</p>",
    "dontShow=&Got it.  Don't show this warning again."
})
public final class OrganizeRules implements ActionListener {

    private DataObject context;
    // Do our processing asynchronously, since we do a parse, but we cannot use
    // asynchronous in the annotation because we need to be on the event queue to
    // get the current caret position prior to processing
    private final RequestProcessor proc = new RequestProcessor("organize-rules", 1, true);

    @SuppressWarnings("unchecked")
    public OrganizeRules(DataObject context) {
        try {
            // Cannot compile if we reference it directly, since it is generated by
            // annotation processors but also this class is processed by them
            Class<?> type = Class.forName("org.nemesis.antlr.file.file.AntlrDataObject");
            this.context = context.getLookup().lookup((Class<DataObject>) type);
        } catch (ClassNotFoundException ex) {
            this.context = null;
            Exceptions.printStackTrace(ex);
        }
    }

    private boolean maybeShowWarning() {
        Preferences prefs = NbPreferences.forModule(OrganizeRules.class);
        if (!prefs.getBoolean("dontShowOrganizeWarning", false)) {
            JPanel pnl = new JPanel(new BorderLayout());
            Frame w = WindowManager.getDefault().getMainWindow();
            // Give it some room to breathe, using the font size as a base so the
            // result is reasonable on high DPI screens
            FontMetrics fm = w.getFontMetrics(pnl.getFont());
            int margin = fm.charWidth('A');
            pnl.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));
            // Swing HTML
            JEditorPane pane = new JEditorPane();
            // Swing HTML will want to make the panel be as wide as the longest line rendered as
            // a single line, so limit it
            // Make sure the background color won't be black on black
            pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            pane.setContentType("text/html");
            pane.setBackground(UIManager.getColor("control"));
            pane.setEditable(false);
            pane.setText(Bundle.warning());
            JScrollPane scroll = new JScrollPane(pane);
            // The usual border-buildup avoidance machinations
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setViewportBorder(BorderFactory.createEmptyBorder());
            pnl.add(scroll, BorderLayout.CENTER);
            scroll.setMaximumSize(new Dimension(Math.max(600, w.getWidth() / 6), Math.max(400, w.getHeight() / 6)));
            // A checkbox to never show the popup again
            JCheckBox box = new JCheckBox();
            // Give it a mnemonic
            Mnemonics.setLocalizedText(box, Bundle.dontShow());
            pnl.add(box, BorderLayout.SOUTH);
            box.setHorizontalAlignment(SwingConstants.TRAILING);
            box.setHorizontalTextPosition(SwingConstants.LEADING);
            NotifyDescriptor desc = new NotifyDescriptor(pnl, Bundle.ttlWarning(),
                    NotifyDescriptor.OK_CANCEL_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE,
                    new Object[]{NotifyDescriptor.OK_OPTION, NotifyDescriptor.CANCEL_OPTION}, NotifyDescriptor.OK_OPTION);
            Object result = DialogDisplayer.getDefault().notify(desc);
            if (NotifyDescriptor.CANCEL_OPTION.equals(result)) {
                // cancellation
                return true;
            }
            if (box.isSelected()) {
                prefs.putBoolean("dontShowOrganizeWarning", true);
            }
        }
        return false;
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        if (context == null) {
            return;
        }
        try {
            if (maybeShowWarning()) {
                // user cancelled
                return;
            }
            /*
            Organize rules action:  Will need to:
            - Sort rules by dependency graph
            - Sort unrelated rules by length
            - Alphabetize when same length
            - Keep lexer mode rules grouped together
            - Group them by type - parser, lexer, fragment
            - Just alphabetize fragments, as they don't capture
             */
            EditorCookie ck = context.getLookup().lookup(EditorCookie.class);
            StyledDocument doc = ck.openDocument();
            int currentCaretPosition = findCaretPosition(ck, doc);
            System.out.println("START WITH CARET POSITION " + currentCaretPosition);
            AtomicReference<CaretRelativeToRuleName> caretRelative = new AtomicReference<>();
            proc.submit(() -> {
                // Do ALL of our work in the document with the document lock,
                // and ensure no jumping or unexpected parser invocations occur,
                // and make it a single undo transation - BUT do nt use
                // DocumentOperator's caret position preserving code, as
                // it will interfere with our own which puts the caret
                // in the same rule, wherever that rule has moved to
                DocumentOperator op = DocumentOperator.builder()
                        .acquireAWTTreeLock()
                        .lockAtomic()
                        .writeLock()
                        .restoringCaretPosition((CaretInformation caret, JTextComponent comp, Document doc1) -> {

                            return ibc -> {
                                CaretRelativeToRuleName info = caretRelative.get();
                                if (info == null) {
                                    ibc.accept(currentCaretPosition, currentCaretPosition);
                                }
                                try {
                                    Extraction revisedExtraction = NbAntlrUtils.parseImmediately(doc1);
                                    NamedSemanticRegions<RuleTypes> revisedRegions = revisedExtraction.namedRegions(RULE_BOUNDS);
                                    NamedSemanticRegion<RuleTypes> targetRegion = revisedRegions.regionFor(info.rule);
                                    System.out.println("RECOMPUTE RELATIVE POSITION FOR " + info.rule + " rel " + info.relativePosition);
                                    if (targetRegion == null) {
                                        System.out.println("NULL TARGET REGION");
                                        ibc.accept(caret.dot(), caret.mark());
                                    } else {
                                        int start = targetRegion.start();
                                        System.out.println("TARGET REGION");
                                        int pos = Math.min(doc1.getLength() - 1, Math.max(0, start + info.relativePosition));
                                        System.out.println("NEW START " + (start + info.relativePosition) + " = " + pos);
                                        // XXX preserve the selection?
                                        ibc.accept(pos, pos);
                                    }
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            };
                        })
                        .blockIntermediateRepaints()
                        .disableTokenHierarchyUpdates()
                        .singleUndoTransaction().writeLock()
                        .build();
                op.run(doc, () -> {
                    try {
                        organizeRules(doc, currentCaretPosition, caretRelative);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                });
            });
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private int findCaretPosition(EditorCookie ck, StyledDocument doc) {
        JTextComponent pane = findEditorPane(ck, doc);
        if (pane != null) {
            return pane.getCaret().getDot();
        }
        return -1;
    }

    private JTextComponent findEditorPane(EditorCookie ck, StyledDocument doc) {
        JTextComponent comp = DocumentOperator.findComponent(doc);
        if (comp == null && ck.getOpenedPanes() != null && ck.getOpenedPanes().length > 0) {
            comp = ck.getOpenedPanes()[0];
        }
        return comp;
    }

    private static class CaretRelativeToRuleName {

        final int relativePosition;
        final String rule;

        CaretRelativeToRuleName(int relativePosition, String rule) {
            this.relativePosition = relativePosition;
            this.rule = rule;
        }
    }

    @Messages("grammarParsedWithErrors=Grammar contains syntax or parse errors that could cause "
            + "the reorganized rules to change the semantics. Cannot organize rules now.")
    private static boolean organizeRules(StyledDocument doc, int caretPosition, AtomicReference<CaretRelativeToRuleName> caretRef) throws Exception {
        // we are in the document lock here.
        Extraction ext = NbAntlrUtils.parseImmediately(doc);
        NamedSemanticRegions<RuleTypes> ruleBounds = ext.namedRegions(RULE_BOUNDS);
        if (ruleBounds.isEmpty()) {
            return false;
        }
        String caretRuleName = null;
        int caretRelativePosition = 0;
        if (caretPosition >= 0) {

            NamedSemanticRegion<RuleTypes> caretRuleRegion = ruleBounds.index().nearestPreceding(caretPosition);
            System.out.println("RULE REGION AT " + caretPosition + ": " + caretRuleRegion);
            if (caretRuleRegion != null) {
                caretRuleName = caretRuleRegion.name();
                caretRelativePosition = caretPosition - caretRuleRegion.start();
                caretRef.set(new CaretRelativeToRuleName(caretRelativePosition, caretRuleName));
                System.out.println("  HAVE CARET RULE " + caretRuleName + " @ " + caretRelativePosition);
            } else {
                System.out.println("NO CARET RULE REGION IN " + ruleBounds);
            }
        }
        StringGraph graph = ext.referenceGraph(RULE_NAME_REFERENCES);
        List<RuleEntry> entries = organizeRules(doc, ruleBounds, graph);
        if (entries == null) {
            EventQueue.invokeLater(() -> {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), Bundle.grammarParsedWithErrors());
            });
            return false;
        }
        updateDocument(ruleBounds, doc, entries);
        return true;
    }

    static void updateDocument(NamedSemanticRegions<RuleTypes> ruleBounds, StyledDocument doc, List<RuleEntry> entries) throws BadLocationException {
        int[] startEnd = allRulesBounds(ruleBounds);
        doc.remove(startEnd[0], startEnd[1] - startEnd[0]);
        int cursor = startEnd[0];
        String lastMode = entries.iterator().next().details.mode;
        boolean lastWasNewline = startEnd[0] > 1 ? "\n".equals(doc.getText(startEnd[0], 1)) : false;
        StringBuilder sb = new StringBuilder();
        // FIXME:  Some off-by-one issues writing directly to the document
        for (RuleEntry e : entries) {
            CharSequence pt = e.precedingTextOrComments;
            if (pt.length() > 0) {
                sb.append(pt);
//                doc.insertString(cursor, pt.toString(), null);
//                cursor += pt.length();
//                lastWasNewline = doc.getText(cursor - 1, 1).equals("\n");
                lastWasNewline = sb.charAt(sb.length() - 1) == '\n';
            }
            if (e.kind() == RuleTypes.LEXER && !e.details.mode.isEmpty() && !"default".equals(e.details.mode) && !lastMode.equals(e.details.mode)) {
                String modeString = "\nmode " + e.details.mode + ";\n\n";
                if (!lastWasNewline) {
                    modeString = "\n" + modeString;
                }
                sb.append(modeString);
//                doc.insertString(cursor, modeString, null);
//                cursor += modeString.length();
                lastWasNewline = e.endsWithNewline();
                lastMode = e.details.mode;
            }
            sb.append(e.ruleBody);
            lastWasNewline = e.endsWithNewline();
//            doc.insertString(cursor, e.ruleBody.toString(), null);
//            cursor += e.ruleBody.length();
            if (!lastWasNewline) {
//                sb.append('\n');
//                doc.insertString(cursor, "\n", null);
//                cursor++;
            }
        }
        doc.insertString(startEnd[0], sb.toString(), null);
        if (!"\n".equals(doc.getText(doc.getLength() - 1, 1))) {
            doc.insertString(doc.getLength(), "\n", null);
        }
    }

    private static int[] allRulesBounds(NamedSemanticRegions<RuleTypes> all) {
//        List<NamedSemanticRegion<RuleTypes>> byOriginalPosition = sortedByOriginalPosition(new ArrayList<>(all.asCollection()));
//        NamedSemanticRegion<RuleTypes> first = byOriginalPosition.get(0);
//        NamedSemanticRegion<RuleTypes> last = byOriginalPosition.get(byOriginalPosition.size() - 1);
//        return new int[]{first.start(), last.end()};
        int[] result = new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (NamedSemanticRegion<RuleTypes> reg : all) {
            result[0] = Math.min(result[0], reg.start());
            result[1] = Math.max(result[1], reg.end());
        }
        return result;
    }

    static List<RuleEntry> organizeRules(StyledDocument doc, NamedSemanticRegions<RuleTypes> ruleBounds, StringGraph graph) throws Exception, BadLocationException {
        List<RuleEntry> entries = new ArrayList<>(ruleBounds.size());
        boolean first = true;
        Map<String, RuleEntry> entryForRule = new HashMap<>();
        boolean[] hasErrors = new boolean[1];
        Map<String, RuleDetails> detailsForRule = collectRuleDetails(doc, hasErrors);
        if (hasErrors[0]) {
            return null;
        }
        Map<String, CharSequence> prevTexts = precedingText(doc, ruleBounds);
        RuleDetails empty = new RuleDetails();
        RuleEntry firstRule = null;
        int firstRuleStart = Integer.MAX_VALUE;
        for (NamedSemanticRegion<RuleTypes> reg : ruleBounds) {
            if (reg.kind() == RuleTypes.NAMED_ALTERNATIVES) {
                continue;
            }
            Segment seg = new Segment();
            try {
                doc.getText(reg.start(), reg.size(), seg);
                RuleDetails det = detailsForRule.getOrDefault(reg.name(), empty);
                CharSequence pt = prevTexts.getOrDefault(reg.name(), "");
                Set<String> closure = graph.closureOf(reg.name());
                RuleEntry rule = new RuleEntry(reg, graph, first, seg, det, pt, closure);
                if (firstRuleStart > reg.start() && rule.kind() == RuleTypes.PARSER) {
                    firstRuleStart = reg.start();
                    firstRule = rule;
                }
                entries.add(rule);
                entryForRule.put(reg.name(), rule);
                first = false;
            } catch (BadLocationException ex) {
                BadLocationException ble = new BadLocationException("Exception fetching rule text for " + reg.name()
                        + " at " + reg.start() + ":" + reg.end() + " in document of length " + doc.getLength(), reg.start());
                ble.initCause(ex);
                throw ble;
            }
        }
        Collections.sort(entries);
        if (firstRule != null) {
            entries.remove(firstRule);
            entries.add(0, firstRule);
        }
        return entries;
    }

    private static final Pattern MODE_DECL = Pattern.compile("(\n?\n?mode\\s+\\w\\S*;\n?)", Pattern.DOTALL);

    static Map<String, CharSequence> precedingText(StyledDocument doc, NamedSemanticRegions<RuleTypes> ruleBounds) throws BadLocationException {
        int precedingTextStart = -1;
        Map<String, CharSequence> result = new HashMap<>();
        List<NamedSemanticRegion<RuleTypes>> ordered = sortedByOriginalPosition(new ArrayList<>(ruleBounds.asCollection()));
        for (int i = 0; i < ordered.size(); i++) {
            NamedSemanticRegion<RuleTypes> region = ordered.get(i);
            if (precedingTextStart != -1) {
                Segment seg = new Segment();
                try {
                    doc.getText(precedingTextStart, region.start() - precedingTextStart, seg);
                    Matcher match = MODE_DECL.matcher(seg);
                    if (match.find()) {
                        result.put(region.name(), match.replaceAll(""));
                    } else {
                        result.put(region.name(), seg);
                    }
                } catch (BadLocationException ex) {
                    BadLocationException e1 = new BadLocationException("Bad location getting text preceding  "
                            + region.name() + " from " + precedingTextStart + " to " + region.start()
                            + " in document of length " + doc.getLength(), precedingTextStart);
                    e1.initCause(ex);
                    throw e1;
                }
            } else {
                result.put(region.name(), "");
            }
            precedingTextStart = region.end();
        }
        return result;
    }

    static Map<String, RuleDetails> collectRuleDetails(StyledDocument doc, boolean[] errors) throws Exception {
        assert errors.length > 0;
        Segment seg = new Segment();
        doc.getText(0, doc.getLength(), seg);
        CharSequenceCharStream charStream = new CharSequenceCharStream("x", seg);
        ANTLRv4Lexer lex = new ANTLRv4Lexer(charStream);
        lex.removeErrorListeners();
        CommonTokenStream cts = new CommonTokenStream(lex);
        ANTLRv4Parser parser = new ANTLRv4Parser(cts);
        V v = new V();
        lex.addErrorListener(v);
        parser.addErrorListener(v);
        parser.removeErrorListeners();
        parser.grammarFile().accept(v);
        errors[0] = v.errorsEncountered;
        return v.detailsForRule;
    }

    static List<NamedSemanticRegion<RuleTypes>> sortedByOriginalPosition(List<NamedSemanticRegion<RuleTypes>> regions) {
        List<NamedSemanticRegion<RuleTypes>> result = new ArrayList<>(regions);
        Collections.sort(result, (a, b) -> {
            return Integer.compare(a.start(), b.start());
        });
        return result;

    }

    static class RuleEntry implements Comparable<RuleEntry> {

        private final StringGraph referenceGraph;
        private final boolean first;
        private final CharSequence ruleBody;
        private final RuleDetails details;
        private final NamedSemanticRegion<RuleTypes> region;
        private final CharSequence precedingTextOrComments;
        private final Set<String> closure;

        public RuleEntry(NamedSemanticRegion<RuleTypes> region, StringGraph referenceGraph, boolean first, CharSequence ruleBody, RuleDetails details,
                CharSequence precedingTextOrComments, Set<String> closure) {
            this.referenceGraph = referenceGraph;
            this.first = first;
            this.ruleBody = ruleBody;
            this.details = details;
            this.region = region;
            this.precedingTextOrComments = precedingTextOrComments;
            this.closure = closure;
        }

        Set<String> initialAtoms() {
            return details.initialAtoms;
        }

        boolean isSingleLiteralLexerAtom() {
            if (kind() != RuleTypes.LEXER) {
                return false;
            }
            if (details.initialAtoms.size() != 1) {
                return false;
            }
            String at = details.initialAtoms.iterator().next();
            if (at.charAt(0) == '\'') {
                return true;
            }
            return false;
        }

        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append(precedingTextOrComments);
            result.append(ruleBody);
            return result.toString();
        }

        boolean endsWithNewline() {
            return ruleBody.length() > 0 && ruleBody.charAt(ruleBody.length() - 1) == '\n';
        }

        RuleTypes kind() {
            return region.kind();
        }

        String name() {
            return region.name();
        }

        @Override
        public int compareTo(RuleEntry o) {
            if (o == this || o.name().equals(name())) {
                return 0;
            }
            if (o.kind() != kind()) {
                return -kind().compareTo(o.kind());
            }
            switch (kind()) {
                case PARSER:
                    if (first) {
                        // we must retain the first rule's position, since it is the entry point to a parser
                        return -1;
                    } else if (o.first) {
                        return 1;
                    }
                    return compareParserRules(o);
                case LEXER:
                    return compareLexerRules(o);
                case FRAGMENT:
                    return compareNames(name(), o.name());
                default:
                    throw new AssertionError(o);
            }
        }

        boolean dependsOn(RuleEntry other) {
            return closure.contains(other.name());
//            return referenceGraph.closureOf(name()).contains(other.name());
        }

        boolean isDependencyOf(RuleEntry other) {
            return other.closure.contains(name());
        }

        boolean isUnrelatedTo(RuleEntry other) {
            return !dependsOn(other) && !isDependencyOf(other);
        }

        boolean sharesAtomsWith(RuleEntry other) {
            Set<String> references = new HashSet<>(referenceGraph.children(name()));
            references.retainAll(referenceGraph.children(other.name()));
            return !references.isEmpty();
        }

        private int compareParserRules(RuleEntry o) {
            int result = 0;
//            if (dependsOn(o)) {
//                result -= 1;
//            }
//            if (isDependencyOf(o)) {
//                result +=1;
//            }
            if (result == 0) {
                if (o.hasInitialAtomMatch(this)) {
                    return details.compareTo(o.details);
                }
                result = compareNames(name(), o.name());
            }
            return result;
        }

        private boolean isSameMode(RuleEntry e) {
            return e.details.mode.equals(details.mode);
        }

        private int compareLexerRules(RuleEntry o) {
            // Sort rules that belong to a specific mode into a group
            // for that mode
            if (!isSameMode(o)) {
                if ("default".equals(details.mode)) {
                    return -1;
                } else if ("default".equals(o.details.mode)) {
                    return 1;
                }
                return details.mode.compareTo(o.details.mode);
            }
            if (hasInitialAtomMatch(o)) {
                // Sort literals like '.' and '..' and '...' such that
                // the longest wins - lexer rule order for overlapping rules is
                // critical - don't assume longest-greedy-match will take care
                // of it because it may be non-greedy, and we aren't capturing
                // that information in sufficient detail to be sure
                if (details.isLiteralAtom()) {
                    int result = -Integer.compare(details.initialAtomLength(), o.details.initialAtomLength());
                    if (result != 0) {
                        return result;
                    }
                }
                // Failover to assuming the more complex rule should sort first
                return -Integer.compare(details.atomCount, o.details.atomCount);
            }
            // Sort rules that consist of a single to the bottom of the group
            // - e.g. Foo : 'foo' - this is usually the Right Thing[tm] for
            // a sanely organized grammar
            boolean single = isSingleLiteralLexerAtom();
            boolean otherSingle = o.isSingleLiteralLexerAtom();
            if (single && !otherSingle) {
                return 1;
            } else if (!single && otherSingle) {
                return -1;
            }
//            if (sharesAtomsWith(o)) {
//                return -details.compareTo(o.details);
//            }
            // Fail over to heuristically comparing names
            return compareNames(name(), o.name());
        }

        boolean hasInitialAtomMatch(RuleEntry o) {
            return details.initialAtomsMatch(o.details);
        }
    }

    static int compareNames(String a, String b) {
        return HeuristicRuleNameComparator.INSTANCE.compare(a, b);

    }

    static class V extends ANTLRv4BaseVisitor<Void> implements ANTLRErrorListener {

        private final Map<String, RuleDetails> detailsForRule = new HashMap<>();
        private RuleDetails currentDetails;
        private String currentMode = "default";
        private boolean errorsEncountered;
        private boolean inOuterLexerRule;

        @Override
        public Void visitErrorNode(ErrorNode node) {
            errorsEncountered = true;
            return super.visitErrorNode(node);
        }

        @Override
        public Void visitModeSpec(ANTLRv4Parser.ModeSpecContext ctx) {
            if (ctx.modeDec() != null && ctx.modeDec().identifier() != null) {
                currentMode = ctx.modeDec().identifier().getText();
            }
            return super.visitModeSpec(ctx); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Void visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
            if (ctx.parserRuleDeclaration() != null && ctx.parserRuleDeclaration().parserRuleIdentifier() != null) {
                String name = ctx.parserRuleDeclaration().parserRuleIdentifier().getText();
                currentDetails = new RuleDetails();
                currentDetails.mode = "";
                detailsForRule.put(name, currentDetails);
            }
            Void result = super.visitParserRuleSpec(ctx);
            currentDetails = null;
            return result;
        }

        @Override
        public Void visitTokenRuleSpec(ANTLRv4Parser.TokenRuleSpecContext ctx) {
            inOuterLexerRule = true;
            if (ctx.tokenRuleDeclaration() != null && ctx.tokenRuleDeclaration().tokenRuleIdentifier() != null) {
                String name = ctx.tokenRuleDeclaration().tokenRuleIdentifier().getText();
                currentDetails = new RuleDetails();
                currentDetails.mode = currentMode;
                detailsForRule.put(name, currentDetails);
            }
            Void result = super.visitTokenRuleSpec(ctx);
            currentDetails = null;
            return result;
        }

        @Override
        public Void visitFragmentRuleSpec(ANTLRv4Parser.FragmentRuleSpecContext ctx) {
            if (ctx.fragmentRuleDeclaration() != null && ctx.fragmentRuleDeclaration().fragmentRuleIdentifier() != null) {
                String name = ctx.fragmentRuleDeclaration().fragmentRuleIdentifier().getText();
                currentDetails = new RuleDetails();
                currentDetails.mode = "";
                detailsForRule.put(name, currentDetails);
            }
            Void result = super.visitFragmentRuleSpec(ctx);
            currentDetails = null;
            return result;
        }

        void withCurrentDetails(Consumer<RuleDetails> supp) {
            if (currentDetails != null) {
                supp.accept(currentDetails);
            }
        }

        @Override
        public Void visitParserRuleAtom(ANTLRv4Parser.ParserRuleAtomContext ctx) {
            withCurrentDetails(det -> {
                det.atomCount++;
                det.addInitialAtom(ctx.getText(), false);
            });;
            return super.visitParserRuleAtom(ctx);
        }

        @Override
        public Void visitLexerRuleAtom(ANTLRv4Parser.LexerRuleAtomContext ctx) {
            withCurrentDetails(det -> {
                det.atomCount++;
                det.addInitialAtom(ctx.getText(), inOuterLexerRule);
            });
            return super.visitLexerRuleAtom(ctx);
        }

        @Override
        public Void visitEbnfSuffix(EbnfSuffixContext ctx) {
            withCurrentDetails(det -> {
                det.addEbnf(ctx);
            });
            return super.visitEbnfSuffix(ctx);
        }

        @Override
        public Void visitLexerRuleElementBlock(LexerRuleElementBlockContext ctx) {
            boolean isInner = TreeUtils.ancestor(ctx, LexerRuleElementBlockContext.class) != null;
            if (isInner) {
                inOuterLexerRule = true;
            }
            return super.visitLexerRuleElementBlock(ctx);
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            errorsEncountered = true;
        }

        @Override
        public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
        }
    }

    static final class RuleDetails implements Comparable<RuleDetails> {

        int atomCount;
        IntSet ebnfsAt = IntSet.create(12);
        IntSet greedyEbnfs = IntSet.create(12);
        String mode;
        Set<String> initialAtoms = new HashSet<>();

        void addInitialAtom(String text, boolean isNewOuterOrBlock) {
            if (!isNewOuterOrBlock && initialAtoms.isEmpty()) {
                initialAtoms.add(text);
            } else if (isNewOuterOrBlock) {
                initialAtoms.add(text);
            }
        }

        void addEbnf(EbnfSuffixContext ctx) {
            boolean greedy = ctx.QUESTION() == null;
            ebnfsAt.add(atomCount);
            if (greedy) {
                greedyEbnfs.add(atomCount);
            }
        }

        @Override
        public int compareTo(RuleDetails o) {
            int result = -Integer.compare(atomCount, o.atomCount);
            if (result == 0) {
                result = -Integer.compare(greedyEbnfs.size(), o.greedyEbnfs.size());
            }
            return result;
        }

        int initialAtomLength() {
            return initialAtoms.size() == 1
                    ? deUnicodeEscape(initialAtoms.iterator().next()).length()
                    : 0;
        }

        boolean isLiteralAtom() {
            return initialAtoms.size() == 1
                    ? initialAtoms.iterator().next().length() > 0
                    && initialAtoms.iterator().next().charAt(0) == '\''
                    : false;

        }

        boolean initialAtomsMatch(RuleDetails other) {
            if (other == this || initialAtoms.isEmpty()) {
                return false;
            }
            for (String a : initialAtoms) {
                for (String b : other.initialAtoms) {
                    if (atomsMatch(a, b)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean atomsMatch(String a, String b) {
            if (a.equals(b)) {
                return true;
            }
            if (a.charAt(0) == '\'' && b.charAt(0) == '\'') {
                a = deUnicodeEscape(Strings.deSingleQuote(a));
                b = deUnicodeEscape(Strings.deSingleQuote(b));
                for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
                    if (a.charAt(i) != b.charAt(i)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        private String deUnicodeEscape(String txt) {
            if (txt.length() == 6 && txt.charAt(0) == '\\' && txt.charAt(1) == 'u') {
                String remainder = txt.substring(2);
                try {
                    int codePoint = Integer.parseInt(remainder, 16);
                    return Character.toString((char) codePoint);
                } catch (NumberFormatException nfe) {

                }
            }
            return txt;
        }
    }
}

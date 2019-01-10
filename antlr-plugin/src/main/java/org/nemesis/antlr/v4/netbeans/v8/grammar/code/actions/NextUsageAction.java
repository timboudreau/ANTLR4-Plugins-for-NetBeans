package org.nemesis.antlr.v4.netbeans.v8.grammar.code.actions;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.nemesis.data.IndexAddressable.IndexAddressableItem;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseAction;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({"next-usage=Next Usage", "no-rule=No Rule Under Caret"})
@EditorActionRegistration(
        name = "next-usage",
        weight = Integer.MAX_VALUE,
        category = "Editing",
        popupText = "Next Usage",
        menuPath = "Source",
        menuText = "Next Usage",
        mimeType = "text/g-4")
public class NextUsageAction extends BaseAction {

    private int direction;

    public NextUsageAction(boolean forwards) {
        super(SAVE_POSITION | SELECTION_REMOVE);
        this.direction = forwards ? 1 : -1;
    }

    public NextUsageAction() {
        this(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae, final JTextComponent jtc) {
        final Document doc = jtc.getDocument();
        final int caret = jtc.getSelectionStart();
        try {
            ParserManager.parseWhenScanFinished(Collections.singleton(Source.create(doc)), new UserTask() {
                @Override
                public void run(ResultIterator ri) throws Exception {
                    Parser.Result res = ri.getParserResult();
                    if (res instanceof NBANTLRv4Parser.ANTLRv4ParserResult) {
                        NBANTLRv4Parser.ANTLRv4ParserResult pr = (NBANTLRv4Parser.ANTLRv4ParserResult) res;
                        ANTLRv4SemanticParser semantics = pr.semanticParser();
                        Extraction ext = semantics.extraction();
                        NamedSemanticRegion<RuleTypes> caretToken = ext.regionOrReferenceAt(caret, AntlrExtractor.RULE_NAMES, AntlrExtractor.RULE_NAME_REFERENCES);
                        if (caretToken != null) {
                            moveCaretToNext(caretToken, doc, jtc, ext, caret);
                        } else {
                            StatusDisplayer.getDefault().setStatusText(
                                    NbBundle.getMessage(NextUsageAction.class, "no-rule"));

                        }
                    }
                }
            });
        } catch (ParseException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private boolean testDirection(IndexAddressableItem caretToken, IndexAddressableItem toTest) {
        switch (direction) {
            case 1:
                return toTest.isAfter(caretToken);
            case -1:
                return toTest.isBefore(caretToken);
            default:
                throw new AssertionError("Direction must be 1 or -1");
        }
    }

    private void moveCaretToNext(NamedSemanticRegion<RuleTypes> caretToken, Document doc, JTextComponent jtc, Extraction ext, int caret) {
        NamedRegionReferenceSets<RuleTypes> refs = ext.nameReferences(AntlrExtractor.RULE_NAME_REFERENCES);
        NamedRegionReferenceSets.NamedRegionReferenceSet<RuleTypes> refSet = refs.references(caretToken.name());
        List<NamedSemanticRegion<RuleTypes>> regions = new ArrayList<>();
        refSet.collectItems(regions);
        regions.add(caretToken);
        NamedSemanticRegions<RuleTypes> nameds = ext.namedRegions(AntlrExtractor.RULE_NAMES);
        if (nameds.contains(caretToken.name())) {
            NamedSemanticRegion<RuleTypes> declaration = nameds.regionFor(caretToken.name());
            if (declaration != null) {
                regions.add(declaration);
            }
        }
        Set<NamedSemanticRegion<RuleTypes>> regionSet = new TreeSet<>(regions);
        if (regionSet.isEmpty() || (regionSet.size() == 1 && regionSet.contains(caretToken))) {
            return;
        }
        NamedSemanticRegion<RuleTypes> candidate = null;
        NamedSemanticRegion<RuleTypes> first = null;
        NamedSemanticRegion<RuleTypes> last = null;
        for (NamedSemanticRegion<RuleTypes> r : regionSet) {
            if (candidate == null && testDirection(caretToken, r)) {
                candidate = r;
            }
            last = r;
            if (first == null) {
                first = r;
            }
        }
        if (candidate == null && !regionSet.isEmpty()) {
            switch (direction) {
                case 1:
                    candidate = first;
                    break;
                case -1:
                    candidate = last;
                    break;
                default:
                    throw new AssertionError(direction);
            }

        }
        if (candidate != null && !caretToken.boundsAndNameEquals(caretToken)) {
            EventQueue.invokeLater(new CaretShifter(jtc, caret, candidate.start()));
        }
    }

    static final class CaretShifter implements Runnable {

        private final JTextComponent comp;
        private final int expectedCaretPosition;
        private final int targetCaretPosition;

        public CaretShifter(JTextComponent comp, int expectedCaretPosition, int targetCaretPosition) {
            this.comp = comp;
            this.expectedCaretPosition = expectedCaretPosition;
            this.targetCaretPosition = targetCaretPosition;
        }

        @Override
        public void run() {
            assert EventQueue.isDispatchThread();
            if (!comp.isShowing()) { // may have changed
                return;
            }
            if (expectedCaretPosition == comp.getSelectionStart()) {
                int length = comp.getDocument().getLength();
                if (length < targetCaretPosition) { // user may have moved caret
                    comp.setSelectionStart(targetCaretPosition);
                }
            }
        }

    }
}

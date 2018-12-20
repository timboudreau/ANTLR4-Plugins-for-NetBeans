package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElement;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind.FRAGMENT_RULE_DECLARATION;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind.FRAGMENT_RULE_REFERENCE;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrFragmentHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, ANTLRv4SemanticParser> {

    public AntlrFragmentHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, GET_SEMANTICS);
    }

    @Override
    protected void refresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        Map<RuleElementKind, AttributeSet> colorings = RuleElementKind.colorings();
        Extraction ext = semantics.extraction();
        NamedSemanticRegions<RuleTypes> rns = ext.namedRegions(AntlrExtractor.RULE_NAMES);
        List<NamedSemanticRegion<AntlrExtractor.RuleTypes>> l = new LinkedList<>();
        rns.forEach(r -> {
            if (r.kind() == RuleTypes.FRAGMENT) {
                l.add(r);
            }
        });
        ext.namedRegions(AntlrExtractor.REFS);
        rns.forEach(r -> {
            if (r.kind() == RuleTypes.FRAGMENT) {
                l.add(r);
            }
        });

        for (NamedSemanticRegion<RuleTypes> fragment : l) {
            if (!fragment.isReference()) {
                bag.addHighlight(fragment.start(), fragment.end(), colorings.get(FRAGMENT_RULE_DECLARATION));
            } else {
                bag.addHighlight(fragment.start(), fragment.end(), colorings.get(FRAGMENT_RULE_REFERENCE));
            }
        }
    }

    private List<RuleElement> lastFragments = new ArrayList<>();

    protected void xrefresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        Map<RuleElementKind, AttributeSet> colorings = RuleElementKind.colorings();
        List<RuleElement> fragments = semantics.allElementsOfType(RuleElementTarget.FRAGMENT);
        if (fragments == lastFragments || fragments.equals(lastFragments)) {
            return;
        }
        lastFragments = fragments;
        bag.clear();
        // Give fragments their own highlighting - cannot be
        // done at the token level
        for (RuleElement fragment : fragments) {
            if (fragment.kind().isDeclaration()) {
                bag.addHighlight(fragment.getStartOffset(), fragment.getEndOffset(), colorings.get(FRAGMENT_RULE_DECLARATION));
            } else {
                bag.addHighlight(fragment.getStartOffset(), fragment.getEndOffset(), colorings.get(FRAGMENT_RULE_REFERENCE));
            }
        }
    }
}

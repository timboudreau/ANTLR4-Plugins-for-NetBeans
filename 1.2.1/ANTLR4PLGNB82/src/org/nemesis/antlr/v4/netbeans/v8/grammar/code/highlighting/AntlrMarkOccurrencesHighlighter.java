package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSets.NamedRegionReferenceSet;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrMarkOccurrencesHighlighter extends AbstractAntlrHighlighter.CaretOriented<ANTLRv4ParserResult, ANTLRv4SemanticParser> {

    public AntlrMarkOccurrencesHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, GET_SEMANTICS);
    }

    private AttributeSet markOccurrencesColoring() {
        MimePath mimePath = MimePath.parse("text/x-java");
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        return fcs.getFontColors(FontColorNames.INC_SEARCH_COLORING);
    }

    @Override
    protected void refresh(Document doc, Integer caretPosition, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        Extraction ext = semantics.extraction();

        NamedSemanticRegions<AntlrExtractor.RuleTypes> ruleBounds = ext.namedRegions(AntlrExtractor.RULE_BOUNDS);
        NamedSemanticRegion<AntlrExtractor.RuleTypes> ruleCaretIsIn = ruleBounds.at(caretPosition);
        if (ruleCaretIsIn != null) {

            NamedSemanticRegions<AntlrExtractor.RuleTypes> names = ext.namedRegions(AntlrExtractor.RULE_NAMES);

            NamedSemanticRegion<AntlrExtractor.RuleTypes> curr = names.at(caretPosition);

            NamedRegionReferenceSets<AntlrExtractor.RuleTypes> nameRefs = ext.nameReferences(AntlrExtractor.RULE_NAME_REFERENCES);
            if (curr == null) {
                curr = nameRefs.at(caretPosition);
            }
            if (curr != null) {
                AttributeSet markColoring = markOccurrencesColoring();
                if (curr.isReference() && names.contains(curr.name())) {
                    NamedSemanticRegion<AntlrExtractor.RuleTypes> decl = names.regionFor(curr.name());
                    bag.addHighlight(decl.start(), decl.end(), markColoring);
                }
//                bag.addHighlight(curr.start(), curr.end(), markColoring);
                NamedRegionReferenceSet<AntlrExtractor.RuleTypes> refs = nameRefs.references(curr.name());
                if (refs != null && refs.size() > 0) {
                    for (NamedSemanticRegions.NamedSemanticRegionReference<AntlrExtractor.RuleTypes> ref : refs) {
                        bag.addHighlight(ref.start(), ref.end(), markColoring);
                    }
                }
            }
        }
    }
}

package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.List;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElement;
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
        RuleElement rule = semantics.ruleElementAtPosition(caretPosition);
        if (rule != null) {
            List<RuleElement> all = semantics.allReferencesTo(rule);
            if (!all.isEmpty()) {
                AttributeSet markColoring = markOccurrencesColoring();
                for (RuleElement ref : semantics.allReferencesTo(rule)) {
                    if (ref != rule) {
                        bag.addHighlight(ref.getStartOffset(), ref.getEndOffset(), markColoring);
                    }
                }
            }
        }
    }
}

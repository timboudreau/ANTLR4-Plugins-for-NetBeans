package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind.PARSER_NAMED_ALTERNATIVE_SUBRULE;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrDeclarationHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, ANTLRv4SemanticParser> {

    public AntlrDeclarationHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, GET_SEMANTICS);
    }

    private final List<RuleDeclaration> lastDeclarations = new ArrayList<>();
    @Override
    protected void refresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        List<RuleDeclaration> declarations = semantics.allDeclarations();
        if (declarations == lastDeclarations || declarations.equals(lastDeclarations)) {
            return;
        }
        Map<RuleElementKind, AttributeSet> colorings = RuleElementKind.colorings();
        AttributeSet alternativeColorings = colorings.get(PARSER_NAMED_ALTERNATIVE_SUBRULE);
        bag.clear();
        for (RuleDeclaration declaration : semantics.allDeclarations()) {
            bag.addHighlight(declaration.getStartOffset(), declaration.getEndOffset(), colorings.get(declaration.kind()));
            if (declaration.hasNamedAlternatives()) {
                for (RuleDeclaration alternative : declaration.namedAlternatives()) {
                    bag.addHighlight(alternative.getStartOffset(), alternative.getEndOffset(), alternativeColorings);
                }
            }
        }
    }
}

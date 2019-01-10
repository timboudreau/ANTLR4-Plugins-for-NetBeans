package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
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

    private RuleElementKind kindFor(NamedSemanticRegion<RuleTypes> r) {
        if (r.isReference()) {
            switch (r.kind()) {
                case FRAGMENT:
                    return RuleElementKind.FRAGMENT_RULE_REFERENCE;
                case PARSER:
                    return RuleElementKind.PARSER_RULE_REFERENCE;
                case LEXER:
                    return RuleElementKind.LEXER_RULE_REFERENCE;
                default:
                    throw new AssertionError(r.kind());
            }
        } else {
            switch (r.kind()) {
                case FRAGMENT:
                    return RuleElementKind.FRAGMENT_RULE_DECLARATION;
                case PARSER:
                    return RuleElementKind.PARSER_RULE_DECLARATION;
                case LEXER:
                    return RuleElementKind.LEXER_RULE_DECLARATION;
                default:
                    throw new AssertionError(r.kind());
            }
        }
    }

    @Override
    protected void refresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        Extraction ext = semantics.extraction();
        bag.clear();
        NamedSemanticRegions<RuleTypes> lbls = ext.namedRegions(AntlrExtractor.NAMED_ALTERNATIVES);
        Map<RuleElementKind, AttributeSet> colorings = RuleElementKind.colorings();
        AttributeSet alternativeColorings = colorings.get(PARSER_NAMED_ALTERNATIVE_SUBRULE);
        for (NamedSemanticRegion<RuleTypes> l : lbls.index()) {
            bag.addHighlight(l.start(), l.end(), alternativeColorings);
        }
        NamedSemanticRegions<RuleTypes> names = ext.namedRegions(AntlrExtractor.RULE_NAMES);
        for (NamedSemanticRegion<RuleTypes> r : names) {
            RuleElementKind kind = kindFor(r);
            bag.addHighlight(r.start(), r.end(), colorings.get(kind));
        }
    }
/*
    private final List<RuleDeclaration> lastDeclarations = new ArrayList<>();

    protected void xrefresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
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
*/
}

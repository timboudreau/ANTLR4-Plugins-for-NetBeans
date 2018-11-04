package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.EbnfElement;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrEbnfHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, ANTLRv4SemanticParser> {

    public AntlrEbnfHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, GET_SEMANTICS);
    }

    private final List<EbnfElement> lastEbnfs = new ArrayList<>();
    public void refresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        List<EbnfElement> ebnfs = semantics.ebnfRanges();
        if (ebnfs == lastEbnfs || ebnfs.equals(lastEbnfs)) {
            return;
        }
        Map<String, AttributeSet> ebnfColorings = EbnfElement.colorings();
        assert ebnfColorings != null : "Ebnf colorings is null";
        assert !ebnfColorings.isEmpty() : "Ebnf colorings is empty";

        // Underline repeating elements
        for (EbnfElement ebnf : ebnfs) {
            if (ebnf.isQuestionMark()) {
                bag.addHighlight(ebnf.getStartOffset(), ebnf.getEndOffset(),
                        ebnfColorings.get(ebnf.coloringName()));
            }
        }
    }
}

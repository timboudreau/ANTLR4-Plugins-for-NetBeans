package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.toolext;

import java.util.Map;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarRootAST;

/**
 *
 * @author Tim Boudreau
 */
final class AlternateTokenLoadingGrammar extends Grammar {

    public AlternateTokenLoadingGrammar(MemoryTool tool, GrammarRootAST ast) {
        super(tool, ast);
    }

    public void importTokensFromTokensFile() {
        String vocab = getOptionString("tokenVocab");
        if (vocab != null) {
            AlternateTokenVocabParser vparser = new AlternateTokenVocabParser(this, (MemoryTool) tool);
            Map<String, Integer> tokens = vparser.load();
            tool.log("grammar", "tokens=" + tokens);
            for (String t : tokens.keySet()) {
                if (t.charAt(0) == '\'') {
                    defineStringLiteral(t, tokens.get(t));
                } else {
                    defineTokenName(t, tokens.get(t));
                }
            }
        }
    }
}

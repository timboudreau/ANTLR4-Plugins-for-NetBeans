package org.nemesis.antlr.memory.tool;

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

    @Override
    public String toString() {
        return name + "(" + fileName + ":" + rules + ")" ;
    }

    @Override
    public void importTokensFromTokensFile() {
        String vocab = getOptionString("tokenVocab");
        if (vocab != null) {
            AlternateTokenVocabParser vparser = new AlternateTokenVocabParser();
            Map<String, Integer> tokens = vparser.load((MemoryTool) tool, this);
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

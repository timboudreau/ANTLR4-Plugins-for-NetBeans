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

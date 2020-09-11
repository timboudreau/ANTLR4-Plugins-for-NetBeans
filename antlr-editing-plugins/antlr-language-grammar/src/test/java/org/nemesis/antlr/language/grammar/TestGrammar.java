/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package org.nemesis.antlr.language.grammar;

import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Escaper;
import java.io.IOException;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;

/**
 *
 * @author Tim Boudreau
 */
public class TestGrammar {
    static String grammar;

    @Test
    public void test() {
        CharStream stream = CharStreams.fromString(grammar);
        ANTLRv4Lexer lex = new ANTLRv4Lexer(stream);
        CommonTokenStream cts = new CommonTokenStream(lex);
        ANTLRv4Parser par = new ANTLRv4Parser(cts);
        par.grammarFile().accept(new V());

        lex.reset();
        for (Token tok= lex.nextToken(); tok.getType() != Lexer.EOF; tok=lex.nextToken()) {
            System.out.println(
                    lex.getVocabulary().getDisplayName(tok.getType())
                    + "\n\t" +
                    Escaper.CONTROL_CHARACTERS.escape(tok.getText().replace("\\s+", " ")));
        }
    }

    @BeforeAll
    public static void load() throws IOException {
        grammar = Streams.readResourceAsUTF8(TestGrammar.class, "RustLexerBroken.g4");
    }

    static final class V extends ANTLRv4BaseVisitor<Void> {

        @Override
        public Void visitErrorNode(ErrorNode node) {
            System.out.println("ERROR NODE " + node + " at " + node.getSymbol().getText()
                + " - " + node.getSymbol().getLine() + ":" + node.getSymbol().getCharPositionInLine());
            return super.visitErrorNode(node);
        }


    }
}

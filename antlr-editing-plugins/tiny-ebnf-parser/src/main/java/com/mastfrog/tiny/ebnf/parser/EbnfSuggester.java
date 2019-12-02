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
package com.mastfrog.tiny.ebnf.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 *
 * @author Tim Boudreau
 */
public class EbnfSuggester {

    private final String ebnfText;

    public EbnfSuggester(String ebnfText) {
        this.ebnfText = ebnfText;
    }

    public List<String> suggest() {
        List<String> result = new ArrayList<>();
        CharStream cs = CharStreams.fromString(ebnfText);
        EbnfLexer lexer = new EbnfLexer(cs);
        CommonTokenStream cts = new CommonTokenStream(lexer);
        EbnfParser parser = new EbnfParser(cts);
        V v = new V();
        parser.ebnf_sequence().accept(v);
        for (EbnfParser.EbnfItemContext ctx : v.elements) {
            result.add(ctx.getText());
        }
        return result;
    }

    static class V extends EbnfBaseVisitor<Void> {

        Set<EbnfParser.EbnfItemContext> elements = new HashSet<>();

        @Override
        public Void visitEbnfItem(EbnfParser.EbnfItemContext ctx) {
//            elements.add(ctx);
            return super.visitEbnfItem(ctx);
        }

        @Override
        public Void visitEbnfsuffix(EbnfParser.EbnfsuffixContext ctx) {
            elements.add((EbnfParser.EbnfItemContext) ctx.parent);
            return super.visitEbnfsuffix(ctx); //To change body of generated methods, choose Tools | Templates.
        }



        @Override
        public Void visitLiteral(EbnfParser.LiteralContext ctx) {
            System.out.println("LITERAL: " + ctx.getText());
            return super.visitLiteral(ctx); //To change body of generated methods, choose Tools | Templates.
        }


    }
}

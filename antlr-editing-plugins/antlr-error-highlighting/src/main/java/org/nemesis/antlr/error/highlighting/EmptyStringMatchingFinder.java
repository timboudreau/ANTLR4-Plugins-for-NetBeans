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
package org.nemesis.antlr.error.highlighting;

import com.mastfrog.range.DataIntRange;
import com.mastfrog.range.Range;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Parser;

/**
 *
 * @author Tim Boudreau
 */
class EmptyStringMatchingFinder extends ANTLRv4BaseVisitor<Void> {

    private int ruleDefinitionsVisited;
    private int dangerousRuleElementsVisited;
    List<DataIntRange<String, ? extends DataIntRange<String, ?>>> ranges = new ArrayList<>(3);
    List<DataIntRange<String, ? extends DataIntRange<String, ?>>> pending = new ArrayList<>(3);

    @Override
    public Void visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
        ruleDefinitionsVisited++;
        return super.visitTokenRuleDeclaration(ctx);
    }

    @Override
    public Void visitParserRuleDefinition(ANTLRv4Parser.ParserRuleDefinitionContext ctx) {
        return super.visitParserRuleDefinition(ctx); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Void visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
        ruleDefinitionsVisited++;
        return super.visitFragmentRuleDeclaration(ctx);
    }

    @Override
    public Void visitParserRuleElement(ANTLRv4Parser.ParserRuleElementContext ctx) {
        ruleDefinitionsVisited++;
        return super.visitParserRuleElement(ctx);
    }

    @Override
    public Void visitLexerRuleElement(ANTLRv4Parser.LexerRuleElementContext ctx) {
        return super.visitLexerRuleElement(ctx); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Void visitEbnf(ANTLRv4Parser.EbnfContext ctx) {
        return super.visitEbnf(ctx); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Void visitParserRuleAlternative(ANTLRv4Parser.ParserRuleAlternativeContext ctx) {
        return super.visitParserRuleAlternative(ctx); //To change body of generated methods, choose Tools | Templates.
    }

    private boolean isEbnfContainer(ParserRuleContext ctx) {
        return ctx instanceof ANTLRv4Parser.LexerRuleElementContext || ctx instanceof ANTLRv4Parser.ParserRuleElementContext || ctx instanceof ANTLRv4Parser.EbnfContext;
    }

    private void addDangerous(ANTLRv4Parser.EbnfSuffixContext ctx) {
        ParserRuleContext curr = ctx.getParent();
        while (curr != null && !isEbnfContainer(curr)) {
            curr = curr.getParent();
        }
        if (curr != null) {
            dangerousRuleElementsVisited++;
            Token startToken = ctx.getStart();
            Token stopToken = ctx.getStop();
            if (stopToken == null) {
                stopToken = startToken;
            }
            DataIntRange<String, ? extends DataIntRange<String, ?>> range = Range.of(startToken.getStartIndex(), stopToken.getStopIndex() + 1, curr.getText());
            pending.add(range);
        }
    }

    private boolean isDangerous(ANTLRv4Parser.EbnfSuffixContext ctx) {
        return (ctx.STAR() != null || ctx.QUESTION() != null) && ctx.PLUS() == null;
    }

    @Override
    public Void visitEbnfSuffix(ANTLRv4Parser.EbnfSuffixContext ctx) {
        if (isDangerous(ctx)) {
            addDangerous(ctx);
        }
        return super.visitEbnfSuffix(ctx);
    }

}

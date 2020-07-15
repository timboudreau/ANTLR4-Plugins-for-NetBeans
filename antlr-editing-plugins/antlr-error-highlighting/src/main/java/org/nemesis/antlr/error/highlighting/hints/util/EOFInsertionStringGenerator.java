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
package org.nemesis.antlr.error.highlighting.hints.util;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.strings.Escaper;
import java.io.IOException;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.AntlrConstants;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
public final class EOFInsertionStringGenerator extends ANTLRv4BaseVisitor<CharSequence> {

    private final StringBuilder sb = new StringBuilder();
    boolean ruleFound;
    int semiPosition;
    int blockCount;
    int atomCount;
    private boolean active;
    private final ANTLRv4Parser parser;
    int tokenIndexPrecedingSemi = -1;
    int tokenIndexPrecedingTokenPrecedingSemi = -1;
    int tokenTypePrecedingSemi = -1;
    int tokenTypePrecedingTokenPrecedingSemi = -1;
    IntSet ruleTypesContainingPrecedingToken = IntSet.create(ANTLRv4Parser.ruleNames.length);
    IntSet ruleTypesContainingTokenPrecedingPrecedingToken = IntSet.create(ANTLRv4Parser.ruleNames.length);
    IntRange<? extends IntRange<?>> ruleBodyBounds = Range.of(0, 0);

    EOFInsertionStringGenerator(ANTLRv4Parser parser) {
        this.parser = parser;
    }

    public static String getEofInsertionString(Document document) throws IOException {
        GrammarSource<Document> gs = GrammarSource.find(document, AntlrConstants.ANTLR_MIME_TYPE);
        ANTLRv4Lexer lex = new ANTLRv4Lexer(gs.stream());
        lex.removeErrorListeners();
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lex));
        parser.removeErrorListeners();;
        EOFInsertionStringGenerator gen = new EOFInsertionStringGenerator(parser);
        return parser.grammarFile().accept(gen).toString();
    }

    public static String getEofInsertionString(GrammarSource<?> gs) throws IOException {
        ANTLRv4Lexer lex = new ANTLRv4Lexer(gs.stream());
        lex.removeErrorListeners();
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lex));
        parser.removeErrorListeners();;
        EOFInsertionStringGenerator gen = new EOFInsertionStringGenerator(parser);
        return parser.grammarFile().accept(gen).toString();
    }

    @Override
    protected CharSequence defaultResult() {
        return sb;
    }

    private void finishResult() {
        switch (tokenTypePrecedingTokenPrecedingSemi) {
            case ANTLRv4Lexer.OR:
                sb.append("| EOF");
                break;
            default:
                sb.append(" EOF");
        }
    }

    @Override
    public CharSequence visitChildren(RuleNode node) {
        if (ruleFound) {
            // short circuit visiting the rest of the tree if we've
            // got what we need
            return sb;
        }
        if (active) {
            if (node instanceof ParserRuleContext) {
                ParserRuleContext prc = (ParserRuleContext) node;
                Interval ival = prc.getSourceInterval();
                if (ival.b == tokenIndexPrecedingSemi) {
                    System.out.println("FOUND RULE " + ANTLRv4Parser.ruleNames[prc.getRuleIndex()]);
                    ruleTypesContainingPrecedingToken.add(prc.getRuleIndex());
                }
                if (ival.b == tokenIndexPrecedingTokenPrecedingSemi) {
                    System.out.println("FOUND PREC " + ANTLRv4Parser.ruleNames[prc.getRuleIndex()]);
                    ruleTypesContainingTokenPrecedingPrecedingToken.add(prc.getRuleIndex());
                }
            }
        }
        return super.visitChildren(node);
    }

    @Override
    public CharSequence visitRuleSpec(ANTLRv4Parser.RuleSpecContext ctx) {
        if (ruleFound) {
            // we will only visit the first rule - we're deciding whether
            // we need parentheses or a leading OR or what
            return sb;
        }
        active = true;
        CharSequence result = super.visitRuleSpec(ctx);
        active = false;
        ruleFound = true;
        finishResult();
        return result;
    }

    private void computePrecedingNonWhitespaceTokenIndices(Token token) {
        Token tokenPrecedingSemi = precedingNonWhitespaceToken(token);
        if (tokenPrecedingSemi != null) {
            tokenIndexPrecedingSemi = tokenPrecedingSemi.getTokenIndex();
            tokenTypePrecedingSemi = tokenPrecedingSemi.getType();
            Token tokenPrecedingTokenPrecedingSemi = precedingNonWhitespaceToken(tokenPrecedingSemi);
            if (tokenPrecedingTokenPrecedingSemi != null) {
                tokenIndexPrecedingTokenPrecedingSemi = tokenPrecedingTokenPrecedingSemi.getTokenIndex();
                tokenTypePrecedingTokenPrecedingSemi = tokenPrecedingTokenPrecedingSemi.getType();
            }
        }
    }

    private Token precedingNonWhitespaceToken(Token token) {
        System.out.println("FIND adj to " + token.getText() + " at " + token.getStartIndex());
        int ix = token.getTokenIndex();
        Token target = null;
        for (int i = ix - 1; i > 0; i--) {
            Token test = parser.getTokenStream().get(i);
            System.out.println("  TEST " + Escaper.CONTROL_CHARACTERS.escape(token.getText()) + " at " + token.getStartIndex());
            String txt = test.getText().trim();
            if (!txt.isEmpty() && !";".equals(txt)) {
                target = test;
                System.out.println("   it is the target");
                break;
            }
        }
        System.out.println("PRECEDING TOKEN IS '" + (target == null ? "null" : Escaper.CONTROL_CHARACTERS.escape(target.getText())) + "'");
        return target;
    }

    @Override
    public CharSequence visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
        System.out.println("THE RULE: '" + Escaper.CONTROL_CHARACTERS.escape(ctx.getText()));
        Token semi;
        if (ctx.SEMI() != null) {
            semi = ctx.SEMI().getSymbol();
//            semiPosition = ctx.SEMI().getSymbol().getStopIndex();
        } else {
            if (ctx.stop.getTokenIndex() != parser.getTokenStream().size() - 1) {
                semi = parser.getTokenStream().get(ctx.stop.getTokenIndex() + 1);
            } else {
                // At the end of a broken source - and we will likely wind up inserting before
                // where we want to
                semi = ctx.stop;
            }
        }
        semiPosition = semi.getTokenIndex();
        computePrecedingNonWhitespaceTokenIndices(semi);
        return super.visitParserRuleSpec(ctx);
    }

    @Override
    public CharSequence visitParserRuleDefinition(ANTLRv4Parser.ParserRuleDefinitionContext ctx) {
        ruleBodyBounds = Range.of(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
        return super.visitParserRuleDefinition(ctx);
    }

    @Override
    public CharSequence visitParserRuleAtom(ANTLRv4Parser.ParserRuleAtomContext ctx) {
        atomCount++;
        return super.visitParserRuleAtom(ctx);
    }

    @Override
    public CharSequence visitBlock(ANTLRv4Parser.BlockContext ctx) {
        blockCount++;
        return super.visitBlock(ctx);
    }
}

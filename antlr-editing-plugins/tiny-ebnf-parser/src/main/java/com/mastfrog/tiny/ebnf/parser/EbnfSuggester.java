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

import com.mastfrog.range.IntRange;
import com.mastfrog.tiny.ebnf.parser.EbnfSuggester.EbItem.Kind;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;

/**
 *
 * @author Tim Boudreau
 */
public class EbnfSuggester {

    private final String ebnfText;

    public EbnfSuggester(String ebnfText) {
        this.ebnfText = ebnfText;
    }

    public Collection<String> suggest() {
        CharStream cs = CharStreams.fromString(ebnfText);
        EbnfLexer lexer = new EbnfLexer(cs);
        lexer.reset();
        List<CommonToken> tokens = new ArrayList<>();
        Token curr = null;
        while ((curr = lexer.nextToken()) != null && curr.getType() != Token.EOF) {
            CommonToken ct = new CommonToken(curr);
            ct.setTokenIndex(tokens.size() - 1);
            tokens.add(ct);
        }
        lexer.reset();
        CommonTokenStream cts = new CommonTokenStream(lexer);
        EbnfParser parser = new EbnfParser(cts);
        V v = new V();
        parser.ebnf_sequence().accept(v);
        List<EbItem> items = new ArrayList<>();
        for (EbnfParser.EbnfItemContext ctx : v.elements) {
            items.add(new EbItem(ctx, tokens));
        }
        EbItems ebis = new EbItems(items, tokens);

        return Collections.singleton(ebis.toString());
    }

    static class EbItems {

        private final List<EbItem> all = new ArrayList<>();
        private final List<CommonToken> tokens;
        private final IntMap<EbItem> tokenOwnership = CollectionUtils.intMap(30);
        private final int first;
        private final int last;

        EbItems(Collection<EbItem> items, List<CommonToken> tokens) {
            all.addAll(items);
            all.sort(Comparator.naturalOrder());
            this.tokens = tokens;
            System.out.println("ITEMS: ");
            int first = Integer.MAX_VALUE;
            int last = Integer.MIN_VALUE;
            for (int i = 0; i < all.size(); i++) {
                EbItem item = all.get(i);
                first = Math.min(item.startToken, first);
                last = Math.max(item.stopToken, last);
                for (int j = item.startToken; j <= item.stopToken; j++) {
                    tokenOwnership.put(j, item);
                }
            }
            this.first = first;
            this.last = last;
            for (int ix = first; ix <= last; ix++) {
                Token tok = tokens.get(ix);
                EbItem ebi = tokenOwnership.get(ix);
                if (ebi == null) {
                    System.out.println("T-" + ix + ". "
                            + "'" + tok.getText() + "' "
                            + " <nobody>"
                    );
                } else {
                    System.out.println("T-" + ix
                            + "'" + tok.getText() + "'"
                            + ". " + ebi);
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = first; i <= last; i++) {
                EbItem ebi = tokenOwnership.get(i);
                if (ebi != null) {

                }
            }
            return sb.toString();
        }
    }

    static final class EbItem implements IntRange<EbItem> {

        final EbnfParser.EbnfItemContext ctx;
        private final int startToken;
        private final int stopToken;
        private final List<CommonToken> toks;

        public EbItem(EbnfParser.EbnfItemContext ctx, int startToken, int endToken, List<CommonToken> toks) {
            this.ctx = ctx;
            this.startToken = startToken;
            this.stopToken = endToken;
            this.toks = toks;
        }

        public EbItem(EbnfParser.EbnfItemContext ctx, List<CommonToken> toks) {
            this.ctx = ctx;
            startToken = ctx.start.getTokenIndex();
            stopToken = ctx.stop.getTokenIndex();
            this.toks = toks;
        }

//        void writeToken(Token token, Transformation xform, StringBuilder sb) {
//            if (xform != null) {
//                if (token.getTokenIndex() == startToken) {
//                    xform.onBegin(sb, this, token);
//                } else if (token.getTokenIndex() == stopToken) {
//                    xform.onEnd(sb, this, token);
//                } else {
//                    sb.append(token.getText());
//                }
//            } else {
//                sb.append(token.getText());
//            }
//        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = startToken; i <= stopToken; i++) {
                sb.append(toks.get(i).getText());
            }
            return sb.toString();
        }

        public void text(StringBuilder into, List<CommonToken> tokens) {
            text(into, tokens, startToken, stopToken);
        }

        public boolean hasVarName() {
            return ctx.pfx != null && ctx.pfx.IDENTIFIER() != null;
        }

        public void text(StringBuilder into, List<CommonToken> tokens, int startAt, int stopAt) {
            int first = Math.max(startAt, ctx.start.getTokenIndex());
            int last = Math.min(stopAt, ctx.stop.getTokenIndex());
            for (int i = first; i <= last; i++) {
                CommonToken tok = tokens.get(i);
                into.append(tok.getText());
            }
        }

        public String textLength() {
            return ctx.getText().toString();
        }

        public boolean owns(Token token) {
            int ix = token.getTokenIndex();
            return ix >= startToken
                    && ix <= stopToken;
        }

        public Kind kind() {
            if (ctx.ebnf != null) {
                EbnfParser.EbnfsuffixContext e = ctx.ebnf;
                if (e.Star() != null) {
                    return Kind.STAR;
                } else if (e.Plus() != null) {
                    return Kind.PLUS;
                } else if (e.Question() != null) {
                    return Kind.QUESTION;
                }
            }
            return Kind.UNKNOWN;
        }

        public boolean isGreedy() {
            return ctx.ebnf == null ? false
                    : ctx.ebnf.ungreedy != null;
        }

        @Override
        public int start() {
            return startToken;
        }

        @Override
        public int end() {
            return stopToken;
        }

        @Override
        public int size() {
            return end() - start();
        }

        @Override
        public EbItem newRange(int start, int size) {
            return new EbItem(ctx, start, start + size, toks);
        }

        @Override
        public EbItem newRange(long start, long size) {
            return new EbItem(ctx, (int) start, (int) (start + size), toks);
        }

        enum Kind {
            PLUS,
            STAR,
            QUESTION,
            UNKNOWN
        }
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

    static interface Transformation {
        boolean canRewrite(EbItem item);
        void rewrite (List<CommonToken> origTokens, EbItem item, TokenStreamRewriter rewriter);
    }

    static class DuplicateMultipleTransform implements Transformation {

        @Override
        public void rewrite(List<CommonToken> origTokens, EbItem item, TokenStreamRewriter rewriter) {
            if (item.hasVarName()) {
                // rewrite x : f=foo* to f=(foo foo+?)

                rewriter.delete(item.ctx.ebnf.Star().getSymbol());
                rewriter.insertAfter(item.ctx.pfx.stop, "(");

                rewriter.insertAfter(item.ctx.stop, ")");
            } else {

            }
//            rewriter.insertBefore(item.ctx.start, "(");
        }

        @Override
        public boolean canRewrite(EbItem item) {
            return item.kind() == Kind.STAR;
        }

    }
}

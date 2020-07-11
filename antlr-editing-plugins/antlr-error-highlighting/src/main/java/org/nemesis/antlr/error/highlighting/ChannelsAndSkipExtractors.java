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

import com.mastfrog.antlr.utils.TreeUtils;
import com.mastfrog.antlr.utils.TreeUtils.TreeNodeSearchResult;
import static com.mastfrog.antlr.utils.TreeUtils.ancestor;
import static com.mastfrog.antlr.utils.TreeUtils.isSingleChildAncestors;
import static com.mastfrog.antlr.utils.TreeUtils.nextSibling;
import static com.mastfrog.antlr.utils.TreeUtils.prevSibling;
import com.mastfrog.util.strings.Strings;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.EbnfContext;
import org.nemesis.antlr.ANTLRv4Parser.FragmentRuleDefinitionContext;
import org.nemesis.antlr.ANTLRv4Parser.FragmentRuleSpecContext;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.ANTLRv4Parser.LEXCOM_SKIP;
import org.nemesis.antlr.ANTLRv4Parser.LabeledParserRuleElementContext;
import org.nemesis.antlr.ANTLRv4Parser.LexerRuleBlockContext;
import org.nemesis.antlr.ANTLRv4Parser.LexerRuleElementBlockContext;
import org.nemesis.antlr.ANTLRv4Parser.LexerRuleElementsContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleDefinitionContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleSpecContext;
import org.nemesis.antlr.ANTLRv4Parser.TerminalContext;
import org.nemesis.antlr.ANTLRv4Parser.TokenRuleDefinitionContext;
import org.nemesis.antlr.ANTLRv4Parser.TokenRuleSpecContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.localizers.annotations.Localize;
import static com.mastfrog.antlr.utils.TreeUtils.searchParentTree;
import java.util.List;
import org.nemesis.antlr.ANTLRv4Parser.BlockContext;
import org.nemesis.antlr.ANTLRv4Parser.EbnfSuffixContext;
import org.nemesis.antlr.ANTLRv4Parser.LexerCommandContext;
import org.nemesis.antlr.ANTLRv4Parser.LexerRuleAltContext;
import org.nemesis.antlr.ANTLRv4Parser.LexerRuleElementContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleElementContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleLabeledAlternativeContext;

/**
 *
 * @author Tim Boudreau
 */
public class ChannelsAndSkipExtractors {

    @Localize(displayName = "Channel And Skip Directives")
    public static final RegionsKey<ChannelOrSkipInfo> CHSKIP = RegionsKey.create(ChannelOrSkipInfo.class,
            "channel-and-skip-directives");

    @Localize(displayName = "String Literals In Parser Rules")
    public static final RegionsKey<TermCtx> LONELY_STRING_LITERALS
            = RegionsKey.create(TermCtx.class, "lonely-terminals");

    @Localize(displayName = "String Literals As Only Rule")
    public static final RegionsKey<SingleTermCtx> SOLO_STRING_LITERALS
            = RegionsKey.create(SingleTermCtx.class, "solo-terminals");

    @Localize(displayName = "All String Literals")
    public static final RegionsKey<String> ALL_STRING_LITERALS
            = RegionsKey.create(String.class, "string-literals");

    @Localize(displayName = "Superfluous Parentheses")
    public static final RegionsKey<Integer> SUPERFLUOUS_PARENTEHSES
            = RegionsKey.create(Integer.class, "superfluous-parentheses");

    @ExtractionRegistration(mimeType = ANTLR_MIME_TYPE,
            entryPoint = ANTLRv4Parser.GrammarFileContext.class)
    public static void populateBuilder(ExtractorBuilder<? super ANTLRv4Parser.GrammarFileContext> bldr) {
        bldr.extractingRegionsUnder(CHSKIP)
                .whenRuleType(ANTLRv4Parser.LexComChannelContext.class)
                .extractingBoundsFromRuleAndKeyWith(ctx -> {
                    if (ctx.INT() != null) {
                        return new ChannelOrSkipInfo(ChSkip.CHANNEL, ctx.INT().getText());
                    } else {
                        return new ChannelOrSkipInfo(ChSkip.CHANNEL, "1"); // XXX
                    }
                })
                .whenTokenTypeMatches(LEXCOM_SKIP).derivingKeyWith(tok -> {
            return new ChannelOrSkipInfo(ChSkip.SKIP, "-1");
        }).finishRegionExtractor();

        bldr.extractingRegionsUnder(LONELY_STRING_LITERALS)
                .summingTokensFor(ANTLRv4Lexer.VOCABULARY)
                .whenRuleType(ANTLRv4Parser.ParserRuleAtomContext.class)
                .whenAncestorRuleOf(ANTLRv4Parser.ParserRuleDefinitionContext.class)
                .extractingKeyAndBoundsFromTerminalNodeWith((ANTLRv4Parser.ParserRuleAtomContext a, BiPredicate<TermCtx, TerminalNode> b) -> {
                    TerminalContext termCtx = a.terminal();
                    if (termCtx != null && termCtx.STRING_LITERAL() != null) {
                        return b.test(new TermCtx(termCtx.STRING_LITERAL()), termCtx.STRING_LITERAL());
                    }
                    return false;
                }).finishRegionExtractor();

        bldr.extractingRegionsUnder(SOLO_STRING_LITERALS)
                .summingTokensFor(ANTLRv4Lexer.VOCABULARY)
                .whenRuleType(ANTLRv4Parser.ParserRuleAtomContext.class)
                .extractingBoundsFromRuleAndKeyWith(atom -> {
                    TerminalContext ter = atom.terminal();
                    if (ter != null && ter.STRING_LITERAL() != null) {
                        if (isSingleChildAncestors(ter, ParserRuleDefinitionContext.class)) {
                            SingleTermCtx termCtx = new SingleTermCtx(ter.STRING_LITERAL(),
                                    RuleTypes.PARSER, ruleName(atom));
                            return termCtx;
                        }
                    }
                    return null;
                }).whenRuleType(ANTLRv4Parser.LexerRuleAtomContext.class)
                .extractingBoundsFromRuleAndKeyWith(atom -> {
                    TerminalContext ter = atom.terminal();
                    if (ter != null && ter.STRING_LITERAL() != null) {
                        if (isSingleChildAncestors(ter, TokenRuleDefinitionContext.class)) {
                            SingleTermCtx termCtx = new SingleTermCtx(ter.STRING_LITERAL(),
                                    RuleTypes.LEXER, ruleName(atom));
                            return termCtx;
                        } else if (isSingleChildAncestors(ter, FragmentRuleDefinitionContext.class)) {
                            SingleTermCtx termCtx = new SingleTermCtx(ter.STRING_LITERAL(),
                                    RuleTypes.FRAGMENT, ruleName(atom));
                            return termCtx;
                        }
                    }
                    return null;
                }).finishRegionExtractor();

        bldr.extractingRegionsUnder(ALL_STRING_LITERALS)
                .summingTokensFor(ANTLRv4Lexer.VOCABULARY)
                .whenRuleType(ANTLRv4Parser.TerminalContext.class)
                .extractingBoundsFromRuleAndKeyWith(term -> {
                    if (term.STRING_LITERAL() != null) {
                        return Strings.deSingleQuote(term.STRING_LITERAL().getText());
                    }
                    return null;
                })
                .finishRegionExtractor();

        bldr.extractingRegionsUnder(SUPERFLUOUS_PARENTEHSES)
                .whenRuleType(ANTLRv4Parser.BlockContext.class)
                .extractingBoundsFromRuleAndKeyWith(ChannelsAndSkipExtractors::checkSuperfluous)
                .whenRuleType(LexerRuleBlockContext.class)
                .whenAncestorRuleOf(LexerRuleElementBlockContext.class)
                .extractingKeyAndBoundsWith(ChannelsAndSkipExtractors::checkSuperfluous)
                .finishRegionExtractor();
    }

    static Integer checkSuperfluous(ANTLRv4Parser.BlockContext block) {
        if (!(block.getParent() instanceof EbnfContext) || ((block.getParent() instanceof EbnfContext && ((EbnfContext) block.getParent()).ebnfSuffix() == null))) {
            if (block.getParent() instanceof ANTLRv4Parser.LabeledParserRuleElementContext) {
                LabeledParserRuleElementContext ctx = (LabeledParserRuleElementContext) block.getParent();
                if (ctx.identifier() != null) {
                    return null;
                }
            }
            ParserRuleElementContext sibling = TreeUtils.findRightAdjacentSiblingOfOutermostAncestor(block, ParserRuleElementContext.class, ParserRuleElementContext.class, ParserRuleSpecContext.class, GrammarFileContext.class);
            // parentheses may be needed preceding an action block to guarantee
            // it runs for any of the parenthesized elements.
            // XXX could refine this to only be active if an OR token is encountered
            // within the parentheses
            if (sibling != null && sibling.actionBlock() != null) {
                return null;
            }
            if (hasSiblingAlternatives(block)) {
                return null;
            }
            return spId(block);
        }
        if (hasSiblingAlternatives(block)) return spId(block);
        return null;
    }

    static boolean isTopLevelWithAlternatives(ANTLRv4Parser.BlockContext block) {
        ParserRuleLabeledAlternativeContext alt = ancestor(block, ParserRuleLabeledAlternativeContext.class);
        if (alt != null) {
            if (alt.getParent() instanceof ParserRuleDefinitionContext) {
                ParserRuleDefinitionContext ctx = (ParserRuleDefinitionContext) alt.getParent();
                List<ParserRuleLabeledAlternativeContext> list = ctx.parserRuleLabeledAlternative();
                if (list != null && list.size() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasSiblingAlternatives(ANTLRv4Parser.BlockContext block) {
        boolean topLevel = ancestor(block, BlockContext.class) == null;
        if (block.altList() != null) {
            if (block.altList() != null && block.altList().parserRuleAlternative() != null
                    && block.altList().parserRuleAlternative().size() <= 1) {
                return false;
            }
            ParserRuleLabeledAlternativeContext alternativeAncestor = ancestor(block, ParserRuleLabeledAlternativeContext.class);
            if (alternativeAncestor != null) {
                boolean top2 = alternativeAncestor.getParent() instanceof ParserRuleDefinitionContext;
                ParseTree sib = TreeUtils.nextSibling(alternativeAncestor);
                ParseTree psib = TreeUtils.prevSibling(alternativeAncestor);

                if (sib instanceof TerminalNode) {
                    TerminalNode term = (TerminalNode) sib;
                    switch(term.getSymbol().getType()) {
                        case ANTLRv4Lexer.OR :
                            return true;
                    }
                }
                if (psib instanceof TerminalNode) {
                    TerminalNode term = (TerminalNode) psib;
                    switch(term.getSymbol().getType()) {
                        case ANTLRv4Lexer.OR :
                            return true;
                    }
                }
                if (topLevel && top2) {
                    return false;
                }
            }
        }
        boolean result = isTopLevelWithAlternatives(block);
        if (!result && topLevel) {
            EbnfContext ebnf = ancestor(block, EbnfContext.class);
            if (ebnf != null) {
                EbnfSuffixContext suffix = ebnf.ebnfSuffix();
                if (suffix == null) {
                    return false;
                }
                if (suffix.QUESTION() != null || suffix.PLUS() != null || suffix.STAR() != null) {
                    return true;
                }
            }
        }

        return result;
    }

    static boolean checkSuperfluous(LexerRuleBlockContext blockContext, BiPredicate<Integer, int[]> consumer) {
        boolean hasOrs = blockContext.OR() != null && !blockContext.OR().isEmpty();

        LexerRuleElementBlockContext immediateBlockAncestor
                = ancestor(blockContext, LexerRuleElementBlockContext.class);
        LexerRuleElementBlockContext topBlockAncestor
                = TreeUtils.findOutermostAncestor(blockContext,
                        LexerRuleElementBlockContext.class,
                        TokenRuleSpecContext.class,
                        FragmentRuleSpecContext.class, GrammarFileContext.class);

        boolean topLevel = immediateBlockAncestor == topBlockAncestor;
        if (hasEbnf(blockContext)) {
            return false;
        }
        if (topLevel && hasActionOrLexerCommands(blockContext)) {
            if (hasOrs) {
                return false;
            }
        } else if (topLevel) {
//            return true;
        }
        LexerRuleAltContext altAncestor = ancestor(blockContext, LexerRuleAltContext.class);
        if (hasOrs && altAncestor != null) {
            TerminalNode term = nextSibling(altAncestor, TerminalNode.class);
            if (term == null) {
                term = prevSibling(altAncestor, TerminalNode.class);
            }
            if (term != null) {
                switch (term.getSymbol().getType()) {
                    case ANTLRv4Lexer.OR:
                        return false;
                    case ANTLRv4Lexer.LPAREN:
                    case ANTLRv4Lexer.RPAREN:
                }
            }
        }
        if (!hasOrs || TreeUtils.isSingleChildDescendants(blockContext)) {
            boolean tres = writeBlockAncestor(immediateBlockAncestor, consumer, blockContext);
            return true;
        }
        TreeNodeSearchResult testRes = searchParentTree(blockContext, ctx -> {
            assert ctx != blockContext : "Got same node";
            if (ctx instanceof LexerRuleElementBlockContext) {
                LexerRuleElementBlockContext bl = (LexerRuleElementBlockContext) ctx;
                if (bl.LPAREN() != null && bl.RPAREN() != null && bl.getChildCount() == 3) {
                    return TreeNodeSearchResult.CONTINUE;
                } else {
                    return TreeNodeSearchResult.FAIL;
                }
            }
            if (ctx instanceof LexerRuleElementsContext) {
                LexerRuleElementsContext els = (LexerRuleElementsContext) ctx;
                if (els.getParent() instanceof LexerRuleAltContext) {
                    LexerRuleAltContext alt = (LexerRuleAltContext) els.getParent();
                    if (alt.lexerCommands() != null) {
                        if (blockContext.OR() != null && !blockContext.OR().isEmpty()) {
                            if (topLevel) {
                                return TreeNodeSearchResult.OK;
                            }
                            return TreeNodeSearchResult.FAIL;
                        }
                    }
                }
                if (els.getChildCount() == 1) {
                    return TreeNodeSearchResult.OK;
                } else {
                    ParseTree node = els.getChild(0);
                    if (ancestor(blockContext, node.getClass()) == node) {
                        return TreeNodeSearchResult.OK;
                    } else {
                        return TreeNodeSearchResult.FAIL;
                    }
                }
            }
            return ctx.getChildCount() == 1 ? TreeNodeSearchResult.CONTINUE : TreeNodeSearchResult.FAIL;
        });
        if (testRes.isSuccess()) {
            // immediateBlockAncestor
            return writeBlockAncestor(immediateBlockAncestor, consumer, blockContext);
        }
        return false;
    }

    static boolean writeBlockAncestor(LexerRuleElementBlockContext immediateBlockAncestor, BiPredicate<Integer, int[]> consumer, LexerRuleBlockContext blockContext) {
        int[] offsets = new int[]{immediateBlockAncestor.start.getStartIndex(),
            immediateBlockAncestor.stop.getStopIndex() + 1};
        return consumer.test(spId(blockContext), offsets);
    }

    static boolean hasActionOrLexerCommands(LexerRuleBlockContext blockContext) {
        boolean hasLexerCommands = TreeUtils.hasRightAdjacentSiblingOfOutrmostAncestor(blockContext, LexerRuleElementsContext.class, LexerCommandContext.class, GrammarFileContext.class, TokenRuleSpecContext.class);
        if (hasLexerCommands) {
            return false;
        }
        LexerRuleAltContext altContext = ancestor(blockContext, LexerRuleAltContext.class);
        if (altContext != null) {
            // This may catch ancestors behind another block context, so make sure there is no
            // block between us and the alt we're dealing with
            LexerRuleBlockContext ancestorBlock = ancestor(blockContext, LexerRuleBlockContext.class);
            if (ancestorBlock == null || ancestorBlock.depth() < altContext.depth()) {
                if (altContext.lexerCommands() != null) {
                    if (blockContext.OR() != null && !blockContext.OR().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasEbnf(LexerRuleBlockContext blockContext) {
        if (blockContext.getParent() != null && blockContext.getParent() instanceof LexerRuleElementBlockContext) {
            LexerRuleElementBlockContext parentBlock = (LexerRuleElementBlockContext) blockContext.getParent();
            if (parentBlock.getParent() != null && parentBlock.getParent() instanceof LexerRuleElementContext) {
                LexerRuleElementContext parentParent = (LexerRuleElementContext) parentBlock.getParent();
                int ix = parentParent.children.indexOf(parentBlock);
                if (parentParent.children.size() > ix + 1) {
                    if (parentParent.getChild(ix + 1) instanceof EbnfSuffixContext) {
                        EbnfSuffixContext suffix = (EbnfSuffixContext) parentParent.getChild(ix + 1);
                        if (suffix.QUESTION() != null || suffix.PLUS() != null || suffix.STAR() != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static int spId(ParserRuleContext ctx) {
        return ctx.getRuleIndex() * (ctx.start.getTokenIndex() + (3775311 * ctx.stop.getTokenIndex()));
    }

    private static String ruleName(ParserRuleContext ctx) {
        do {
            if (ctx instanceof ANTLRv4Parser.ParserRuleSpecContext) {
                ParserRuleSpecContext decl = (ParserRuleSpecContext) ctx;
                if (decl.parserRuleDeclaration() != null && decl.parserRuleDeclaration().parserRuleIdentifier() != null) {
                    return decl.parserRuleDeclaration().parserRuleIdentifier().getText();
                } else {
                    return null;
                }
            } else if (ctx instanceof TokenRuleSpecContext) {
                TokenRuleSpecContext decl = (TokenRuleSpecContext) ctx;
                if (decl.tokenRuleDeclaration() != null && decl.tokenRuleDeclaration().tokenRuleIdentifier() != null) {
                    return decl.tokenRuleDeclaration().tokenRuleIdentifier().getText();
                } else {
                    return null;
                }
            } else if (ctx instanceof ANTLRv4Parser.FragmentRuleSpecContext) {
                FragmentRuleSpecContext decl = (FragmentRuleSpecContext) ctx;
                if (decl.fragmentRuleDeclaration() != null && decl.fragmentRuleDeclaration().id != null) {
                    return decl.fragmentRuleDeclaration().id.getText();
                }
            }
            ctx = ctx.getParent();
        } while (ctx != null && !(ctx instanceof GrammarFileContext));
        return null;
    }

    static class TermCtx {

        final String text;
        final int start;
        final int end;

        TermCtx(TerminalNode nd) {
            Token tok = nd.getSymbol();
            start = tok.getStartIndex();
            end = tok.getStopIndex() + 1;
            text = tok.getText();
        }

        public String toString() {
            return "LonelyTerm(" + text + "@" + start + ":" + end + ")";
        }
    }

    static class SingleTermCtx {

        final String text;
        final int start;
        final int end;
        final RuleTypes ruleType;
        final String ruleName;

        SingleTermCtx(TerminalNode nd, RuleTypes ruleType, String ruleName) {
            Token tok = nd.getSymbol();
            start = tok.getStartIndex();
            end = tok.getStopIndex() + 1;
            text = Strings.deSingleQuote(tok.getText());
            this.ruleType = ruleType;
            this.ruleName = ruleName;
        }

        @Override
        public String toString() {
            return "'" + text + "' in " + ruleName + " " + ruleType + " @ " + start + ":" + end;
        }

        @Override
        public int hashCode() {
            return text.hashCode() * 71;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof SingleTermCtx) {
                SingleTermCtx stc = (SingleTermCtx) o;
                return Objects.equals(text, stc.text);
            }
            return false;
        }
    }

    public enum ChSkip {
        CHANNEL,
        SKIP;
    }

    static class ChannelOrSkipInfo {

        private final ChSkip kind;
        private final String channelText;

        public ChannelOrSkipInfo(ChSkip kind, String channelText) {
            this.kind = kind;
            this.channelText = channelText;
        }

        public String toString() {
            return kind.name() + ":" + channelText;
        }

        public ChSkip kind() {
            return kind;
        }

        public int channelNumber() {
            if ("-1".equals(channelText)) {
                return 1;
            }
            try {
                return Integer.parseInt(channelText);
            } catch (NumberFormatException ex) {
                return 1;
            }
        }

    }

    ChannelsAndSkipExtractors() {
        throw new AssertionError();
    }
}

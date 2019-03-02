package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.EbnfProperty;
import static org.nemesis.antlr.common.extractiontypes.EbnfProperty.PLUS;
import static org.nemesis.antlr.common.extractiontypes.EbnfProperty.QUESTION;
import static org.nemesis.antlr.common.extractiontypes.EbnfProperty.STAR;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion.FoldableKind;
import org.nemesis.antlr.common.extractiontypes.HeaderMatter;
import org.nemesis.antlr.common.extractiontypes.ImportKinds;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.FragmentRuleDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarTypeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TokenRuleDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarType;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.NamedRegionData;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.UnknownNameReference.UnknownNameReferenceResolver;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrExtractor {

    public static final String MISSING_TOKEN_ID = "<missing TOKEN_ID>";
    public static final String MISSING_ID = "<missing ID>";

    private final Extractor<ANTLRv4Parser.GrammarFileContext> extractor;

    private static boolean containsNewlines(ParserRuleContext ctx) {
        String txt = ctx.getText();
        return txt == null ? false : txt.trim().indexOf('\n') > 0;
    }

    static void extractFragmentRuleBounds(FragmentRuleDeclarationContext ctx, BiConsumer<FoldableRegion, ParserRuleContext> cons) {
        System.out.println("EXTRACT FRAGMENT " + ctx.TOKEN_ID().getText());
        if (containsNewlines(ctx)) {
            cons.accept(FoldableKind.RULE.createFor(ctx.TOKEN_ID().getText()), ctx);
        }
    }

    static void extractLexerRuleBounds(TokenRuleDeclarationContext ctx, BiConsumer<FoldableRegion, ParserRuleContext> cons) {
        System.out.println("EXTRACT LEXER " + ctx.TOKEN_ID().getText());
        if (containsNewlines(ctx)) {
            cons.accept(FoldableKind.RULE.createFor(ctx.TOKEN_ID().getText()), ctx);
        }
    }

    static void extractParserRuleBounds(ParserRuleDeclarationContext ctx, BiConsumer<FoldableRegion, ParserRuleContext> cons) {
        System.out.println("EXTRACT PARSER " + ctx.parserRuleIdentifier().PARSER_RULE_ID().getText());
        if (containsNewlines(ctx)) {
            cons.accept(FoldableKind.RULE.createFor(ctx.parserRuleIdentifier().PARSER_RULE_ID().getText()), ctx);
        }
    }

    static void extractActionBounds(ParserRuleContext ctx, BiConsumer<FoldableRegion, ParserRuleContext> cons) {
        System.out.println("EXTRACT ACTION");
        cons.accept(FoldableKind.ACTION.createFor(ctx.getText()), ctx);
    }

    static FoldableRegion regionForToken(Token token) {
        switch (token.getType()) {
            case ANTLRv4Lexer.CHN_BLOCK_COMMENT:
            case ANTLRv4Lexer.HEADER_BLOCK_COMMENT:
            case ANTLRv4Lexer.HEADER_P_BLOCK_COMMENT:
            case ANTLRv4Lexer.ID_BLOCK_COMMENT:
            case ANTLRv4Lexer.IMPORT_BLOCK_COMMENT:
            case ANTLRv4Lexer.LEXCOM_BLOCK_COMMENT:
            case ANTLRv4Lexer.OPT_BLOCK_COMMENT:
            case ANTLRv4Lexer.PARDEC_BLOCK_COMMENT:
            case ANTLRv4Lexer.TOK_BLOCK_COMMENT:
            case ANTLRv4Lexer.BLOCK_COMMENT:
                System.out.println("EXTRACT COMMENT FOR " + ANTLRv4Lexer.tokenNames[token.getType()]);
                return FoldableKind.COMMENT.createFor(token.getText());
            case ANTLRv4Lexer.DOC_COMMENT:
                System.out.println("EXTRACT DOC COMMENT FOR " + ANTLRv4Lexer.tokenNames[token.getType()]);
                return FoldableKind.DOC_COMMENT.createFor(token.getText());
            default:
                return null;
        }
    }

    @ExtractionRegistration(mimeType=ANTLR_MIME_TYPE, entryPoint=GrammarFileContext.class)
    static void populateBuilder(ExtractorBuilder<? super GrammarFileContext> bldr) {
        // First build code-fold regions - all comments and rules, plus
        // miscellaneous such as action and options blocks
        bldr.extractingRegionsUnder(AntlrKeys.FOLDABLES)
                .whenTokenTypeMatches(
                        ANTLRv4Lexer.BLOCK_COMMENT,
                        ANTLRv4Lexer.DOC_COMMENT,
                        ANTLRv4Lexer.CHN_BLOCK_COMMENT,
                        ANTLRv4Lexer.HEADER_BLOCK_COMMENT,
                        ANTLRv4Lexer.HEADER_P_BLOCK_COMMENT,
                        ANTLRv4Lexer.ID_BLOCK_COMMENT,
                        ANTLRv4Lexer.IMPORT_BLOCK_COMMENT,
                        ANTLRv4Lexer.LEXCOM_BLOCK_COMMENT,
                        ANTLRv4Lexer.OPT_BLOCK_COMMENT,
                        ANTLRv4Lexer.PARDEC_BLOCK_COMMENT,
                        ANTLRv4Lexer.TOK_BLOCK_COMMENT)
                .derivingKeyWith(AntlrExtractor::regionForToken)
                .whenRuleType(ANTLRv4Parser.FragmentRuleDeclarationContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractFragmentRuleBounds)
                .whenRuleType(ANTLRv4Parser.ParserRuleDeclarationContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractParserRuleBounds)
                .whenRuleType(ANTLRv4Parser.TokenRuleDeclarationContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractLexerRuleBounds)
                .whenRuleType(ANTLRv4Parser.ActionContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.ActionBlockContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.AnalyzerDirectiveSpecContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.TokenVocabSpecContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.RuleActionContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.OptionsSpecContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .finishRegionExtractor()
                // Extract a singleton, the grammar type
                .extractingSingletonUnder(AntlrKeys.GRAMMAR_TYPE)
                .using(GrammarTypeContext.class)
                .extractingObjectWith(AntlrExtractor::findGrammarType)
                .finishObjectExtraction()
                // No extract imports
                .extractNamedRegionsKeyedTo(ImportKinds.class)
                .recordingNamePositionUnder(AntlrKeys.IMPORTS)
                .whereRuleIs(ANTLRv4Parser.TokenVocabSpecContext.class)
                .derivingNameFromTokenWith(ImportKinds.TOKEN_VOCAB, AntlrExtractor::extractImportFromTokenVocab)
                .whereRuleIs(ANTLRv4Parser.DelegateGrammarContext.class)
                .derivingNameFromTerminalNodeWith(ImportKinds.DELEGATE_GRAMMAR, AntlrExtractor::extractImportFromDelegateGrammar)
                .finishNamedRegions()
                // Extract named regions so they are addressable by name or position in file
                .extractNamedRegionsKeyedTo(RuleTypes.class)
                // Store the bounds of the entire rule in the resulting Extraction under the key
                // RULE_BOUNDS, which will be parameterized on RuleTypes so we can query for a
                // NamedSemanticRegions<RuleTypes>
                .recordingRuleRegionUnder(AntlrKeys.RULE_BOUNDS)
                // Store the bounds of just the rule name (for goto declaration) under the key
                // RULE_NAMES in the resulting extractoin
                .recordingNamePositionUnder(AntlrKeys.RULE_NAMES)
                // Extracts the rule id from a token declaration
                .whereRuleIs(ANTLRv4Parser.TokenRuleDeclarationContext.class)
                .derivingNameWith(AntlrExtractor::deriveIdFromLexerRule)
                // Extracts the rule id from a parser rule declaration, using reflection instead of
                // a method, to show that works
                .whereRuleIs(ANTLRv4Parser.ParserRuleSpecContext.class)
                .derivingNameWith("parserRuleDeclaration().parserRuleIdentifier().PARSER_RULE_ID()", RuleTypes.PARSER)
                // Extracts the rule id from a fragment declaration
                .whereRuleIs(ANTLRv4Parser.FragmentRuleDeclarationContext.class)
                .derivingNameWith(AntlrExtractor::deriveIdFromFragmentDeclaration)
                // Generate fake definitions for declared tokens so we don't flag them as errors
                .whereRuleIs(ANTLRv4Parser.TokenListContext.class)
                .derivingNameFromTerminalNodes(RuleTypes.LEXER, AntlrExtractor::deriveTokenIdFromTokenList)
                // Now collect usages of the rule ids we just collected.  This gets us both
                // the ability to query for a NamedReferenceSet<RuleTypes> which has the reference
                // and can resolve the reference, and a bidirectional graph built from arrays of BitSets
                // which can let us walk the closure of a rule in either direction
                .collectingReferencesUnder(AntlrKeys.RULE_NAME_REFERENCES)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.TerminalContext.class)
                .derivingReferenceOffsetsFromTokenWith(AntlrExtractor::deriveReferencedNameFromTerminalContext)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.ParserRuleReferenceContext.class)
                // Include the type hint PARSER so we can detect references that should be parser rules but are not
                .derivingReferenceOffsetsFromTokenWith(RuleTypes.PARSER, AntlrExtractor::deriveReferenceFromParserRuleReference)
                // Done specifying how to collect references
                .finishReferenceCollector()
                .finishNamedRegions()
                // Just collect the offsets of all BlockContext trees, in a nested data structure
                .extractingRegionsUnder(AntlrKeys.BLOCKS)
                .whenRuleType(ANTLRv4Parser.BlockContext.class)
                .extractingBoundsFromRule()
                .finishRegionExtractor()
                // More complex nested region extraction for EBNF contexts, so we can progressively
                // darket the background color in the editor of nested repetitions
                .extractingRegionsUnder(AntlrKeys.EBNFS)
                .whenRuleType(ANTLRv4Parser.EbnfContext.class)
                .extractingKeyAndBoundsWith(AntlrExtractor::extractEbnfPropertiesFromEbnfContext)
                .whenRuleType(ANTLRv4Parser.ParserRuleElementContext.class)
                .extractingKeyAndBoundsWith(AntlrExtractor::extractEbnfRegionFromParserRuleElement)
                .whenRuleType(ANTLRv4Parser.LexerRuleElementContext.class)
                .extractingKeyAndBoundsWith(AntlrExtractor::extractEbnfRegionFromLexerRuleElement)
                .finishRegionExtractor()
                // Extract named regions
                .extractNamedRegionsKeyedTo(RuleTypes.class)
                .recordingNamePositionUnder(AntlrKeys.NAMED_ALTERNATIVES)
                .whereRuleIs(ANTLRv4Parser.ParserRuleLabeledAlternativeContext.class)
                //                .derivingNameWith("identifier().ID()", Alternatives.NAMED_ALTERNATIVES)
                .derivingNameWith(AntlrExtractor::extractAlternativeLabelInfo)
                .finishNamedRegions()
                // Collect various header stuff we want to italicize
                .extractingRegionsUnder(AntlrKeys.HEADER_MATTER)
                .whenRuleType(ANTLRv4Parser.MemberActionContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.MEMBERS_DECLARATION)
                .whenRuleType(ANTLRv4Parser.HeaderActionBlockContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.HEADER_ACTION)
                .whenRuleType(ANTLRv4Parser.ImportDeclarationContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.IMPORT)
                .whenRuleType(ANTLRv4Parser.TokensSpecContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.TOKENS)
                .whenRuleType(ANTLRv4Parser.OptionsSpecContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.OPTIONS)
                .whenRuleType(ANTLRv4Parser.PackageDeclarationContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.PACKAGE)
                .whenRuleType(ANTLRv4Parser.ImportDeclarationContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.IMPORT)
                .whenRuleType(ANTLRv4Parser.AnalyzerDirectiveSpecContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.DIRECTIVE)
                .finishRegionExtractor()
                ;
    }

    AntlrExtractor() {
        ExtractorBuilder<GrammarFileContext> builder = Extractor.builder(GrammarFileContext.class);
        populateBuilder(builder);
        extractor = builder.build();
    }

    static GrammarType findGrammarType(ANTLRv4Parser.GrammarTypeContext gtc) {
        GrammarType grammarType = GrammarType.UNDEFINED;
        TerminalNode lexer = gtc.LEXER();
        TerminalNode parser = gtc.PARSER();
        TerminalNode grammar = gtc.GRAMMAR();
        if (lexer != null
                && parser == null
                && grammar != null) {
            grammarType = GrammarType.LEXER;
        }
        if (lexer == null
                && parser != null
                && grammar != null) {
            grammarType = GrammarType.PARSER;
        }
        if (lexer == null
                && parser == null
                && grammar != null) {
            grammarType = GrammarType.COMBINED;
        }
        return grammarType;
    }

    public Extraction extract(GrammarFileContext ctx, GrammarSource<?> src) {
        Supplier<TokenStream> streamSource = () -> {
            try {
                ANTLRv4Lexer lex = new ANTLRv4Lexer(src.stream());
                BufferedTokenStream stream = new BufferedTokenStream(lex);
                return stream;
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        };
        return extract(ctx, src, streamSource);
    }

    public Extraction extract(GrammarFileContext ctx, GrammarSource<?> src, Supplier<TokenStream> tokensSource) {
        return extractor.extract(ctx, src, tokensSource);
    }

    public Extraction extract(GrammarSource<?> src) throws IOException {
        ANTLRv4Lexer lex = new ANTLRv4Lexer(src.stream());
        lex.removeErrorListeners();
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lex, 0));
        parser.removeErrorListeners();
        return extract(parser.grammarFile(), src);
    }

    private static final AntlrExtractor INSTANCE = new AntlrExtractor();

    public static AntlrExtractor getDefault() {
        return INSTANCE;
    }

    private static NamedRegionData<RuleTypes> deriveIdFromLexerRule(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
        TerminalNode tn = ctx.TOKEN_ID();
        if (tn != null) {
            org.antlr.v4.runtime.Token tok = tn.getSymbol();
            if (tok != null) {
                return NamedRegionData.create(tok.getText(), RuleTypes.LEXER, tok.getStartIndex(), tok.getStopIndex() + 1);
            }
        }
        return null;
    }

    private static NamedRegionData<RuleTypes> deriveIdFromParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
        ANTLRv4Parser.ParserRuleDeclarationContext decl = ctx.parserRuleDeclaration();
        if (decl != null) {
            ANTLRv4Parser.ParserRuleIdentifierContext ident = decl.parserRuleIdentifier();
            if (ident != null) {
                TerminalNode tn = ident.PARSER_RULE_ID();
                if (tn != null) {
                    Token id = tn.getSymbol();
                    if (id != null) {
                        String ruleId = id.getText();
                        if (!MISSING_ID.equals(ruleId)) {
                            return NamedRegionData.create(ruleId, RuleTypes.PARSER, id.getStartIndex(), id.getStopIndex() + 1);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static NamedRegionData<RuleTypes> deriveIdFromFragmentDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
        TerminalNode idTN = ctx.TOKEN_ID();
        if (idTN != null) {
            Token idToken = idTN.getSymbol();
            if (idToken != null && !MISSING_TOKEN_ID.equals(idToken.getText())) {
                return NamedRegionData.create(idToken.getText(), RuleTypes.FRAGMENT, idToken.getStartIndex(), idToken.getStopIndex() + 1);
//                    names.add(idToken.getText(), AntlrRuleKind.FRAGMENT_RULE, idToken.getStartIndex(), idToken.getStopIndex() + 1);
            }
        }
        return null;
    }

    private static List<? extends TerminalNode> deriveTokenIdFromTokenList(ANTLRv4Parser.TokenListContext ctx) {
        return ctx.TOKEN_ID();
    }

    private static Token deriveReferencedNameFromTerminalContext(ANTLRv4Parser.TerminalContext ctx) {
        TerminalNode idTN = ctx.TOKEN_ID();
        if (idTN != null) {
            Token idToken = idTN.getSymbol();
//            System.out.println("IN TERMINAL CONTEXT '" + ctx.getText() + "' with tok '" + idToken.getText() + "'");
            if (idToken != null) {
                String id = idToken.getText();
                if (!MISSING_ID.equals(id) && !MISSING_TOKEN_ID.equals(id)) {
                    return idToken;
                }
            }
        }
        return null;
    }

    private static Token deriveReferenceFromParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
        ANTLRv4Parser.ParserRuleIdentifierContext pric = ctx.parserRuleIdentifier();
        if (pric != null) {
            TerminalNode pridTN = pric.PARSER_RULE_ID();
            if (pridTN != null) {
                Token tok = pridTN.getSymbol();
                if (tok != null && !MISSING_ID.equals(tok.getText())) {
//                        names.onReference(tok.getText(), tok.getStartIndex(), tok.getStopIndex() + 1);
                    return tok;
                }
            }
        }
        return null;
    }

    private static void extractEbnfPropertiesFromEbnfContext(ANTLRv4Parser.EbnfContext ctx, BiConsumer<Set<EbnfProperty>, int[]> c) {
        maybeAddEbnf(ctx.block(), ctx.ebnfSuffix(), c);
    }

    private static void extractEbnfRegionFromParserRuleElement(ANTLRv4Parser.ParserRuleElementContext ctx, BiConsumer<Set<EbnfProperty>, int[]> c) {
        if (ctx.parserRuleAtom() != null) {
            maybeAddEbnf(ctx.parserRuleAtom(), ctx.ebnfSuffix(), c);
        } else if (ctx.labeledParserRuleElement() != null) {
            maybeAddEbnf(ctx.labeledParserRuleElement(), ctx.ebnfSuffix(), c);
        }
    }

    private static void extractEbnfRegionFromLexerRuleElement(ANTLRv4Parser.LexerRuleElementContext ctx, BiConsumer<Set<EbnfProperty>, int[]> c) {
        if (ctx.lexerRuleAtom() != null) {
            maybeAddEbnf(ctx.lexerRuleAtom(), ctx.ebnfSuffix(), c);
        } else if (ctx.lexerRuleElementBlock() != null) {
            maybeAddEbnf(ctx.lexerRuleElementBlock(), ctx.ebnfSuffix(), c);
        }
    }

    private static NamedRegionData<RuleTypes> extractAlternativeLabelInfo(ANTLRv4Parser.ParserRuleLabeledAlternativeContext ctx) {
        ANTLRv4Parser.IdentifierContext idc = ctx.identifier();
        if (idc != null && ctx.SHARP() != null) {
            TerminalNode idTN = idc.ID();
//            return idTN;
            if (idTN != null) {
                Token labelToken = idTN.getSymbol();
                if (labelToken != null) {
                    String altnvLabel = labelToken.getText();
                    return NamedRegionData.create(altnvLabel,
                            RuleTypes.NAMED_ALTERNATIVES, ctx.SHARP().getSymbol().getStartIndex(), labelToken.getStopIndex() + 1);
                }
            }
        }
        return null;
    }

    private static Token extractImportFromTokenVocab(ANTLRv4Parser.TokenVocabSpecContext ctx) {
        ANTLRv4Parser.IdentifierContext idctx = ctx.identifier();
        if (idctx != null) {
            TerminalNode tn = idctx.ID();
            if (tn != null) {
                return tn.getSymbol();
            }
        }
        return null;
    }

    private static TerminalNode extractImportFromDelegateGrammar(ANTLRv4Parser.DelegateGrammarContext ctx) {
        ANTLRv4Parser.GrammarIdentifierContext gic = ctx.grammarIdentifier();
        if (gic != null) {
            ANTLRv4Parser.IdentifierContext ic = gic.identifier();
            if (ic != null) {
                return ic.ID();
            }
        }
        return null;
    }

    private static void maybeAddEbnf(ParserRuleContext repeated, ANTLRv4Parser.EbnfSuffixContext suffix, BiConsumer<Set<EbnfProperty>, int[]> c) {
        if (suffix == null || repeated == null) {
            return;
        }
        String ebnfString = suffix.getText();
        if (!ebnfString.isEmpty()) {
            Set<EbnfProperty> key = ebnfPropertiesForSuffix(suffix);
            if (!key.isEmpty()) {
                int start = repeated.getStart().getStartIndex();
                int end = suffix.getStop().getStopIndex() + 1;
                c.accept(key, new int[]{start, end});
            }
        }
    }

    private static Set<EbnfProperty> ebnfPropertiesForSuffix(ANTLRv4Parser.EbnfSuffixContext ctx) {
        Set<EbnfProperty> result = EnumSet.noneOf(EbnfProperty.class);
        if (ctx.PLUS() != null) {
            result.add(PLUS);
        }
        if (ctx.STAR() != null) {
            result.add(STAR);
        }
        if (ctx.QUESTION() != null && ctx.QUESTION().size() > 0) {
            //                System.out.println("question; " + ctx.QUESTION());
            result.add(QUESTION);
        }
        return result;
    }

    private static UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolver;

    public static UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolver() {
        if (resolver == null) {
            resolver = new UnknownResolver();
        }
        return resolver;
    }

    private static Extraction resolveImport(Extraction in, String importedGrammarName) {
        return in.resolveExtraction(AntlrExtractor.getDefault().extractor, importedGrammarName, gs -> {
            try {
                return AntlrExtractor.getDefault().extract(gs);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private static class UnknownResolver implements UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> {

        @Override
        public <X> X resolve(Extraction extraction, UnknownNameReference<RuleTypes> ref, UnknownNameReference.ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes, X> c) throws IOException {
            Set<String> imports = extraction.allKeys(AntlrKeys.IMPORTS);
            for (String importedGrammarName : imports) {
                Extraction impExt = resolveImport(extraction, importedGrammarName);
                if (impExt != null) {
                    String name = ref.name();
                    NamedSemanticRegions<RuleTypes> names = impExt.namedRegions(AntlrKeys.RULE_NAMES);
                    if (names.contains(name)) {
                        NamedSemanticRegion<RuleTypes> decl = names.regionFor(name);
                        if (decl != null) {
                            return c.resolved(ref, impExt.source(), names, decl);
                        }
                    }
                }
            }
            return null;
        }
    }
}

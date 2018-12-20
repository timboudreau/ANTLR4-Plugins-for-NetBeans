package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrExtractor {

    static final String MISSING_TOKEN_ID = "<missing TOKEN_ID>";
    static final String MISSING_ID = "<missing ID>";
    public static final NamedRegionExtractorBuilder.NamedRegionKey<RuleTypes> NAMED_ALTERNATIVES = NamedRegionExtractorBuilder.NamedRegionKey.create("labels", RuleTypes.class);
    public static final NamedRegionExtractorBuilder.NamedRegionKey<RuleTypes> RULE_NAMES = NamedRegionExtractorBuilder.NamedRegionKey.create("ruleNames", RuleTypes.class);
    public static final NamedRegionExtractorBuilder.NamedRegionKey<RuleTypes> RULE_BOUNDS = NamedRegionExtractorBuilder.NamedRegionKey.create("ruleBounds", RuleTypes.class);
    public static final NamedRegionExtractorBuilder.NameReferenceSetKey<RuleTypes> REFS = NamedRegionExtractorBuilder.NameReferenceSetKey.create("ruleRefs", RuleTypes.class);
    public static final GenericExtractorBuilder.RegionsKey<Void> BLOCKS = GenericExtractorBuilder.RegionsKey.create(Void.class, "blocks");
    public static final GenericExtractorBuilder.RegionsKey<Set<EbnfProperty>> EBNFS = GenericExtractorBuilder.RegionsKey.create(Set.class, "ebnfs");

    private final GenericExtractorBuilder.GenericExtractor<ANTLRv4Parser.GrammarFileContext> extractor;

    AntlrExtractor() {
        extractor = new GenericExtractorBuilder<>(ANTLRv4Parser.GrammarFileContext.class)
                // Extract named regions so they are addressable by name or position in file
                .extractNamedRegions(RuleTypes.class)
                // Store the bounds of the entire rule in the resulting Extraction under the key
                // RULE_BOUNDS, which will be parameterized on RuleTypes so we can query for a
                // NamedSemanticRegions<RuleTypes>
                .recordingRuleRegionUnder(RULE_BOUNDS)
                // Store the bounds of just the rule name (for goto declaration) under the key
                // RULE_NAMES in the resulting extractoin
                .recordingNamePositionUnder(RULE_NAMES)
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
                .collectingReferencesUnder(REFS)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.TerminalContext.class)
                .derivingReferenceOffsetsFromTokenWith(AntlrExtractor::deriveReferencedNameFromTerminalContext)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.ParserRuleReferenceContext.class)
                .derivingReferenceOffsetsFromTokenWith(AntlrExtractor::deriveReferenceFromParserRuleReference)
                // Done specifying how to collect references
                .finishReferenceCollector()
                .finishNamedRegions()
                // Just collect the offsets of all BlockContext trees, in a nested data structure
                .extractingRegionsTo(BLOCKS)
                .whenRuleType(ANTLRv4Parser.BlockContext.class)
                .extractingBoundsFromRule()
                // More complex nested region extraction for EBNF contexts, so we can progressively
                // darket the background color in the editor of nested repetitions
                .extractingRegionsTo(EBNFS)
                .whenRuleType(ANTLRv4Parser.EbnfContext.class)
                .extractingKeyAndBoundsFromWith(AntlrExtractor::extractEbnfPropertiesFromEbnfContext)
                .whenRuleType(ANTLRv4Parser.ParserRuleElementContext.class)
                .extractingKeyAndBoundsFromWith(AntlrExtractor::extractEbnfRegionFromParserRuleElement)
                .whenRuleType(ANTLRv4Parser.LexerRuleElementContext.class)
                .extractingKeyAndBoundsFromWith(AntlrExtractor::extractEbnfRegionFromLexerRuleElement)
                .finishRegionExtractor()
                // Extract named regions
                .extractNamedRegions(RuleTypes.class)
                .recordingNamePositionUnder(NAMED_ALTERNATIVES)
                .whereRuleIs(ANTLRv4Parser.ParserRuleLabeledAlternativeContext.class)
//                .derivingNameWith("identifier().ID()", Alternatives.NAMED_ALTERNATIVES)
                .derivingNameWith(AntlrExtractor::extractAlternativeLabelInfo)
                .finishNamedRegions()
                .build();
    }

    public Extraction extract(CharStream stream) {
        ANTLRv4Lexer lex = new ANTLRv4Lexer(stream);
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lex, 0));
        return extract(parser.grammarFile());
    }

    public Extraction extract(GrammarFileContext ctx) {
        return extractor.extract(ctx);
    }

    private static final AntlrExtractor INSTANCE = new AntlrExtractor();

    public static AntlrExtractor getDefault() {
        return INSTANCE;
    }

    private static NamedRegionExtractorBuilder.NamedRegionData<RuleTypes> deriveIdFromLexerRule(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
        TerminalNode tn = ctx.TOKEN_ID();
        if (tn != null) {
            org.antlr.v4.runtime.Token tok = tn.getSymbol();
            if (tok != null) {
                return new NamedRegionExtractorBuilder.NamedRegionData(tok.getText(), RuleTypes.LEXER, tok.getStartIndex(), tok.getStopIndex() + 1);
            }
        }
        return null;
    }

    private static NamedRegionExtractorBuilder.NamedRegionData<RuleTypes> deriveIdFromParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
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
                            return new NamedRegionExtractorBuilder.NamedRegionData(ruleId, RuleTypes.PARSER, id.getStartIndex(), id.getStopIndex() + 1);
                        }
                    }
                }
            }
        }

        // parserRuleDeclaration().parserRuleIdentifier().PARSER_RULE_ID()
        return null;
    }

    private static NamedRegionExtractorBuilder.NamedRegionData<RuleTypes> deriveIdFromFragmentDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
        TerminalNode idTN = ctx.TOKEN_ID();
        if (idTN != null) {
            Token idToken = idTN.getSymbol();
            if (idToken != null && !MISSING_TOKEN_ID.equals(idToken.getText())) {
                return new NamedRegionExtractorBuilder.NamedRegionData(idToken.getText(), RuleTypes.FRAGMENT, idToken.getStartIndex(), idToken.getStopIndex() + 1);
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
            System.out.println("IN TERMINAL CONTEXT '" + ctx.getText() + "' with tok '" + idToken.getText() + "'");
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


    static NamedRegionExtractorBuilder.NamedRegionData<RuleTypes> extractAlternativeLabelInfo(ANTLRv4Parser.ParserRuleLabeledAlternativeContext ctx) {
        ANTLRv4Parser.IdentifierContext idc = ctx.identifier();
        if (idc != null && ctx.SHARP() != null) {
            TerminalNode idTN = idc.ID();
//            return idTN;
            if (idTN != null) {
                Token labelToken = idTN.getSymbol();
                if (labelToken != null) {
                    String altnvLabel = labelToken.getText();
                    return new NamedRegionExtractorBuilder.NamedRegionData<>(altnvLabel, 
                            RuleTypes.NAMED_ALTERNATIVES, ctx.SHARP().getSymbol().getStartIndex(), labelToken.getStopIndex() + 1);
                }
            }
        }
        return null;
    }


    public enum RuleTypes {
        FRAGMENT,
        LEXER,
        PARSER,
        NAMED_ALTERNATIVES // only used for names, not bounds
    }

    public enum EbnfProperty {
        STAR,
        QUESTION,
        PLUS;

        static Set<EbnfProperty> forSuffix(ANTLRv4Parser.EbnfSuffixContext ctx) {
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
    }

    private static void maybeAddEbnf(ParserRuleContext repeated, ANTLRv4Parser.EbnfSuffixContext suffix, BiConsumer<Set<EbnfProperty>, int[]> c) {
        if (suffix == null || repeated == null) {
            return;
        }
        String ebnfString = suffix.getText();
        if (!ebnfString.isEmpty()) {
            Set<EbnfProperty> key = EbnfProperty.forSuffix(suffix);
            if (!key.isEmpty()) {
                int start = repeated.getStart().getStartIndex();
                int end = suffix.getStop().getStopIndex() + 1;
                c.accept(key, new int[]{start, end});
            }
        }
    }
}

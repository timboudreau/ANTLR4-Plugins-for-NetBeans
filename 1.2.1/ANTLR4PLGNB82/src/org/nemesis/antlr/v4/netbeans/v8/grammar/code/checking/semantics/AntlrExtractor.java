package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarTypeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.GrammarSource;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarType;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrExtractor {

    static final String MISSING_TOKEN_ID = "<missing TOKEN_ID>";
    static final String MISSING_ID = "<missing ID>";
    public static final NamedRegionExtractorBuilder.NamedRegionKey<ImportKinds> IMPORTS = NamedRegionExtractorBuilder.NamedRegionKey.create("imports", ImportKinds.class);
    public static final NamedRegionExtractorBuilder.NamedRegionKey<RuleTypes> NAMED_ALTERNATIVES = NamedRegionExtractorBuilder.NamedRegionKey.create("labels", RuleTypes.class);
    public static final NamedRegionExtractorBuilder.NamedRegionKey<RuleTypes> RULE_NAMES = NamedRegionExtractorBuilder.NamedRegionKey.create("ruleNames", RuleTypes.class);
    public static final NamedRegionExtractorBuilder.NamedRegionKey<RuleTypes> RULE_BOUNDS = NamedRegionExtractorBuilder.NamedRegionKey.create("ruleBounds", RuleTypes.class);
    public static final NamedRegionExtractorBuilder.NameReferenceSetKey<RuleTypes> RULE_NAME_REFERENCES = NamedRegionExtractorBuilder.NameReferenceSetKey.create("ruleRefs", RuleTypes.class);
    public static final GenericExtractorBuilder.RegionsKey<Void> BLOCKS = GenericExtractorBuilder.RegionsKey.create(Void.class, "blocks");
    public static final GenericExtractorBuilder.RegionsKey<HeaderMatter> HEADER_MATTER = GenericExtractorBuilder.RegionsKey.create(HeaderMatter.class, "headerMatter");
    public static final GenericExtractorBuilder.RegionsKey<Set<EbnfProperty>> EBNFS = GenericExtractorBuilder.RegionsKey.create(Set.class, "ebnfs");
    public static final GenericExtractorBuilder.SingleObjectKey<GrammarType> GRAMMAR_TYPE = GenericExtractorBuilder.SingleObjectKey.create(GrammarType.class);

    private final GenericExtractorBuilder.GenericExtractor<ANTLRv4Parser.GrammarFileContext> extractor;

    AntlrExtractor() {
        extractor = new GenericExtractorBuilder<>(ANTLRv4Parser.GrammarFileContext.class)
                .extractingSingletonUnder(GRAMMAR_TYPE)
                .whereRuleIs(GrammarTypeContext.class)
                .extractingObjectWith(AntlrExtractor::findGrammarType)
                .finishObjectExtraction()

                .extractNamedRegions(ImportKinds.class)
                .recordingNamePositionUnder(IMPORTS)
                .whereRuleIs(ANTLRv4Parser.TokenVocabSpecContext.class)
                .derivingNameFromTokenWith(ImportKinds.TOKEN_VOCAB, AntlrExtractor::extractImportFromTokenVocab)
                .whereRuleIs(ANTLRv4Parser.DelegateGrammarContext.class)
                .derivingNameFromTerminalNodeWith(ImportKinds.DELEGATE_GRAMMAR, AntlrExtractor::extractImportFromDelegateGrammar)
                .finishNamedRegions()
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
                .collectingReferencesUnder(RULE_NAME_REFERENCES)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.TerminalContext.class)
                .derivingReferenceOffsetsFromTokenWith(AntlrExtractor::deriveReferencedNameFromTerminalContext)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.ParserRuleReferenceContext.class)
                // Include the type hint PARSER so we can detect references that should be parser rules but are not
                .derivingReferenceOffsetsFromTokenWith(RuleTypes.PARSER, AntlrExtractor::deriveReferenceFromParserRuleReference)
                // Done specifying how to collect references
                .finishReferenceCollector()
                .finishNamedRegions()
                // Just collect the offsets of all BlockContext trees, in a nested data structure
                .extractingRegionsTo(BLOCKS)
                .whenRuleType(ANTLRv4Parser.BlockContext.class)
                .extractingBoundsFromRule()
                .finishRegionExtractor()
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
                // Collect various header stuff we want to italicize
                .extractingRegionsTo(HEADER_MATTER)
                .whenRuleType(ANTLRv4Parser.MemberActionContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.MEMBERS_DECL)
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
                .build();
    }

    public enum HeaderMatter {
        MEMBERS_DECL,
        HEADER_ACTION,
        IMPORT,
        TOKENS,
        OPTIONS,
        PACKAGE,
        DIRECTIVE,
    }

    public enum RuleTypes {
        FRAGMENT,
        LEXER,
        PARSER,
        NAMED_ALTERNATIVES; // only used for names, not bounds

        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum ImportKinds {
        TOKEN_VOCAB,
        DELEGATE_GRAMMAR
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

    static final GenericExtractorBuilder.RelativeFileObjectResolver ANTLR_RESOLVER = (rt, nm, in) -> {
        return ProjectHelper.resolveRelativeGrammar(rt, nm);
    };

    public static GrammarSource<?> grammarSource(Document doc) {
        return GrammarSource.forDocument(doc, ANTLR_RESOLVER);
    }

    public static GrammarSource<?> grammarSource(FileObject fo) {
        return GrammarSource.forFileObject(fo, ANTLR_RESOLVER);
    }

    public Extraction extract(FileObject fo) throws IOException {
        DataObject dob = DataObject.find(fo);
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        if (ck != null) {
            Document d = ck.getDocument();
            if (d != null) {
                return extract(d);
            }
        }
        return extract(CharStreams.fromString(fo.asText()));
    }

    public Extraction extract(Document doc) throws IOException {
        return extract(grammarSource(doc));
    }

    public Extraction extract(CharStream stream) throws IOException {
        return extract(GrammarSource.forSingleCharStream("x", stream));
    }

    public Extraction extract(GrammarFileContext ctx, GrammarSource<?> src) {
        return extractor.extract(ctx, src);
    }

    public Extraction extract(GrammarSource<?> src) throws IOException {
        ANTLRv4Lexer lex = new ANTLRv4Lexer(src.stream());
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lex, 0));
        return extract(parser.grammarFile(), src);
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

    static Token extractImportFromTokenVocab(ANTLRv4Parser.TokenVocabSpecContext ctx) {
        ANTLRv4Parser.IdentifierContext idctx = ctx.identifier();
        if (idctx != null) {
            TerminalNode tn = idctx.ID();
            if (tn != null) {
                return tn.getSymbol();
            }
        }
        return null;
    }

    static TerminalNode extractImportFromDelegateGrammar(ANTLRv4Parser.DelegateGrammarContext ctx) {
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
            Set<EbnfProperty> key = EbnfProperty.forSuffix(suffix);
            if (!key.isEmpty()) {
                int start = repeated.getStart().getStartIndex();
                int end = suffix.getStop().getStopIndex() + 1;
                c.accept(key, new int[]{start, end});
            }
        }
    }

    private static UnknownResolver resolver;

    public static NamedRegionExtractorBuilder.UnknownNameReference.UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolver() {
        if (resolver == null) {
            resolver = new UnknownResolver();
        }
        return resolver;
    }

    static Extraction resolveImport(Extraction in, String importedGrammarName) {
        return in.resolveExtraction(AntlrExtractor.getDefault().extractor, importedGrammarName, gs -> {
            try {
                return AntlrExtractor.getDefault().extract(gs);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    static class UnknownResolver implements NamedRegionExtractorBuilder.UnknownNameReference.UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> {

        @Override
        public <X> X resolve(Extraction extraction, NamedRegionExtractorBuilder.UnknownNameReference ref,
                NamedRegionExtractorBuilder.UnknownNameReference.ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes, X> c) {

            Set<String> imports = extraction.allKeys(IMPORTS);
            for (String importedGrammarName : imports) {
                Extraction impExt = resolveImport(extraction, importedGrammarName);
                if (impExt != null) {
                    String name = ref.name();
                    NamedSemanticRegions<RuleTypes> names = impExt.namedRegions(RULE_NAMES);
                    if (names.contains(name)) {
                        NamedSemanticRegions.NamedSemanticRegion<RuleTypes> decl = names.regionFor(name);
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

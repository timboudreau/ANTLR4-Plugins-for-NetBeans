package org.nemesis.data;

import org.nemesis.data.graph.hetero.BitSetHeteroObjectGraph;
import org.nemesis.data.named.NamedSemanticRegions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.BlockContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.EbnfContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarTypeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexerRuleElementContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleElementContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleLabeledAlternativeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TokenRuleDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractorTest;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor.MISSING_ID;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor.MISSING_TOKEN_ID;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.NamedRegionData;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.source.impl.GSAccessor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarType;
import org.nemesis.graph.IntGraph;
import org.nemesis.graph.StringGraph;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticParserTest {

    static NamedRegionData<RuleTypes> deriveIdFromLexerRule(TokenRuleDeclarationContext ctx) {
        TerminalNode tn = ctx.TOKEN_ID();
        if (tn != null) {
            org.antlr.v4.runtime.Token tok = tn.getSymbol();
            if (tok != null) {
                return NamedRegionData.create(tok.getText(), RuleTypes.LEXER, tok.getStartIndex(), tok.getStopIndex() + 1);
            }
        }
        return null;
    }

    static NamedRegionData<RuleTypes> deriveIdFromParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
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

        // parserRuleDeclaration().parserRuleIdentifier().PARSER_RULE_ID()
        return null;
    }

    static NamedRegionData<RuleTypes> deriveIdFromFragmentDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
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

    static List<? extends TerminalNode> deriveTokenIdFromTokenList(ANTLRv4Parser.TokenListContext ctx) {
        return ctx.TOKEN_ID();
    }

    static Token deriveReferencedNameFromTerminalContext(ANTLRv4Parser.TerminalContext ctx) {
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

    static Token deriveReferenceFromParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
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

    static void extractEbnfPropertiesFromEbnfContext(ANTLRv4Parser.EbnfContext ctx, BiConsumer<Set<EbnfProperty>, int[]> c) {
        maybeAddEbnf(ctx.block(), ctx.ebnfSuffix(), c);
    }

    static void extractEbnfRegionFromParserRuleElement(ANTLRv4Parser.ParserRuleElementContext ctx, BiConsumer<Set<EbnfProperty>, int[]> c) {
        if (ctx.parserRuleAtom() != null) {
            maybeAddEbnf(ctx.parserRuleAtom(), ctx.ebnfSuffix(), c);
        } else if (ctx.labeledParserRuleElement() != null) {
            maybeAddEbnf(ctx.labeledParserRuleElement(), ctx.ebnfSuffix(), c);
        }
    }

    static void extractEbnfRegionFromLexerRuleElement(ANTLRv4Parser.LexerRuleElementContext ctx, BiConsumer<Set<EbnfProperty>, int[]> c) {
        if (ctx.lexerRuleAtom() != null) {
            maybeAddEbnf(ctx.lexerRuleAtom(), ctx.ebnfSuffix(), c);
        } else if (ctx.lexerRuleElementBlock() != null) {
            maybeAddEbnf(ctx.lexerRuleElementBlock(), ctx.ebnfSuffix(), c);
        }
    }

    enum EbnfProperty {
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

    static void maybeAddEbnf(ParserRuleContext repeated, ANTLRv4Parser.EbnfSuffixContext suffix, BiConsumer<Set<EbnfProperty>, int[]> c) {
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

    static NamedRegionData<RuleTypes> extractAlternativeLabelInfo(ParserRuleLabeledAlternativeContext ctx) {
        ANTLRv4Parser.IdentifierContext idc = ctx.identifier();
        if (idc != null) {
            TerminalNode idTN = idc.ID();
//            return idTN;
            if (idTN != null) {
                Token labelToken = idTN.getSymbol();
                if (labelToken != null) {
                    String altnvLabel = labelToken.getText();
                    return NamedRegionData.create(altnvLabel, RuleTypes.ALTERNATIVE_LABEL, labelToken.getStartIndex(), labelToken.getStopIndex() + 1);
                }
            }
        }
        return null;
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

    public enum RuleTypes {
        FRAGMENT,
        LEXER,
        PARSER,
        ALTERNATIVE_LABEL
    }
    static final NamedRegionKey<RuleTypes> NAMED_ALTERNATIVES = NamedRegionKey.create("namedAlternatives", RuleTypes.class);
    static final NamedRegionKey<RuleTypes> RULE_NAMES = NamedRegionKey.create("ruleNames", RuleTypes.class);
    static final NamedRegionKey<RuleTypes> RULE_BOUNDS = NamedRegionKey.create("ruleBounds", RuleTypes.class);
    static final NameReferenceSetKey<RuleTypes> REFS = RULE_NAMES.createReferenceKey("ruleRefs");
    static final RegionsKey<Void> BLOCKS = RegionsKey.create(Void.class, "blocks");
    static final RegionsKey<Set<EbnfProperty>> EBNFS = RegionsKey.create(Set.class, "ebnfs");
    static final SingletonKey<GrammarType> GRAMMAR_TYPE = SingletonKey.create(GrammarType.class);

    @Test
    public void testGenericExtractor() throws Throwable {
        Extractor<GrammarFileContext> ext = Extractor.builder(GrammarFileContext.class)
                .extractingSingletonUnder(GRAMMAR_TYPE).using(GrammarTypeContext.class)
                .extractingObjectWith(SemanticParserTest::findGrammarType)
                .finishObjectExtraction()
                // Extract named regions so they are addressable by name or position in file
                .extractNamedRegionsKeyedTo(RuleTypes.class)
                // Store the bounds of the entire rule in the resulting Extraction under the key
                // RULE_BOUNDS, which will be parameterized on RuleTypes so we can query for a
                // NamedSemanticRegions<RuleTypes>
                .recordingRuleRegionUnder(RULE_BOUNDS)
                // Store the bounds of just the rule name (for goto declaration) under the key
                // RULE_NAMES in the resulting extractoin
                .recordingNamePositionUnder(RULE_NAMES)
                // Extracts the rule id from a token declaration
                .whereRuleIs(ANTLRv4Parser.TokenRuleDeclarationContext.class)
                .derivingNameWith(SemanticParserTest::deriveIdFromLexerRule)
                // Extracts the rule id from a parser rule declaration, using reflection instead of
                // a method, to show that works
                .whereRuleIs(ANTLRv4Parser.ParserRuleSpecContext.class)
                .derivingNameWith("parserRuleDeclaration().parserRuleIdentifier().PARSER_RULE_ID()", RuleTypes.PARSER)
                // Extracts the rule id from apublic  fragment declaration
                .whereRuleIs(ANTLRv4Parser.FragmentRuleDeclarationContext.class)
                .derivingNameWith(SemanticParserTest::deriveIdFromFragmentDeclaration)
                // Generate fake definitions for declared tokens so we don't flag them as errors
                .whereRuleIs(ANTLRv4Parser.TokenListContext.class)
                .derivingNameFromTerminalNodes(RuleTypes.LEXER, SemanticParserTest::deriveTokenIdFromTokenList)
                // Now collect usages of the rule ids we just collected.  This gets us both
                // the ability to query for a NamedReferenceSet<RuleTypes> which has the reference
                // and can resolve the reference, and a bidirectional graph built from arrays of BitSets
                // which can let us walk the closure of a rule in either direction
                .collectingReferencesUnder(REFS)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.TerminalContext.class)
                .derivingReferenceOffsetsFromTokenWith(SemanticParserTest::deriveReferencedNameFromTerminalContext)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.ParserRuleReferenceContext.class)
                .derivingReferenceOffsetsFromTokenWith(SemanticParserTest::deriveReferenceFromParserRuleReference)
                // Done specifying how to collect references
                .finishReferenceCollector()
                .finishNamedRegions()
                .extractNamedRegionsKeyedTo(RuleTypes.class)
                .recordingNamePositionUnder(NAMED_ALTERNATIVES)
                .whereRuleIs(ParserRuleLabeledAlternativeContext.class)
                //                .derivingNameWith(SemanticParserTest::extractAlternativeLabelInfo)
                .derivingNameWith("identifier().ID()", RuleTypes.ALTERNATIVE_LABEL)
                .finishNamedRegions()
                // Just collect the offsets of all BlockContext trees, in a nested data structure
                .extractingRegionsUnder(BLOCKS)
                .whenRuleType(BlockContext.class)
                .extractingBoundsFromRule()
                .finishRegionExtractor()
                // More complex nested region extraction for EBNF contexts, so we can progressively
                // darket the background color in the editor of nested repetitions
                .extractingRegionsUnder(EBNFS)
                .whenRuleType(EbnfContext.class)
                .extractingKeyAndBoundsWith(SemanticParserTest::extractEbnfPropertiesFromEbnfContext)
                .whenRuleType(ParserRuleElementContext.class)
                .extractingKeyAndBoundsWith(SemanticParserTest::extractEbnfRegionFromParserRuleElement)
                .whenRuleType(LexerRuleElementContext.class)
                .extractingKeyAndBoundsWith(SemanticParserTest::extractEbnfRegionFromLexerRuleElement)
                .finishRegionExtractor()
                .build();

        ANTLRv4Lexer lex = new ANTLRv4Lexer(nmg.stream());
        CommonTokenStream cts = new CommonTokenStream(lex, 0);
        ANTLRv4Parser p = new ANTLRv4Parser(cts);
        GrammarSource<String> nmgSource = GSAccessor.getDefault().newGrammarSource(nmg);
        Extraction extraction = ext.extract(p.grammarFile(), nmgSource, () -> {
            try {
                ANTLRv4Lexer lexer = new ANTLRv4Lexer(nmgSource.stream());
                return new BufferedTokenStream(lexer);
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        });
//        });

        System.out.println("GRAMMAR TYPE: " + extraction.encounters(GRAMMAR_TYPE));

        System.out.println("EXTRACTION: \n" + extraction);

        System.out.println("\n\n-------------------------- BLOCKS -------------------------------");
        SemanticRegions<Void> blocks = extraction.regions(BLOCKS);
        assertNotNull(blocks);
        String txt = nmg.text();
        for (SemanticRegion<Void> r : blocks) {
            String s = txt.substring(r.start(), r.end());
            System.out.println(r + ": '" + s + "'");
        }

        System.out.println("\n\n-------------------------- EBNFS -------------------------------");
        SemanticRegions<Set<EbnfProperty>> ebnfs = extraction.regions(EBNFS);
        assertNotNull(ebnfs);
        for (SemanticRegion<Set<EbnfProperty>> r : ebnfs) {
            String s = txt.substring(r.start(), r.end());
            System.out.println(r + ": '" + s + "'");
        }

        NamedSemanticRegions<RuleTypes> nameds = extraction.namedRegions(RULE_NAMES);

        System.out.println("\n\n-------------------------- NAMEDS -------------------------------");
        for (NamedSemanticRegion<RuleTypes> r : nameds) {
            System.out.println(r);
        }

//        System.out.println("NAMEDS: " + nameds);
        System.out.println("\n\n-------------------------- BOUNDS -------------------------------");
        NamedSemanticRegions<RuleTypes> bounds = extraction.namedRegions(RULE_BOUNDS);

        for (NamedSemanticRegion<RuleTypes> r : bounds) {
            System.out.println(r);
        }

        assertFalse(nameds.equals(bounds));

        NamedRegionReferenceSets<RuleTypes> refs = extraction.references(REFS);

        System.out.println("\n\n-------------------------- REFS -------------------------------");
        for (NamedRegionReferenceSet<RuleTypes> r : refs) {
            System.out.println("   ----------------- " + r.name() + " -----------------");
            for (NamedSemanticRegion<RuleTypes> i : r) {
                System.out.println("  " + i);
            }
        }

        StringGraph graph = extraction.referenceGraph(REFS);

        System.out.println("GRAPH: \n" + graph);

        BitSetHeteroObjectGraph<NamedSemanticRegion<RuleTypes>, NamedSemanticRegionReference<RuleTypes>, ?, NamedRegionReferenceSets<RuleTypes>> cr = bounds.crossReference(refs);
//        BitSetHeteroObjectGraph<SemanticRegion<Void>, NamedSemanticRegion<RuleTypes>, ?, NamedSemanticRegions<RuleTypes>> cr = blocks.crossReference(nameds);

        System.out.println("CROSS REF\n" + cr);

        BitSetHeteroObjectGraph<NamedSemanticRegion<RuleTypes>, SemanticRegion<Set<EbnfProperty>>, ?, SemanticRegions<Set<EbnfProperty>>> cr2 = bounds.crossReference(ebnfs);

        System.out.println("\n\n-------------------------- RULES CONTAINING EBNFS -------------------------------");
        System.out.println(cr2);

        NamedSemanticRegions<RuleTypes> alts = extraction.namedRegions(NAMED_ALTERNATIVES);
        System.out.println("\n\n-------------------------- NAMED ALTERNATIVES -------------------------------");
        for (NamedSemanticRegion<RuleTypes> r : alts) {
            System.out.println(r);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oout = new ObjectOutputStream(out);
            oout.writeObject(extraction);
        } finally {
            out.close();
        }

        byte[] b = out.toByteArray();
        System.out.println("EXTRACTED TO " + b.length + " BYTES\n");
        System.out.println(new String(b, UTF_8));
        ByteArrayInputStream in = new ByteArrayInputStream(b);
        ObjectInputStream oin = new ObjectInputStream(in);
        Extraction ex2 = (Extraction) oin.readObject();
        assertTrue(ex2.namedRegions(RULE_NAMES).equalTo(nameds));

        Iterable<NamedSemanticRegion<RuleTypes>> ite = nameds.combinedIterable(refs, true);
        System.out.println("\nREFS COMBINED WITH ALTS:");
        for (NamedSemanticRegion<RuleTypes> nse : ite) {
            System.out.println("  " + nse);
        }

        BitSetHeteroObjectGraph<NamedSemanticRegion<RuleTypes>, NamedSemanticRegion<RuleTypes>, ?, NamedSemanticRegions<RuleTypes>> cr3 = alts.crossReference(bounds);
        System.out.println("\n\n-------------------------- CROSS ALTS -------------------------------");
        System.out.println(cr3);
    }

    private static final String[] expectedNmmBlocks = new String[]{
        "(map (Comma map)*)",
        "(Comma map)",
        "(mapItem (Comma mapItem)*)",
        "(Comma mapItem)",
        "(True | False)"
    };

    @Test
    public void testSomeMethod() throws Throwable {
        Extraction ri = AntlrExtractor.getDefault().extract(GSAccessor.getDefault().newGrammarSource(rust));

        assertNotNull("Refs missing", ri.nameReferences(AntlrKeys.RULE_NAME_REFERENCES));

        int ix = 0;
        ix = 0;
        System.out.println("\n-------------------------- RULES --------------------------------");
        for (NamedSemanticRegion<org.nemesis.antlr.common.extractiontypes.RuleTypes> i : ri.namedRegions(AntlrKeys.RULE_BOUNDS)) {
            System.out.println(ix++ + ": " + i);
        }
        System.out.println("\n-------------------------- UNKNOWN REFS --------------------------------");
        ix = 0;
        for (SemanticRegion<UnknownNameReference<org.nemesis.antlr.common.extractiontypes.RuleTypes>> e : ri.unknowns(AntlrKeys.RULE_NAME_REFERENCES)) {
            System.out.println(e);
        }

        System.out.println("\n-------------------------- USAGES --------------------------------");
        System.out.println(ri.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES));

        ix = 0;
        System.out.println("\n-------------------------- LABELS --------------------------------");
        for (NamedSemanticRegion<org.nemesis.antlr.common.extractiontypes.RuleTypes> i : ri.namedRegions(AntlrKeys.NAMED_ALTERNATIVES)) {
            System.out.println(ix++ + ": " + i);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
            oout.writeObject(ri);
        }
        byte[] b = out.toByteArray();
        System.out.println("SERIALIZED TO " + b.length + " bytes");
        System.out.println("SER: '" + new String(b, UTF_8) + "'");
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(b));
//        RulesInfo deserialized = RulesInfo.load(in);
        Extraction deserialized = (Extraction) in.readObject();
        Iterator<NamedSemanticRegion<org.nemesis.antlr.common.extractiontypes.RuleTypes>> ai = allItems(ri).iterator();
        Iterator<NamedSemanticRegion<org.nemesis.antlr.common.extractiontypes.RuleTypes>> bi = allItems(deserialized).iterator();
        assert ai.hasNext() && bi.hasNext();
        while (ai.hasNext() && bi.hasNext()) {
            NamedSemanticRegion<org.nemesis.antlr.common.extractiontypes.RuleTypes> i1 = ai.next();
            NamedSemanticRegion<org.nemesis.antlr.common.extractiontypes.RuleTypes> i2 = bi.next();
            assertEquals(i1.name(), i2.name());
            assertEquals(i1.isReference(), i2.isReference());
            assertEquals(i1.index(), i2.index());
            assertEquals(i1.kind(), i2.kind());
            assertEquals(i1.ordering(), i2.ordering());
            assertTrue(ai.hasNext() == bi.hasNext());
        }
        assertEquals(ri.referenceGraph(REFS), deserialized.referenceGraph(REFS));

        assertTrue(ri.regions(EBNFS).equalTo(deserialized.regions(EBNFS)));
        assertTrue(ri.regions(BLOCKS).equalTo(deserialized.regions(BLOCKS)));
    }

    private List<NamedSemanticRegion<org.nemesis.antlr.common.extractiontypes.RuleTypes>> allItems(Extraction ext) {
        List<NamedSemanticRegion<org.nemesis.antlr.common.extractiontypes.RuleTypes>> result = new ArrayList<>();
        NamedSemanticRegions<org.nemesis.antlr.common.extractiontypes.RuleTypes> rn = ext.namedRegions(AntlrKeys.RULE_NAMES);
        assertNotNull("Names missing or not deserialized", rn);
        rn.collectItems(result);

        NamedSemanticRegions<org.nemesis.antlr.common.extractiontypes.RuleTypes> na = ext.namedRegions(AntlrKeys.NAMED_ALTERNATIVES);
        assertNotNull("Alternatives missing or not deserialized", na);
        na.collectItems(result);

        NamedRegionReferenceSets<org.nemesis.antlr.common.extractiontypes.RuleTypes> refs = ext.nameReferences(AntlrKeys.RULE_NAME_REFERENCES);
        assertNotNull("Refs missing or not deserialized", refs);
        refs.collectItems(result);
        Collections.sort(result);
        return result;
    }

    @Test
    public void testBstSerialization() throws Throwable {
        BitSet[] bsa = new BitSet[3];
        for (int i = 0; i < bsa.length; i++) {
            bsa[i] = new BitSet(3);
            switch (i) {
                case 0:
                    bsa[i].set(1);
                    bsa[i].set(2);
                    break;
                case 1:
                    bsa[i].set(0);
                    break;
                case 2:
                    bsa[i].set(2);
            }
        }
        IntGraph bst = IntGraph.create(bsa);
        StringGraph g = StringGraph.create(bst, new String[]{"A", "B", "C"});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
            bst.save(oout);
        }
        out.close();
        byte[] b = out.toByteArray();
        System.out.println("BST SERIALIZED TO " + b.length + " BYTES");
        IntGraph bst2 = IntGraph.load(new ObjectInputStream(new ByteArrayInputStream(b)));
        assertEquals(bst, bst2);

        out = new ByteArrayOutputStream();
        try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
            g.save(oout);
        }
        out.close();
        b = out.toByteArray();
        StringGraph g2 = StringGraph.load(new ObjectInputStream(new ByteArrayInputStream(b)));
        assertEquals(g, g2);
    }

    AntlrExtractorTest.GS2 rust;
    AntlrExtractorTest.GS antlr;
    AntlrExtractorTest.GS2 nmg;

    @Before
    public void setup() {
        antlr = new AntlrExtractorTest.GS("ANTLRv4", "grammar/grammar_syntax_checking/ANTLRv4.g4")
                .addChild("ANTLRv4Lexer", "grammar/grammar_syntax_checking/ANTLRv4Lexer.g4")
                .addChild("LexBasic", "grammar/imports/LexBasic.g4");

        rust = new AntlrExtractorTest.GS2("Rust", "Rust-Minimal._g4")
                .addChild("xidcontinue", "xidcontinue._g4")
                .addChild("xidstart", "xidstart._g4");

        nmg = new AntlrExtractorTest.GS2("NestedMapGrammar", "NestedMapGrammar.g4");
    }
}

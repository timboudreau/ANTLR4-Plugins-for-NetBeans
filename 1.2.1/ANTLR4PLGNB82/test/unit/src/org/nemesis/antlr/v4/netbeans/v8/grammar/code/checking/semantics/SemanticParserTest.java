package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.BlockContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.EbnfContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexerRuleElementContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleElementContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleLabeledAlternativeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TokenRuleDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.GenericExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.RegionsKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NamedRegionData;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NamedRegionKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.AntlrRuleKind;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.CharStreamSource;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.CharStreamSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.RulesInfo;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.MISSING_ID;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.MISSING_TOKEN_ID;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticParserTest {

    private final RustCSS rustLoader = new RustCSS();
    private final AntlrCSS antlrLoader = new AntlrCSS();
    private final NestedMapCSS nestedMapLoader = new NestedMapCSS();
//    SemanticParser sem = new SemanticParser("NestedMapGrammar", nestedMapLoader);
//    SemanticParser sem = new SemanticParser("Rust", rustLoader);
//    SemanticParser sem = new SemanticParser("ANTLRv4", antlrLoader);

    static NamedRegionData<RuleTypes> deriveIdFromLexerRule(TokenRuleDeclarationContext ctx) {
        TerminalNode tn = ctx.TOKEN_ID();
        if (tn != null) {
            org.antlr.v4.runtime.Token tok = tn.getSymbol();
            if (tok != null) {
                return new NamedRegionData(tok.getText(), RuleTypes.LEXER, tok.getStartIndex(), tok.getStopIndex() + 1);
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
                            return new NamedRegionData(ruleId, RuleTypes.PARSER, id.getStartIndex(), id.getStopIndex() + 1);
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
                return new NamedRegionData(idToken.getText(), RuleTypes.FRAGMENT, idToken.getStartIndex(), idToken.getStopIndex() + 1);
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
                    return new NamedRegionData<>(altnvLabel, RuleTypes.ALTERNATIVE_LABEL, labelToken.getStartIndex(), labelToken.getStopIndex() + 1);
                }
            }
        }
        return null;
    }

    enum RuleTypes {
        FRAGMENT,
        LEXER,
        PARSER,
        ALTERNATIVE_LABEL
    }
    static final NamedRegionKey<RuleTypes> NAMED_ALTERNATIVES = NamedRegionKey.create("namedAlternatives", RuleTypes.class);
    static final NamedRegionKey<RuleTypes> RULE_NAMES = NamedRegionKey.create("ruleNames", RuleTypes.class);
    static final NamedRegionKey<RuleTypes> RULE_BOUNDS = NamedRegionKey.create("ruleBounds", RuleTypes.class);
    static final NameReferenceSetKey<RuleTypes> REFS = NameReferenceSetKey.create("ruleRefs", RuleTypes.class);
    static final RegionsKey<Void> BLOCKS = RegionsKey.create(Void.class, "blocks");
    static final RegionsKey<Set<EbnfProperty>> EBNFS = RegionsKey.create(Set.class, "ebnfs");

    @Test
    public void testGenericExtractor() throws Throwable {
        GenericExtractor<GrammarFileContext> ext = new GenericExtractorBuilder<>(GrammarFileContext.class)
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
                .derivingNameWith(SemanticParserTest::deriveIdFromLexerRule)
                // Extracts the rule id from a parser rule declaration, using reflection instead of
                // a method, to show that works
                .whereRuleIs(ANTLRv4Parser.ParserRuleSpecContext.class)
                .derivingNameWith("parserRuleDeclaration().parserRuleIdentifier().PARSER_RULE_ID()", RuleTypes.PARSER)
                // Extracts the rule id from a fragment declaration
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
                .extractNamedRegions(RuleTypes.class)
                .recordingNamePositionUnder(NAMED_ALTERNATIVES)
                .whereRuleIs(ParserRuleLabeledAlternativeContext.class)
//                .derivingNameWith(SemanticParserTest::extractAlternativeLabelInfo)
                .derivingNameWith("identifier().ID()", RuleTypes.ALTERNATIVE_LABEL)
                .finishNamedRegions()
                // Just collect the offsets of all BlockContext trees, in a nested data structure
                .extractingRegionsTo(BLOCKS)
                .whenRuleType(BlockContext.class)
                .extractingBoundsFromRule()
                // More complex nested region extraction for EBNF contexts, so we can progressively
                // darket the background color in the editor of nested repetitions
                .extractingRegionsTo(EBNFS)
                .whenRuleType(EbnfContext.class)
                .extractingKeyAndBoundsFromWith(SemanticParserTest::extractEbnfPropertiesFromEbnfContext)
                .whenRuleType(ParserRuleElementContext.class)
                .extractingKeyAndBoundsFromWith(SemanticParserTest::extractEbnfRegionFromParserRuleElement)
                .whenRuleType(LexerRuleElementContext.class)
                .extractingKeyAndBoundsFromWith(SemanticParserTest::extractEbnfRegionFromLexerRuleElement)
                .finishRegionExtractor()
                .build();
        Extraction extraction = nestedMapLoader.charStream("NestedMapGrammar", (lm, s) -> {
            ANTLRv4Lexer lex = new ANTLRv4Lexer(s.get());
            CommonTokenStream cts = new CommonTokenStream(lex, 0);
            ANTLRv4Parser p = new ANTLRv4Parser(cts);
            return ext.extract(p.grammarFile());
        });

        System.out.println("\n\n-------------------------- BLOCKS -------------------------------");
        SemanticRegions<Void> blocks = extraction.regions(BLOCKS);
        assertNotNull(blocks);
        String txt = nmmText();
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

        NamedSemanticRegions.NamedRegionReferenceSets<RuleTypes> refs = extraction.references(REFS);

        System.out.println("\n\n-------------------------- REFS -------------------------------");
        for (NamedSemanticRegions.NamedRegionReferenceSets.NamedRegionReferenceSet<RuleTypes> r : refs) {
            System.out.println("   ----------------- " + r.name() + " -----------------");
            for (NamedSemanticRegion<RuleTypes> i : r) {
                System.out.println("  " + i);
            }
        }

        BitSetStringGraph graph = extraction.referenceGraph(REFS);

        System.out.println("GRAPH: \n" + graph);

        BitSetHeteroObjectGraph<NamedSemanticRegion<RuleTypes>, NamedSemanticRegions.NamedSemanticRegionReference<RuleTypes>, ?, NamedSemanticRegions.NamedRegionReferenceSets<RuleTypes>> cr = bounds.crossReference(refs);
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
    }

//    @Test
    public void testSimpleGrammar() throws Throwable {
        RulesInfo ri = new SemanticParser("NestedMapGrammar", nestedMapLoader).parse();

        String txt = nmmText();

        System.out.println("EBNFS: \n" + ri.ebnfs());

        Set<String> names = new HashSet<>(Arrays.asList(ri.ruleNames().nameArray()));
        assertEquals(new HashSet<>(Arrays.asList("items", "map", "mapItem", "value", "Comma",
                "booleanValue", "numberValue", "stringValue", "Number", "Digits", "String",
                "Whitespace", "OpenBrace", "CloseBrace", "Colon", "True", "False", "Identifier",
                "TRUE", "FALSE", "Minus", "ID", "STRING", "STRING2", "DIGIT", "WHITESPACE",
                "ESC", "ESC2")), names);

        Set<String> labels = new HashSet<>();
        for (NamedSemanticRegion<AntlrRuleKind> i : ri.labels()) {
            System.out.println("LABEL: " + i);
            labels.add(i.name());
            assertEquals(i.name(), txt.substring(i.start(), i.end()));
        }
        assertEquals(new HashSet<>(Arrays.asList("Bool", "Num", "Str")), labels);

        BitSetStringGraph g = ri.usageGraph();
        System.out.println("GRAPH:\n" + g);

        assertTrue(g.children("map").contains("mapItem"));
        assertTrue(g.children("map").contains("OpenBrace"));
        assertTrue(g.children("map").contains("CloseBrace"));
        assertTrue(g.children("items").contains("map"));
        assertTrue(g.children("Whitespace").contains("WHITESPACE"));

        assertTrue(g.closureOf("map").contains("String"));
        assertTrue(g.reverseClosureOf("String").contains("map"));

        for (NamedSemanticRegion<AntlrRuleKind> o : ri.ruleNames()) {
            NamedSemanticRegion<AntlrRuleKind> ruleBounds = ri.ruleBounds().regionFor(o.name());
            assertNotNull(ruleBounds);
            assertTrue(ruleBounds.containsPosition(o.start()));
            assertTrue(ruleBounds.containsPosition(o.end()));
        }

        System.out.println("BLOCKS: " + ri.blocks());
        List<String> subs = new ArrayList<>();
        for (SemanticRegions.SemanticRegion<Void> b : ri.blocks()) {
            String blk = txt.substring(b.start(), b.end());
            subs.add(blk);
            assertEquals(b, ri.blocks().at(b.start()));
            System.out.println("\"" + blk + "\",");
        }
        assertEquals(Arrays.asList(expectedNmmBlocks), subs);

        List<NamedSemanticRegion<AntlrRuleKind>> bvs = ri.referencesTo("booleanValue");
        assertEquals(1, bvs.size());
        NamedSemanticRegion<AntlrRuleKind> bv = bvs.iterator().next();
        assertEquals("booleanValue", txt.substring(bv.start(), bv.end()));
        NamedSemanticRegion<AntlrRuleKind> valueRule = ri.ruleBounds().regionFor("value");
        assertNotNull(valueRule);
        assertTrue(valueRule.containsPosition(bv.start()));
        assertTrue(valueRule.containsPosition(bv.end()));

        for (NamedSemanticRegion<AntlrRuleKind> i : ri.allItems()) {
            System.out.println(i);
        }
    }

    private static final String[] expectedNmmBlocks = new String[]{
        "(map (Comma map)*)",
        "(Comma map)",
        "(mapItem (Comma mapItem)*)",
        "(Comma mapItem)",
        "(True | False)"
    };

//    @Test
    public void testSomeMethod() throws Throwable {
        SemanticParser sem = new SemanticParser("Rust", rustLoader);
        RulesInfo ri = sem.parse();
        int ix = 0;
        System.out.println("\n-------------------------- ITEMS --------------------------------");
        for (NamedSemanticRegion<AntlrRuleKind> i : ri.allItems()) {
            System.out.println(ix++ + ": " + i);
        }
        ix = 0;
        System.out.println("\n-------------------------- RULES --------------------------------");
        for (NamedSemanticRegion<AntlrRuleKind> i : ri.allRules()) {
            System.out.println(ix++ + ": " + i);
        }
        System.out.println("\n-------------------------- UNKNOWN REFS --------------------------------");
        ix = 0;
        for (Map.Entry<String, List<int[]>> e : ri.unknownReferences().entrySet()) {
            StringBuilder sb = new StringBuilder(e.getKey());
            for (Iterator<int[]> i = e.getValue().iterator(); i.hasNext();) {
                int[] curr = i.next();
                sb.append(' ').append(curr[0]).append(':').append(curr[1]);
            }
            System.out.println(sb);
        }

        ix = 0;
        System.out.println("\n-------------------------- FOREIGN REFS --------------------------------");
        for (NamedSemanticRegions.ForeignNamedSemanticRegion<AntlrRuleKind, String> i : ri.foreignItems()) {
            System.out.println(ix++ + ": " + i);
        }

        System.out.println("\n-------------------------- USAGES --------------------------------");
        System.out.println(ri.usageGraph());

        ix = 0;
        System.out.println("\n-------------------------- LABELS --------------------------------");
        for (NamedSemanticRegion<AntlrRuleKind> i : ri.labels()) {
            System.out.println(ix++ + ": " + i);
        }

        ix = 0;
        System.out.println("\n-------------------------- LABEL ATOMS --------------------------------");
        for (NamedSemanticRegion<AntlrRuleKind> i : ri.labelClauses()) {
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
        RulesInfo deserialized = (RulesInfo) in.readObject();
        Iterator<NamedSemanticRegion<AntlrRuleKind>> ai = ri.allItems().iterator();
        Iterator<NamedSemanticRegion<AntlrRuleKind>> bi = deserialized.allItems().iterator();
        assert ai.hasNext() && bi.hasNext();
        while (ai.hasNext() && bi.hasNext()) {
            NamedSemanticRegion<AntlrRuleKind> i1 = ai.next();
            NamedSemanticRegion<AntlrRuleKind> i2 = bi.next();
            assertEquals(i1.name(), i2.name());
            assertEquals(i1.isReference(), i2.isReference());
            assertEquals(i1.index(), i2.index());
            assertEquals(i1.kind(), i2.kind());
            assertEquals(i1.ordering(), i2.ordering());
            assertTrue(ai.hasNext() == bi.hasNext());
        }
        assertEquals(ri.usageGraph(), deserialized.usageGraph());

        assertTrue(ri.ebnfs().equalTo(deserialized.ebnfs()));
        assertTrue(ri.blocks().equalTo(deserialized.blocks()));
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
        BitSetTree bst = new BitSetTree(bsa);
        BitSetStringGraph g = new BitSetStringGraph(bst, new String[]{"A", "B", "C"});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
            bst.save(oout);
        }
        out.close();
        byte[] b = out.toByteArray();
        System.out.println("BST SERIALIZED TO " + b.length + " BYTES");
        BitSetTree bst2 = BitSetTree.load(new ObjectInputStream(new ByteArrayInputStream(b)));
        assertEquals(bst, bst2);

        out = new ByteArrayOutputStream();
        try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
            g.save(oout);
        }
        out.close();
        b = out.toByteArray();
        BitSetStringGraph g2 = BitSetStringGraph.load(new ObjectInputStream(new ByteArrayInputStream(b)));
        assertEquals(g, g2);
    }

    static final class AntlrCSS implements CharStreamSource {

        @Override
        public <T> T charStream(String name, SemanticParser.CharStreamConsumer<T> cons) throws IOException {
            try {
                Path path;
                switch (name) {
                    case "ANTLRv4":
                        path = TestDir.projectBaseDir().resolve("grammar/grammar_syntax_checking/ANTLRv4.g4");
                        break;
                    case "ANTLRv4Lexer":
                        path = TestDir.projectBaseDir().resolve("grammar/grammar_syntax_checking/ANTLRv4Lexer.g4");
                        break;
                    case "LexBasic":
                        path = TestDir.projectBaseDir().resolve("grammar/imports/LexBasic.g4");
                        break;
                    default:
                        throw new IOException(name);
                }
                long lm = Files.getLastModifiedTime(path).toMillis();
                return cons.consume(lm, () -> CharStreams.fromPath(path));
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
        }
    }

    static final class RustCSS implements CharStreamSource {

        @Override
        public <T> T charStream(String name, SemanticParser.CharStreamConsumer<T> cons) throws IOException {
            String streamName;
            switch (name) {
                case "Rust":
                    streamName = "Rust-Minimal._g4";
                    break;
                case "xidcontinue":
                    streamName = "xidcontinue._g4";
                    break;
                case "xidstart":
                    streamName = "xidstart._g4";
                    break;
                default:
                    throw new IOException("Not a known grammar:" + name);
            }
            long lm = System.currentTimeMillis();
            try {
                Path path = TestDir.testResourcePath(TestDir.class, streamName);
                CharStreamSupplier supp = () -> {
                    return CharStreams.fromPath(path);
                };
                return cons.consume(lm, supp);
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
        }
    }

    static final class NestedMapCSS implements CharStreamSource {

        @Override
        public <T> T charStream(String name, SemanticParser.CharStreamConsumer<T> cons) throws IOException {
            switch (name) {
                case "NestedMapGrammar": {
                    try {
                        Path path = TestDir.testResourcePath(TestDir.class, "NestedMapGrammar.g4");
                        long lm = Files.getLastModifiedTime(path).toMillis();
                        return cons.consume(lm, () -> {
                            return CharStreams.fromPath(path);
                        });
                    } catch (URISyntaxException ex) {
                        throw new IOException(ex);
                    }
                }
            }
            throw new IOException("Unknown name " + name);
        }
    }

    static String nmmText() throws URISyntaxException, IOException {
        Path path = TestDir.testResourcePath(TestDir.class, "NestedMapGrammar.g4");
        return new String(Files.readAllBytes(path), UTF_8);
    }
}

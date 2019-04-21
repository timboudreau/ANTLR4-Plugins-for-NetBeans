package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import org.nemesis.antlr.common.extractiontypes.EbnfProperty;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.data.named.NamedSemanticRegions;
import java.io.IOException;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.nemesis.source.impl.GSAccessor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarType;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.graph.StringGraph;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.extraction.SingletonEncounters;

public class AntlrExtractorTest {

    private static Set<String> setOf(String... all) {
        return new HashSet<>(Arrays.asList(all));
    }

    @Test
    public void testForeignReferences() throws Throwable {
        Extraction ext = AntlrExtractor.getDefault().extract(GSAccessor.getDefault().newGrammarSource(rust));

        assertNotNull("Grammar type not encountered", ext.encounters(AntlrKeys.GRAMMAR_TYPE));
        assertFalse("Multiple grammar statements claimed to be present", ext.encounters(AntlrKeys.GRAMMAR_TYPE).hasMultiple());
        assertTrue("Wrong grammar type found", ext.encounters(AntlrKeys.GRAMMAR_TYPE).is(GrammarType.COMBINED));

        SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = ext.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
        System.out.println("---------------------- UNKNOWNS -----------------------------");
        Set<String> unknownNames = new HashSet<>();
        Set<String> expectedUnknownNames = setOf("XID_Start", "XID_Continue", "EOF");

        for (SemanticRegion<UnknownNameReference<RuleTypes>> u : unknowns) {
            unknownNames.add(u.key().name());
        }
        assertEquals(expectedUnknownNames, unknownNames);
        Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved = ext.resolveUnknowns(AntlrKeys.RULE_NAME_REFERENCES, AntlrExtractor.resolver());
        SemanticRegions<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes>> resolvedItems = resolved.attributed();
        System.out.println("---------------------- RESOLVED TO -----------------------------");
        for (SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes>> item : resolvedItems) {
            unknownNames.remove(item.key().name());
            GrammarSource<?> src = item.key().source();
            assertTrue(src + "", src.lookup(GS.class).isPresent());
            assertTrue(src + "", src.lookup(GS.class).get() instanceof GS);
            GS gs = src.lookup(GS.class).get();
            switch (item.key().name()) {
                case "XID_Continue":
                    assertEquals("xidcontinue", item.key().source().name());
                    assertEquals(0, item.key().element().index());
                    assertEquals(RuleTypes.FRAGMENT, item.key().element().kind());
                    String txt = gs.textFor(item.key().element());
                    System.out.println("TXT FOR XID_CONTINUE: " + txt);
                    assertEquals(item.key().name(), gs.textFor(item.key().element()));
                    break;
                case "XID_Start":
                    assertEquals("xidstart", item.key().source().name());
                    assertEquals(0, item.key().element().index());
                    assertEquals(RuleTypes.FRAGMENT, item.key().element().kind());
                    assertEquals(item.key().name(), gs.textFor(item.key().element()));
                    break;
                default:
                    fail("Surprise resolved element " + item);
            }
        }
        assertEquals(setOf("EOF"), unknownNames);
        System.out.println("FOLDABLES: ");
        for (SemanticRegion<FoldableRegion> region : ext.regions(AntlrKeys.FOLDABLES)) {
            System.out.println("FOLD: " + region);
        }

        SingletonEncounters<GrammarType> grammarType = ext.encounters(AntlrKeys.GRAMMAR_TYPE);
        assertNotNull(grammarType);
        System.out.println("GRAMMAR TYPE " + grammarType);
        System.out.println("VALUE " + grammarType.first());
        assertTrue(grammarType.is(GrammarType.COMBINED));

        System.out.println("EXTRACTION:\n" + ext);
    }

    @Test
    public void testSimpleGrammar() throws Throwable {
        Extraction ri = AntlrExtractor.getDefault().extract(GSAccessor.getDefault().newGrammarSource(nmg));

        String txt = nmg.text();

        System.out.println("EBNFS: \n" + ri.regions(AntlrKeys.EBNFS));

        int ec = 0;
        for (SemanticRegion<Set<EbnfProperty>> ebnf : ri.regions(AntlrKeys.EBNFS)) {
            assertEquals(ec, ebnf.index());
            System.out.println("EBNF: " + ebnf);
            switch (ec++) {
                case 0 :
                    assertEquals(40, ebnf.start());
                    assertEquals(52, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.STAR), ebnf.key());
                    assertEquals(0, ebnf.nestingDepth());
                    break;
                case 1 :
                    assertEquals(76, ebnf.start());
                    assertEquals(103, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.QUESTION), ebnf.key());
                    assertEquals(0, ebnf.nestingDepth());
                    break;
                case 2 :
                    assertEquals(85, ebnf.start());
                    assertEquals(101, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.STAR), ebnf.key());
                    assertEquals(1, ebnf.nestingDepth());
                    break;
                case 3:
                    assertEquals(328, ebnf.start());
                    assertEquals(334, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.QUESTION), ebnf.key());
                    assertEquals(0, ebnf.nestingDepth());
                    break;
                case 4:
                    assertEquals(353, ebnf.start());
                    assertEquals(359, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.PLUS), ebnf.key());
                    assertEquals(0, ebnf.nestingDepth());
                    break;
                case 5:
                    assertEquals(626, ebnf.start());
                    assertEquals(635, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.STAR, EbnfProperty.QUESTION), ebnf.key());
                    assertEquals(0, ebnf.nestingDepth());
                    break;
                case 6:
                    assertEquals(663, ebnf.start());
                    assertEquals(673, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.STAR, EbnfProperty.QUESTION), ebnf.key());
                    assertEquals(0, ebnf.nestingDepth());
                    break;
                case 7:
                    assertEquals(726, ebnf.start());
                    assertEquals(736, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.PLUS), ebnf.key());
                    assertEquals(0, ebnf.nestingDepth());
                    break;
                case 8:
                    assertEquals(776, ebnf.start());
                    assertEquals(811, ebnf.end());
                    assertEquals(EnumSet.of(EbnfProperty.PLUS), ebnf.key());
                    assertEquals(0, ebnf.nestingDepth());
                    break;
                default:
                    fail("Unexpected extra ebnf: " + ebnf);
            }
        }

        Set<String> names = new HashSet<>(Arrays.asList(ri.namedRegions(AntlrKeys.RULE_NAMES).nameArray()));
        assertEquals(new HashSet<>(Arrays.asList("items", "map", "mapItem", "value", "Comma",
                "booleanValue", "numberValue", "stringValue", "Number", "Digits", "String",
                "Whitespace", "OpenBrace", "CloseBrace", "Colon", "True", "False", "Identifier",
                "TRUE", "FALSE", "Minus", "ID", "STRING", "STRING2", "DIGIT", "WHITESPACE",
                "ESC", "ESC2")), names);

        Set<String> labels = new HashSet<>();
        int lc = 0;
        for (NamedSemanticRegion<RuleTypes> i : ri.namedRegions(AntlrKeys.NAMED_ALTERNATIVES)) {
            labels.add(i.name());
            assertEquals("#" + i.name(), txt.substring(i.start(), i.end()));
            switch (lc) {
                case 0:
                    assertEquals("Bool", i.name());
                    break;
                case 1:
                    assertEquals("Num", i.name());
                    break;
                case 2:
                    assertEquals("Str", i.name());
                    break;
                default:
                    fail("Unexpected additional label " + i);
            }
            lc++;
        }
        assertEquals(new HashSet<>(Arrays.asList("Bool", "Num", "Str")), labels);

        StringGraph g = ri.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES);
        System.out.println("GRAPH:\n" + g);

        assertTrue(g.children("map").contains("mapItem"));
        assertTrue(g.children("map").contains("OpenBrace"));
        assertTrue(g.children("map").contains("CloseBrace"));
        assertTrue(g.children("items").contains("map"));
        assertTrue(g.children("Whitespace").contains("WHITESPACE"));

        assertTrue(g.closureOf("map").contains("String"));
        assertTrue(g.reverseClosureOf("String").contains("map"));

        assertEquals(setOf("Whitespace", "items"), g.topLevelOrOrphanNodes());

        for (NamedSemanticRegion<RuleTypes> o : ri.namedRegions(AntlrKeys.RULE_NAMES)) {
            IndexAddressable.IndexAddressableItem ruleBounds = ri.namedRegions(AntlrKeys.RULE_BOUNDS).regionFor(o.name());
            assertNotNull(ruleBounds);
            assertTrue(ruleBounds.containsPosition(o.start()));
            assertTrue(ruleBounds.containsPosition(o.end()));
        }
        SemanticRegions<Void> blocks = ri.regions(AntlrKeys.BLOCKS);
        System.out.println("BLOCKS: " + blocks);
        List<String> subs = new ArrayList<>();
        for (SemanticRegion<Void> b : blocks) {
            String blk = txt.substring(b.start(), b.end());
            subs.add(blk);
            assertEquals(b, blocks.at(b.start()));
            System.out.println("\"" + blk + "\",");
        }
        assertEquals(Arrays.asList(expectedNmmBlocks), subs);

        NamedRegionReferenceSet<RuleTypes> bvs = ri.nameReferences(AntlrKeys.RULE_NAME_REFERENCES).references("booleanValue");
        assertEquals(1, bvs.size());
        NamedSemanticRegionReference<RuleTypes> bv = bvs.iterator().next();
        assertEquals("booleanValue", txt.substring(bv.start(), bv.end()));
        NamedSemanticRegion<RuleTypes> valueRule = ri.namedRegions(AntlrKeys.RULE_BOUNDS).regionFor("value");
        assertNotNull(valueRule);
        assertTrue(valueRule.containsPosition(bv.start()));
        assertTrue(valueRule.containsPosition(bv.end()));
    }

    private static final String[] expectedNmmBlocks = new String[]{
        "(map (Comma map)*)",
        "(Comma map)",
        "(mapItem (Comma mapItem)*)",
        "(Comma mapItem)",
        "(True | False)"
    };

    GS2 rust;
    GS antlr;
    GS2 nmg;

    @Before
    public void setup() {
        antlr = new GS("ANTLRv4", "src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4.g4")
                .addChild("ANTLRv4Lexer", "src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.g4")
                .addChild("LexBasic", "src/main/antlr4/imports/LexBasic.g4");

        rust = new GS2("Rust", "Rust-Minimal._g4")
                .addChild("xidcontinue", "xidcontinue._g4")
                .addChild("xidstart", "xidstart._g4");

        nmg = new GS2("NestedMapGrammar", "NestedMapGrammar.g4");
    }

    public static class GS extends GrammarSourceImplementation<String> {

        private final String name;
        final String path;
        List<GrammarSourceImplementation<String>> kids;
        private String text;

        public GS(String name, String path) {
            this(name, path, null);
        }

        public GS(String name, String path, List<GrammarSourceImplementation<String>> kids) {
            super(String.class);
            this.name = name;
            this.path = path;
            this.kids = kids;
        }

        public String textFor(IndexAddressable.IndexAddressableItem item) throws URISyntaxException, IOException {
            String txt = text();
            if (item.end() > txt.length() || item.start() > txt.length() || item.start() < 0 || item.end() < 0) {
                throw new IllegalArgumentException("Item bounds out of range: " + item + " for text length " + txt.length());
            }
            return txt.substring(item.start(), item.end());
        }

        public GS child(String name) {
            if (kids == null) {
                return null;
            }
            for (GrammarSourceImplementation<String> k : kids) {
                if (name.equals(k.name())) {
                    return k.lookup(GS.class);
                }
            }
            return null;
        }

        public String text() throws URISyntaxException, IOException {
            if (text != null) {
                return text;
            }
            Path path = path();
            byte[] b = Files.readAllBytes(path);
            return text = new String(b, UTF_8);
        }

        public GS addChild(String name, String path) {
            if (kids == null) {
                kids = new ArrayList<>(3);
            }

            kids.add(new GS(name, path, null));
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        protected Path path() throws URISyntaxException {
            return TestDir.projectBaseDir().resolve(this.path);
        }

        @Override
        public CharStream stream() throws IOException {
            try {
                return CharStreams.fromPath(path());
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
        }

        @Override
        public GrammarSourceImplementation<?> resolveImport(String name) {
            if (kids == null) {
                return null;
            }
            for (GrammarSourceImplementation<String> kid : kids) {
                if (name.equals(kid.name())) {
                    System.out.println("RESOLVING CHILD " + name + " with " + kid);
                    return kid;
                }
            }
            return null;
        }

        @Override
        public String source() {
            return path;
        }

        @Override
        public String toString() {
            return name + ":" + path;
        }
    }

    public static class GS2 extends GS {

        public GS2(String name, String path) {
            super(name, path);
        }

        @Override
        public GS2 addChild(String name, String path) {
            if (kids == null) {
                kids = new ArrayList<>(3);
            }
            kids.add(new GS2(name, path));
            return this;
        }

        @Override
        protected Path path() throws URISyntaxException {
            return TestDir.testResourcePath(TestDir.class, this.path);
        }
    }
}

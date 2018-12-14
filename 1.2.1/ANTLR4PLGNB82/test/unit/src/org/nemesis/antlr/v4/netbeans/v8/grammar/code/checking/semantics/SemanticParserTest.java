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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.BlockContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.GenericExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.RegionsKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.AntlrRuleKind;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.CharStreamSource;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.CharStreamSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.RulesInfo;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;

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

    @Test
    public void testGenericExtractor() throws Throwable {
        AtomicReference<RegionsKey<Void>> key = new AtomicReference<>();
        GenericExtractor<GrammarFileContext> ext = new GenericExtractorBuilder<>(GrammarFileContext.class)
                .extractRegionsFor(BlockContext.class)
                .named("blocks")
                .build(key::set).build();
        RegionsKey<Void> k = key.get();
        assertNotNull(k);
        Extraction extraction = nestedMapLoader.charStream("NestedMapGrammar", (lm, s) -> {
            ANTLRv4Lexer lex = new ANTLRv4Lexer(s.get());
            CommonTokenStream cts = new CommonTokenStream(lex, 0);
            ANTLRv4Parser p = new ANTLRv4Parser(cts);
            return ext.extract(p.grammarFile());
        });

        SemanticRegions<Void> blocks = extraction.regions(k);
        System.out.println("GOT BACK BLOCKS: \n" + blocks);
        assertNotNull(blocks);
        String txt = nmmText();
        for (SemanticRegion<Void> r : blocks) {
            String s = txt.substring(r.start(), r.end());
            System.out.println(r + ": '" + s + "'");
        }
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
            assertEquals(b, ri.blocks().regionAt(b.start()));
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

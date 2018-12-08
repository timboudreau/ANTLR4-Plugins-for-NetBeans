package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Iterator;
import org.antlr.v4.runtime.CharStreams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.Offsets.Item;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.AntlrRuleKind;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.CharStreamSource;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.CharStreamSupplier;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticParser.RulesInfo;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticParserTest {

    private final RustCSS rustLoader = new RustCSS();
    SemanticParser sem = new SemanticParser("Rust", rustLoader);

    @Test
    public void testSomeMethod() throws Throwable {
        RulesInfo ri = sem.parse();
        int ix = 0;
        System.out.println("\n-------------------------- ITEMS --------------------------------");
        for (Item<AntlrRuleKind> i : ri.allItems()) {
            System.out.println(ix++ + ": " + i);
        }
        System.out.println("\n-------------------------- RULES --------------------------------");
        for (Item<AntlrRuleKind> i : ri.allRules()) {
            System.out.println(ix++ + ": " + i);
        }
        System.out.println("\n-------------------------- UNKNOWN REFS --------------------------------");
        System.out.println(ri.unknownReferenceNames());

        System.out.println("\n-------------------------- USAGES --------------------------------");

        System.out.println(ri.usageGraph());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
            oout.writeObject(ri);
        }
        byte[] b = out.toByteArray();
        System.out.println("SERIALIZED TO " + b.length + " bytes");
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(b));
//        RulesInfo deserialized = RulesInfo.load(in);
        RulesInfo deserialized = (RulesInfo) in.readObject();
        Iterator<Item<AntlrRuleKind>> ai = ri.allItems().iterator();
        Iterator<Item<AntlrRuleKind>> bi = deserialized.allItems().iterator();
        assert ai.hasNext() && bi.hasNext();
        while (ai.hasNext() && bi.hasNext()) {
            Item<AntlrRuleKind> i1 = ai.next();
            Item<AntlrRuleKind> i2 = bi.next();
            assertEquals(i1.name(), i2.name());
            assertEquals(i1.isReference(), i2.isReference());
            assertEquals(i1.index(), i2.index());
            assertEquals(i1.kind(), i2.kind());
            assertEquals(i1.ordering(), i2.ordering());
            assertTrue(ai.hasNext() == bi.hasNext());
        }
        assertEquals(ri.usageGraph(), deserialized.usageGraph());
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
        System.out.println("BST SAVED AS " + b.length);
        System.out.println("==" + new String(b));
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
}

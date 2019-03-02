package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.ByteArrayOutputStream;
import org.nemesis.jfs.javac.CompileJavaSources;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.ANTLRv4GrammarChecker;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.CompiledParserRunner;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ExtractionCodeGenerator;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParserRunResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParserRunnerBuilder;
import org.nemesis.source.api.GrammarSource;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class ErrorHandlingTest {

    private static final String TEST_TEXT = "grammar foo;\n"
            + "thing : Digit (Underscore | Digit)*;\n"
            + "Digit : [0-9];\n"
            + "Underscore: '_';\n";
    private static Path dir;
    private static Path errorGrammarDir;
    private static Path goodGrammarDir;
    private static Path errorFile;
    private static Path goodFile;
    private static AntlrLibrary lib;
    private static Path errorOutputDir1;
    private static Path goodOutputDir1;
    private static Path errorOutputDir2;
    private static Path goodClasspathRoot;
    private static Path goodOutputDir2;
    private static Path goodClasspathRoot2;
    private static Path goodClasspathRoot3;
    private static Path goodOutputDir3;

    @Test
    public void testErrorGrammar() throws Throwable {
        GrammarJavaSourceGenerator runner = new GrammarJavaSourceGenerator(errorFile, "com.foo", errorOutputDir1, null, false, AntlrRunOption.GENERATE_LEXER);

        System.out.println("CMDLINE: " + Arrays.toString(runner.antlrArguments()));

        ForeignInvocationResult<AntlrInvocationResult> res1 = runner.run(lib);

        System.out.println("RES1 " + res1.invocationResult());

        res1.rethrow();

        System.out.println("\n-------------------------------------\nRUN 2\n");

        runner = new GrammarJavaSourceGenerator(errorFile, "com.foo", errorOutputDir2, null, true, AntlrRunOption.GENERATE_LEXER);

        ForeignInvocationResult<AntlrInvocationResult> res2 = runner.run(lib);

        System.out.println("RES2 " + res2.invocationResult());

        res2.rethrow();

        assertEquals("Wrong number of errors in " + res1.invocationResult(), 5, res1.invocationResult().errors().size());
        assertEquals("Wrong number of errors in " + res2.invocationResult(), 5, res2.invocationResult().errors().size());

        assertEquals(126, res1.invocationResult().errors().get(0).code());
        assertEquals(125, res1.invocationResult().errors().get(1).code());
        assertEquals(126, res1.invocationResult().errors().get(2).code());
        assertEquals(125, res1.invocationResult().errors().get(3).code());
        assertEquals(51, res1.invocationResult().errors().get(4).code());
        assertEquals(res1.invocationResult().errors(), res2.invocationResult().errors());
    }

    @Test
    public void testBuildAndParseGrammar() throws Throwable {
        System.out.println("\n************* BUILDER *********************************");
        GenerateBuildAndRunGrammarResult result = GrammarJavaSourceGeneratorBuilder
                .forAntlrSource(goodFile)
                .withAntlrLibrary(lib)
                .withOutputRoot(goodClasspathRoot2)
                .withRunOptions(AntlrRunOption.GENERATE_LEXER)
                .withRunOptions(AntlrRunOption.GENERATE_VISITOR)
                .toParseAndRunBuilder()
                .parse("2323 32_52 -32 210_7");

        result.rethrow();
        assertTrue("Compile did not complete", result.compileResult().isPresent());
        assertTrue("Parse did not complete", result.parseResult().isPresent());

        System.out.println("SOURCES IN " + result.compileResult().get().sourceRoot());
        System.out.println("SOURCES: " + result.compileResult().get().sources());

        System.out.println(result.parseResult().get());

        System.out.println("\n************* END BUILDER *********************************");
    }

    @Test
    public void testGoodGrammar() throws Throwable {
        System.out.println("\n\n************* GOOD GRAMMAR ***********************");
        GrammarJavaSourceGenerator runner = new GrammarJavaSourceGenerator(goodFile, "com.foo", goodOutputDir1, null, false, AntlrRunOption.GENERATE_LEXER, AntlrRunOption.GENERATE_VISITOR);

        System.out.println("GOOD CMDLINE: " + Arrays.toString(runner.antlrArguments()));

        ForeignInvocationResult<AntlrInvocationResult> res = runner.run(lib);

        System.out.println("GOOD RES " + res.invocationResult());

        res.rethrow();
        assertTrue(res.isSuccess());

        String[] names = new String[]{"NoErrorsBaseListener.java",
            "NoErrorsBaseVisitor.java",
            "NoErrors.interp",
            "NoErrorsLexer.interp",
            "NoErrorsLexer.java",
            "NoErrorsLexer.tokens",
            "NoErrorsListener.java",
            "NoErrorsParser.java",
            "NoErrors.tokens",
            "NoErrorsVisitor.java"};
        for (String fn : names) {
            Path path = goodOutputDir1.resolve(fn);
            assertTrue(path + "", Files.exists(path));
        }

        Path lexerExtractor = ExtractionCodeGenerator.saveExtractorSourceTo(
                goodFile, "com.foo", "NoErrors", goodOutputDir1);

        CompileJavaSources comp = new CompileJavaSources(ParserRunnerBuilder.class);
        CompileResult compRes = comp.compile(goodClasspathRoot, goodClasspathRoot, lib.paths());

        System.out.println("COMP RESULT IN " + goodClasspathRoot + ": " + compRes.ok());
        for (String fn : names) {
            if (fn.endsWith(".java")) {
                fn = fn.substring(0, fn.length() - 5) + ".class";
                assertTrue(fn, Files.exists(goodOutputDir1.resolve(fn)));
            }
        }

        CompiledParserRunner parse = new CompiledParserRunner(goodClasspathRoot, "com.foo", lib.with(goodClasspathRoot.toUri().toURL()));
        ParserRunResult parseResult = parse.parseAndExtract("2323 32_52 -32 210_7");
        assertNotNull(parseResult);
        parseResult.rethrow();
        assertTrue(parseResult.parseTree().isPresent());
        ParseTreeProxy extraction = parseResult.parseTree().get();
        System.out.println("GOT BACK " + extraction);

        for (int i = 0; i < extraction.tokens().size(); i++) {
            ProxyToken tt = extraction.tokens().get(i);
            ProxyToken st1 = extraction.tokenAtPosition(tt.getStartIndex());
            ProxyToken st2 = extraction.tokenAtPosition(tt.getStopIndex());
            assertSame("\nsearch for token " + i + " - " + tt + " failed for " + tt.getStartIndex() + "\n", tt, st1);
            assertSame("\nsearch for token " + i + " - " + tt + " failed for " + tt.getStopIndex() + "\n", tt, st2);
        }
        ProxyToken last = extraction.tokens().get(extraction.tokens().size() - 1);
        ProxyToken st3 = extraction.tokenAtPosition(last.getStopIndex() + 2);
        assertNull(st3);

        System.out.println("\n************************************\n\n");
    }

    @Test
    public void testExtraction() throws Throwable {
        String text;
        try (InputStream in = Files.newInputStream(goodFile, StandardOpenOption.READ)) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                FileUtil.copy(in, out);
                text = new String(out.toByteArray(), UTF_8);
            }
        }

        ANTLRv4GrammarChecker gc = NBANTLRv4Parser.parse(GrammarSource.find(goodFile, ANTLR_MIME_TYPE));
        ANTLRv4SemanticParser sem = gc.getSemanticParser();
        assertNotNull(sem);

        List<Item> all = Arrays.asList(new Item[]{
            new Item(19, 24, 38, "thing"),
            new Item(40, 44, 64, "item"),
            new Item(66, 72, 180, "number"),
            new Item(182, 187, 200, "words"),
            new Item(202, 208, 218, "Digits"),
            new Item(220, 224, 232, "Word"),
            new Item(234, 244, 275, "Whitespace"),
            new Item(277, 283, 290, "Hyphen"),
            new Item(291, 301, 308, "Underscore"),
            new Item(319, 324, 310, 333, "DIGIT"),
            new Item(343, 353, 334, 367, "WHITESPACE"),
            new Item(377, 381, 368, 394, "WORD")
        });
        assertEquals(all.size(), sem.extraction().namedRegions(AntlrKeys.RULE_NAMES).size());
        Iterable<NamedSemanticRegion<RuleTypes>> decls = sem.extraction().namedRegions(AntlrKeys.RULE_BOUNDS).index();
        Iterable<NamedSemanticRegion<RuleTypes>> names = sem.extraction().namedRegions(AntlrKeys.RULE_NAMES).index();
        Iterator<NamedSemanticRegion<RuleTypes>> declarationsIterator = decls.iterator();
        Iterator<Item> itemsIterator = all.iterator();
        for (NamedSemanticRegion<RuleTypes> name : names) {
            NamedSemanticRegion<RuleTypes> ruleBounds = declarationsIterator.next();
            Item item = itemsIterator.next();
            assertTrue(name.start() < name.end());
            assertTrue(name.start() < name.end());
            assertTrue(name.end() < ruleBounds.end());
            assertEquals(name.name(), ruleBounds.name());
            String nm = text.substring(name.start(), name.end());
            assertEquals(nm, name.name());
            String body = text.substring(ruleBounds.start(), ruleBounds.end());
            assertTrue(body, body.startsWith("fragment " + nm) || body.startsWith(name.name()));
            assertTrue(body, body.endsWith(";"));
            item.assertMatches(name, ruleBounds);
        }
    }

    private static final class Item {

        private final int nameStart;
        private final int nameEnd;
        private final int ruleStart;
        private final int ruleEnd;
        private final String name;

        public Item(int nameStart, int nameEnd, int ruleEnd, String name) {
            this.nameStart = this.ruleStart = nameStart;
            this.nameEnd = nameEnd;
            this.ruleEnd = ruleEnd;
            this.name = name;
        }

        public Item(int nameStart, int nameEnd, int ruleStart, int ruleEnd, String name) {
            this.nameStart = nameStart;
            this.ruleStart = ruleStart;
            this.nameEnd = nameEnd;
            this.ruleEnd = ruleEnd;
            this.name = name;
        }

        public void assertMatches(NamedSemanticRegion<?> name, NamedSemanticRegion<?> ruleBounds) {
            assertEquals("name", this.name, name.name());
            assertEquals("name", this.name, ruleBounds.name());
            assertEquals("nameStart", nameStart, name.start());
            assertEquals("ruleStart", ruleStart, ruleBounds.start());
            assertEquals("nameEnd", nameEnd, name.end());
            assertEquals("ruleEnd", ruleEnd, ruleBounds.end());
        }
    }

    @BeforeClass
    public static void setup() throws IOException, URISyntaxException {
        dir = Paths.get(System.getProperty("java.io.tmpdir"), ErrorHandlingTest.class.getSimpleName() + "-" + System.currentTimeMillis());
        errorGrammarDir = dir.resolve("errorgrammar");
        goodGrammarDir = dir.resolve("goodgrammar");
        errorOutputDir1 = dir.resolve("errorgrammar/output1");
        goodOutputDir1 = dir.resolve("goodgrammar/output1/com/foo");
        goodClasspathRoot = dir.resolve("goodgrammar/output1");
        goodOutputDir2 = dir.resolve("goodgrammar/output2/com/foo");
        goodOutputDir3 = dir.resolve("goodgrammar/output3/com/foo");
        goodClasspathRoot2 = dir.resolve("goodgrammar/output2");
        goodClasspathRoot3 = dir.resolve("goodgrammar/output3");
        errorOutputDir2 = dir.resolve("errorgrammar/output2");
        Files.createDirectories(goodOutputDir1);
        Files.createDirectories(errorOutputDir1);
        Files.createDirectories(errorOutputDir2);
        InputStream errorStream = ErrorHandlingTest.class.getResourceAsStream("HasErrors.g4");
        assertNotNull(errorStream);
        InputStream goodStream = ErrorHandlingTest.class.getResourceAsStream("NoErrors.g4");
        errorFile = errorGrammarDir.resolve("HasErrors.g4");
        goodFile = goodGrammarDir.resolve("NoErrors.g4");
        try (OutputStream out = Files.newOutputStream(errorFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileUtil.copy(errorStream, out);
        }
        try (OutputStream out = Files.newOutputStream(goodFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileUtil.copy(goodStream, out);
        }
        lib = new UnitTestAntlrLibrary();
        System.out.println("LIB: \n" + lib);
    }

}

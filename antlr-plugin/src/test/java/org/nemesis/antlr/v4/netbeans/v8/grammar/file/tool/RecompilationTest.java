package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.After;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxySyntaxError;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParserRunResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParserRunnerBuilder;

/**
 *
 * @author Tim Boudreau
 */
public class RecompilationTest {

    private TestDir nestedGrammarDir;
    private ParserRunnerBuilder bldr;

    public static final String TEXT_1 =
            "{ skiddoo : 23, meaningful : true, meaning: '42' }";
    public static final String TEXT_2 =
            "{ skiddoo : 53, meaningful : false, meaning: 'hey' }";

    @Test
    public void testRecompilation() throws IOException, Throwable {
        GenerateBuildAndRunGrammarResult res = bldr.parse(TEXT_1);
        res.rethrow();
        boolean usable = res.onSuccess((AntlrSourceGenerationResult genResult, CompileResult compileResult, ParserRunResult parserRunResult) -> {
            assertTrue(parserRunResult.parseTree().isPresent());
            ParserAssertions as = new ParserAssertions(parserRunResult.parseTree().get());
            as.assertNextNonWhitespace("{", null, "map");
            as.assertNextNonWhitespace("skiddoo", "Identifier", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("23", "Number", "map", "mapItem", "value", "numberValue");
            as.assertNextNonWhitespace(",", null, "map");
            as.assertNextNonWhitespace("meaningful", "Identifier", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("true", "True", "value", "booleanValue");
            as.assertNextNonWhitespace(",", null, "map");
            as.assertNextNonWhitespace("meaning", "Identifier", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("'42'", "String", "map", "mapItem", "value", "stringValue");
            as.assertNextNonWhitespace("}", null, "map");
        });
        assertTrue(res.wasCompiled());
        assertTrue(res.wasParsed());
        assertTrue(res.parseResult().isPresent());
        assertTrue(res.parseResult().get().parseTree().isPresent());
        assertFalse(res.parseResult().get().parseTree().get().hasErrors());
        List<ProxySyntaxError> errs = res.parseResult().get().parseTree().get().syntaxErrors();
        assertTrue(errs.toString(), errs.isEmpty());
        assertTrue(usable);

        res = bldr.parse(TEXT_2);
        usable = res.onSuccess((AntlrSourceGenerationResult genResult, CompileResult compileResult, ParserRunResult parserRunResult) -> {
            assertTrue(parserRunResult.parseTree().isPresent());
            ParserAssertions as = new ParserAssertions(parserRunResult.parseTree().get());
            as.assertNextNonWhitespace("{", null, "map");
            as.assertNextNonWhitespace("skiddoo", "Identifier", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("53", "Number", "map", "mapItem", "value", "numberValue");
            as.assertNextNonWhitespace(",", null, "map");
            as.assertNextNonWhitespace("meaningful", "Identifier", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("false", "False", "value", "booleanValue");
            as.assertNextNonWhitespace(",", null, "map");
            as.assertNextNonWhitespace("meaning", "Identifier", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("'hey'", "String", "map", "mapItem", "value", "stringValue");
            as.assertNextNonWhitespace("}", null, "map");
        });
        assertFalse(res.wasCompiled());
        assertTrue(usable);
        nestedGrammarDir.modifyGrammar(line -> {
            return line
                    .replaceAll("Identifier", "ArgleBargle");
        });
        res = bldr.parse(TEXT_2);
        assertTrue(res.wasCompiled());
        assertTrue(res.isUsable());
        usable = res.onSuccess((AntlrSourceGenerationResult genResult, CompileResult compileResult, ParserRunResult parserRunResult) -> {
            assertTrue(parserRunResult.parseTree().isPresent());
            ParserAssertions as = new ParserAssertions(parserRunResult.parseTree().get());
            as.assertNextNonWhitespace("{", null, "map");
            as.assertNextNonWhitespace("skiddoo", "ArgleBargle", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("53", "Number", "map", "mapItem", "value", "numberValue");
            as.assertNextNonWhitespace(",", null, "map");
            as.assertNextNonWhitespace("meaningful", "ArgleBargle", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("false", "False", "value", "booleanValue");
            as.assertNextNonWhitespace(",", null, "map");
            as.assertNextNonWhitespace("meaning", "ArgleBargle", "items", "map", "mapItem");
            as.assertNextNonWhitespace(":", null, "mapItem");
            as.assertNextNonWhitespace("'hey'", "String", "map", "mapItem", "value", "stringValue");
            as.assertNextNonWhitespace("}", null, "map");
        });
    }

    Path root;
    @Before
    public void setUp() throws IOException, URISyntaxException {
        root = Paths.get(System.getProperty("java.io.tmpdir"), RecompilationTest.class.getSimpleName() + "-" + System.currentTimeMillis());
        nestedGrammarDir = new TestDir(root, "NestedMapGrammar", "NestedMapGrammar.g4", "com.nested");
        bldr = GrammarJavaSourceGeneratorBuilder.forAntlrSource(nestedGrammarDir.antlrSourceFile)
                .withOutputRoot(nestedGrammarDir.javaClasspathRoot)
                .withPackage(nestedGrammarDir.packageName)
                .withAntlrLibrary(AntlrLibrary.getDefault())
                .withRunOptions(AntlrRunOption.GENERATE_LEXER)
                .withRunOptions(AntlrRunOption.GENERATE_VISITOR)
                .toParseAndRunBuilder();
    }

    static boolean reallyCleanup;
    @After
    public void cleanup() throws IOException {
        if (nestedGrammarDir != null) {
            nestedGrammarDir.cleanUp();
        }
//        if (root != null && Files.exists(root)) {
//            long size = Files.list(root).count();
//            assertEquals("Some files left behind in " + root
//                    + ": " + files(root), 0, size);
//            if (reallyCleanup) {
//                Files.delete(root);
//            }
//        }
    }

    private String files(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        Files.list(p).forEach(path -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.relativize(path));
        });
        return sb.toString();
    }

}

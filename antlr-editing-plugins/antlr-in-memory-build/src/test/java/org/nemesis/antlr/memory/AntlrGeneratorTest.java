package org.nemesis.antlr.memory;

import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.CLASS;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.nio.BlockStorageKind;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrGeneratorTest {

    private JFS jfs;
    // Intentionally using a non-standard location to make sure nothing
    // assumes StandardLocation.SOURCE_PATH
    private static final Location SOURCE_DEST = StandardLocation.CLASS_OUTPUT;
    private static final Location GENERATED_DEST = StandardLocation.SOURCE_PATH;
    private static final String G4_EXT = ".g4";
    private static final String GRAMMAR_NAME = "NestedMapGrammar";
    private static final String GRAMMAR_FILE_NAME = GRAMMAR_NAME + G4_EXT;
    private String pkg;
    private UnixPath packagePath;
    private UnixPath sourceFile;
    private AntlrGeneratorBuilder<AntlrGenerator> bldr;
    private ByteArrayOutputStream out;
    private final Set<String> createdPaths = new HashSet<>();

    @Test
    public void testGeneration() throws Throwable {
        AntlrGenerationResult result = bldr.building(packagePath).run(GRAMMAR_FILE_NAME, new PrintStream(out), true);
        result.rethrow();
        assertTrue(result.success);
        assertEquals(0, result.code);
        assertNotNull(result.grammarName);
        assertNotNull(result.mainGrammar);
        assertEquals(GRAMMAR_NAME, result.mainGrammar.name);
        assertEquals(GRAMMAR_NAME, result.grammarName);
        assertFalse(result.modifiedFiles.isEmpty());

        assertSetsEqual(expectedFileNames(), createdPaths);
        assertEquals(SOURCE_DEST, result.grammarSourceLocation);
        assertEquals(GENERATED_DEST, result.javaSourceOutputLocation);
        assertTrue(result.errors.isEmpty());
        assertNotNull(result.grammarFile);
        assertEquals(pkg, result.packageName);
        assertTrue(result.grammarFile.resolveOriginal().getName().endsWith("/" + GRAMMAR_FILE_NAME));
        assertEquals(UnixPath.get(result.grammarFile.resolveOriginal().getName()).getParent(), packagePath);

        List<JavaFileObject> all = new ArrayList<>();
        jfs.list(SOURCE_DEST, "", EnumSet.of(CLASS), true).forEach(all::add);

        assertTrue(result.currentStatus().isUpToDate());
        JFSFileObject outfile = jfs.getFileForOutput(SOURCE_DEST, sourceFile);
        long oldLastModified = outfile.getLastModified();
        Thread.sleep(1); // on a hypothetical, extremely fast machine we could do all of this in the same millisecond
        try (Writer w = outfile.openWriter()) {
            w.append("Hello world\n").append("This has been altered.\n");
        }
        long newLastModified = outfile.getLastModified();
        assertTrue(newLastModified > oldLastModified);
        assertFalse(result.currentStatus().isUpToDate());

        try (PrintStream printStream = AntlrLoggers.getDefault().printStream(UnixPath.get(GRAMMAR_FILE_NAME), AntlrLoggers.STD_TASK_GENERATE_ANTLR)) {
            AntlrGenerationResult newResult = bldr.building(packagePath).run(GRAMMAR_FILE_NAME, printStream, true);
            assertFalse(newResult.isUsable());
        }

        assertEquals(packagePath.resolve("NestedMapGrammar.g4"), result.primaryFiles.get("NestedMapGrammar"));
        assertEquals(packagePath.resolve("NestedMapGrammar.g4"), result.primaryFiles.get("NestedMapGrammarLexer"));

        Set<UnixPath> outputFiles = result.outputFiles.get(packagePath.resolve("NestedMapGrammar.g4"));
        Set<UnixPath> output2 = result.outputFilesForGrammar("NestedMapGrammar");
        assertNotNull(outputFiles);
        assertNotNull(output2);
        assertSame(outputFiles, output2);
        assertTrue(outputFiles.contains(packagePath.resolve("NestedMapGrammarParser.java")));
        assertTrue(outputFiles.contains(packagePath.resolve("NestedMapGrammarLexer.java")));
        assertTrue(outputFiles.contains(packagePath.resolve("NestedMapGrammarListener.java")));
        assertTrue(outputFiles.contains(packagePath.resolve("NestedMapGrammarVisitor.java")));
        assertTrue(outputFiles.contains(packagePath.resolve("NestedMapGrammarBaseListener.java")));
        assertTrue(outputFiles.contains(packagePath.resolve("NestedMapGrammarBaseVisitor.java")));
        assertTrue(outputFiles.contains(packagePath.resolve("NestedMapGrammar.tokens")));
        assertTrue(outputFiles.contains(packagePath.resolve("NestedMapGrammar.interp")));

        assertEquals(1, result.inputFiles.size());
        Set<UnixPath> in = result.inputFiles.get("NestedMapGrammar");
        assertNotNull(in);
        assertEquals(1, in.size());
        assertEquals(packagePath.resolve("NestedMapGrammar.g4"), in.iterator().next());
    }

    private String output() {
        return new String(out.toByteArray(), UTF_8);
    }

    private void onFileCreated(Location location, FileObject file) {
        createdPaths.add(file.getName());
    }

    static <T> void assertSetsEqual(Set<T> expected, Set<T> got) {
        if (!expected.equals(got)) {
            Set<T> missing = new HashSet<>(expected);
            missing.removeAll(got);
            Set<T> unexpected = new HashSet<>(got);
            unexpected.removeAll(expected);
            StringBuilder sb = new StringBuilder("Sets do not match. Missing: ")
                    .append(Strings.join(',', missing)).append("; unexpected")
                    .append(Strings.join(',', unexpected)).append("; all items:");
            for (T t : got) {
                sb.append('\n').append(t);
            }
            fail(sb.toString());
        }
    }

    static final String[] GEN_SUFFIXES = new String[]{
        ".g4",
        ".tokens",
        ".interp",
        "Lexer.tokens",
        "Lexer.interp",
        "Lexer.java",
        "Parser.java",
        "Listener.java",
        "Visitor.java",
        "BaseVisitor.java",
        "BaseListener.java",};

    Set<String> expectedFileNames() {
        Set<String> result = new HashSet<>();
        for (String suffix : GEN_SUFFIXES) {
            String fn = GRAMMAR_NAME + suffix;
            result.add(packagePath.resolve(fn).toString());
        }
        return result;
    }

    @BeforeEach
    public void setup() throws IOException {
        out = new ByteArrayOutputStream(2048);
        pkg = "com.foo." + getClass().getName().toLowerCase()
                + ".test" + Long.toString(System.currentTimeMillis());

        packagePath = UnixPath.get(pkg.replace('.', '/'));
        sourceFile = packagePath.resolve(GRAMMAR_FILE_NAME);

        String contents = Streams.readResourceAsUTF8(AntlrGeneratorTest.class, GRAMMAR_FILE_NAME);
        jfs = JFS.builder().useBlockStorage(BlockStorageKind.HEAP)
                .withListener(this::onFileCreated).withCharset(UTF_8).build();
        jfs.create(sourceFile, SOURCE_DEST, contents);

        JFSFileObject srcFile = jfs.get(SOURCE_DEST, sourceFile);
        assertNotNull(srcFile);
        String srcFileContents = srcFile.getCharContent(false).toString();
        assertEquals(contents, srcFileContents, "Source file contents from JFS"
                + " are incorrect");
        bldr = AntlrGenerator.builder(() -> jfs)
                .grammarSourceInputLocation(SOURCE_DEST)
                .javaSourceOutputLocation(GENERATED_DEST)
                .withOriginalFile(Paths.get("AntlrGeneratorTest"))
                .withTokensHash("-zzzz-")
                .generateIntoJavaPackage(pkg);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (jfs != null) { // error during setup
            jfs.close();
        }
    }

}

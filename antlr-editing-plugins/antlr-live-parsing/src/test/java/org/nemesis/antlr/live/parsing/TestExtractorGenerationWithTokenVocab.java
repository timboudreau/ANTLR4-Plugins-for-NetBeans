/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.live.parsing;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.StandardLocation;
import org.antlr.v4.tool.Grammar;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.live.execution.AntlrRunSubscriptions;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParsersTest.FakeParserFactory;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.ExtractionCodeGenerationResult;
import org.nemesis.antlr.live.parsing.extract.ExtractionCodeGenerator;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner;
import static org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner.findLexerGrammar;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.nb.NbJFSUtilities;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
@TestMethodOrder(Alphanumeric.class)
@Execution(ExecutionMode.SAME_THREAD)
public class TestExtractorGenerationWithTokenVocab {

    private static GeneratedMavenProject gen;
    private static ThrowingRunnable shutdown;
    private static final String SOME_MARKDOWN = "# Well Hello There\n\nThis is some markdown,"
            + " which shall be parsed.\n\n * Is that\n * Cool and stuff\n * Or What?\n\n"
            + "This should work, I hope.\n";
    private static Path markdownParserFile;
    private static Path markdownLexerFile;
    private static AntlrGenerationResult lastGenResult;

    private JFS findJFS() throws Exception {
        AtomicReference<JFS> jfs = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        FileObject fo = gen.file("MarkdownParser.g4");
        assertNotNull(fo, "Parser not found");
        RebuildSubscriptions.subscribe(fo, new Subscriber() {
            @Override
            public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
                lastGenResult = res;
                jfs.set(res.jfs());
                latch.countDown();
            }
        });
        for (int i = 0; i < 20; i++) {
            if (jfs.get() == null) {
                latch.await(1, TimeUnit.SECONDS);
            } else {
                break;
            }
        }
        assertNotNull("No JFS received by timeout");
        return jfs.get();
    }

    @Test
    public void testCodeGeneration() throws Exception {
        assertNotNull(gen);
        JFS jfs = findJFS();
        assertNotNull(lastGenResult);
        Grammar lg = findLexerGrammar(lastGenResult);
        assertNotNull(lg);
        // Make sure we can really find the lexer grammar we depend on
        assertEquals("MarkdownLexer", lg.name);
        ExtractionCodeGenerationResult codeGenResult = ExtractionCodeGenerator.saveExtractorSourceCode(markdownParserFile, jfs, "com.goob", "MarkdownParser", null);
        JFSFileObject file = codeGenResult.file();
        assertNotNull(file, "Code not generated");
        String txt = file.getCharContent(true).toString();
        assertLexerIsCreatedUsingCorrectClass(txt);
        // No try passing the lexer name
        codeGenResult = ExtractionCodeGenerator.saveExtractorSourceCode(markdownParserFile, jfs, "com.goob", "MarkdownParser", "MarkdownLexer");
        file = codeGenResult.file();
        assertNotNull(file, "Code not generated");
        txt = file.getCharContent(true).toString();
        assertLexerIsCreatedUsingCorrectClass(txt);
    }

    private void assertLexerIsCreatedUsingCorrectClass(String extractorText) {
        String[] lines = extractorText.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("lex = new")) {
                assertTrue(lines[i].contains("MarkdownLexer(new CharSequenceCharStream(text))"),
                        "The lexer is what should be instantiated against a CharStream, but the "
                        + "generated code contains '" + lines[i] + "' which probably "
                        + "failed to compile.  Check the lexer type detection code.");
            }
        }
    }

    @Test
    public void testParserFindsCorrectLexer() throws Exception {
        AtomicReference<JFS> jfs = new AtomicReference<>();
        assertNotNull(gen.file("MarkdownParser.g4"));
        RebuildSubscriptions.subscribe(gen.file("MarkdownParser.g4"), new Subscriber() {
            @Override
            public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
                jfs.set(res.jfs());
            }
        });

        EmbeddedAntlrParser p = EmbeddedAntlrParsers.forGrammar("test", gen.file("MarkdownParser.g4"));
        EmbeddedAntlrParserResult res = p.parse(SOME_MARKDOWN);
        GrammarRunResult<?> r = res.runResult();
        assertNotNull(jfs.get(), "Not rebuilt?");

        JFSFileObject extractorSource = jfs.get().get(StandardLocation.SOURCE_PATH, UnixPath.get("com/goob/ParserExtractor.java"));
        assertNotNull(extractorSource, "Extractor code not generated to SOURCE_PATH at " + "com/goob/ParserExtractor.java");
        String extractorText = extractorSource.getCharContent(false).toString();
        assertLexerIsCreatedUsingCorrectClass(extractorText);

        assertNotNull(r, "No run result - compile failed?");
        assertTrue(r.isUsable(), r.diagnostics() + "");

        ParseTreeProxy ptp = res.proxy();
        assertFalse(ptp.isUnparsed());
        assertNotNull(r);
        assertFalse(r.compileFailed());
    }

    @BeforeAll
    public static void setup() throws IOException, ClassNotFoundException, URISyntaxException {

        Class.forName(AntlrNbParser.class.getName());

        ProjectTestHelper helper = ProjectTestHelper.relativeTo(TestExtractorGenerationWithTokenVocab.class);
        markdownParserFile = helper.projectBaseDir().getParent().resolve(
                "antlr-in-memory-build/src/test/resources/org/nemesis/antlr/memory/MarkdownParser.g4");
        markdownLexerFile = helper.projectBaseDir().getParent().resolve(
                "antlr-in-memory-build/src/test/resources/org/nemesis/antlr/memory/MarkdownLexer.g4");
        assertTrue(Files.exists(markdownParserFile), "Could not find markdown parser " + markdownParserFile);
        assertTrue(Files.exists(markdownLexerFile), "Could not find markdown lexer " + markdownLexerFile);
        gen = ProjectTestHelper.projectBuilder().copyMainAntlrSource(markdownLexerFile, "com/goob")
                .copyMainAntlrSource(markdownParserFile, "com/goob").verboseLogging().build("markdown");

        Path pth = gen.allFiles().get("MarkdownParser.g4");
        assertNotNull(pth);
        String mime = AdhocMimeTypes.mimeTypeForPath(pth);
        shutdown = initAntlrTestFixtures(true)
                .addToNamedLookup(AntlrRunSubscriptions.pathForType(EmbeddedParser.class), ProxiesInvocationRunner.class)
                .addToNamedLookup(mime, FakeParserFactory.class)
                .build();

        gen.deletedBy(shutdown);
        shutdown.andAlways(() -> {
            if (fixtures != null) {
                try {
                    fixtures.onShutdown();
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }
        });
        assertNotNull(gen);
        assertNotNull(gen.file("MarkdownParser.g4"));
        assertNotNull(gen.file("MarkdownLexer.g4"));
        assertNotNull(MimeLookup.getLookup(mime).lookup(ParserFactory.class));
    }

    @AfterAll
    public static void tearDown() throws Exception {
        shutdown.run();
    }

    static TestFixtures fixtures;

    public static TestFixtures initAntlrTestFixtures(boolean verbose) {
        fixtures = new TestFixtures();
        if (verbose) {
            fixtures.verboseGlobalLogging();
        }
        DocumentFactory fact = new DocumentFactoryImpl();
        return fixtures.addToMimeLookup("", fact)
                .addToMimeLookup("text/x-g4", AntlrNbParser.AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter(), fact)
                .addToNamedLookup(org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
                        FakeG4DataLoader.class,
                        MavenFolderStrategyFactory.class,
                        NbMavenProjectFactory.class,
                        AntlrFileObjectRelativeResolver.class,
                        NbProjectManager.class,
                        NbJFSUtilities.class
                );
    }
}

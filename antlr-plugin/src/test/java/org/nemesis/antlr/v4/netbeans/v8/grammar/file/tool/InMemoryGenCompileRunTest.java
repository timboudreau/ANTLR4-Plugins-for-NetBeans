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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.junit.After;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.RecompilationTest.TEXT_1;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.RecompilationTest.TEXT_2;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir.projectBaseDir;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir.testResourcePath;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParseProxyBuilder;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class InMemoryGenCompileRunTest {

    private Path importdir;
    private Path lexer;
    private Path grammar;
    private String sampleGrammar;
    private Path tokens;
    private Path interp;
    private Path lextokens;
    private Path lexinterp;
    private Path tmpFile;
    private Path rust;
    private Path xidstart;
    private Path xidcontinue;

//    @Test
    public void testSimpleGrammar() throws Throwable {
        InMemoryAntlrSourceGenerationBuilder bldr = new InMemoryAntlrSourceGenerationBuilder(tmpFile)
                .setAntlrLibrary(new UnitTestAntlrLibrary());
        ParseProxyBuilder runner = bldr.toParseAndRunBuilder();

        GenerateBuildAndRunGrammarResult result = runner.parse(TEXT_1);

        System.out.println(result.parseResult().get().parseTree().get());

        GenerateBuildAndRunGrammarResult result2 = runner.parse(TEXT_2);

        System.out.println(result2.parseResult().get().parseTree().get());
        assertFalse(result2.wasCompiled());
        assertTrue(result2.wasParsed());

        GenerateBuildAndRunGrammarResult result3 = runner.parse(TEXT_2);
        assertSame(result2, result3);
    }

//    @Test
    public void testParseLexerGrammar() throws Throwable {
        Path g = importdir.resolve("LexBasic.g4");
        InMemoryAntlrSourceGenerationBuilder bldr = new InMemoryAntlrSourceGenerationBuilder(g)
                .setAntlrLibrary(new UnitTestAntlrLibrary());
        ParseProxyBuilder runner = bldr.toParseAndRunBuilder();

        GenerateBuildAndRunGrammarResult result = runner.parse(sampleGrammar);
        if (result.thrown().isPresent()) {
            System.out.println("\nFAILED.  FS CONTENTS:\n");
            bldr.jfs().listAll((loc, fo) -> {
                System.out.println(" - " + loc + " - " + fo);
            });
        }
    }

//    @Test
    public void testGenerateAndInvoke() throws Throwable {
        InMemoryAntlrSourceGenerationBuilder bldr = new InMemoryAntlrSourceGenerationBuilder(grammar)
                .withImportDir(importdir)
                .mapIntoSourcePackage(lexer)
                .mapIntoSourcePackage(grammar)
                .copyIntoSourcePackage(tokens)
                .copyIntoSourcePackage(interp)
                .copyIntoSourcePackage(lextokens)
                .copyIntoSourcePackage(lexinterp)
                .setAntlrLibrary(new UnitTestAntlrLibrary());

        ParseProxyBuilder runner = bldr.toParseAndRunBuilder();
        GenerateBuildAndRunGrammarResult result = runner.parse(sampleGrammar);

        if (result.thrown().isPresent()) {
            System.out.println("\nFAILED.  FS CONTENTS:\n");
            bldr.jfs().listAll((loc, fo) -> {
//                if (fo.getName().endsWith(".java") || fo.getName().endsWith(".class")) {
                System.out.println(" - " + loc + " - " + fo);
                if (fo.getName().contains("ParserExtr")) {
                    try {
                        System.out.println(fo.getCharContent(true));
//                }
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
        }

        result.rethrow();
        assertTrue(result.parseResult().isPresent());
        assertTrue(result.parseResult().get().parseTree().isPresent());
        ParseTreeProxy prx = result.parseResult().get().parseTree().get();
        System.out.println("PRX: " + prx);

        bldr.stale = true;
        GenerateBuildAndRunGrammarResult result2 = runner.parse(sampleGrammar);
        assertNotSame(result, result2);
        assertNotSame(result.parseResult().get().parseTree().get(), result2.parseResult().get().parseTree().get());
    }

    @Test
    public void testGenerateWithImportsRetainsImportsAcrossRuns() throws IOException, URISyntaxException {
        InMemoryAntlrSourceGenerationBuilder bldr = new InMemoryAntlrSourceGenerationBuilder(td.antlrSourceFile)
                .withImportDir(td.antlrSources.resolve("import"))
                .setAntlrLibrary(new UnitTestAntlrLibrary());

        ParseProxyBuilder pbuilder = bldr.toParseAndRunBuilder();

        GenerateBuildAndRunGrammarResult res = pbuilder.parse(RUST_SAMPLE);
        
        bldr.stale = true;

        res = pbuilder.parse(RUST_SAMPLE + "\n// hey\n");

        GenerateBuildAndRunGrammarResult res2 = pbuilder.parse(RUST_SAMPLE);
        assertNotSame(res, res2);
        assertTrue(res2.isUsable());
        assertTrue(res2.generationResult().isUsable());
        assertFalse(res2.parseResult().get().parseTree().get().isUnparsed());

        System.out.println("PARSE RESULT: " + res2);
    }

    private TestDir td;

    @Before
    public void setup() throws URISyntaxException, IOException {
        System.setProperty("fs.off.heap", "true");
        Path baseDir = projectBaseDir();
        importdir = baseDir.resolve("src/main/antlr4/imports");
        lexer = baseDir.resolve("src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.g4");
        grammar = baseDir.resolve("src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4.g4");
        tokens = baseDir.resolve("target/classes/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4.tokens");
        interp = baseDir.resolve("target/classes/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4.interp");
        lextokens = baseDir.resolve("target/classes/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.tokens");
        lexinterp = baseDir.resolve("target/classes/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.interp");

        rust = testResourcePath(InMemoryGenCompileRunTest.class, "Rust-Minimal._g4");
        xidstart = testResourcePath(InMemoryGenCompileRunTest.class, "xidstart._g4");
        xidcontinue = testResourcePath(InMemoryGenCompileRunTest.class, "xidcontinue._g4");

        Path root = Paths.get(System.getProperty("java.io.tmpdir"), InMemoryGenCompileRunTest.class.getSimpleName() + "-" + System.currentTimeMillis());

        td = new TestDir(root, "InMemoryGenCompileRunTest", "Rust-Minimal._g4", "com.wurgle", InMemoryGenCompileRunTest.class);
        td.addImportFile("xidstart.g4", xidstart);
        td.addImportFile("xidcontinue.g4", xidcontinue);

        assertTrue(tokens + "", Files.exists(tokens));
        assertTrue(interp + "", Files.exists(interp));
        try (InputStream in = InMemoryGenCompileRunTest.class.getResourceAsStream("NestedMapGrammar.g4")) {
            assertNotNull(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FileUtil.copy(in, out);
            sampleGrammar = new String(out.toByteArray(), UTF_8);
        }
        tmpFile = Paths.get(System.getProperty("java.io.tmpdir"), "SampleGrammar.g4");
        Files.write(tmpFile, sampleGrammar.getBytes(UTF_8), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    @After
    public void cleanup() throws IOException {
        if (tmpFile != null && Files.exists(tmpFile)) {
            Files.delete(tmpFile);
        }
        td.cleanUp();
    }

    private static final String RUST_SAMPLE
            = "// ignore-emscripten no threads support\n"
            + "\n"
            + "use std::thread;\n"
            + "\n"
            + "pub fn main() {\n"
            + "    let mut result = thread::spawn(child);\n"
            + "    println!(\"1\");\n"
            + "    thread::yield_now();\n"
            + "    println!(\"2\");\n"
            + "    thread::yield_now();\n"
            + "    println!(\"3\");\n"
            + "    result.join();\n"
            + "}\n"
            + "\n"
            + "fn child() {\n"
            + "    println!(\"4\"); thread::yield_now(); println!(\"5\"); thread::yield_now(); println!(\"6\");\n"
            + "}";

}

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
package org.nemesis.antlr.live.execution;

import org.junit.jupiter.api.Test;
import com.mastfrog.function.throwing.ThrowingRunnable;
import static com.mastfrog.util.collections.CollectionUtils.map;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.thread.ResettableCountDownLatch;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.compilation.AntlrGeneratorAndCompiler;
import org.nemesis.antlr.compilation.AntlrGeneratorAndCompilerBuilder;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.memory.AntlrGeneratorBuilder;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSClassLoader;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.modules.parsing.api.ParserManager;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRunSubscriptionsTest {

    private ThrowingRunnable onShutdown;
    private GeneratedMavenProject genProject;
    public static final String TEXT_1
            = "{ skiddoo : 23, meaningful : true,\n"
            + "meaning: '42', \n"
            + "thing: 51 }";

    @Test
    @SuppressWarnings({"unchecked", "rawtype", "UnusedAssignment", "SleepWhileInLoop"})
    public void testReparseHappens() throws Throwable {
        // Runs some trivial code that runs the parser and extracts
        // a little info to prove it

        assertTrue(ParserManager.canBeParsed(ANTLR_MIME_TYPE), "Parsing support "
                + "for Antlr not registered");

        FileObject fo = genProject.file("NestedMaps.g4");
        Bic bic = new Bic();
        InvocationSubscriptions<Map> is = AntlrRunSubscriptions.forType(Map.class);
        Runnable unsub = is.subscribe(fo, bic);
        GrammarRunResult<Map> r = bic.assertExtracted();
        assertNotNull(unsub);
        for (int i = 0; i < 100 && IR.lastClassloader() == null; i++) {
            Thread.sleep(10);
        }
        JFSClassLoader cl = IR.lastClassloader();
        assertNotNull(cl, "No JFS classloader, or IR instance replaced");

        JFS jfs = r.jfs();
        JFSFileObject jfo = jfs.get(StandardLocation.SOURCE_PATH, UnixPath.get("com/foo/bar/NestedMaps.g4"));
        assertNotNull(jfo);
        JFSFileObject jfo2 = jfs.get(StandardLocation.SOURCE_PATH, UnixPath.get("imports/NMLexer.g4"));
        assertNotNull(jfo2);

        assertEquals(2, r.diagnostics().size(), r.diagnostics().toString());
        Set<String> dgs = new HashSet<>();
        for (JavacDiagnostic d : r.diagnostics()) {
            dgs.add(d.sourceCode());
        }
        assertEquals(setOf("compiler.note.unchecked.filename", "compiler.note.unchecked.recompile"), dgs);
        r.rethrow();
        assertTrue(r.isUsable());
        assertNotNull(r.get());
        Map<String, Object> m = r.get();
        assertNotNull(m.get("errors"));
        assertTrue(m.get("errors") instanceof List<?>);
        assertTrue((((List<?>) m.get("errors"))).isEmpty());
        assertEquals("map mapItem value numberValue mapItem value booleanValue mapItem value stringValue mapItem value numberValue",
                m.get("tree"));

        unsub.run();
        for (int i = 0; i < 100 && !cl.isClosed(); i++) {
            Thread.sleep(10);
        }
        assertTrue(cl.isClosed());
        WeakReference<JFSClassLoader> weak = new WeakReference<>(cl);
        IR.clearLast();
        is = null;
        cl = null;
        r = null;
        m = null;
        for (int i = 0; i < 50; i++) {
            System.gc();
            System.runFinalization();
            Thread.yield();
        }
        cl = weak.get();
        if (cl != null) {
            Map<String, Object> roots = map("Default Lookup").to(Lookup.getDefault())
                    .map("text/x-g4 MimeLookup").to(MimeLookup.getLookup(ANTLR_MIME_TYPE))
                    .map("JFS").finallyTo(jfs);
            List<String> refs = ReferencesFinder.detect(cl, roots);
            if (refs.isEmpty()) {
                fail("JFSClassLoader still referenced, but did not find the path to it from " + roots);
            } else {
                StringBuilder sb = new StringBuilder("JFSClassLoader still referenced via:");
                for (String path : refs) {
                    sb.append("\n  * ").append(path);
                }
                fail(sb.toString());
            }
        }
        // failover
        assertNull(weak.get(), "JFSClassLoader is still referenced after close");

    }

    static final class Bic implements BiConsumer<Extraction, GrammarRunResult<Map>> {

        private volatile GrammarRunResult<Map> map;
        private final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);

        public GrammarRunResult<Map> assertExtracted() throws InterruptedException {
            for (int i = 0; i < 10; i++) {
                if (map != null) {
                    break;
                }
                if (latch.await(1000, TimeUnit.MILLISECONDS)) {
                    latch.reset(1);
                    if (map != null) {
                        System.out.println("  HAVE IT");
                        break;
                    }
                }
            }
            GrammarRunResult<Map> result = map;
            map = null;
            assertNotNull(result, "Generation may have failed:\n" + FakeAntlrLoggers.lastText());
            return result;
        }

        @Override
        public void accept(Extraction t, GrammarRunResult<Map> u) {
            System.out.println("\n\nBIC ACCEPT " + u + "\n\n");
            map = u;
            latch.countDown();
        }
    }

    @BeforeEach
    public void setup() throws IOException, ClassNotFoundException {
        onShutdown = initAntlrTestFixtures()
                .addToNamedLookup(AntlrRunSubscriptions.pathForType(Map.class), IR.class)
                .verboseGlobalLogging(
                        ParsingUtils.class,
                        RebuildSubscriptions.class,
                        AntlrRunSubscriptions.class,
                        AntlrGenerator.class,
                        FakeAntlrLoggers.class,
                        "org.nemesis.antlr.memory.tool.ToolContext",
                        "org.nemesis.antlr.project.AntlrConfigurationCache",
                        "org.nemesis.antlr.project.impl.FoldersHelperTrampoline",
                        "org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation",
                        "org.nemesis.antlr.project.impl.InferredConfig",
                        JFSClassLoader.class,
                        AntlrGeneratorAndCompiler.class,
                        AntlrGeneratorBuilder.class,
                        AntlrGeneratorAndCompilerBuilder.class,
                        InvocationRunner.class
                )
                .excludeLogs("KeyMapsStorage", "KeyBindingSettingsImpl")
                .build();
        genProject = ProjectTestHelper.projectBuilder().writeStockTestGrammarSplit("com.foo.bar")
                .build(AntlrRunSubscriptionsTest.class.getSimpleName())
                .deletedBy(onShutdown);
        assertNotNull(genProject.project(), "Project could not be loaded for " + genProject.dir());
    }

    @AfterEach
    public void shutdown() throws Exception {
        if (onShutdown != null) {
            onShutdown.run();
        }
    }

    public static TestFixtures initAntlrTestFixtures() {
        return initAntlrTestFixtures(false);
    }

    public static TestFixtures initAntlrTestFixtures(boolean verbose) {
        TestFixtures fixtures = new TestFixtures();
        if (verbose) {
            fixtures.verboseGlobalLogging(
                    ParsingUtils.class,
                    RebuildSubscriptions.class,
                    AntlrRunSubscriptions.class,
                    AntlrGenerator.class,
                    "org.nemesis.antlr.memory.tool.ToolContext",
                    "org.nemesis.antlr.project.AntlrConfigurationCache",
                    "org.nemesis.antlr.project.impl.FoldersHelperTrampoline",
                    "org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation",
                    "org.nemesis.antlr.project.impl.InferredConfig",
                    AntlrGeneratorAndCompiler.class,
                    AntlrGeneratorBuilder.class,
                    AntlrGeneratorAndCompilerBuilder.class,
                    InvocationRunner.class
            );
        }
        DocumentFactory fact = new DocumentFactoryImpl();
        return fixtures.addToMimeLookup("", fact)
                .addToMimeLookup("text/x-g4", AntlrNbParser.AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter(), fact)
                .addToNamedLookup(org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
                        FakeAntlrLoggers.class,
                        FakeG4DataLoader.class,
                        MavenFolderStrategyFactory.class,
                        NbMavenProjectFactory.class,
                        AntlrFileObjectRelativeResolver.class
                );
    }
}

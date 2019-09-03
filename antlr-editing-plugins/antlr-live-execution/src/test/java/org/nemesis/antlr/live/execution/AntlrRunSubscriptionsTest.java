/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.execution;

import org.junit.jupiter.api.Test;
import com.mastfrog.function.throwing.ThrowingRunnable;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.thread.OneThreadLatch;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.nemesis.test.fixtures.support.MavenProjectBuilder;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRunSubscriptionsTest {

    private ThrowingRunnable onShutdown;
    private MavenProjectBuilder.GeneratedMavenProject genProject;
    public static final String TEXT_1
            = "{ skiddoo : 23, meaningful : true,\n"
            + "meaning: '42', \n"
            + "thing: 51 }";

    @Test
    public void testReparseHappens() throws Throwable {
        // Runs some trivial code that runs the parser and extracts
        // a little info to prove it
        FileObject fo = genProject.file("NestedMaps.g4");
        Bic bic = new Bic();
        InvocationSubscriptions<Map> is = AntlrRunSubscriptions.subscribe(Map.class);
        Runnable unsub = is.subscribe(fo, bic);
        assertNotNull(unsub);
        assertNotNull(IR.IR);
        Thread.sleep(1000);
        GrammarRunResult<Map> r = bic.assertExtracted();
        System.out.println("RUN RESULT: " + r);
        System.out.println("MAP: " + r.get());
        System.out.println("DIAGS: " + r.diagnostics());
        System.out.println("DIAGS: " + r.diagnostics());
        assertEquals(2, r.diagnostics().size(), () -> r.diagnostics().toString());
        Set<String> dgs = new HashSet<>();
        for (JavacDiagnostic d : r.diagnostics()) {
            System.out.println("  diag: '" + d.sourceCode()+ "'");
            dgs.add(d.sourceCode());
        }
        assertEquals(setOf("compiler.note.unchecked.filename", "compiler.note.unchecked.recompile"), dgs);
        r.jfs().listAll((loc, f) -> {
            System.out.println(" " + loc + ":\t" + f);
        });
        r.rethrow();
        assertTrue(r.isUsable());
        assertNotNull(r.get());
        Map<String, Object> m = r.get();
        assertNotNull(m.get("errors"));
        assertTrue(m.get("errors") instanceof List<?>);
        assertTrue((((List<?>) m.get("errors"))).isEmpty());
        assertEquals("map mapItem value numberValue mapItem value booleanValue mapItem value stringValue mapItem value numberValue",
                m.get("tree"));

    }

    static final class Bic implements BiConsumer<Extraction, GrammarRunResult<Map>> {

        private GrammarRunResult<Map> map;
        private final OneThreadLatch latch = new OneThreadLatch();

        public GrammarRunResult<Map> assertExtracted() throws InterruptedException {
            for (int i = 0; i < 10; i++) {
                if (map != null) {
                    break;
                }
                latch.await(100, TimeUnit.MILLISECONDS);
            }
            GrammarRunResult<Map> result = map;
            map = null;
            assertNotNull(result);
            return result;
        }

        @Override
        public void accept(Extraction t, GrammarRunResult<Map> u) {
            System.out.println("MAP: " + u);
            map = u;
            latch.releaseOne();
        }
    }

    @BeforeEach
    public void setup() throws IOException {
        onShutdown = initAntlrTestFixtures()
                .addToNamedLookup(AntlrRunSubscriptions.pathForType(Map.class), IR.class)
                .verboseGlobalLogging()
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
                        AntlrFileObjectRelativeResolver.class
                );
    }

    static {
        // Preinitialize its logger
        preinit(RebuildSubscriptions.class);
        preinit(AntlrRunSubscriptions.class);
    }

    static void preinit(Class<?> type) {
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FoldersHelperTrampoline.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }
}

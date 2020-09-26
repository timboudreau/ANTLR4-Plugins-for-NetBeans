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
import com.mastfrog.util.thread.OneThreadLatch;
import java.io.IOException;
import java.nio.file.Path;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.function.BiConsumer;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.execution.AntlrRunSubscriptions;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.ParserExtractor;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.nb.NbJFSUtilities;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.editor.document.DocumentFactory;

/**
 *
 * @author Tim Boudreau
 */
public class EmbeddedAntlrParsersTest {

    private static GeneratedMavenProject gen;
    private static ThrowingRunnable shutdown;

    public static final String TEXT_1
            = "{ skiddoo : 23, meaningful : true,\n"
            + "meaning: '42', \n"
            + "thing: 51 }";

    static final class AwaitUpdate implements BiConsumer<Extraction, GrammarRunResult<?>> {

        OneThreadLatch latch = new OneThreadLatch();
        volatile Extraction ext;

        @Override
        public void accept(Extraction t, GrammarRunResult<?> u) {
            ext = t;
            latch.releaseOne();
        }

        Extraction await() throws InterruptedException {
            Extraction e = ext;
            if (e == null) {
                latch.await(10, SECONDS);
                e = ext;
            }
            ext = null;
            return e;
        }
    }

    @Test
    public void testSubscriptionsWorkAsExpected() throws Exception {
        if (true) {
            // Pending updates with the rewrite of RebuildSubscriptions
            return;
        }
        assertNotNull(gen);
        assertNotNull(ParserExtractor.class.getResourceAsStream("ParserExtractor.template"),
                "Parser extractor not generated");

        assertTrue(ParserManager.canBeParsed("text/x-g4"), "Antlr parser not "
                + "registered.");

        System.out.println("\n\ndo the thing\n");
        EmbeddedAntlrParser p = EmbeddedAntlrParsers.forGrammar("test", gen.file("NestedMaps.g4"));

        System.out.println("\n\ndone did the thing");
        assertEquals(1, p.rev());

        AntlrProxies.ParseTreeProxy ptp = p.parse(TEXT_1).proxy();
        assertNotNull(ptp);
        assertFalse(ptp.isUnparsed());
        assertNotNull(ptp);
        ptp.rethrow();
        assertTrue(p.isUpToDate());
        assertFalse(ptp.hasErrors());
        assertFalse(ptp.isUnparsed());
        assertEquals(gen.get("NestedMaps.g4"), ptp.grammarPath());
        assertEquals("NestedMaps", ptp.grammarName(), () -> {
            return "Wrong name " + ptp.grammarName() + "\n"
                    + FakeAntlrLoggers.lastText();
        });

        AntlrProxies.ParseTreeProxy ptp1 = p.parse(TEXT_1).proxy();
        assertEquals("NestedMaps", ptp1.grammarName(), () -> {
            return "Wrong name " + ptp1.grammarName() + "\n"
                    + FakeAntlrLoggers.lastText();
        });
        // XXX disabled caching for now
//        assertSame(ptp, ptp1);
        assertEquals(ptp, ptp1);
        assertEquals(1, p.rev());
//        assertSame(ptp, p.parse(null).proxy());
        assertNotSame(ptp, p.parse(TEXT_1 + "  ").proxy());

        assertTrue(p.isUpToDate());
        AwaitUpdate await = new AwaitUpdate();
        p.listen(await);
//        CountDownLatch latch = new CountDownLatch(1);
//        p.listen((ext, l) -> {
//            latch.countDown();
//        });
        FakeAntlrLoggers.reset();
        gen.replaceString("NestedMaps.g4", "numberValue", "poozleHoozle");

        Extraction nue = await.await();
        assertNotNull(nue);
        assertEquals(1, p.rev());
//        latch.await(1000, TimeUnit.MILLISECONDS);
        assertFalse(p.isUpToDate());

        AntlrProxies.ParseTreeProxy ptp2 = p.parse(TEXT_1).proxy();
        assertEquals("NestedMaps", ptp2.grammarName(), () -> {
            return "Wrong name " + ptp2.grammarName() + "\n"
                    + FakeAntlrLoggers.lastText();
        });

        assertNotNull(ptp2);
        assertFalse(ptp2.isUnparsed());
        assertFalse(ptp2.hasErrors());
        assertEquals(2, p.rev(), () -> "Wrong rev " + p.rev() + ":\n" + FakeAntlrLoggers.lastText());
        assertTrue(p.isUpToDate(), "Reparse did not make it to update the "
                + "parser");

        boolean seen = false;
        for (AntlrProxies.ParseTreeElement e : ptp2.allTreeElements()) {
            seen |= "poozleHoozle".equals(e.name(ptp2));
        }
        assertTrue(seen, "Did not see a renamed tree element after altering "
                + "the grammar");
    }

    @Test
    public void testFlags() {
        for (PredictionMode pd : PredictionMode.values()) {
            int fl = ParserExtractor.flagsforPredictionMode(pd);
            PredictionMode got = ParserExtractor.predictionModeForFlags(fl);
            assertSame(pd, got, "Wrong result for flags " + fl + " should be " 
                    + pd + " but was " + got);
        }
    }

    @Test
    public void testFeatures() {
        EmbeddedParserFeatures fe = new EmbeddedParserFeatures();
        int defFlags = fe.currentFlags();
        assertNotSame(EmbeddedParserFeatures.getInstance(null), fe);
        PredictionMode defaultMode = fe.currentPredictionMode();
        assertSame(PredictionMode.LL, defaultMode);
        fe.setPredictionMode(PredictionMode.SLL);
        assertSame(PredictionMode.SLL, fe.currentPredictionMode());
        assertNotSame(defFlags, fe.currentFlags());
        assertEquals(0, fe.currentFlags());
        fe.setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
        assertSame(PredictionMode.LL_EXACT_AMBIG_DETECTION, fe.currentPredictionMode());
    }

    @BeforeAll
    public static void setup() throws IOException, ClassNotFoundException {
        Class.forName(AntlrNbParser.class.getName());
        gen = ProjectTestHelper.projectBuilder()
                .verboseLogging()
                .writeStockTestGrammar("com.foo")
                .build("foo");
        Path grammarFile = gen.allFiles().get("NestedMaps.g4");
        shutdown = initAntlrTestFixtures(true, grammarFile)
                .addToNamedLookup(AntlrRunSubscriptions.pathForType(EmbeddedParser.class), ProxiesInvocationRunner.class)
                .build();
        gen.deletedBy(shutdown);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        shutdown.run();
    }

    public static TestFixtures initAntlrTestFixtures(boolean verbose, Path grammarFile) {
        TestFixtures fixtures = new TestFixtures();
        if (verbose) {
            fixtures.verboseGlobalLogging();
        }
        String synthesizedMimeType = AdhocMimeTypes.mimeTypeForPath(grammarFile);
        DocumentFactory fact = new WrapDocumentFactory();
        return fixtures.addToMimeLookup("", fact)
                .verboseGlobalLogging(ProxiesInvocationRunner.class,
                        EmbeddedAntlrParserImpl.class,
                        EmbeddedAntlrParsers.class
                )
                .avoidStartingModuleSystem()
                .addToMimeLookup(synthesizedMimeType, FakeParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter(), fact)
                .addToNamedLookup(org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
                        FakeAntlrLoggers.class,
                        MockModules.class,
                        WrapDocumentFactory.class,
                        FakeG4DataLoader.class,
                        MavenFolderStrategyFactory.class,
                        NbMavenProjectFactory.class,
                        AntlrFileObjectRelativeResolver.class,
                        NbProjectManager.class,
                        NbJFSUtilities.class
                );
    }
}

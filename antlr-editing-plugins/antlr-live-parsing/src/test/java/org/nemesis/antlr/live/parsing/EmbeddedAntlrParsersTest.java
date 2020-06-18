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
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.execution.AntlrRunSubscriptions;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.ParserExtractor;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
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

    @Test
    public void testSubscriptionsWorkAsExpected() throws Exception {
        assertNotNull(gen);
        assertNotNull(ParserExtractor.class.getResourceAsStream("ParserExtractor.template"),
                "Parser extractor not generated");

        assertTrue(ParserManager.canBeParsed("text/x-g4"), "Antlr parser not "
                + "registered.");

        EmbeddedAntlrParser p = EmbeddedAntlrParsers.forGrammar("test", gen.file("NestedMaps.g4"));
        AntlrProxies.ParseTreeProxy ptp = p.parse(TEXT_1).proxy();
        assertNotNull(ptp);
        assertFalse(ptp.isUnparsed());
        assertNotNull(ptp);
        ptp.rethrow();
        assertTrue(p.isUpToDate());
        assertFalse(ptp.hasErrors());
        assertFalse(ptp.isUnparsed());
        assertEquals(gen.get("NestedMaps.g4"), ptp.grammarPath());
        assertEquals("NestedMaps", ptp.grammarName());

        AntlrProxies.ParseTreeProxy ptp1 = p.parse(TEXT_1).proxy();
        // XXX disabled caching for now
//        assertSame(ptp, ptp1);
        assertEquals(ptp, ptp1);
        assertEquals(1, p.rev());
//        assertSame(ptp, p.parse(null).proxy());
        assertNotSame(ptp, p.parse(TEXT_1 + "  ").proxy());

        assertTrue(p.isUpToDate());
        CountDownLatch latch = new CountDownLatch(1);
        p.listen((ext, l) -> {
            latch.countDown();
        });
        gen.replaceString("NestedMaps.g4", "numberValue", "poozleHoozle");

        latch.await(1000, TimeUnit.MILLISECONDS);
        assertFalse(p.isUpToDate());

        AntlrProxies.ParseTreeProxy ptp3 = p.parse(TEXT_1).proxy();

        assertNotNull(ptp3);
        assertFalse(ptp3.isUnparsed());
        assertFalse(ptp3.hasErrors());
        assertEquals(2, p.rev());
        assertTrue(p.isUpToDate(), "Reparse did not make it to update the "
                + "parser");

        boolean seen = false;
        for (AntlrProxies.ParseTreeElement e : ptp3.allTreeElements()) {
            seen |= "poozleHoozle".equals(e.name());
        }
        assertTrue(seen, "Did not see a renamed tree element after altering "
                + "the grammar");
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
                .addToMimeLookup(synthesizedMimeType, FakeParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter(), fact)
                .addToNamedLookup(org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
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

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
package org.nemesis.antlr.live.parsing;

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.execution.AntlrRunSubscriptions;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.ParserExtractor;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.editor.document.DocumentFactory;

/**
 *
 * @author Tim Boudreau
 */
public class EmbeddedAntlrParsersTest {

    private GeneratedMavenProject gen;
    private ThrowingRunnable shutdown;

    public static final String TEXT_1
            = "{ skiddoo : 23, meaningful : true,\n"
            + "meaning: '42', \n"
            + "thing: 51 }";

    @Test
    public void testSubscriptionsWorkAsExpected() throws Exception {
        assertNotNull(ParserExtractor.class.getResourceAsStream("ParserExtractor.template"),
                "Parser extractor not generated");

        assertTrue(ParserManager.canBeParsed("text/x-g4"), "Antlr parser not "
                + "registered.");

        EmbeddedAntlrParser p = EmbeddedAntlrParsers.forGrammar(gen.file("NestedMaps.g4"));
        AntlrProxies.ParseTreeProxy ptp = p.parse(TEXT_1);
        assertNotNull(ptp);
        assertTrue(p.isUpToDate());
//        assertFalse(ptp.hasErrors());
        assertFalse(ptp.isUnparsed());
        assertEquals(gen.get("NestedMaps.g4"), ptp.grammarPath());
        assertEquals("NestedMaps", ptp.grammarName());

        AntlrProxies.ParseTreeProxy ptp1 = p.parse(TEXT_1);
        assertNotSame(ptp, ptp1);
        assertEquals(ptp, ptp1);
        assertEquals(1, p.rev());

        assertTrue(p.isUpToDate());
        gen.replaceString("NestedMaps.g4", "numberValue", "poozleHoozle");
        assertFalse(p.isUpToDate());

        AntlrProxies.ParseTreeProxy ptp3 = p.parse(TEXT_1);

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

    @BeforeEach
    public void setup() throws IOException, ClassNotFoundException {
        Class.forName(AntlrNbParser.class.getName());
        shutdown = initAntlrTestFixtures(true)
                .addToNamedLookup(AntlrRunSubscriptions.pathForType(EmbeddedParser.class), ProxiesInvocationRunner.class)
                .build();
        gen = ProjectTestHelper.projectBuilder()
                .verboseLogging()
                .writeStockTestGrammar("com.foo")
                .build("foo");
        gen.deletedBy(shutdown);
    }

    @AfterEach
    public void tearDown() throws Exception {
        shutdown.run();
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
                        AntlrFileObjectRelativeResolver.class,
                        NbProjectManager.class
                );
    }

}

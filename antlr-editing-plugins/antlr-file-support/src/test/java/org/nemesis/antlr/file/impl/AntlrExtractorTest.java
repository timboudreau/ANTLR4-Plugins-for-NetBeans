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
package org.nemesis.antlr.file.impl;

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.swing.text.Document;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.nb.AntlrDocumentRelativeResolverImplementation;
import org.nemesis.extraction.nb.AntlrSnapshotRelativeResolver;
import org.nemesis.extraction.nb.CharStreamGrammarSourceFactory;
import org.nemesis.extraction.nb.DocumentGrammarSourceFactory;
import org.nemesis.extraction.nb.FileObjectGrammarSourceImplementationFactory;
import org.nemesis.extraction.nb.RelativeResolverRegistryImpl;
import org.nemesis.extraction.nb.SnapshotGrammarSource;
import org.nemesis.extraction.nb.extractors.NbExtractors;
import org.nemesis.jfs.nb.NbJFSUtilities;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.core.NbLoaderPool;
import org.netbeans.modules.masterfs.watcher.nio2.NioNotifier;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.nb.DataObjectEnvFactory;
import org.netbeans.modules.parsing.nb.EditorMimeTypesImpl;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrExtractorTest {

    private ThrowingRunnable onShutdown;
    private GeneratedMavenProject project;

    @Test
    public void testNeighborsAreResolved() throws Throwable {
        FileObject fo = project.file("NestedMaps.g4");
        GrammarSource<FileObject> fgs = GrammarSource.find(fo, "text/x-g4");
        assertNotNull(fgs);
        GrammarSource<?> lexerFgs = fgs.resolveImport("NMLexer");
        assertNotNull(lexerFgs);
        Optional<FileObject> ofo = lexerFgs.lookup(FileObject.class);
        assertNotNull(ofo);
        assertTrue(ofo.isPresent());
        assertEquals(project.file("NMLexer.g4"), ofo.get());

        Snapshot snap = Source.create(fo).createSnapshot();
        GrammarSource<Snapshot> snapGs = GrammarSource.find(snap, "text/x-g4");
        assertNotNull(snapGs);

        GrammarSource<?> lexerSnap = fgs.resolveImport("NMLexer");
        assertNotNull(lexerSnap);

        DataObject dob = DataObject.find(fo);
        assertNotNull(dob);
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        assertNotNull(ck);

        Document doc = ck.openDocument();
        assertNotNull(doc);

        GrammarSource<Document> docGs = GrammarSource.find(doc, "text/x-g4");
        assertNotNull(docGs);

        GrammarSource<?> lexerDoc = docGs.resolveImport("NMLexer");
        assertNotNull(lexerDoc);
    }

    @Test
    public void testAttirbution() throws Throwable {
        System.out.println("PROJECT: " + project.allFiles());

        FileObject fo = project.file("NestedMaps.g4");
        assertNotNull(fo);
        Source src = Source.create(fo);
        Extraction[] ers = new Extraction[1];
        ParserManager.parse(Collections.singleton(src), new UserTask() {
            @Override
            public void run(ResultIterator resultIterator) throws Exception {
                Parser.Result res = resultIterator.getParserResult();
                assertNotNull(res);
                assertTrue(res instanceof ExtractionParserResult);
                ers[0] = ((ExtractionParserResult) res).extraction();
            }
        });
        Extraction ext = ers[0];
        assertNotNull(ext);
        SemanticRegions<UnknownNameReference<RuleTypes>> unks = ext.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
        System.out.println("UNKNOWN: "
                + unks);

        Set<String> unknownNames = new HashSet<>();
        unks.forEach(reg -> {
            String name = reg.key().name();
            if (!"EOF".equals(name)) {
                unknownNames.add(name);
            }
        });
        System.out.println("UNKNOWN: " + unknownNames);
        assertFalse(unknownNames.isEmpty());

        assertFalse(unks.isEmpty());

        Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> attributions
                = ext.resolveAll(AntlrKeys.RULE_NAME_REFERENCES);

        assertTrue(AntlrRuleReferenceResolver.instanceCreated());
        assertNotNull(attributions);

        System.out.println("ATTRIBUTIONS: " + attributions);
        Set<String> attributedNames = new HashSet<>();
        attributions.attributed().forEach(reg -> {
            String name = reg.key().name();
            attributedNames.add(name);
        });

        System.out.println("ATTRIBUTED NAMES: " + attributedNames);

        assertEquals(unknownNames, attributedNames);
    }

    @BeforeEach
    public void setup() throws Throwable {
        TestFixtures fixtures = new TestFixtures();
        onShutdown = fixtures.verboseGlobalLogging()
                .addToMimeLookup("text/x-g4", AntlrNbParser.AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter())
                .addToNamedLookup(org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
                        FakeG4DataLoader.class,
                        MavenFolderStrategyFactory.class,
                        NbMavenProjectFactory.class,
                        AntlrFileObjectRelativeResolver.class,
                        FileObjectGrammarSourceImplementationFactory.class,
                        DocumentGrammarSourceFactory.class,
                        AntlrDocumentRelativeResolverImplementation.class,
                        SnapshotGrammarSource.Factory.class,
                        RelativeResolverRegistryImpl.class,
                        CharStreamGrammarSourceFactory.class,
                        NbProjectManager.class,
                        NbJFSUtilities.class,
                        NioNotifier.class,
                        NbLoaderPool.class,
                        DataObjectEnvFactory.class,
                        EditorMimeTypesImpl.class,
                        NbExtractors.class
                )
                .addToNamedLookup("antlr/resolvers/text/x-g4", AntlrRuleReferenceResolver.class)
                .addToNamedLookup("antlr-languages/relative-resolvers/text/x-g4",
                        AntlrDocumentRelativeResolverImplementation.class,
                        AntlrFileObjectRelativeResolver.class,
                        AntlrSnapshotRelativeResolver.class
                )
                .addToNamedLookup("antlr/extractors/text/x-g4/org/nemesis/antlr/ANTLRv4Parser/GrammarFileContext",
                        org.nemesis.antlr.file.AntlrKeys_SyntaxTreeNavigator_Registration_ExtractionContributor_extractTree.class,
                        org.nemesis.antlr.file.Antlr_whatevs_grammarSpec_headerAction_actionBlockRuleHighlighting_ExtractionContributor_extract.class,
                        org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.class
                )
                .build();
//        ProjectTestHelper helper = ProjectTestHelper.relativeTo(AntlrExtractorTest.class);
        project = ProjectTestHelper.projectBuilder().writeStockTestGrammarSplit("foo.bar").build("Whatzit")
                .deletedBy(onShutdown);
    }

    @AfterEach
    public void teardown() throws Exception {
        onShutdown.run();
    }

}

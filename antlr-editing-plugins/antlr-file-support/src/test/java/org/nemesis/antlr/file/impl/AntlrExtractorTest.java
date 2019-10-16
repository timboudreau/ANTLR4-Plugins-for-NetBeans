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
package org.nemesis.antlr.file.impl;

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.antlr.v4.runtime.ParserRuleContext;
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
import org.nemesis.antlr.file.G4ImportFinder_IMPORTS;
import org.nemesis.antlr.file.G4Resolver_RULE_NAMES;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.extraction.Extraction;
import org.nemesis.antlr.nbinput.AntlrDocumentRelativeResolverImplementation;
import org.nemesis.antlr.nbinput.AntlrSnapshotRelativeResolver;
import org.nemesis.antlr.nbinput.CharStreamGrammarSourceFactory;
import org.nemesis.antlr.nbinput.DocumentGrammarSourceFactory;
import org.nemesis.antlr.nbinput.FileObjectGrammarSourceImplementationFactory;
import org.nemesis.antlr.nbinput.RelativeResolverRegistryImpl;
import org.nemesis.antlr.nbinput.SnapshotGrammarSource;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extractors;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.jfs.nb.NbJFSUtilities;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.core.NbLoaderPool;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;

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
        if (true) {
            // WTF - we keep initializing the module system
            return;
        }
        System.out.println("PROJECT: " + project.allFiles());

        FileObject fo = project.file("NestedMaps.g4");
        assertNotNull(fo);
        Extraction[] ers = new Extraction[1];
        // if we use parser manager here, we accidentally start
        // trying to load and start the entire IDE.
        /*
        Source src = Source.create(fo);
        ParserManager.parse(Collections.singleton(src), new UserTask() {
            @Override
            public void run(ResultIterator resultIterator) throws Exception {
                Parser.Result res = resultIterator.getParserResult();
                assertNotNull(res);
                assertTrue(res instanceof ExtractionParserResult);
                ers[0] = ((ExtractionParserResult) res).extraction();
            }
        });
         */
        ers[0] = NbAntlrUtils.parseImmediately(fo);
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

//        assertTrue(AntlrRuleReferenceResolver.instanceCreated());
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

    static {
        System.setProperty("java.awt.headless", "true");
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
                        DF.class,
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
//                        NbProjectManager.class,
                        NbJFSUtilities.class,
//                        NioNotifier.class,
                        NbLoaderPool.class,
//                        DataObjectEnvFactory.class,
//                        EditorMimeTypesImpl.class,
//                        NbExtractors.class
                        FakeExtractors.class
                )
                .addToMimeLookup("text/x-g4", new DF())
                .addToNamedLookup("antlr/resolvers/text/x-g4", G4ImportFinder_IMPORTS.class, G4Resolver_RULE_NAMES.class)
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

    public static final class DF implements DocumentFactory {
        private static final Map<FileObject, Document> docForFile
                = new HashMap<>();
        private static final Map<Document, FileObject> fileForDoc
                = new IdentityHashMap<>();

        @Override
        public Document createDocument(String mimeType) {
            BaseDocument doc = new BaseDocument(true, mimeType);
            return doc;
        }

        @Override
        public Document getDocument(FileObject file) {
            Document result = docForFile.get(file);
            if (result == null) {
                result = createDocument(file.getMIMEType());
                docForFile.put(file, result);
                fileForDoc.put(result, file);
                try {
                    result.insertString(0, file.asText(), null);
                } catch (IOException | BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            return result;
        }

        @Override
        public FileObject getFileObject(Document document) {
            return fileForDoc.get(document);
        }
    }

    public static final class FakeExtractors extends Extractors {

        @Override
        public <P extends ParserRuleContext> Extraction extract(String mimeType, GrammarSource<?> src, Class<P> type) {
            // Avoid accidentally initializing the modules system
            try {
                if (src.source() instanceof FileObject) {
                    FileObject fo = (FileObject) src.source();
                    return NbAntlrUtils.parseImmediately(fo);
                } else if (src.source() instanceof Document) {
                    Document doc = (Document) src.source();
                    return NbAntlrUtils.parseImmediately(doc);
                }
                Optional<Document> doc = src.lookup(Document.class);
                if (doc.isPresent()) {
                    return NbAntlrUtils.parseImmediately(doc.get());
                }
                throw new IllegalStateException("No doc for " + src + " with " + src.source());
            } catch (IOException ex) {
                return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
            } catch (Exception ex) {
                return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
            }
        }

    }

}

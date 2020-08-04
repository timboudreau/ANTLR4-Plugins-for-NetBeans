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
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrHierarchy;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.file.AntlrNbParser.AntlrParserFactory;
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
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.Extractors;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.jfs.nb.NbJFSUtilities;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.core.NbLoaderPool;
import org.netbeans.modules.editor.NbEditorDocument;
import org.netbeans.modules.editor.plain.PlainKit;
import org.netbeans.modules.masterfs.watcher.nio2.NioNotifier;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.impl.TaskProcessor;
import org.netbeans.modules.parsing.nb.DataObjectEnvFactory;
import org.netbeans.modules.parsing.nb.EditorMimeTypesImpl;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.netbeans.spi.queries.FileEncodingQueryImplementation;
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
    public void testAttribution() throws Throwable {
        FileObject fo = project.file("NestedMaps.g4");
        assertNotNull(fo);
        DataObject dob = DataObject.find(fo);
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        assertNotNull(ck);

        Document doc = ck.openDocument();
        assertNotNull(doc);
        doc.putProperty("Tools-Options->Editor->Formatting->Preview - Preferences", Preferences.userRoot()); // don't ask.



        Extraction ext = parseImmediately(doc);
        assertNotNull(ext);
        SemanticRegions<UnknownNameReference<RuleTypes>> unks = ext.unknowns(AntlrKeys.RULE_NAME_REFERENCES);

        Set<String> unknownNames = new HashSet<>();
        unks.forEach(reg -> {
            String name = reg.key().name();
            if (!"EOF".equals(name)) {
                unknownNames.add(name);
            }
        });

        if (true) {
            return;
        }
        assertFalse(unknownNames.isEmpty());
        assertFalse(unks.isEmpty());

        // While we're in here, test that the new code for
        // mapping extracted items back to parser rules, which
        // is needed for generic code completion
        List<String> parserRuleIdentifiers = new ArrayList<>();
        ext.namesForRule(ANTLRv4Parser.RULE_parserRuleIdentifier, "", Integer.MAX_VALUE, "", (s, e) -> {
            parserRuleIdentifiers.add(s);
        });
        List<String> lexerRuleIdentifiers = new ArrayList<>();
        ext.namesForRule(ANTLRv4Parser.RULE_tokenRuleIdentifier, "", Integer.MAX_VALUE, "", (s, e) -> {
            lexerRuleIdentifiers.add(s);
        });
//        List<String> fragmentRuleIdentifiers = new ArrayList<>();
//        ext.namesForRule(ANTLRv4Parser.RULE_fragmentRuleIdentifier, "", Integer.MAX_VALUE, (s, e) -> {
//            System.out.println("fragment id '" + s + "' " + e);
//            fragmentRuleIdentifiers.add(s);
//        });
        assertFalse(parserRuleIdentifiers.isEmpty(), "No parser rule names");
//        assertFalse(lexerRuleIdentifiers.isEmpty(), "No lexer rule names");
//        assertFalse(fragmentRuleIdentifiers.isEmpty(), "No fragement rule names");


        Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> attributions
                = ext.resolveAll(AntlrKeys.RULE_NAME_REFERENCES);

//        assertTrue(AntlrRuleReferenceResolver.instanceCreated());
        assertNotNull(attributions);

        Set<String> attributedNames = new HashSet<>();
        attributions.attributed().forEach(reg -> {
            String name = reg.key().name();
            attributedNames.add(name);
        });


        Thread.sleep(2000);

        assertEquals(unknownNames, attributedNames);
    }

    @BeforeEach
    public void setup() throws Throwable {
        Class.forName(TaskProcessor.class.getName());
        Class<?> antlrEditorKit = Class.forName("org.nemesis.antlr.file.file.AntlrEditorKit");
        Field f = antlrEditorKit.getDeclaredField("INSTANCE");
        f.setAccessible(true);
        EditorKit kit = (EditorKit) f.get(null);
        assertNotNull("Editor kit field not populated");
        TestFixtures fixtures = new TestFixtures().avoidStartingModuleSystem();
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
                        NbProjectManager.class,
                        NbJFSUtilities.class,
                        NioNotifier.class,
                        NbLoaderPool.class,
                        DataObjectEnvFactory.class,
                        EditorMimeTypesImpl.class,
                        DF.class,
                        FQ.class,
                        FakeExtractors.class
                )
                .addToMimeLookup("", new DF())
                .addToMimeLookup("text/x-g4", new DF(), new FakePrefs(""),
                        kit,
                        new PlainKit(),
                        AntlrParserFactory.class,
                        AntlrHierarchy.antlrLanguage(), FQ.class)
                .addToMimeLookup("text/plain", new DF(), new FakePrefs(""))
                .addToNamedLookup("antlr/resolvers/text/x-g4", G4ImportFinder_IMPORTS.class, G4Resolver_RULE_NAMES.class)
                .addToNamedLookup("antlr-languages/relative-resolvers/text/x-g4",
                        AntlrDocumentRelativeResolverImplementation.class,
                        AntlrFileObjectRelativeResolver.class,
                        AntlrSnapshotRelativeResolver.class
                )
                .addToNamedLookup("antlr/extractors/text/x-g4/org/nemesis/antlr/ANTLRv4Parser/GrammarFileContext",
                        org.nemesis.antlr.file.AntlrKeys_SyntaxTreeNavigator_Registration_ExtractionContributor_extractTree.class,
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

    public static final class FQ extends FileEncodingQueryImplementation {

        @Override
        public Charset getEncoding(FileObject file) {
            return UTF_8;
        }

    }

    public static final class DF implements DocumentFactory {

        private static final Map<FileObject, Document> docForFile
                = new HashMap<>();
        private static final Map<Document, FileObject> fileForDoc
                = new IdentityHashMap<>();

        @Override
        public Document createDocument(String mimeType) {
            NbEditorDocument doc = new NBD(mimeType);
//            BaseDocument doc = new BaseDocument(true, mimeType);
            return doc;
        }

        static class NBD extends NbEditorDocument {

            public NBD(String mimeType) {
                super(mimeType);
            }

            protected @Override
            Dictionary createDocumentProperties(Dictionary origDocumentProperties) {
                if (true) {
                    return new Hashtable();
                }
                // Overridden to avoid initializing the module system
                return new LazyPropertyMap(origDocumentProperties) {
                    public @Override
                    Object put(Object key, Object value) {
                        Object origValue = super.put(key, value);
                        if (Document.StreamDescriptionProperty.equals(key)) {
                            assert value != null;
                            if (origValue == null) {
                                // XXX: workaround for #137528, touches project settings
                            } else {
                                // this property should only ever be set once. even if it
                                // is set more times it must never be set to a different value
                                assert origValue.equals(value);
                            }
                        }

                        return origValue;
                    }
                };
            }

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
                    return parseImmediately(fo);
//                    return NbAntlrUtils.parseImmediately(fo);
                } else if (src.source() instanceof Document) {
                    Document doc = (Document) src.source();
                    return parseImmediately(doc);
//                    return NbAntlrUtils.parseImmediately(doc);
                }
                Optional<Document> doc = src.lookup(Document.class);
                if (doc.isPresent()) {
//                    return NbAntlrUtils.parseImmediately(doc.get());
                    return parseImmediately(doc.get());
                }
                throw new IllegalStateException("No doc for " + src + " with " + src.source());
            } catch (IOException ex) {
                return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
            } catch (Exception ex) {
                return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
            }
        }
    }

    private static Extraction parseImmediately(Document doc) throws IOException, BadLocationException {
        return parse(GrammarSource.find(doc, ANTLR_MIME_TYPE), doc);
    }

    private static Extraction parseImmediately(FileObject doc) throws IOException, BadLocationException {
        return parse(GrammarSource.find(doc, ANTLR_MIME_TYPE), doc);
    }

    private static Extraction parse(GrammarSource<?> src, Object o) throws IOException {
        CharStream str = src.stream();
        Extractor<? super GrammarFileContext> ext = Extractor.forTypes(ANTLR_MIME_TYPE, GrammarFileContext.class);
//        System.out.println("EXTRACTOR: " + ext);
        ANTLRv4Lexer lex = new ANTLRv4Lexer(str);
        List<Token> tokens = new ArrayList<>();
        Token t;
        int ix = 0;
        while ((t = lex.nextToken()) != null && t.getType() != Token.EOF) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix++);
        }
        lex.reset();
//        lex.removeErrorListeners();
        CommonTokenStream cts = new CommonTokenStream(lex);
        ANTLRv4Parser parser = new ANTLRv4Parser(cts);
        GrammarFileContext ctx = parser.grammarFile();
        Extraction result = ext.extract(ctx, src, tokens);
        return result;
    }

    static class FakePrefs extends AbstractPreferences {

        public FakePrefs(String name) {
            super(null, name);
        }

        @Override
        protected void putSpi(String key, String value) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected String getSpi(String key) {
            return null;
        }

        @Override
        protected void removeSpi(String key) {
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return new String[0];
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return this;
        }

        @Override
        protected void syncSpi() throws BackingStoreException {

        }

        @Override
        protected void flushSpi() throws BackingStoreException {

        }
    }
}

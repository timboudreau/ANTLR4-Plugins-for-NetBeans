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
package org.nemesis.antlr.live.impl;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.thread.OneThreadLatch;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.compilation.AntlrGeneratorAndCompiler;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.grammar.file.resolver.AntlrFileObjectRelativeResolver;
import org.nemesis.antlr.live.FakeFolderLoader;
import org.nemesis.antlr.live.FakeG4DataLoader;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.helpers.maven.MavenFolderStrategyFactory;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.JFSStorageKind;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.api.project.Project;
import org.netbeans.core.NbLoaderPool;
import org.netbeans.modules.editor.impl.DocumentFactoryImpl;
import org.netbeans.modules.masterfs.filebasedfs.FileBasedFileSystem;
import org.netbeans.modules.maven.NbMavenProjectFactory;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrGenerationSubscriptionsImplTest {

    private static ThrowingRunnable shutdown;
    private static GeneratedMavenProject nmProject;
    private static GeneratedMavenProject nmProject2;
    private static GeneratedMavenProject nmProject3;
    private static GeneratedMavenProject nmProject4;

    @Test
    public void testRebuilds() throws Exception {
        AntlrGenerationSubscriptionsForProject.LOG.setLevel(Level.ALL);
        AntlrGenerationSubscriptionsImpl.LOG.setLevel(Level.ALL);
        Project project = nmProject4.project();
        FileObject parserGrammar = nmProject4.file("NestedMaps.g4");
        FileObject lexerGrammar = nmProject4.file("NMLexer.g4");
        FileObject fragmentsGrammar = nmProject4.file("nm.g4");
        assertNotNull(parserGrammar);
        Sub sub = new Sub();

        Parser.Result res1 = ParsingUtils.parse(lexerGrammar, x -> x);
        assertNotNull(res1);
        Parser.Result res2 = ParsingUtils.parse(parserGrammar, x -> x);
        assertNotNull(res2);
        System.out.println("RES 2 " + res2);

        assertTrue(AntlrGenerationSubscriptionsImpl.subscribe(lexerGrammar, sub));
        assertTrue(AntlrGenerationSubscriptionsImpl.subscribe(parserGrammar, sub));
        assertTrue(AntlrGenerationSubscriptionsImpl.subscribe(fragmentsGrammar, sub));

        sub.assertRebuilt(parserGrammar, lexerGrammar);

        replaceText(lexerGrammar, txt -> txt.replace("OpenBrace", "WookieBrace"));
        replaceText(parserGrammar, txt -> txt.replace("OpenBrace", "WookieBrace"));

        Parser.Result res3 = ParsingUtils.parse(lexerGrammar, x -> x);
        assertNotNull(res3);
        Parser.Result res4 = ParsingUtils.parse(parserGrammar, x -> x);
        assertNotNull(res4);
        System.out.println("RES 4 " + res4);

        sub.assertRebuilt(parserGrammar, lexerGrammar);

        replaceText(fragmentsGrammar, txt -> {
            return txt.replace("TRUE", "TRUTHY").replace("FALSE", "FALLACIOUS");
        });

        Parser.Result res5 = ParsingUtils.parse(fragmentsGrammar, x -> x);
        assertNotNull(res5);
        ParsingUtils.parse(lexerGrammar);
        ParsingUtils.parse(parserGrammar);
        sub.assertRebuilt(parserGrammar, lexerGrammar, fragmentsGrammar);
    }

    private void replaceText(FileObject fo, Function<String, String> modifier) throws IOException {
        String txt = fo.asText();
        txt = modifier.apply(txt);
        try (OutputStream out = fo.getOutputStream()) {
            out.write(txt.getBytes(UTF_8));
        }
    }

    static class Sub implements Subscriber {

        OneThreadLatch latch = new OneThreadLatch();
        List<FileObject> filesRebuilt = new CopyOnWriteArrayList<>();

        @Override
        public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
            extraction.source().lookup(FileObject.class, filesRebuilt::add);
            latch.releaseAll();
        }

        void assertRebuilt(FileObject... files) throws InterruptedException {
            for (int i = 0; i < 4; i++) {
                if (filesRebuilt.isEmpty() || !filesRebuilt.containsAll(Arrays.asList(files))) {
                    latch.await(10, TimeUnit.SECONDS);
                }
            }
            List<FileObject> copy = new LinkedList<>(filesRebuilt);
            filesRebuilt.clear();
            assertTrue(copy.containsAll(Arrays.asList(files)), () -> {
                String cnames = Strings.join(", ", copy);
                String enames = Strings.join(", ", Arrays.asList(files));

                List<FileObject> l = new ArrayList<>(Arrays.asList(files));
                l.removeAll(copy);

                return "Rebuilt files " + cnames + " does not contain all of " + enames
                        + " - missing " + CollectionUtils.transform(l, f -> f.getNameExt());
            });
        }
    }

    @Test
    public void testProjectWideMapping() throws Exception {
        Project project = nmProject3.project();
        JFSManager jfses = new JFSManager();
        AtomicBoolean deleted = new AtomicBoolean();
        PerProjectJFSMappingManager prjJfs = new PerProjectJFSMappingManager(project, jfses, () -> deleted.set(true), (oldFile, newFile) -> {
            System.out.println("FILE REPLACED " + oldFile + " -> " + newFile);
        });
        prjJfs.initMappings();
        JFS jfs = jfses.forProject(project);
        assertEquals(setOf("NestedMaps.g4", "NMLexer.g4", "nm.g4"), jfsFileNames(jfs, fo -> {
            assertSame(JFSStorageKind.MASQUERADED_FILE, fo.storageKind());
        }));
        FileObject mainLex = nmProject3.file("NMLexer.g4");

        assertEquals("NMLexer.g4", mainLex.getNameExt(),
                "Project test support gave wrong file");

        JFSFileObject pgram = jfs.get(StandardLocation.SOURCE_PATH,
                UnixPath.get("org/whatever/NestedMaps.g4"));
        assertNotNull(pgram);
        JFSCoordinates.Resolvable ref = pgram.toReference();
        assertNotNull(ref);
        assertSame(ref.resolveOriginal(), pgram);

        DataObject mainLexerDataObject = DataObject.find(mainLex);
        mainLexerDataObject.rename("Fnord");

        assertEquals(setOf("NestedMaps.g4", "Fnord.g4", "nm.g4"), jfsFileNames(jfs));

        FileObject flex = nmProject3.file("nm.g4");
        assertEquals("nm.g4", flex.getNameExt());
        DataObject fdob = DataObject.find(flex);
        assertEquals("nm", fdob.getName());

        fdob.rename("wug");
        assertEquals(setOf("NestedMaps.g4", "Fnord.g4", "wug.g4"), jfsFileNames(jfs));

        StyledDocument doc = fdob.getLookup().lookup(EditorCookie.class).openDocument();
        assertNotNull(doc, "Document should have been created");

        JFSCoordinates coords = prjJfs.mappings.forFileObject(fdob.getPrimaryFile());
        assertNotNull(coords);
        assertEquals(StandardLocation.SOURCE_PATH, coords.location());

        for (int i = 0; i < 50; i++) {
            JFSFileObject fo = coords.resolve(jfs);
            assertNotNull(fo, "Could not resolve " + coords);
            if (fo.storageKind() == JFSStorageKind.MASQUERADED_DOCUMENT) {
                break;
            }
            Thread.sleep(30);
        }

        assertEquals(setOf("NestedMaps.g4", "Fnord.g4", "wug.g4"), jfsFileNames(jfs, fo -> {
            if (fo.getName().endsWith("wug.g4")) {
                assertSame(JFSStorageKind.MASQUERADED_DOCUMENT, fo.storageKind());
            }
        }));

        mainLexerDataObject.delete();
        assertEquals(setOf("NestedMaps.g4", "wug.g4"), jfsFileNames(jfs));

        project.getProjectDirectory().delete();

        assertEquals(Collections.emptySet(), jfsFileNames(jfs));
        assertTrue(jfs.isReallyClosed());
        assertNull(ref.resolveOriginal());

        assertTrue(deleted.get(), "Project deletion hook should have been "
                + "called");
    }

    Set<String> jfsFileNames(JFS jfs) {
        return jfsFileNames(jfs, fo -> {
        });
    }

    Set<String> jfsFileNames(JFS jfs, Consumer<JFSFileObject> c) {
        Set<String> names = new HashSet<>();
        jfs.list(StandardLocation.SOURCE_PATH, (loc, fl) -> {
//            System.out.println("JFS: " + fl.getName());
            names.add(UnixPath.get(fl.getName()).getFileName().toString());
        });
        return names;
    }

    @Test
    public void testJfsMapping() throws Exception {
        Project project = nmProject2.project();
        assertNotNull(project);
        JFS jfs = JFS.builder().build();
        Supplier<JFS> jfss = () -> jfs;

        listeningToGrammarFile("NestedMaps.g4", nmProject2, (FileObject file, FileEvents evts,
                EditorCookieListener lis) -> {

            MappingL l = new MappingL();
            UnixPath mappingPath = UnixPath.get("com/foo/NestedMaps.g4");
            JFSCoordinates coords = JFSCoordinates.create(StandardLocation.SOURCE_PATH, mappingPath);
            OneFileMapping m = new OneFileMapping(file, coords, l::onPrimaryFileChange, l::onFileRename, jfss, Folders.ANTLR_GRAMMAR_SOURCES);
            assertEquals(JFSMappingMode.FILE, m.mappingMode());

            JFSFileObject fo = coords.resolve(jfs);
            assertNotNull(fo);
            assertEquals(mappingPath.toString(), fo.getName());
            assertSame(JFSStorageKind.MASQUERADED_FILE, fo.storageKind());

            assertNull(lis.documentRef);
            assertNull(m.cookieListener.documentRef);
            EditorCookie.Observable obs = lis.obs.get();
            assertNotNull(obs);

            Document doc = obs.openDocument();
            assertNotNull(doc);
            evts.assertDocReplaced();
            for (int i = 0; i < 5; i++) {
                if (m.mappingMode() != JFSMappingMode.DOCUMENT) {
                    System.out.println("  not doc yet - sleep");
                    Thread.sleep(50);
                } else {
                    break;
                }
            }
            assertEquals(JFSMappingMode.DOCUMENT, m.mappingMode());

            fo = coords.resolve(jfs);
            assertNotNull(fo);
            assertEquals(mappingPath.toString(), fo.getName());
            assertSame(JFSStorageKind.MASQUERADED_DOCUMENT, fo.storageKind());

            DataObject dob = DataObject.find(file);
            dob.rename("Wookie");
            assertEquals("Wookie.g4", dob.getPrimaryFile().getNameExt());
            l.assertFileRenamed("Wookie.g4");

            assertEquals(JFSMappingMode.DOCUMENT, m.mappingMode());
            dob.delete();
            assertEquals(JFSMappingMode.UNMAPPED, m.mappingMode());

            fo = coords.resolve(jfs);
            assertNull(fo, "JFS File should be deleted when the real file is");
        });
    }

    static class MappingL {

        private FileObject newPrimaryFile;
        boolean primaryFileChanged = false;
        boolean primaryFileNulled = false;
        String newName;

        void assertPrimaryFileChanged() {
            boolean res = primaryFileChanged;
            primaryFileChanged = false;
            assertTrue(res, "Primary file not changed");
        }

        void assertPrimaryFileNulled() {
            assertPrimaryFileChanged();
            boolean res = primaryFileNulled;
            primaryFileNulled = false;
            assertTrue(res, "Primary file not nulled");
        }

        void onPrimaryFileChange(FileObject old, FileObject nue, OneFileMapping mapping) {
            primaryFileChanged = true;

        }

        void onFileRename(FileObject fo, String oldName, String newName, OneFileMapping mapping) {
            this.newName = newName;
        }

        void assertFileRenamed(String expected) {
            String nm = newName;
            newName = null;
            assertEquals(expected, nm);
        }
    }

    @Test
    public void testListening() throws Exception {
        Project project = nmProject.project();
        assertNotNull(project);

        listeningToGrammarFile("NestedMaps.g4", nmProject, (FileObject file, FileEvents evts,
                EditorCookieListener lis) -> {
            FileObject parentDir = file.getParent();
            assertTrue(lis.listeningToEditorCookie, "Did not start listening");
            assertNull(lis.document(), "Document already opened");
            EditorCookie.Observable ec = lis.obs.get();
            assertNotNull(ec);
            Document doc = ec.openDocument();
            evts.assertDocReplaced();
            assertNotNull(lis.documentRef);
            assertNotNull(lis.documentRef.get());
            assertSame(doc, lis.documentRef.get());
            assertNotNull(doc, "No document");
            DataObject dob = DataObject.find(file);
            dob.rename("Nestification");
            evts.assertFileRenamed();
            FileObject fo = dob.getPrimaryFile();
            assertEquals("Nestification.g4", fo.getNameExt());
            assertEquals(parentDir, fo.getParent());
            FileObject newFolder = parentDir.createFolder("stuff");
            assertNotNull(newFolder);
            assertTrue(newFolder.isFolder());
            DataFolder df = DataFolder.findFolder(newFolder);
            dob.move(df);
            assertEquals("stuff", dob.getPrimaryFile().getParent().getNameExt());
            evts.assertFileNotNulled();
            evts.assertFileReplaced();
            dob.delete();
            Thread.sleep(100);
            evts.assertDocNulled();
            evts.assertFileNulled();
        });
    }

    private static void listeningToGrammarFile(String name, GeneratedMavenProject prj,
            ThrowingTriConsumer<FileObject, FileEvents, EditorCookieListener> consumer) throws Exception {
        FileObject theFile = prj.file(name);
        assertNotNull(theFile);
        FileEvents fileFileEvents = new FileEvents();
        EditorCookieListener fileListener = fileFileEvents.listener(theFile);
        assertNotNull(fileFileEvents.lookupResult);
        consumer.accept(theFile, fileFileEvents, fileListener);
    }

    static final class FileEvents {

        Lookup.Result<EditorCookie.Observable> lookupResult;
        private final OneThreadLatch docChangeWait = new OneThreadLatch();

        EditorCookieListener listener(FileObject file) throws DataObjectNotFoundException {
            EditorCookieListener result
                    = new EditorCookieListener(this::fileReplaced, this::documentReplaced, this::fileRenamed);
            lookupResult = result.attachTo(file);
            return result;
        }

        boolean docReplaced;
        boolean fileRenamed;
        boolean fileReplaced;
        boolean docNulled;
        boolean fileNulled;

        void assertDocReplaced() throws InterruptedException {
            if (!docReplaced) {
                docChangeWait.await(10, SECONDS);
            }
            boolean old = docReplaced;
            docReplaced = false;
            assertTrue(old, "document not replaced");
        }

        void assertDocNulled() throws InterruptedException {
            if (!docReplaced) {
                docChangeWait.await(10, SECONDS);
            }
            boolean old = docReplaced;
            boolean oldNulled = docNulled;
            docNulled = false;
            docReplaced = false;
            assertTrue(old, "document not replaced");
            assertTrue(oldNulled, "new document was not null");
        }

        void assertFileReplaced() {
            boolean old = fileReplaced;
            fileReplaced = false;
            assertTrue(old, "document not replaced");
        }

        void assertFileNulled() {
            boolean old = fileReplaced;
            boolean oldNulled = fileNulled;
            fileReplaced = false;
            fileNulled = false;
            assertTrue(old, "file not replaced");
            assertTrue(oldNulled, "file not nulled");
        }

        void assertFileNotNulled() {
            boolean old = fileNulled;
            fileNulled = false;
            assertFalse(old, "File was nulled unexpectedy");
        }

        void assertFileRenamed() {
            boolean old = fileRenamed;
            fileRenamed = false;
            assertTrue(old, "document not replaced");
        }

        void fileReplaced(FileObject old, FileObject nue) {
            fileReplaced = true;
            fileNulled = nue == null;
        }

        void documentReplaced(Document old, Document nue) {
            docReplaced = true;
            docNulled = nue == null;
            docChangeWait.releaseOne();
        }

        void fileRenamed(FileObject fo, String oldName, String newName) {
            fileRenamed = true;
        }
    }

    @BeforeAll
    public static void setup() throws IOException {
        shutdown = initAntlrTestFixtures(false).build();
        ProjectTestHelper helper = ProjectTestHelper.relativeTo(AntlrGenerationSubscriptionsImplTest.class);
        nmProject = ProjectTestHelper.projectBuilder().writeStockTestGrammarSplit("org.whatever", true)
                .build("NM").deletedBy(shutdown);
        nmProject2 = ProjectTestHelper.projectBuilder().writeStockTestGrammarSplit("org.whatever", true)
                .build("NM2").deletedBy(shutdown);
        nmProject3 = ProjectTestHelper.projectBuilder().writeStockTestGrammarSplit("org.whatever", true)
                .build("NM3").deletedBy(shutdown);
        nmProject4 = ProjectTestHelper.projectBuilder().writeStockTestGrammarSplit("org.whatever", true)
                .build("NM3").deletedBy(shutdown);
    }

    @AfterAll
    public static void tearDown() throws Throwable {
        if (shutdown != null) {
            shutdown.run();
        }
    }

    public static TestFixtures initAntlrTestFixtures(boolean verbose) {
        TestFixtures fixtures = new TestFixtures();
        if (verbose) {
            fixtures.verboseGlobalLogging(AntlrGenerationSubscriptionsImpl.class,
                    AntlrGenerator.class,
                    "org.nemesis.antlr.memory.tool.ToolContext",
                    "org.nemesis.antlr.project.AntlrConfigurationCache",
                    "org.nemesis.antlr.project.impl.FoldersHelperTrampoline",
                    "org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation",
                    "org.nemesis.antlr.project.impl.InferredConfig",
                    AntlrGeneratorAndCompiler.class);
        }
        DocumentFactory fact = new DocumentFactoryImpl();
        fixtures.addToMimeLookup("", fact)
                .addToMimeLookup("text/x-g4", AntlrNbParser.AntlrParserFactory.class)
                .addToMimeLookup("text/x-g4", AntlrNbParser.createErrorHighlighter(), fact)
                .addToNamedLookup(org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder.REGISTRATION_PATH,
                        new org.nemesis.antlr.file.impl.AntlrExtractor_ExtractionContributor_populateBuilder())
                .addToDefaultLookup(
                        FakeG4DataLoader.class,
                        FakeFolderLoader.class,
                        FileBasedFileSystem.Factory.class,
                        MavenFolderStrategyFactory.class,
                        NbMavenProjectFactory.class,
                        AntlrFileObjectRelativeResolver.class,
                        NbLoaderPool.class
                )
                .avoidStartingModuleSystem();
        return fixtures;
    }
}

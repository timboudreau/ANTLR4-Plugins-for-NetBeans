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
package org.nemesis.antlr.nbinput;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.strings.RandomStrings;
import java.io.File;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Random;
import javax.swing.text.Document;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.extraction.nb.extractors.NbExtractors;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.nemesis.test.fixtures.support.TestFixtures;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.projectapi.nb.NbProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
public class RelativeResolverRegistryImplTest {

    private static Path tmp;
    private static Path firstFile;
    private static Path secondFile;
    private static ThrowingRunnable onShutdown;
    private static final String GOOB_MIME = "text/x-goob";
    private static final String FIRST_FILE = "first";
    private static final String SECOND_FILE = "second";

//    @Test
    public void testSomeMethod() throws Exception {
        FileObject fo = FileObjectToDocumentAdapter.pathToFileObject(firstFile);
        assertNotNull(fo, "No file object found");
        assertEquals(GOOB_MIME, fo.getMIMEType(), "Mime resolver not used");
        GrammarSource<FileObject> fgs = GrammarSource.find(fo, GOOB_MIME);
        assertNotNull(fgs, "No grammar source returned");
        assertSame(fo, fgs.source(), "Got a different fileobject");
        assertTrue(fgs.lookup(Path.class).isPresent(), "No path");
        assertEquals(firstFile, fgs.lookup(Path.class).get(), "Wrong file - should be " + firstFile);

        assertTrue(fgs.lookup(DataObject.class).isPresent());

        GrammarSource<?> neighbor = fgs.resolveImport("second");

        assertNotNull(goobResolver, "Resolver not created");
        Optional<Path> lastResolved = goobResolver.assertCalled();
        assertTrue(lastResolved.isPresent(), "Resolver did not resolve");

        assertNotNull(neighbor, secondFile::toString);
        System.out.println("fo second neighbor src " + neighbor.source() + " " + neighbor.source().getClass().getName());
        assertEquals("second", neighbor.name());
        assertTrue(neighbor.lookup(Path.class).isPresent(), secondFile::toString);
        assertEquals(secondFile, neighbor.lookup(Path.class).get());
    }

//    @Test
    public void testDocumentsAreResolved() throws IOException {
        FileObject fo = FileObjectToDocumentAdapter.pathToFileObject(firstFile);
        Document doc = FileObjectToDocumentAdapter.toDocument(fo);
        assertNotNull(doc);
        doc.putProperty("mimeType", GOOB_MIME);
        assertEquals(GOOB_MIME, NbEditorUtilities.getMimeType(doc), "Wrong mime type");

        GrammarSource<Document> dgs = GrammarSource.find(doc, GOOB_MIME);
        assertNotNull(dgs);

        assertNotNull(dgs.lookup(Path.class));
        assertTrue(dgs.lookup(Path.class).isPresent());
        assertEquals(firstFile, dgs.lookup(Path.class).get());
        assertNotNull(dgs.lookup(File.class));
        assertTrue(dgs.lookup(File.class).isPresent());
        assertEquals(firstFile.toFile(), dgs.lookup(File.class).get());

        GrammarSource<?> neighbor = dgs.resolveImport("second");

        assertNotNull(goobResolver, "Resolver not created");
        Optional<Path> lastResolved = goobResolver.assertCalled();
        assertTrue(lastResolved.isPresent(), "Resolver did not resolve");

        assertNotNull(neighbor, secondFile::toString);
        System.out.println("doc neighbor src " + neighbor.source() + " " + neighbor.source().getClass().getName());
        assertEquals("second", neighbor.name());
        assertTrue(neighbor.lookup(Path.class).isPresent(), secondFile::toString);
        assertEquals(secondFile, neighbor.lookup(Path.class).get());
    }

    @Test
    public void testSnapshotsAreResolved() throws IOException {
        FileObject fo = FileObjectToDocumentAdapter.pathToFileObject(firstFile);
        Document doc = FileObjectToDocumentAdapter.toDocument(fo);
        doc.putProperty("mimeType", GOOB_MIME);
        assertEquals(GOOB_MIME, NbEditorUtilities.getMimeType(doc), "Wrong mime type");
        Snapshot snap = Source.create(doc).createSnapshot();
        assertNotNull(doc);

        GrammarSource<Snapshot> dgs = GrammarSource.find(snap, GOOB_MIME);
        assertNotNull(dgs);

        assertNotNull(dgs.lookup(Path.class));
        assertTrue(dgs.lookup(Path.class).isPresent());
        assertEquals(firstFile, dgs.lookup(Path.class).get());
        assertNotNull(dgs.lookup(File.class));
        assertTrue(dgs.lookup(File.class).isPresent());
        assertEquals(firstFile.toFile(), dgs.lookup(File.class).get());

        GrammarSource<?> neighbor = dgs.resolveImport("second");
        
        assertNotNull(goobResolver, "Resolver not created");
        Optional<Path> lastResolved = goobResolver.assertCalled();
        assertTrue(lastResolved.isPresent(), "Resolver did not resolve");

        assertNotNull(neighbor, secondFile::toString);
        System.out.println("snap neighbor src " + neighbor.source() + " " + neighbor.source().getClass().getName());
        assertEquals("second", neighbor.name());
        assertTrue(neighbor.lookup(Path.class).isPresent(), secondFile::toString);
        assertEquals(secondFile, neighbor.lookup(Path.class).get());
    }

    @Test
    public void testSOurcesAreResolved() throws IOException {
        FileObject fo = FileObjectToDocumentAdapter.pathToFileObject(firstFile);
        Document doc = FileObjectToDocumentAdapter.toDocument(fo);
        doc.putProperty("mimeType", GOOB_MIME);
        assertEquals(GOOB_MIME, NbEditorUtilities.getMimeType(doc), "Wrong mime type");
        Source snap = Source.create(doc);
        assertNotNull(doc);

        GrammarSource<Source> dgs = GrammarSource.find(snap, GOOB_MIME);
        assertNotNull(dgs);

        assertNotNull(dgs.lookup(Path.class));
        assertTrue(dgs.lookup(Path.class).isPresent());
        assertEquals(firstFile, dgs.lookup(Path.class).get());
        assertNotNull(dgs.lookup(File.class));
        assertTrue(dgs.lookup(File.class).isPresent());
        assertEquals(firstFile.toFile(), dgs.lookup(File.class).get());

        GrammarSource<?> neighbor = dgs.resolveImport("second");

        assertNotNull(goobResolver, "Resolver not created");
        Optional<Path> lastResolved = goobResolver.assertCalled();
        assertTrue(lastResolved.isPresent(), "Resolver did not resolve");

        assertNotNull(neighbor, secondFile::toString);
        assertEquals("second", neighbor.name());
        System.out.println("second neighbor src " + neighbor.source() + " " + neighbor.source().getClass().getName());
        assertEquals("second", neighbor.name());
        assertTrue(neighbor.lookup(Path.class).isPresent(), secondFile::toString);
        assertEquals(secondFile, neighbor.lookup(Path.class).get());
    }


    @BeforeAll
    public static void setup() throws IOException {
        tmp = FileUtils.newTempDir("RelativeResolverRegistryImplTest");
        Random rnd = new Random(129912090L);
        RandomStrings s = new RandomStrings(rnd);

        firstFile = randomWordsFile(tmp, FIRST_FILE, rnd, s);
        secondFile = randomWordsFile(tmp, SECOND_FILE, rnd, s);

        System.out.println("created \n" + firstFile + "\n" + secondFile);

        TestFixtures fix = new TestFixtures();
        onShutdown = fix.addToDefaultLookup(GoobMimeResolver.class,
                RelativeResolverRegistryImpl.class,
                FileObjectGrammarSourceImplementationFactory.class,
                DocumentGrammarSourceFactory.class,
                SnapshotGrammarSource.Factory.class,
                CharStreamGrammarSourceFactory.class,
                NbExtractors.class,
                NbProjectManager.class,
                FileObjectToDocumentAdapter.class,
                FileObjectToDocumentAdapter.DataObjectToFileObjectAdapter.class,
                FileObjectToDocumentAdapter.DocumentToFileObjectAdapter.class,
                FileObjectToDocumentAdapter.PathToFileObjectAdapter.class,
                FileObjectToDocumentAdapter.PathToFileAdapter.class,
                FileObjectToDocumentAdapter.SnapshotToDocumentAdapter.class,
                FileObjectToDocumentAdapter.SnapshotToFileObjectAdapter.class,
                FileObjectToDocumentAdapter.SourceToDocumentAdapter.class
        )
                .addToNamedLookup("antlr-languages/relative-resolvers/" + GOOB_MIME, GoobRelativeResolver.class)
                .addToNamedLookup("antlr-languages/relative-resolvers/text/x-g4", AntlrDocumentRelativeResolverImplementation.class)
                .addToNamedLookup("antlr-languages/relative-resolvers/text/x-g4", AntlrSnapshotRelativeResolver.class)
                .build();
    }

    private static Path randomWordsFile(Path dir, String name, Random rnd, RandomStrings s) throws IOException {
        String words = randomWords(50, s, rnd);
        Path file = dir.resolve(name + ".goob");
        Files.write(file, words.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return file;
    }

    private static String randomWords(int ct, RandomStrings s, Random rnd) {
        StringBuilder sb = new StringBuilder(ct * 7);
        for (int i = 0; i < ct; i++) {
            if (sb.length() > 0) {
                if (i % 12 == 0) {
                    sb.append('\n');
                } else {
                    sb.append(' ');
                }
            }
            String word = s.get(2 + rnd.nextInt(5));
            sb.append(word);
        }
        return sb.toString();
    }

    @AfterAll
    public static void teardown() throws Exception {
        onShutdown.run();
        FileUtils.deltree(tmp);
    }

    static GoobRelativeResolver goobResolver;

    public static final class GoobRelativeResolver extends RelativeResolverImplementation<Path> {

        Optional<Path> lastResult;

        public GoobRelativeResolver() {
            super(Path.class);
            goobResolver = this;
            System.out.println("Create a goob resolver");
        }

        public Optional<Path> assertCalled() {
            Optional<Path> result = lastResult;
            lastResult = null;
            assertNotNull(result);
            return result;
        }

        @Override
        public Optional<Path> resolve(Path relativeTo, String name) {
            Path parent = relativeTo.getParent();
            Path sibling = parent.resolve(name + ".goob");
            boolean exists = Files.exists(sibling);
            System.out.println("GOOB try to resolve " + sibling + " relative to " + relativeTo
                    + " exists? " + exists);
            Optional<Path> result = exists ? Optional.of(sibling) : Optional.empty();
            System.out.println("   returning " + result);
            lastResult = result;
            return result;
        }
    }

    public static final class GoobMimeResolver extends MIMEResolver {

        @Override
        public String findMIMEType(FileObject fo) {
            if ("goob".equals(fo.getExt())) {
                return GOOB_MIME;
            }
            return null;
        }
    }
}

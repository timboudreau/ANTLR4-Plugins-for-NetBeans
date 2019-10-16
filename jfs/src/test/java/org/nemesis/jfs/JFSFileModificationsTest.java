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
package org.nemesis.jfs;

import com.mastfrog.function.throwing.io.IOConsumer;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.jfs.JFSFileModifications.FileChanges;
import org.nemesis.jfs.result.UpToDateness;

/**
 *
 * @author Tim Boudreau
 */
public class JFSFileModificationsTest {

    private static final UnixPath PATH_1 = UnixPath.get("com/foo/Whatever.txt");
    private static final UnixPath PATH_2 = UnixPath.get("com/foo/MoreStuff.txt");
    private static final UnixPath PATH_3 = UnixPath.get("com/foo/somewhere/else/Stuff.txt");
    private static final UnixPath PATH_4 = UnixPath.get("RootStuff.txt");
    private static final UnixPath MAPPED_PATH = UnixPath.get("foo/bar/SomethingElse.txt");
    private static final UnixPath DOC_PATH = UnixPath.get("foo/bar/Whee.txt");
    private static final String FILE_TEXT = "This is a bunch of text here.";
    private static final String DOC_TEXT = "DocText goes here.\n  How about that?\n";

    private static final UnixPath[] VIRTUAL_PATHS = new UnixPath[]{PATH_1, PATH_2, PATH_3, PATH_4};
    private static final String[] VIRTUAL_INITIAL_TEXT = new String[]{
        "This is some text",
        "Some more text and why we would want to write it.  Why would we, anyway?",
        "This is a bunch of stuff here.",
        "It is still a bunch of stuff."
    };

    private static final UnixPath[] ALL_PATHS = new UnixPath[]{PATH_1, PATH_2, PATH_3, PATH_4, MAPPED_PATH, DOC_PATH};
    private static final String[] ALL_TEXT = new String[VIRTUAL_INITIAL_TEXT.length + 2];

    static {
        System.arraycopy(VIRTUAL_INITIAL_TEXT, 0, ALL_TEXT, 0, VIRTUAL_INITIAL_TEXT.length);
        ALL_TEXT[ALL_TEXT.length - 2] = FILE_TEXT;
        ALL_TEXT[ALL_TEXT.length - 1] = DOC_TEXT;
    }

    private JFS jfs;
    private Path tempFile;
    private Document doc;

    @Test
    public void testRewritesThatDoNotChangeContentResultInUnmodifiedStatus() throws IOException, BadLocationException {
        JFSFileModifications mods = jfs.status(StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT);
        for (int i=0; i < VIRTUAL_PATHS.length; i++) {
            JFSFileObject fo = jfs.get(StandardLocation.SOURCE_PATH, VIRTUAL_PATHS[i]);
            assertNotNull(fo);
            CharSequence seq = fo.getCharContent(true);
            assertNotNull(seq);
            fo.setTextContent(seq.toString());
            FileChanges ch = mods.changes();
            assertNotNull(ch);
            assertTrue("Modification detected after change of " + fo.getName() + " in " + ch, ch.isUpToDate());
        }
        doc.remove(0, doc.getLength());
        assertFalse(mods.changes().isUpToDate());
        doc.insertString(0, DOC_TEXT, null);
        assertTrue(mods.changes().isUpToDate());
    }

    @Test
    public void testTouchOneVirtualFileAndReset() throws IOException, InterruptedException {
        for (int i = 0; i < ALL_PATHS.length; i++) {
            StandardLocation loc = i < VIRTUAL_PATHS.length ? StandardLocation.SOURCE_PATH
                    : StandardLocation.SOURCE_OUTPUT;
            testTextChange("New text " + i, ALL_PATHS[i], loc);
        }
    }

    @Test
    public void testAddsDetected() throws Throwable {
        JFSFileModifications state = jfs.status(StandardLocation.SOURCE_PATH);
        JFSFileObject fo = jfs.create(UnixPath.get("foo/bar/goo/baz.txt"), StandardLocation.SOURCE_PATH, "Hello world");
        FileChanges changes = state.changes();
        assertFalse(changes.added().isEmpty());
        assertEquals(UnixPath.get("foo/bar/goo/baz.txt"), changes.added().iterator().next());
        state.refresh();
        fo.delete();
        changes = state.changes();
        assertFalse(changes.deleted().isEmpty());
        assertTrue(changes.modified().isEmpty());
        assertTrue(changes.added().isEmpty());
        assertEquals(UnixPath.get("foo/bar/goo/baz.txt"), changes.deleted().iterator().next());
    }

    private void testTextChange(String newText, UnixPath toChange, Location loc) throws IOException, InterruptedException {
        testOneChange(fo -> {
            assertEquals(toChange, UnixPath.get(fo.getName()));
            switch (fo.storageKind()) {
                case DISCARDED:
                    throw new AssertionError("Unexpected kind");
                case MASQUERADED_DOCUMENT: {
                    try {
                        doc.insertString(0, newText, null);
                    } catch (BadLocationException ex) {
                        throw new AssertionError(ex);
                    }
                }
                break;
                case MASQUERADED_FILE:
                    Files.write(tempFile, newText.getBytes(UTF_8), StandardOpenOption.WRITE);
                    break;
                default:
                    fo.setBytes(newText.getBytes(UTF_8), System.currentTimeMillis());
                    break;
            }
        }, toChange, loc);
    }

    private void testOneChange(IOConsumer<JFSFileObject> changer, UnixPath toChange, Location loc) throws IOException, InterruptedException {
        JFSFileModifications allState = jfs.status(StandardLocation.SOURCE_OUTPUT, StandardLocation.SOURCE_PATH);
        JFSFileModifications outState = jfs.status(StandardLocation.SOURCE_OUTPUT);
        JFSFileModifications srcState = jfs.status(StandardLocation.SOURCE_PATH);
        assertTrue(allState.changes().isUpToDate());
        assertTrue(outState.changes().isUpToDate());
        assertTrue(srcState.changes().isUpToDate());
        JFSFileObject fo = jfs.get(loc, toChange);
        assertNotNull("No file for " + toChange + " in " + loc, fo);
        long oldLastModified = fo.getLastModified();
        // Ensure we're not on an absurdly fast machine where setup runs the same millisecond as now,
        // and compensate for > 10ms clock resolution
        Thread.sleep(10);
        changer.accept(fo);

        assertNotEquals("Update to " + toChange + " did not change the last modified time",
                oldLastModified, fo.getLastModified());

        FileChanges changes = allState.changes();
        assertNotNull(changes);
        assertFalse(changes.isUpToDate());
        assertTrue(changes.modified().contains(toChange));
        assertEquals(1, changes.modified().size());
        assertTrue(changes.added().isEmpty());
        assertTrue(changes.deleted().isEmpty());
        assertTrue(changes.status().mayRequireRebuild());

        FileChanges srcChanges = srcState.changes();
        FileChanges outChanges = outState.changes();

        FileChanges expectedChanged = loc == StandardLocation.SOURCE_PATH ? srcChanges : outChanges;
        FileChanges expectedUnchanged = loc == StandardLocation.SOURCE_PATH ? outChanges : srcChanges;

        assertNotNull(expectedChanged);
        assertFalse(expectedChanged.isUpToDate());
        assertTrue(expectedChanged.modified().contains(toChange));
        assertEquals(1, expectedChanged.modified().size());
        assertTrue(expectedChanged.added().isEmpty());
        assertTrue(expectedChanged.deleted().isEmpty());
        assertTrue(expectedChanged.status().mayRequireRebuild());

        assertTrue(expectedUnchanged.isUpToDate());
        assertTrue(expectedUnchanged.modified().isEmpty());

        assertSame(UpToDateness.CURRENT, expectedUnchanged.status());

        allState.refresh();
        srcState.refresh();
        outState.refresh();

        assertTrue(allState.changes().isUpToDate());
        assertTrue(srcState.changes().isUpToDate());
        assertTrue(outState.changes().isUpToDate());
    }

    @Test
    public void sanityCheck() throws IOException {
        JFSFileModifications allState = jfs.status(StandardLocation.SOURCE_OUTPUT, StandardLocation.SOURCE_PATH);
        JFSFileModifications outState = jfs.status(StandardLocation.SOURCE_OUTPUT);
        JFSFileModifications srcState = jfs.status(StandardLocation.SOURCE_PATH);
        assertNotNull(allState);
        assertNotNull(outState);
        assertNotNull(srcState);
        assertTrue(allState.initialState().timestamps.containsKey(StandardLocation.SOURCE_OUTPUT));
        assertTrue(allState.initialState().timestamps.containsKey(StandardLocation.SOURCE_PATH));
        assertTrue(outState.initialState().timestamps.containsKey(StandardLocation.SOURCE_OUTPUT));
        assertFalse(outState.initialState().timestamps.containsKey(StandardLocation.SOURCE_PATH));
        assertFalse(srcState.initialState().timestamps.containsKey(StandardLocation.SOURCE_OUTPUT));
        assertTrue(srcState.initialState().timestamps.containsKey(StandardLocation.SOURCE_PATH));
        for (int i = 0; i < ALL_PATHS.length; i++) {
            String text = ALL_TEXT[i];
            UnixPath path = ALL_PATHS[i];
            StandardLocation loc = i < VIRTUAL_PATHS.length ? StandardLocation.SOURCE_PATH
                    : StandardLocation.SOURCE_OUTPUT;

            JFSFileObject fo = jfs.get(loc, path);
            assertNotNull("In " + loc + " no " + path, fo);
            String found = fo.getCharContent(true).toString();
            assertEquals("Wrong text for " + fo + " in " + loc, text, found);
            if (MAPPED_PATH.equals(path)) {
                assertEquals(JFSStorageKind.MASQUERADED_FILE, fo.storageKind());
                assertEquals("Last modified time for masqueraded file does not match "
                        + "actual file time", Files.getLastModifiedTime(tempFile).toMillis(), fo.getLastModified());
            } else if (DOC_PATH.equals(path)) {
                assertEquals(JFSStorageKind.MASQUERADED_DOCUMENT, fo.storageKind());
                assertTrue(fo.getLastModified() < System.currentTimeMillis());
            }
        }
        FileChanges allCh = allState.changes();
        FileChanges outCh = outState.changes();
        FileChanges srcCh = srcState.changes();
        assertTrue(allCh.isUpToDate());
        assertTrue(outCh.isUpToDate());
        assertTrue(srcCh.isUpToDate());
    }

    @Before
    public void before() throws IOException, BadLocationException {
        doc = new DefaultStyledDocument();
        doc.insertString(0, DOC_TEXT, null);
        tempFile = FileUtils.newTempFile();
        Files.write(tempFile, FILE_TEXT.getBytes(UTF_8),
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE);

        jfs = JFS.builder().withCharset(UTF_8).build();
        for (int i = 0; i < VIRTUAL_PATHS.length; i++) {
            String txt = VIRTUAL_INITIAL_TEXT[i];
            jfs.create(VIRTUAL_PATHS[i], StandardLocation.SOURCE_PATH, txt);
        }
        jfs.masquerade(tempFile, StandardLocation.SOURCE_OUTPUT, MAPPED_PATH);
        jfs.masquerade(doc, StandardLocation.SOURCE_OUTPUT, DOC_PATH);
    }

    @After
    public void after() throws IOException {
        if (jfs != null) {
            jfs.close();
        }
    }
}

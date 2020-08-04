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

import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.jfs.nio.BlockStorageKind;
import org.netbeans.ProxyURLStreamHandlerFactory;
import org.netbeans.junit.MockServices;

/**
 *
 * @author Tim Boudreau
 */
public class JFSFileReferenceTest {

    private static final String SP1 = "source/path/one.txt";
    private static final String SP2 = "source/path/two.class";
    private static final String SP3 = "path/to/another/file.txt";
    private static final String SP4 = "fileInRoot.java";
    private static final String SP5 = "a/deeper/path/which/should/be/perfectly/fine.txt";
    private static final String[] ALL_PATHS = new String[]{
        SP1, SP2, SP3, SP4, SP5
    };

    private static final Set<StandardLocation> LOCATIONS
            = EnumSet.of(StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT,
                    StandardLocation.CLASS_OUTPUT);
    private final Map<BlockStorageKind, JFS> jfsForStorageKind = new LinkedHashMap<>();
    private final Map<BlockStorageKind, Map<Location, List<JFSFileObject>>> fileObjectsForKind = new LinkedHashMap<>();
    private final Map<BlockStorageKind, Map<Location, List<JFSFileReference>>> refsForKind = new LinkedHashMap<>();
    private final Set<JFSFileReference> allReferences = new LinkedHashSet<>();
    private final List<JFSFileReference> allReferencesList = new ArrayList<>();
    private final Set<JFSFileObject> allFiles = new LinkedHashSet<>();
    private final List<JFSFileObject> allFilesList = new ArrayList<>();
    private JFS alt;

    @Test
    public void testRefs() throws IOException {
//        System.out.println(stringify());
        for (Map.Entry<BlockStorageKind, JFS> e : jfsForStorageKind.entrySet()) {
            Map<Location, List<JFSFileObject>> fos = fileObjectsForKind.get(e.getKey());
            Map<Location, List<JFSFileReference>> refs = refsForKind.get(e.getKey());
            for (Location loc : LOCATIONS) {
                List<JFSFileObject> files = fos.get(loc);
                List<JFSFileReference> refList = refs.get(loc);
                testOneGroup(e.getKey(), loc, e.getValue(), files, refList);
            }
        }
        assertEquals("Some created files equals() each other.", allFilesList.size(), allFiles.size());
        assertEquals("Some created references equals() each other", allReferencesList.size(), allReferences.size());
    }

    private String stringify() {
        StringBuilder sb = new StringBuilder("CONTENTS:\n");
        String i1 = "  ";
        String i2 = "    ";
        String i3 = "      ";
        String i4 = "        ";
        for (Map.Entry<BlockStorageKind, JFS> e : jfsForStorageKind.entrySet()) {
            sb.append(e.getKey()).append(' ').append(e.getValue().id()).append('\n');
            Map<Location, List<JFSFileObject>> fos = fileObjectsForKind.get(e.getKey());
            Map<Location, List<JFSFileReference>> refs = refsForKind.get(e.getKey());
            for (Location loc : LOCATIONS) {
                sb.append(i1).append(loc).append('\n');
                List<JFSFileObject> files = fos.get(loc);
                List<JFSFileReference> refList = refs.get(loc);
                for (int i = 0; i < Math.min(files.size(), refList.size()); i++) {
                    JFSFileObjectImpl fo = (JFSFileObjectImpl) files.get(i);
                    JFSFileReference ref = refList.get(i);
                    sb.append(i3).append(i).append('\n');
                    sb.append(i4).append("file ").append(fo.toURLString()).append(' ').append(fo.getName()).append('\n');
                    sb.append(i4).append("reff ").append(ref).append(' ').append(ref.path()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private void testOneGroup(BlockStorageKind block, Location loc, JFS jfs, List<JFSFileObject> files, List<JFSFileReference> refs) throws IOException {
        String msgPrefix = block.name() + ":" + loc.getName() + ":" + jfs.id() + ": ";
        int total = files.size();
        assertEquals("Test is broken - files and references created do not match", total, refs.size());
        for (int i = 0; i < total; i++) {
            JFSFileObject file = files.get(i);
            JFSFileReference ref = refs.get(i);
            testFileAndRef(msgPrefix, file, ref, block, loc, jfs);
        }
    }

    private void testFileAndRef(String msgPrefix, JFSFileObject file, JFSFileReference ref, BlockStorageKind block, Location loc, JFS jfs) throws IOException {
        assert file instanceof JFSFileObjectImpl;
        assertNotNull(msgPrefix + " file is null", file);
        assertNotNull(msgPrefix + " ref is null", ref);
        UnixPath refPath = ref.path();
        assertNotNull(msgPrefix + " ref returns null path " + ref, refPath);
        assertFalse(msgPrefix + " ref " + ref + " returns empty UnixPath", refPath.isEmpty());
        assertTrue(msgPrefix + "JFS ID " + jfs.id() + " not present in ref url " + ref, ref.toString().contains(jfs.id()));
        JFSFileObject resolvedByPassingJFS = ref.resolve(jfs);
        assertEquals(msgPrefix + " expected same file " + file + " for " + ref, file, resolvedByPassingJFS);
        UnixPath filePath = UnixPath.get(resolvedByPassingJFS.getName());

        assertEquals(msgPrefix + "Ref path and file path are not the same", filePath, refPath);

        JFSFileObject resolvedByURL = ref.resolveOriginal();
        assertNotNull(msgPrefix + " by url resolved to null", resolvedByURL);
        assertEquals(msgPrefix + " expected same file " + file + " for " + ref, file, resolvedByURL);

        JFSFileObject altResolve = ref.resolve(alt);
        assertNotNull(msgPrefix + "Failed to resolve " + ref + " against alternate with same contents", altResolve);

        JFSFileReference another = (JFSFileReference) file.toReference();
        assertEquals(msgPrefix + "A new reference to the same file is not equal", ref, another);

        String expectedContent = contentFor(file.getName(), block, loc, jfs.id());
        String gotContent = resolvedByURL.getCharContent(false).toString();
        assertEquals(msgPrefix + "Wrong content for " + ref + " / " + resolvedByURL + ": " + gotContent, expectedContent, gotContent);

        assertEquals(msgPrefix + "Coordinates do not match", ref.toCoordinates(), file.toCoordinates());
    }

    @Before
    public void setup() throws IOException {
        ProxyURLStreamHandlerFactory.register();
        MockServices.setServices(JFSUrlStreamHandlerFactory.class);
        for (BlockStorageKind block : BlockStorageKind.values()) {
            JFS jfs = JFS.builder().useBlockStorage(block).build();
            assertFalse("Created a new JFS equals to " + jfs + " in " + jfsForStorageKind.values(),
                    jfsForStorageKind.values().contains(jfs));
            jfsForStorageKind.put(block, jfs);
            createInitialFiles(jfs, block);
        }
        alt = JFS.builder().build();
        for (Location loc : LOCATIONS) {
            createInitialFiles(alt, BlockStorageKind.HEAP, loc, new ArrayList<>(), new ArrayList<>());
        }
    }

    @After
    public void teardown() throws IOException {
        jfsForStorageKind.clear();
        fileObjectsForKind.clear();
        refsForKind.clear();
        allReferencesList.clear();
        allReferences.clear();
        allFiles.clear();
        allFilesList.clear();
        if (alt != null) {
            alt.close();
        }
        if (jfsForStorageKind != null) {
            for (Map.Entry<BlockStorageKind, JFS> e : jfsForStorageKind.entrySet()) {
                if (!e.getValue().isReallyClosed()) {
                    e.getValue().close();
                }
            }
        }
    }

    private void createInitialFiles(JFS jfs, BlockStorageKind kind) throws IOException {
        Map<Location, List<JFSFileObject>> fosForLocation = fileObjectsForKind.get(kind);
        if (fosForLocation == null) {
            fosForLocation = new LinkedHashMap<>();
            fileObjectsForKind.put(kind, fosForLocation);
        }
        Map<Location, List<JFSFileReference>> refsForLocation = refsForKind.get(kind);
        if (refsForLocation == null) {
            refsForLocation = new LinkedHashMap<>();
            refsForKind.put(kind, refsForLocation);
        }
        for (Location loc : LOCATIONS) {
            List<JFSFileObject> files = fosForLocation.get(loc);
            if (files == null) {
                files = new ArrayList<>();
                fosForLocation.put(loc, files);
            }
            List<JFSFileReference> refs = refsForLocation.get(loc);
            if (refs == null) {
                refs = new ArrayList<>();
                refsForLocation.put(loc, refs);
            }
            createInitialFiles(jfs, kind, loc, files, refs);
        }
    }

    private void createInitialFiles(JFS jfs, BlockStorageKind kind, Location loc, List<JFSFileObject> files, List<JFSFileReference> refs) throws IOException {
        String fsId = jfs.id();
        assertTrue("Passed wrong list of files " + kind + " " + fsId + " " + files, files.isEmpty());
        assertTrue("Passed wrong list of refs " + kind + " " + fsId + " " + refs, files.isEmpty());
        for (String file : ALL_PATHS) {
            UnixPath path = UnixPath.get(file);
            JFSFileObject fo = jfs.create(path, loc, contentFor(file, kind, loc, jfs.id()));
            allFiles.add(fo);
            allFilesList.add(fo);
            assert fo instanceof JFSFileObjectImpl;
            JFSFileReference ref = ((JFSFileObjectImpl) fo).toReference();
            allReferences.add(ref);
            allReferencesList.add(ref);
            files.add(fo);
            refs.add(ref);
            assertEquals("Created with wrong filesystem id: " + ref + " with file "
                    + fo + " urlstring " + ((JFSFileObjectImpl) fo).toURLString(), fsId, ref.fsId());
            assertEquals("Passed wrong lists?", files.size(), refs.size());
            for (JFSFileReference ref1 : refs) {
                assertEquals("Found mix of filesystem ids in list for " + jfs
                        + " " + fsId + " " + kind + " " + ref1.fsId() + " and " + fsId
                        + ". All:\n" + refs, fsId, ref1.fsId());
            }
        }
        String fsId2 = jfs.id();
        assertEquals("JFS id changed?", fsId, fsId2);
    }

    private String contentFor(String filename, BlockStorageKind kind, Location loc, String jfsId) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(kind.name()).append(' ').append(loc.getName()).append(' ').append(filename)
                    .append(' ').append(jfsId)
                    .append('\n');
        }
        sb.append("And that's all.\n");
        return sb.toString();
    }
}

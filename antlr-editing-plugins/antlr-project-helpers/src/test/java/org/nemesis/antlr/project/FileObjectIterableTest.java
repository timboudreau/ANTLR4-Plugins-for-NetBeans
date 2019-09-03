package org.nemesis.antlr.project;

import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class FileObjectIterableTest {

    Path realA;
    Path realB;
    Path fakeA;
    Path fakeB;

    @Test
    public void testSinglesUseOptimizedIterables() {
        Iterable<FileObject> fos = FileObjectIterable.create(Arrays.asList(realA));
        assertFalse(fos instanceof FileObjectIterable);
        assertEquals(FolderQuery.class.getPackage().getName() + ".SingleIterable", fos.getClass().getName());
    }

    @Test
    public void testInconvertibleFilesGetOptimizedEmptyIterable() {
        Iterable<FileObject> fos = FileObjectIterable.create(Collections.singleton(fakeA));
        assertFalse(fos.iterator().hasNext());
        assertEquals(FolderQuery.class.getPackage().getName() + ".SingleIterable$EmptyIterable", fos.getClass().getName());
    }

    @Test
    public void testEmptyIterable() {
        Iterable<String> it = FoldersHelperTrampoline.getDefault().newEmptyIterable();
        assertFalse(it.iterator().hasNext());
        Iterable<String> it2 = FoldersHelperTrampoline.getDefault().newEmptyIterable();
        assertSame(it, it2);
    }

    @Test
    public void testSingleNullIterable() {
        Iterable<String> it = FoldersHelperTrampoline.getDefault().newSingleIterable(null);
        assertFalse(it.iterator().hasNext());
    }

    @Test
    public void testNormalIterable() {
        Iterable<FileObject> fos = FileObjectIterable.create(Arrays.asList(realA, realB));
        assertSize(2, fos);
        assertTrue(fos instanceof FileObjectIterable);
        int ct = 0;
        for (FileObject fo : fos) {
            switch (ct++) {
                case 0:
                    assertEquals(realA, FileUtil.toFile(fo).toPath());
                    break;
                case 1:
                    assertEquals(realB, FileUtil.toFile(fo).toPath());
                    break;
            }
        }
    }

    @Test
    public void testNonExistentFilesAreElided() {
        Iterable<FileObject> fos = FileObjectIterable.create(Arrays.asList(fakeA, realA, fakeA, realB, fakeB, fakeB));
        assertSize(2, fos);
        assertTrue(fos instanceof FileObjectIterable);
        int ct = 0;
        for (FileObject fo : fos) {
            switch (ct++) {
                case 0:
                    assertEquals(realA, FileUtil.toFile(fo).toPath());
                    break;
                case 1:
                    assertEquals(realB, FileUtil.toFile(fo).toPath());
                    break;
            }
        }

    }

    private void assertSize(int expected, Iterable<?> i) {
        int count = 0;
        for (Object o : i) {
            count++;
        }
        assertEquals(expected, count, i.toString());
    }

    @BeforeEach
    public void setup() throws IOException {
        realA = FileUtils.newTempFile("a-");
        realB = FileUtils.newTempFile("b-");
        fakeA = FileUtils.newTempFile("c-");
        fakeB = FileUtils.newTempFile("d-");
        FileUtils.deleteIfExists(fakeA);
        FileUtils.deleteIfExists(fakeB);
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteIfExists(realA, realB, fakeA, fakeB);
    }

}

package org.nemesis.jfs.javac;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import javax.tools.StandardLocation;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSClassLoader;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.JFSStorageKind;
import org.nemesis.jfs.JFSUrlStreamHandlerFactory;
import org.nemesis.jfs.NbJFSUtilities;
import org.nemesis.jfs.result.UpToDateness;
import org.nemesis.jfs.spi.JFSLifecycleHook;
import org.netbeans.junit.MockServices;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.editor.NbEditorDocument;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class JFSCompileBuilderTest {

    private JFS jfs;
    private static final String TYPE = "urgle.murb.MurgleWurgle";
    private static final UnixPath SOURCE_FILE_PATH = UnixPath.get(TYPE.replace('.', '/') + ".java");
    private static final UnixPath CLASS_FILE_PATH = UnixPath.get(TYPE.replace('.', '/') + ".class");
    private static int methodInsertPosition;

    @Test
    public void testCompileResultWithGarbage() throws Throwable {
        assertNotNull("setup was not run", jfs);
        JFSFileObject jfo = jfs.create(SOURCE_FILE_PATH, StandardLocation.SOURCE_PATH, withGarbage);
        CompileResult firstCompileResult = new JFSCompileBuilder(jfs).addSourceLocation(StandardLocation.SOURCE_PATH)
                .compile();
        assertTrue(firstCompileResult.compileFailed());
        assertFalse(firstCompileResult.isUsable());
        assertFalse(firstCompileResult.callResult);
        assertFalse(firstCompileResult.diagnostics().isEmpty());
        for (JavacDiagnostic diag : firstCompileResult.diagnostics()) {
            if (diag.isError()) {
                assertEquals("compiler.err.illegal.start.of.type", diag.sourceCode());
                assertEquals(3, diag.lineNumber());
            }
        }
        jfo.setTextContent(baseContent);
        testModification(jfo, () -> {
            jfo.setTextContent(withAddedMethod);
        });
    }

    @Test
    public void testCompileResultStatusCorrectWithDocumentMappedFile() throws Throwable {
        Path tempFile = FileUtils.newTempFile("JFSCompileBuilderTest");
        try {
            Files.write(tempFile, baseContent.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            JFSFileObject jfo = jfs.masquerade(tempFile, StandardLocation.SOURCE_PATH, SOURCE_FILE_PATH);
            testModification(jfo, () -> {
                Files.write(tempFile, withAddedMethod.getBytes(UTF_8), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            });
        } finally {
            FileUtils.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testCompileResultStatusCorrectWithDocumentMappedBytes() throws Throwable {
        NbEditorDocument doc = new NbEditorDocument("text/plain");
        doc.insertString(0, baseContent, null);
        JFSFileObject jfo = jfs.masquerade(doc, StandardLocation.SOURCE_PATH, SOURCE_FILE_PATH);
        testModification(jfo, () -> {
            doc.insertString(methodInsertPosition, addedMethod, null);
        });
    }

    @Test
    public void testCompileResultStatusCorrectWithJFSStoredBytes() throws Throwable {
        assertNotNull("setup was not run", jfs);
        JFSFileObject jfo = jfs.create(SOURCE_FILE_PATH, StandardLocation.SOURCE_PATH, baseContent);
        testModification(jfo, () -> {
            jfo.setTextContent(withAddedMethod);
        });
    }

    private void testModification(JFSFileObject jfo, ThrowingRunnable modifier) throws Throwable {
        JFSStorageKind sk = jfo.storageKind();
        String baseMsg = sk + "-" + jfo.toString();
        long sourceInitialModification = jfo.getLastModified();
        CompileResult firstCompileResult = new JFSCompileBuilder(jfs).addSourceLocation(StandardLocation.SOURCE_PATH)
                .compile();

        assertNotNull(baseMsg, firstCompileResult);
        assertTrue(baseMsg, firstCompileResult.callResult);
        assertFalse(baseMsg, firstCompileResult.compileFailed());
        firstCompileResult.rethrow();
        firstCompileResult.sources();
        UpToDateness status = firstCompileResult.currentStatus();
        assertNotNull(baseMsg, status);
        assertTrue(baseMsg + " - " + status.toString(), status.isUpToDate());
        JFSFileObject classFile = jfs.get(StandardLocation.CLASS_OUTPUT, CLASS_FILE_PATH);
        assertNotNull(baseMsg + " - " + CLASS_FILE_PATH.toString(), classFile);
        long classInitialModification = classFile.getLastModified();

        Reference<JFSClassLoader> cl = testClassLoader(baseMsg, false);

        NbTestCase.assertGC("Classloader not gc'd", cl,
                CollectionUtils.<Object>setOf(this, jfs));

        modifier.run();
        assertNotEquals(baseMsg, sourceInitialModification, jfo.getLastModified());
        UpToDateness newStatus = firstCompileResult.currentStatus();
        assertNotSame(baseMsg, status, newStatus);
        assertFalse(baseMsg, newStatus.isUpToDate());
        assertFalse(baseMsg, newStatus.isUnknownStatus());
        assertTrue(firstCompileResult.filesState().changes().modified().contains(SOURCE_FILE_PATH));

        CompileResult compileResult2 = new JFSCompileBuilder(jfs)
                .addSourceLocation(StandardLocation.SOURCE_PATH)
                .compile();

        assertNotNull(baseMsg, compileResult2);
        JFSFileObject classFile2 = jfs.get(StandardLocation.CLASS_OUTPUT, CLASS_FILE_PATH);
        assertSame(baseMsg, classFile, classFile2);
        long classSecondModification = classFile.getLastModified();

        assertNotEquals(baseMsg, classInitialModification, classSecondModification);
        cl = testClassLoader(baseMsg, true);

        NbTestCase.assertGC("Second classloader not gc'd", cl,
                CollectionUtils.<Object>setOf(this, jfs));

        UpToDateness origStatusRevised = firstCompileResult.currentStatus();
        assertFalse(origStatusRevised.isUpToDate());
        assertTrue(compileResult2.currentStatus().isUpToDate());

        JFSFileModifications.FileChanges ch = firstCompileResult.filesState().changesAndReset();
        assertTrue(ch.modified().contains(SOURCE_FILE_PATH));
    }

    private Reference<JFSClassLoader> testClassLoader(String baseMsg, boolean includeAddedMethod) throws Exception {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        WeakReference<JFSClassLoader> result;
        try (JFSClassLoader cl = jfs.getClassLoader(StandardLocation.CLASS_OUTPUT)) {
            result = new WeakReference<>(cl);
            Class<?> type = cl.loadClass(TYPE);
            Method m = type.getMethod("add", Integer.TYPE, Integer.TYPE);
            Integer methodResult = (Integer) m.invoke(null, 5, 7);
            assertEquals("Add wrong for " + baseMsg, Integer.valueOf(12), methodResult);
            if (includeAddedMethod) {
                m = type.getMethod("multiply", Integer.TYPE, Integer.TYPE);
                methodResult = (Integer) m.invoke(null, 7, 7);
                assertEquals("Multiply wrong for " + baseMsg, Integer.valueOf(49), methodResult);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
        return result;
    }

    @Before
    public void setup() throws IOException {
        jfs = JFS.builder().withCharset(UTF_8).build();
        assertNotNull(jfs);
        HOOK.assertAdded(jfs);
        int ct = 0;
        for (int i = 0; i < baseContent.length(); i++) {
            if (baseContent.charAt(i) == '\n') {
                ct++;
                if (ct == 2) {
                    methodInsertPosition = i;
                    break;
                }
            }
        }
    }

    @After
    public void tearDown() throws Throwable {
        assertNotNull(jfs);
        try {
            jfs.close();
            HOOK.assertClosed(jfs);
            assertTrue(jfs.isReallyClosed());
        } finally {
            // Ensure tests don't interfere with each other
            HOOK.removeRegardless(jfs);
        }
        jfs = null;
    }

    @BeforeClass
    public static void setupServices() throws Throwable {
        MockServices.setServices(NbJFSUtilities.class, Hook.class);
        JFSUrlStreamHandlerFactory streamHandler = Lookup.getDefault().lookup(JFSUrlStreamHandlerFactory.class);
        assertNotNull(streamHandler);
        assertNotNull(HOOK);
    }

    @AfterClass
    public static void tearDownServices() throws Throwable {
        MockServices.setServices();
        HOOK = null;
    }

    private static final String addedMethod = "    public static int multiply(int a, int b) {\n"
            + "        return a * b;\n"
            + "    }\n";

    private static final String baseContent = "package urgle.murb;"
            + "public class MurgleWurgle {\n\n"
            + "    public static int add(int a, int b) {\n"
            + "        return a + b;\n"
            + "    }"
            + "}\n";
    private static final String withAddedMethod = "package urgle.murb;"
            + "public class MurgleWurgle {\n\n"
            + addedMethod
            + "    public static int add(int a, int b) {\n"
            + "        return a + b;\n"
            + "    }"
            + "}\n";

    private static final String withGarbage = "package urgle.murb;"
            + "public class MurgleWurgle {\n\n"
            + "230 alsjdhfaio 09wef asdl asd f\n{{{{|asd8f90"
            + "    public static int add(int a, int b) {\n"
            + "        return a + b;\n"
            + "    }"
            + "}\n";

    static Hook HOOK;

    public static class Hook extends JFSLifecycleHook {

        Set<JFS> jfses = new HashSet<>();
        Set<JFS> closedJfses = new HashSet<>();

        public Hook() {
            assertNull("Created twice", HOOK);
            HOOK = this;
        }

        void removeRegardless(JFS jfs) {
            jfses.remove(jfs);
            closedJfses.remove(jfs);
        }

        void assertAdded(JFS jfs) {
            assertFalse(closedJfses.contains(jfs));
            assertTrue(jfses.contains(jfs));
        }

        void assertClosed(JFS jfs) {
            assertFalse(jfses.contains(jfs));
            assertTrue(closedJfses.contains(jfs));
            closedJfses.remove(jfs);
        }

        @Override
        public void jfsCreated(JFS jfs) {
            assertTrue(jfses.add(jfs));
        }

        @Override
        public void jfsClosed(JFS jfs) {
            assertTrue(jfses.remove(jfs));
            assertTrue(closedJfses.add(jfs));
        }

    }
}

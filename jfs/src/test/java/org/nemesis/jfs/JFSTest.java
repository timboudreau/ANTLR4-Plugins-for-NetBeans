package org.nemesis.jfs;

import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import javax.tools.ToolProvider;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.nemesis.jfs.JFSUrlStreamHandlerFactory.URL_PATTERN;
import org.nemesis.jfs.javac.JavacOptions;
import org.netbeans.junit.MockServices;
import org.netbeans.spi.queries.FileEncodingQueryImplementation;

/**
 *
 * @author Tim Boudreau
 */
public class JFSTest {

    private static final String EXP1 = "com/foo/bar/Baz.java";
    private static final String EXP2 = "com/foo/bar/Baz.class";
    private static final String EXP3 = "Foo.class";
    private static final String EXP4 = ".poof";

    String SIMPLE_MAIN = "public class Main {\npublic static void main(String[] args) {\nSystem.out.println(\"Hello world\");\n}\n}\n";
    String COMPILE_ME = "package com.testit;\n"
            + "public class TestIt {\n"
            + "    public static String foo() {\n"
            + "        return \"Hello!\";\n"
            + "    }\n"
            + "    public static final class Something {\n"
            + "        public static int skiddoo() {\n"
            + "            return 23;\n"
            + "        }\n"
            + "    }\n"
            + "}";

    @Test
    @SuppressWarnings({"UnusedAssignment", "try"})
    public void testCompilation() throws IOException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
        LogIt logIt = new LogIt();
        JFS jfs = new JFS(US_ASCII, logIt);
        final int jfsidhc = System.identityHashCode(jfs);
        assertFalse(JFSUrlStreamHandlerFactory.noLongerRegistered(jfsidhc));

        UnixPath path = UnixPath.get("com/testit/TestIt.java");
        JFSFileObject fo = jfs.create(path, SOURCE_PATH, COMPILE_ME);
        assertNotNull(fo);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> options = new JavacOptions().options(compiler);
        options.add("-encoding");
        options.add("UTF-16");

        JavaCompiler.CompilationTask task = compiler.getTask(null,
                jfs, logIt, options, null,
                jfs.list(SOURCE_PATH, "", EnumSet.of(SOURCE), true));
        long then = System.currentTimeMillis();
        Boolean res = task.call();
        logIt.assertNoErrors();
        System.out.println("Compilation took " + (System.currentTimeMillis() - then) + "ms");
        assertTrue("Compile failed", res);

        Set<Name> names = toSet(jfs.list(StandardLocation.CLASS_OUTPUT, "", EnumSet.allOf(JavaFileObject.Kind.class), true), f -> {
            return ((JFSFileObjectImpl) f).name();
        });

        for (Name n : names) {
            assertEquals("Wrong type", CLASS, n.kind());
        }
        assertEquals("Unexpected number of class names: " + names + "", 2, names.size());
        Name generatedClass1 = Name.forClassName("com.testit.TestIt", CLASS);
        Name generatedClass2 = Name.forClassName("com.testit.TestIt$Something", CLASS);
        assertTrue("Not present: " + generatedClass1 + " in " + names.toString(), names.contains(generatedClass1));
        assertTrue("Not present: " + generatedClass2 + " in " + names.toString(), names.contains(generatedClass2));

        Set<JavaFileObject> fos = toSet(jfs.list(CLASS_OUTPUT, "", EnumSet.allOf(JavaFileObject.Kind.class), true));
        assertFalse("No file objects returned", fos.isEmpty());
        Set<URL> foUrls = new HashSet<>();
        for (JavaFileObject f : fos) {
            assertTrue(".class files should be represented by an instance of"
                    + "JFSJavaFileObject (JavaFileObject) but got "
                    + f.getClass().getName(), f instanceof JFSJavaFileObjectImpl);
            JFSJavaFileObjectImpl ff = (JFSJavaFileObjectImpl) f;
            assertTrue("Should not be empty: " + ff, ff.length() > 0);
            foUrls.add(((JFSJavaFileObjectImpl) f).toURL());
        }

        assertFalse("No URLs", foUrls.isEmpty());
        JFSClassLoader ldr = jfs.getClassLoader(CLASS_OUTPUT);
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ldr);
        Reference<Class<?>> typeRef;
        Reference<Class<?>> typeRef2;
        then = System.currentTimeMillis();
        try {
            Class<?> type = ldr.loadClass("com.testit.TestIt");
            typeRef = new WeakReference<>(type);
            assertNotNull("Could not load com.testit.TestIt", type);
            Method foo = type.getMethod("foo");
            String got = (String) foo.invoke(null);
            assertNotNull(got);
            assertEquals("Wrong result - test source changed?", "Hello!", got);

            type = ldr.loadClass("com.testit.TestIt$Something");
            typeRef2 = new WeakReference<>(type);
            assertNotNull("Could not load com.testit.TestIt", type);
            Method skiddoo = type.getMethod("skiddoo");
            Integer sk = (Integer) skiddoo.invoke(null);
            assertNotNull("Null result - test source changed?", sk);
            assertEquals("Wrong result - test source changed?", 23, sk.intValue());
            // Paranoia
            type = null;
            foo = null;
            skiddoo = null;
        } finally {
            System.out.println("load and reflectively invoke took " + (System.currentTimeMillis() - then) + "ms");
            Thread.currentThread().setContextClassLoader(old);
            forceGC();
        }
        long size = jfs.size();
        assertTrue("JFS should report a size of > 0", size > 0);

        // Now test that we really don't leak references to things
        // that we shouldn't
        // Create some WeakReferences that should be cleared once
        // the referent is garbage collected
        Reference<JFS> jfsRef = new WeakReference<>(jfs);
        Reference<JFSClassLoader> clRef = new WeakReference<>(ldr);
        // wipe out strong references
        compiler = null;
        task = null;
        fos.clear();
        fos = null;
        forceGC();
        // We did not clear the classloader - it must still operate correctly,
        // and should keep the JFS from being de-registered, which would break
        // URL resolution when the classloader might still be asked for
        // resources
        assertNotNull("A live classloader should keep the filesystem "
                + "from being garbage collected", jfsRef.get());
        // the variable was cleared above - someone introduced a bug
        assertNotNull("Test is broken", clRef.get());
        // URLs should all still resolve - the JFS is still alive if they do
        for (URL u : foUrls) {
            try (InputStream in = u.openStream()) {
                assertTrue("0 length stream from " + u, in.available() > 0);
            }
        }
        // Now call close on the JFS.  This won't actually de-register it
        // because the classloader is still alive, so JFSUrlStreamHandlerFactory
        // should still be able to resolve URLs.  Make sure it didn't get
        // de-registered.
        jfsRef.get().close();
        assertTrue("JFS should have its closeCalled flag set after a call to close",
                jfsRef.get().closeCalled());
        assertFalse("JFS should not really be closed while a live classloader "
                + "exists that could produce URLs into it", jfsRef.get().isReallyClosed());
        forceGC();
        assertNotNull("JFS should not be garbage collected while a classloader over "
                + "one of its locations is still strongly referenced", jfsRef.get());
        assertNotNull("Test is broken", clRef.get());
        // URLs should all still resolve - the JFS is still alive if they do
        for (URL u : foUrls) {
            try (InputStream in = u.openStream()) {
                assertTrue("0 length stream from " + u, in.available() > 0);
            }
        }
        // Classloader's existence should keep the JFS referenced so URLs can
        // be resolved
        jfs = jfsRef.get();
        assertFalse("JFS unregistered from URL resolution prematurely",
                JFSUrlStreamHandlerFactory.noLongerRegistered(jfsidhc));
        assertNotNull("JFS garbage collected prematurely", jfs);
        assertTrue("JFS size should not have been zeroed out while a live "
                + "classloader over some of its contents exists", jfs.size() > 0);
        assertTrue("JFS should still have location CLASS_OUTPUT while a "
                + "classloader over it exists", jfs.hasLocation(CLASS_OUTPUT));
        assertFalse("JFS should no longer contain SOURCE_PATH since close() was "
                + "called and no classloader over that exists", jfs.hasLocation(SOURCE_PATH));
        assertTrue("Close called flag is not set after a call to close()", jfs.closeCalled());
        assertFalse("JFS reports that it was really closed when a classloader over "
                + "some of its contents still exists", jfs.isReallyClosed());
        assertTrue("JFS reports no contents for CLASS_OUTPUT - cleared prematurely while "
                + "classloader still open over them.", jfs.list(CLASS_OUTPUT,
                        "", EnumSet.allOf(JavaFileObject.Kind.class), true).iterator().hasNext());
        assertTrue("JFS should not report size 0 when a classloader over its "
                + "non-empty CLASS_OUTPUT storage still exists", jfs.size() != 0);
        // Now close the classloader - it should be the only reference path that
        // still strongly references the filesystem, and it clears that reference
        // when it is closed
        clRef.get().close();
        ldr = null;
        // Make sure the JFS was notified and deregisters itself
        assertTrue("Closing the last classloader over a JFS should really close it"
                + "de-register", jfs.isReallyClosed());
        assertTrue("Closing the last classloader over a JFS should unregister it "
                + "for resolving JFS urls", JFSUrlStreamHandlerFactory.noLongerRegistered(jfsidhc));
        jfs = null;
        // Now it should be garbage collected
        forceGC();
        assertNull("After unreferencing and closing last classloader, JFS should "
                + "have been garbage collected", jfsRef.get());
        // Make sure none of the URLs resolve
        for (URL u : foUrls) {
            try {
                try (InputStream in = u.openStream()) {
                    fail("Should not have gotten stream");
                }
            } catch (IOException ioe) {
//                System.out.println(ioe.getMessage());
            }
        }
        // And most importantly, make sure any classes it loaded have become
        // unreferenced - that's really the point of all this is not to leak
        // classes endlessly
        Class<?> a = typeRef.get();
        Class<?> b = typeRef2.get();
        assertNull("Class should have been unloaded: " + a, a);
        assertNull("Class should have been unloaded: " + b, b);
    }

    private static void forceGC() {
        for (int i = 0; i < 7; i++) {
            System.gc();
            System.runFinalization();
        }
    }

    static <T> Set<T> toSet(Iterable<T> stuff) {
        Set<T> result = new HashSet<>();
        for (T obj : stuff) {
            result.add(obj);
        }
        return result;
    }

    static <T, R> Set<R> toSet(Iterable<T> stuff, Function<T, R> xform) {
        Set<R> result = new HashSet<R>();
        for (T obj : stuff) {
            result.add(xform.apply(obj));
        }
        return result;
    }

    public static final class LogIt implements BiConsumer<Location, FileObject>, DiagnosticListener<JavaFileObject> {

        private Set<String> errors = new HashSet<>();

        @Override
        public void accept(Location t, FileObject u) {
            System.out.println("jfs: " + t + ": " + u);
        }

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            System.out.println("javac: " + diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(diagnostic.toString());
            }
        }

        public void assertNoErrors() {
            assertTrue(errors.toString(), errors.isEmpty());
        }
    }

    @Test
    public void testURLPattern() {
        String u = "jfs://jnoblf0x-1kukpfmuoda20/SOURCE_PATH/com/foo/bar/Baz.java";
        Matcher m = URL_PATTERN.matcher(u);
        assertTrue(m.find());
        String ident = m.group(1);
        String loc = m.group(2);
        String path = m.group(3);
        assertEquals("jnoblf0x-1kukpfmuoda20", ident);
        assertEquals("SOURCE_PATH", loc);
        assertEquals("com/foo/bar/Baz.java", path);
    }

    @Test
    public void testJFS() throws IOException {
        JFS jfs = new JFS();
        UnixPath path = UnixPath.get(EXP1);
        JFSFileObject fo = jfs.create(path, SOURCE_PATH, SIMPLE_MAIN);

        assertNotNull(fo);
        assertEquals(fo.length(), SIMPLE_MAIN.getBytes(UTF_16).length);
        assertSame(fo, jfs.get(SOURCE_PATH, path));
        try (Reader r = fo.openReader(false)) {
            CharBuffer buf = CharBuffer.allocate(SIMPLE_MAIN.length());
            r.read(buf);
            buf.flip();
            assertEquals(SIMPLE_MAIN, buf.toString());
        }
        assertTrue(contains(jfs.list(SOURCE_PATH, "", EnumSet.of(SOURCE), true), fo));
        assertTrue(contains(jfs.list(SOURCE_PATH, "com.foo.bar", EnumSet.of(SOURCE), false), fo));
        assertTrue(contains(jfs.list(SOURCE_PATH, "com.foo", EnumSet.of(SOURCE), true), fo));
        assertTrue(contains(jfs.list(SOURCE_PATH, "com", EnumSet.of(SOURCE), true), fo));
        assertFalse(contains(jfs.list(SOURCE_PATH, "com.foo", EnumSet.of(SOURCE), false), fo));

        byte[] bytes = new byte[30];
        ThreadLocalRandom.current().nextBytes(bytes);
        try (OutputStream out = jfs.getFileForOutput(CLASS_OUTPUT, "com.foo.bar", "Baz.class", fo).openOutputStream()) {
            out.write(bytes);
        }
        JFSFileObjectImpl written = (JFSFileObjectImpl) jfs.get(CLASS_OUTPUT, UnixPath.get(EXP2));
        assertNotNull(written);
        assertEquals(bytes.length, written.length());
        assertArrayEquals(bytes, written.asBytes());
        byte[] nue = new byte[30];
        try (InputStream in = jfs.getFileForOutput(CLASS_OUTPUT, "com.foo.bar", "Baz.class", fo).openInputStream()) {
            int count = in.read(nue);
            assertEquals(bytes.length, count);
        }
        assertArrayEquals(bytes, nue);
        assertEquals((long) SIMPLE_MAIN.getBytes(UTF_16).length + bytes.length, jfs.size());

        assertEquals(Name.forClassName("com.foo.bar.Baz", CLASS), written.name());

        assertTrue(contains(jfs.list(CLASS_OUTPUT, "com.foo.bar", EnumSet.of(CLASS), false), written));
        assertTrue(contains(jfs.list(CLASS_OUTPUT, "com.foo", EnumSet.of(CLASS), true), written));

        URL url = written.toURL();

        assertEquals("jfs", url.getProtocol());
        assertEquals(jfs.id(), url.getAuthority());
        assertEquals("/CLASS_OUTPUT/com/foo/bar/Baz.class", url.getFile());

        assertNotNull(url);
        byte[] fromUrl = new byte[bytes.length];
        try (InputStream in = url.openStream()) {
            in.read(fromUrl);
        }
        assertArrayEquals(bytes, fromUrl);

        written.delete();
        assertEquals(0, written.length());
        assertFalse(contains(jfs.list(CLASS_OUTPUT, "com.foo.bar", EnumSet.of(CLASS), false), written));

        assertEquals((long) SIMPLE_MAIN.getBytes(UTF_16).length, jfs.size());
    }

    private boolean contains(Iterable<?> it, Object obj) {
        boolean found = false;
        for (Object o : it) {
            found = Objects.equals(o, obj);
        }
        return found;
    }

    @Test
    public void testNameExt() {
        String[] parts = nameExt(UnixPath.get("Baz.java"));
        assertNotNull(parts);
        assertEquals(2, parts.length);
        assertEquals("Baz", parts[0]);
        assertEquals("java", parts[1]);
        parts = nameExt(UnixPath.get(".foo"));
        assertEquals(".foo", parts[0]);
        assertEquals("", parts[1]);
        parts = nameExt(UnixPath.get("foo"));
        assertEquals("foo", parts[0]);
        assertEquals("", parts[1]);
        parts = nameExt(UnixPath.get("."));
        assertEquals(".", parts[0]);
        assertEquals("", parts[1]);
        parts = nameExt(UnixPath.get(""));
        assertEquals("", parts[0]);
        assertEquals("", parts[1]);
    }

    @Test
    public void testName() {
        Name name = Name.forFileName(EXP1);
        testOneName("forFileName.java", EXP1, name);
        name = Name.forPath(UnixPath.get(EXP1));
        testOneName("forPath.java", EXP1, name);
        UnixPath p1 = UnixPath.get("/a/b/c");
        UnixPath p2 = p1.resolve(EXP1);

//    private static final String EXP1 = "com/foo/bar/Baz.java";
//    private static final String EXP2 = "com/foo/bar/Baz.class";
//    private static final String EXP3 = "Foo.class";
//    private static final String EXP4 = ".poof";
        name = Name.forPath(p2, p1);
        testOneName("forPath-relative-absolute-path.java", EXP1, name);
        p1 = UnixPath.get("a/b/c");
        p2 = p1.resolve(EXP1);
        name = Name.forPath(p2, p1);
        testOneName("forPath-relative-relative.java", EXP1, name);
        name = Name.forClassName("com.foo.bar.Baz", JavaFileObject.Kind.SOURCE);
        testOneName("forClassName.class", EXP1, name);
        name = Name.forFileName(EXP2);
        testOneName("forFileName.class", EXP2, name);
        name = Name.forFileName(EXP3);
        testOneName("forFileName.defaultPackage", EXP3, name);
        name = Name.forClassName("Foo", JavaFileObject.Kind.CLASS);
        testOneName("forClass.defaultPackage", EXP3, name);
        name = Name.forFileName("", "Foo.class");
        testOneName("forFileName.defaultPackage", EXP3, name);
        name = Name.forFileName(EXP4);
        testOneName("dotFile.forFileName.defaultPackage", EXP4, name);
        name = Name.forFileName("/" + EXP1);
        testOneName("forFileName.absolutePath", EXP1, name);
        name = Name.forPath(UnixPath.get("/" + EXP1));
        testOneName("forPath.absolutePath", EXP1, name);
    }

    private void testOneName(String msg, String expect, Name name) {
        assertEquals("Expected '" + expect + "' got '" + name + "'", expect, name.toString());
        UnixPath pth = UnixPath.get(expect);
        String[] nameExt = nameExt(pth);
        assertNotNull(nameExt);
        assertEquals(2, nameExt.length);
        assertEquals(nameExt[0], name.getNameBase());
        assertEquals(pth.getFileName().toString(), name.getName());
        String derivedNameExt = nameExt[1].isEmpty() ? nameExt[0] : nameExt[0] + '.' + nameExt[1];
        assertEquals(derivedNameExt, name.getName());
        assertEquals(nameExt[1], name.extension());
        assertEquals(expect.replace('/', '.').replace(".java", "").replace(".class", ""), name.asClassName());
        UnixPath parent = pth.getParent() == null ? UnixPath.empty() : pth.getParent();
        assertEquals(parent.toString().replace('/', '.'), name.packageName());
        assertTrue(msg, name.packageMatches(parent.toString().replace('/', '.')));
        assertTrue(msg, name.isPackage(parent, false));
        assertTrue(msg, name.isPackage(parent, true));
        if (pth.getParent() != null) {
            assertTrue(msg, name.isPackage(parent.getParent(), true));
        }
        switch (nameExt[1]) {
            case "class":
                assertEquals(msg, JavaFileObject.Kind.CLASS, name.kind());
                break;
            case "java":
                assertEquals(msg, JavaFileObject.Kind.SOURCE, name.kind());
                break;
            default:
                assertEquals(msg, JavaFileObject.Kind.OTHER, name.kind());
        }
    }

    @Test
    public void testJFSRealFiles() throws Throwable {
        JFS jfs = new JFS(UTF_16);
        Path tempFile = Paths.get(System.getProperty("java.io.tmpdir"), "map-me.txt");
        try {
            Files.write(tempFile, Arrays.asList("Hello utf 16!"), UTF_16, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            JFSFileObject fo = jfs.masquerade(tempFile, SOURCE_PATH, UnixPath.get("foo/bar.utf16"), UTF_16);
            assertNotNull(fo);
            assertSame(fo, jfs.get(SOURCE_PATH, UnixPath.get("foo/bar.utf16")));

            assertTrue(fo instanceof JFSFileObjectImpl);
            JFSFileObjectImpl foi = (JFSFileObjectImpl) fo;

            System.out.println("STORAGE " + foi.storage.getClass().getName());

            long lm = fo.getLastModified();

            String msg = fo.getCharContent(true).toString();
            assertEquals("Hello utf 16!\n", msg);

            Files.write(tempFile, Arrays.asList("Hello again utf 16!"), UTF_16, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            String msg2 = fo.getCharContent(true).toString();
            assertEquals("Hello again utf 16!\n", msg2);
//            assertNotEquals(lm, fo.getLastModified());
            try {
                fo.openOutputStream();
                fail("Should not be able to overwrite disk files");
            } catch (IOException ex) {
                // do nothing
            }
            fo.delete();
            assertNull("Still present", jfs.get(SOURCE_PATH, UnixPath.get("foo/bar.utf16")));
            assertTrue("Disk file was deleted", Files.exists(tempFile));
            jfs.close();

            jfs = new JFS(UTF_16);
            fo = jfs.copy(tempFile, UTF_16, SOURCE_PATH, UnixPath.get("poozle/wheez.plee"));
            assertNotNull(fo);
            assertEquals("poozle/wheez.plee", fo.getName());
            assertEquals("Hello again utf 16!\n", fo.getCharContent(true).toString());
            try (OutputStream str = fo.openOutputStream()) {
                str.write("Uh oh".getBytes(UTF_16));
            }
            assertEquals("Uh oh", fo.getCharContent(true).toString());
            fo.delete();
            assertNull(jfs.get(SOURCE_PATH, UnixPath.get("map-me.txt")));
        } finally {
            if (Files.exists(tempFile)) {
                Files.delete(tempFile);
            }
        }
    }

    Document document;

    @Test
    public void testMapDocuments() throws Throwable {
        JFS jfs = new JFS(UTF_16);
        document = new DefaultStyledDocument();
        String txt = "This is some text\nWhich shall be given up for you\n";
        document.insertString(0, txt, null);
        UnixPath pth = UnixPath.get("/some/docs/Doc.txt");
        JFSFileObject fo = jfs.masquerade(document, SOURCE_PATH, pth);
        assertNotNull(fo);
        assertEquals(txt, fo.getCharContent(true).toString());
        long lm = fo.getLastModified();
        long len = fo.length();
        String newText = "And now things are different\nin this here document.\n";
        Thread.sleep(100);
        try (OutputStream out = fo.openOutputStream()) {
            out.write(newText.getBytes(UTF_16));
        }
        assertNotEquals(lm, fo.getLastModified());
        assertNotEquals(len, fo.length());
        assertEquals(newText, fo.getCharContent(true).toString());

        try (InputStream in = fo.openInputStream()) {
            byte[] bytes = new byte[fo.length() + 20];
            int count = in.read(bytes);
            String s = new String(bytes, 0, count, UTF_16);
            assertEquals(newText.length(), s.length());
            assertEquals(newText, s);
        }
        jfs.close();
    }

    @BeforeClass
    public static void setup() {
        MockServices.setServices(JFSUrlStreamHandlerFactory.class, FEQImpl.class);
        org.netbeans.ProxyURLStreamHandlerFactory.register();
    }

    public static final class FEQImpl extends FileEncodingQueryImplementation {

        @Override
        public Charset getEncoding(org.openide.filesystems.FileObject fo) {
            // ensures that we get the correct encoding for our test UTF-16 file object
            if ("map-me.txt".equals(fo.getNameExt())) {
                return UTF_16;
            }
            return null;
        }
    }

    static String[] nameExt(UnixPath path) {
        String fileName = path.getFileName().toString();
        String ext = "";
        int extIx = fileName.lastIndexOf('.');
        if (extIx > 0 && extIx < fileName.length() - 1) {
            ext = fileName.substring(extIx + 1);
            fileName = fileName.substring(0, extIx);
        }
        return new String[]{fileName, ext};
    }
}

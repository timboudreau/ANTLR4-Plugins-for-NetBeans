package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.EXTENSIONS_REGISTRY;
import org.netbeans.core.NbLoaderPool;
import org.netbeans.junit.MockServices;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataLoaderPool;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocDataLoaderTest {

    private static final Path grammarFile = Paths.get("/home/user/work/FooGrammar.g4");
    private String mimeType;
    private String ext;
    private FileSystem fs;
    private FileObject fooFile;
    private FileObject fooFile2;
    private FileObject generatedExtensionFile;
    private FileObject glorkFile;

    @Test
    public void testFilesAreRecognized() throws Throwable {
        DataLoaderPool pool = DataLoaderPool.getDefault();
        assertTrue(pool instanceof NbLoaderPool);
        assertEquals(mimeType, generatedExtensionFile.getMIMEType());
        assertEquals(EXTENSIONS_REGISTRY.toString(), mimeType, fooFile.getMIMEType());
        assertEquals(mimeType, fooFile2.getMIMEType());

        DataObject genDob = DataObject.find(generatedExtensionFile);
        assertTrue("Wrong loader: " + genDob.getLoader().getClass().getName(), genDob.getLoader() instanceof AdhocDataLoader);

        DataObject fooDob = DataObject.find(fooFile);
        assertTrue("Wrong loader: " + fooDob.getLoader().getClass().getName(), fooDob.getLoader() instanceof AdhocDataLoader);

        DataObject fooDob2 = DataObject.find(fooFile2);
        assertTrue("Wrong loader: " + fooDob2.getLoader().getClass().getName(), fooDob2.getLoader() instanceof AdhocDataLoader);

        // Keep a reference
        AdhocDataLoader ldr = (AdhocDataLoader) fooDob2.getLoader();

        assertNotNull(genDob.getLookup().lookup(AdhocDataObject.class));
        assertNotNull(fooDob.getLookup().lookup(AdhocDataObject.class));
        assertNotNull(fooDob2.getLookup().lookup(AdhocDataObject.class));

        assertEquals("content/unknown", glorkFile.getMIMEType());
        DataObject preRegister = DataObject.find(glorkFile);
        System.out.println("ORIGINAL DOB IS " + preRegister.getClass().getName());
        assertNull(preRegister.getLookup().lookup(AdhocDataObject.class));

        preRegister = null;

        L l = new L();
        AdhocMimeTypes.listenForRegistrations(l);

        AdhocMimeTypes.registerFileNameExtension("glork", mimeType);
        l.assertChanged("glork", mimeType);

        Thread.sleep(200);

        DataObject nue = DataObject.find(glorkFile);

        System.out.println("NEW DATAOBJECT TYPE: " + nue.getClass().getName());

        String resolvedType = Lookup.getDefault().lookup(AdhocMimeResolver.class).findMIMEType(glorkFile);
        assertNotNull(resolvedType);
        assertEquals(mimeType, resolvedType);

        System.out.println("NUE IS " + nue.getClass().getName());
        assertEquals(mimeType, nue.getPrimaryFile().getMIMEType());
        assertNotNull(nue.getLookup().lookup(AdhocDataObject.class));

    }

    class L implements BiConsumer<String, String> {

        private CountDownLatch latch = new CountDownLatch(1);

        private String ext;
        private String mime;
        private Throwable stack = null;

        void assertChanged(String expectedExt, String expectedMime) {
            String e, m;
            synchronized (this) {
                e = ext;
                m = mime;
            }
            if (e == null && m == null) {
                await();
                synchronized (this) {
                    e = ext;
                    m = mime;
                }
            }
            try {
                assertEquals(expectedExt, e);
                assertEquals(expectedMime, m);
            } catch (AssertionError ae) {
                if (stack != null) {
                    ae.initCause(stack);
                }
                throw ae;
            }
        }

        void await() {
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                latch = new CountDownLatch(1);
            }
        }

        @Override
        public synchronized void accept(String ext, String mime) {
            System.out.println("NOITIFIED: " + ext + " for " + mime);
            this.ext = ext;
            this.mime = mime;
            stack = new Exception();
            latch.countDown();
        }

    }

    @Test
    public void testLoaderIsPresent() throws Throwable {
        DataLoaderPool pool = DataLoaderPool.getDefault();
        Set<DataLoader> ldrs = new HashSet<>();

        Enumeration<DataLoader> all = pool.allLoaders();
        while (all.hasMoreElements()) {
            DataLoader dl = all.nextElement();
            ldrs.add(dl);
        }
        Set<Class<?>> producers = new HashSet<>();
        Enumeration<DataLoader> prodEnum = pool.producersOf(AdhocDataObject.class);
        while (prodEnum.hasMoreElements()) {
            DataLoader dl = prodEnum.nextElement();
            producers.add(dl.getClass());
        }
        assertTrue(producers.contains(AdhocDataLoader.class));

    }

    @Before
    public void setup() throws IOException, InvalidMimeTypeRegistrationException {
        MockServices.setServices(AdhocDataLoader.class,
                AdhocMimeDataProvider.class, AdhocMimeResolver.class, NbLoaderPool.class);
        AdhocMimeTypes._reinitAndDeleteCache();

        DataLoaderPool dlp = DataLoaderPool.getDefault();
        Enumeration<DataLoader> loaders = dlp.allLoaders();
        // Ensure data loaders are initialized and listening
        while (loaders.hasMoreElements()) {
            DataLoader ldr = loaders.nextElement();
            System.out.println("LOADER: " + ldr);
        }

        mimeType = AdhocMimeTypes.mimeTypeForPath(grammarFile);
        ext = AdhocMimeTypes.fileExtensionFor(mimeType);
        System.out.println("MIME TYPE IS '" + mimeType + "'");
        L l = new L();
        AdhocMimeTypes.listenForRegistrations(l);
        DynamicLanguageSupport.registerGrammar(mimeType, null);
        AdhocMimeTypes.registerFileNameExtension("foo", mimeType);
        // Ensures that our test listener isn't notified about this
        // change while we're waiting for another - if we get here,
        // notifications are done
        l.assertChanged("foo", mimeType);
        fs = FileUtil.createMemoryFileSystem();
        assertFalse(ext, ext.contains("/"));
        assertFalse(ext, ext.contains("."));
        generatedExtensionFile = FileUtil.createData(fs.getRoot(), "Something." + ext);
        fooFile = FileUtil.createData(fs.getRoot(), "SomeFile.foo");;
        fooFile2 = FileUtil.createData(fs.getRoot(), "AnotherFile.foo");;
        glorkFile = FileUtil.createData(fs.getRoot(), "Hoobie.glork");
    }
}

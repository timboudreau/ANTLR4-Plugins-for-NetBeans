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
package org.nemesis.antlr.live.language;

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
import org.nemesis.adhoc.mime.types.AdhocMimeResolver;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.adhoc.mime.types.AdhocMimeTypesTests;
import org.nemesis.adhoc.mime.types.InvalidMimeTypeRegistrationException;
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
        assertEquals(AdhocMimeTypesTests.extensionsRegistry().toString(), mimeType, fooFile.getMIMEType());
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
        assertNull(preRegister.getLookup().lookup(AdhocDataObject.class));

        preRegister = null;

        L l = new L();
        AdhocMimeTypes.listenForRegistrations(l);

        AdhocMimeTypes.registerFileNameExtension("glork", mimeType);
        l.assertChanged("glork", mimeType);
        preRegister = null;
        for (int i = 0; i < 5; i++) {
            System.gc();
            System.runFinalization();
        }
        DataObject nue = null;
        // Changing dataobject type will complete asynchronously, and in a test
        // we have to start the event queue and a bunch of other plumbing, so
        // busywait a reasonable amount of time before assuming type changing
        // has failed
        for (int i = 0; i < 250; i++) {
            nue = DataObject.find(glorkFile);
            if (nue.getLookup().lookup(AdhocDataObject.class) != null) {
                break;
            } else {
                Thread.sleep(100);
            }
        }

        String resolvedType = Lookup.getDefault().lookup(AdhocMimeResolver.class).findMIMEType(glorkFile);
        assertNotNull(resolvedType);
        assertEquals(mimeType, resolvedType);
        assertEquals(mimeType, nue.getPrimaryFile().getMIMEType());
        assertTrue(nue.getLoader() instanceof AdhocDataLoader);
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
        AdhocMimeTypesTests.reinitAndDeleteCache();

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
        DynamicLanguages.ensureRegistered(mimeType);
        AdhocMimeTypes.registerFileNameExtension("foo", mimeType);
        // Ensures that our test listener isn't notified about this
        // change while we're waiting for another - if we get here,
        // notifications are done
        l.assertChanged("foo", mimeType);
        fs = FileUtil.createMemoryFileSystem();
        assertFalse(ext, ext.contains("/"));
        assertFalse(ext, ext.contains("."));
        generatedExtensionFile = FileUtil.createData(fs.getRoot(), "Something." + ext);
        fooFile = FileUtil.createData(fs.getRoot(), "SomeFile.foo");
        fooFile2 = FileUtil.createData(fs.getRoot(), "AnotherFile.foo");
        glorkFile = FileUtil.createData(fs.getRoot(), "Hoobie.glork");
    }
}

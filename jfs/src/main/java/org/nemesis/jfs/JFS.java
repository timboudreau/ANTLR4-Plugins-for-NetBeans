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

import com.mastfrog.function.throwing.io.IORunnable;
import com.mastfrog.function.throwing.io.IOSupplier;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.nemesis.jfs.Checkpoint.Checkpoints;
import org.nemesis.jfs.nio.BlockStorageKind;
import org.nemesis.jfs.spi.JFSUtilities;

/**
 * In-memory implementation of JavaFileManager, which creates virtual files
 * entirely stored in-memory. By default, stores content in byte arrays on the
 * Java heap. An alternative, experimental implementation of backing storage
 * using NIO direct buffers will be used if the system property 'jfs.off.heap'
 * is set to true.
 * <p>
 * This does all of the normal things StandardJavaFileManager does, and can be
 * used against Javac for cases where complication output should be written
 * entirely to memory (i.e. you want to compile something and then run it in an
 * isolating classlaoder).
 * </p>
 * <p>
 * Additionally, the 'masquerade' methods allow you to insert a file on disk or
 * an editable Document into the filesystem, and have it be read from as if it
 * were a file - so a JFS can be kept open over "live" files that are
 * always-up-to-date representations of data that is actually on disk or open in
 * an editor. Masqueraded documents are writable. Masqueraded files are not, as
 * blurring the distinction between virtual and non-virtual would make it easy
 * to have data-clobbering bugs that are catastrophic for the user.
 * </p>
 * <p>
 * The classpath is taken care of by delegating to StandardJavaFileManager for
 * the CLASS_PATH and PLATFORM_CLASS_PATH locations - to add JARs there, use the
 * setClasspath() methods, not the file-adding methods (unlike
 * StandardJavaFileManager, copying or masquerading a JAR does not cause its
 * <i>contents</i> to appear to be part of the file system).
 * </p>
 * <p>
 * A note on character sets: The best performing character set to use will
 * frequently be UTF-16, the native storage format for in-memory strings in the
 * JVM. However, this doubles the memory requirements for ASCII text, which Java
 * code frequently is. The right choice is going to depend on the size of the
 * source files being compiled, and the frequency with which they will be
 * rewritten, since that generated memory pressure.
 * </p>
 * <p>
 * Calling close() on a JFS will free all cached bytes <i>unless</i> an unclosed
 * classloader created over its storage exists, in which case resources will not
 * be freed until that classloader is closed (jfs URLs created by it need to
 * remain resolvable, and closing a JFS de-registers it from its URL stream
 * handler factory).
 * </p>
 * <p>
 * JFS also permits mapping <code>Document</code> and <code>Path</code>
 * instances into its filesystem, to operate against live documents open in an
 * editor, or local files in-place.
 * </p>
 * If <code>JFSUrlStreamHandlerFactory</code> is registered with the system (in
 * NetBeans this will happen automatically via the service provider file), then
 * this JFS's files' URLs will be resolvable - e.g.
 * jfs:CLASS_OUTPUT/com/foo/Bar.txt.
 *
 * @author Tim Boudreau
 */
public final class JFS implements JavaFileManager {

    private final Map<Location, JFSStorage> storageForLocation = new IdentityHashMap<>();
    private final String fsid;
    private final JFSStorageAllocator<?> allocator;
    private final BiConsumer<Location, FileObject> listener;
    private final StandardJavaFileManager delegate;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    final Checkpoints checkpoints = Checkpoint.newCheckpoints();
    static final Logger LOG = Logger.getLogger(JFS.class.getName());

    JFS() {
        this(JFSStorageAllocator.defaultAllocator(), null);
    }

    JFS(Charset charset) {
        this(JFSStorageAllocator.defaultAllocator().withEncoding(charset), null);
    }

    /**
     * Create a JFS, passing in a listener which will be notified of new file
     * creation.
     *
     * @param charset The character set to use for files
     * @param listener A listener
     */
    JFS(Charset charset, BiConsumer<Location, FileObject> listener) {
        this(JFSStorageAllocator.defaultAllocator().withEncoding(charset), listener);
    }

    JFS(BiConsumer<Location, FileObject> listener) {
        this(JFSStorageAllocator.defaultAllocator(), listener);
    }

    public Checkpoint newCheckpoint() {
        return checkpoints.newCheckpoint();
    }

    @SuppressWarnings("LeakingThisInConstructor")
    private JFS(JFSStorageAllocator<?> allocator, BiConsumer<Location, FileObject> listener) {
        this(allocator, listener, Locale.getDefault());
    }

    @Override
    public String toString() {
        return "JFS-" + this.fsid + "-" + allocator.encoding() + "-" + allocator;
    }

    /**
     * JFS instances have a ReentrantReadWriteLock which can be used for
     * exclusive read or write access; this method offers to run something under
     * read access. Note: This lock does not protect against concurrent
     * modifications to masqueraded files or documents which are mapped into the
     * JFS namespace.
     * <p>
     * This lock is provided for <i>convenience</i> - it is not used internally
     * by the JFS. It is up to the caller to use the read- and write-locks
     * appropriately when performing operations that modify or read the JFS;
     * these methods simply guarantee there is one lock all consumers of this
     * JFS will have access to.
     * </p>
     *
     * @param <T> The type to return
     * @param supplier A supplier
     * @return The result of the supplier
     * @throws IOException If something goes wrong
     */
    public <T> T whileReadLocked(IOSupplier<T> supplier) throws IOException {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        readLock.lock();
        try {
            return supplier.get();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * JFS instances have a ReentrantReadWriteLock which can be used for
     * exclusive read or write access; this method offers to run something under
     * <i>write</i> access. Note: This lock does not protect against concurrent
     * modifications to masqueraded files or documents which are mapped into the
     * JFS namespace.
     * <p>
     * This lock is provided for <i>convenience</i> - it is not used internally
     * by the JFS. It is up to the caller to use the read- and write-locks
     * appropriately when performing operations that modify or read the JFS;
     * these methods simply guarantee there is one lock all consumers of this
     * JFS will have access to.
     * </p>
     *
     * @param <T> The type to return
     * @param supplier A supplier
     * @return The result of the supplier
     * @throws IOException If something goes wrong
     */
    public <T> T whileWriteLocked(IOSupplier<T> supplier) throws IOException {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            return supplier.get();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * JFS instances have a ReentrantReadWriteLock which can be used for
     * exclusive read or write access; this method offers to run something under
     * write access, then downgrade the lock as described in the javadoc for
     * ReentrantReadWriteLock without unlocking. Note: This lock does not
     * protect against concurrent modifications to masqueraded files or
     * documents which are mapped into the JFS namespace.
     * <p>
     * This method guarantees that the JFS will be locked continuously once
     * runUnderWrite is entered until runUnderRead exits; runUnderRead will not
     * run if runUnderWrite throws a Throwable, in which case the write lock is
     * exited and the exception or error is rethrown.
     * </p>
     *
     * @param <T> The type to return
     * @param runUnderWrite A runnable to run under the write lock
     * @param runUnderRead A result-returning Supplier which runs under the read
     * lock subsequent to runUnderWrite, assuming no Throwable is thrown by
     * runUnderWrite
     * @return The result of the supplier
     * @throws IOException If something goes wrong
     */
    @SuppressWarnings("FinallyDiscardsException")
    public <T> T whileLockedWithWithLockDowngrade(IORunnable runUnderWrite, IOSupplier<T> runUnderRead) throws IOException {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        writeLock.lock();
        Throwable thrown = null;
        try {
            runUnderWrite.run();
        } catch (Exception | Error ex) {
            thrown = ex;
        } finally {
            if (thrown == null) {
                readLock.lock();
            }
            writeLock.unlock();
            if (thrown != null) {
                // This will rethrow whatever it is (including
                // undeclared throwables from other calls to Exceptions.chuck()
                // made by runUnderWrite) - despite appearances, the exception
                // is rethrown here.
                return Exceptions.chuck(thrown);
            }
            try {
                return runUnderRead.get();
            } finally {
                readLock.unlock();
            }
        }
    }

    private JFS(JFSStorageAllocator<?> allocator, BiConsumer<Location, FileObject> listener, Locale locale) {
        this.fsid = Integer.toString(System.identityHashCode(this), 36)
                + "."
                + Long.toString(System.currentTimeMillis(), 36).toLowerCase();
        this.allocator = allocator;
        this.listener = listener;
        this.delegate = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null,
                locale, allocator.encoding());
        JFSUrlStreamHandlerFactory.register(this);
        LOG.log(Level.FINE, "Created JFS {0}", fsid);
    }

    /**
     * In the case of files added to the filesystem using one of the
     * <code>masquerade</code> methods, which make a file appear to be part of
     * the JFS filesystem while delegating to the file or document for
     * retrieving actual bytes, this method will return the original object
     * (possibly applying some conversion). The implementation of
     * <code>JFSUtilities.convert()</code> is used for any non-trivial
     * conversions (such as NetBeans Document -&gt; FileObject -&gt; Path), so
     * the exact operation of this method depends on that.
     *
     * @param <T> The type to return as
     * @param fo The file object in question
     * @param as The type requested
     * @return An object or null
     */
    public <T> T originOf(FileObject fo, Class<T> as) {
        if (fo instanceof JFSFileObjectImpl) {
            JFSFileObjectImpl file = (JFSFileObjectImpl) fo;
            Object origin = null;
            if (file.storage instanceof DocumentBytesStorageWrapper) {
                DocumentBytesStorageWrapper stor = (DocumentBytesStorageWrapper) file.storage;
                origin = stor.document();
            } else if (file.storage instanceof FileBytesStorageWrapper) {
                FileBytesStorageWrapper stor = (FileBytesStorageWrapper) file.storage;
                origin = stor.path;
            }
            if (origin != null) {
                return JFSUtilities.convertOrigin(file, as, origin);
            }
        }
        return null;
    }

    /**
     * Get the default character set encoding this filesystem <i>returns</i>
     * data in.
     *
     * @return
     */
    public Charset encoding() {
        return allocator.encoding();
    }

    /**
     * Create a builder for creating in-memory java file managers.
     *
     * @return A builder
     */
    public static JFSBuilder builder() {
        return new JFSBuilder();
    }

    /**
     * Get a JFSFileModifications object, which collects last modified dates for
     * all files in the specified locations, and can report if they have been
     * modified, deleted, et cetera, at some later time, to figure out if a
     * rebuild is needed.
     *
     * @param locations A set of locations
     * @throws IllegalArgumentException if the set of locations is empty
     * @return A JFSFileModifications
     */
    public final JFSFileModifications status(Set<? extends Location> locations) {
        if (locations.isEmpty()) {
            throw new IllegalArgumentException("No locations");
        }
        return new JFSFileModifications(this, locations);
    }

    /**
     * Get a JFSFileModifications object, which collects last modified dates for
     * all files in the specified locations, and can report if they have been
     * modified, deleted, et cetera, at some later time, to figure out if a
     * rebuild is needed.
     *
     * @param locations A set of locations
     * @param filter Filter the set of files that are tracked
     * @throws IllegalArgumentException if the set of locations is empty
     * @return A JFSFileModifications
     */
    public final JFSFileModifications status(Set<? extends Location> locations, Predicate<UnixPath> filter) {
        if (locations.isEmpty()) {
            throw new IllegalArgumentException("No locations");
        }
        return new JFSFileModifications(this, filter, locations);
    }

    /**
     * Get a JFSFileModifications object, which collects last modified dates for
     * all files in the specified locations, and can report if they have been
     * modified, deleted, et cetera, at some later time, to figure out if a
     * rebuild is needed.
     *
     * @param first The first location
     * @param filter Filter the set of files that are tracked
     * @param more Any additional locations
     * @return A JFSFileModifications
     */
    public final JFSFileModifications status(Location first, Location... more) {
        return new JFSFileModifications(this, first, more);
    }

    /**
     * Get a JFSFileModifications object, which collects last modified dates for
     * all files in the specified locations, and can report if they have been
     * modified, deleted, et cetera, at some later time, to figure out if a
     * rebuild is needed.
     *
     * @param first The first location
     * @param more Any additional locations
     * @return A JFSFileModifications
     */
    public final JFSFileModifications status(Predicate<UnixPath> filter, Location first, Location... more) {
        return new JFSFileModifications(this, filter, first, more);
    }

    /**
     * A builder for JFS instances.
     */
    public static final class JFSBuilder {

        private BiConsumer<Location, FileObject> listener;
        private Locale locale = Locale.getDefault();
        private Charset encoding;
        private final Set<File> classpath = new LinkedHashSet<>();
        private BlockStorageKind storageKind;

        JFSBuilder() {

        }

        private void add(URL url) throws URISyntaxException {
            add(new File(url.toURI()));
        }

        private void add(File file) {
            classpath.add(file);
        }

        /**
         * Add some files to the javac classpath.
         *
         * @param jar A jar
         * @param moreJars Optional additional jars
         */
        public JFSBuilder addToClasspath(File jar, File... moreJars) {
            add(jar);
            for (File next : moreJars) {
                add(next);
            }
            return this;
        }

        /**
         * Add some files to the javac classpath.
         *
         * @param jar A jar
         * @param moreJars Optional additional jars
         */
        public JFSBuilder addToClasspath(Path jar, Path... moreJars) {
            add(jar.toFile());
            for (Path next : moreJars) {
                add(next.toFile());
            }
            return this;
        }

        /**
         * Add some files to the javac classpath.
         *
         * @param jar A jar
         * @param moreJars Optional additional jars
         */
        public JFSBuilder addToClasspath(URL jar, URL... moreJars) throws URISyntaxException {
            add(jar);
            for (URL next : moreJars) {
                add(next);
            }
            return this;
        }

        /**
         * Add some files to the javac classpath.
         *
         * @param jar A jar
         * @param moreJars Optional additional jars
         */
        public JFSBuilder addToClasspath(Iterable<URL> jars) throws URISyntaxException {
            for (URL u : jars) {
                add(u);
            }
            return this;
        }

        /**
         * Set the character set to be used by the input streams of returned
         * files.
         *
         * @param charset The character set
         * @return this
         */
        public JFSBuilder withCharset(Charset charset) {
            this.encoding = charset;
            return this;
        }

        /**
         * Use an experimental memory manager for JFS which uses a memory-mapped
         * temporary file which is deleted when the JFS is fully closed. The
         * allocator is efficient and reclaims freed storage, but may have lower
         * liveness than others due to locking.
         *
         * @return this
         */
        public JFSBuilder useExperimentalMemoryMappedStorage() {
            this.storageKind = BlockStorageKind.MAPPED_TEMP_FILE;
            return this;
        }

        /**
         * Use a memory manager backed by
         * <code>ByteBuffer.allocateDirect()</code> to avoid consuming heap
         * memory.
         *
         * @return this
         */
        public JFSBuilder useOffHeapStorage() {
            this.storageKind = BlockStorageKind.OFF_HEAP;
            return this;
        }

        /**
         * Creates an in-memory JavaFileManager for use with javac.
         *
         * @return A JFS instance
         *
         * @throws IOException If something goes wrong
         */
        public JFS build() throws IOException {
            JFSStorageAllocator<?> alloc;
            if (storageKind != null && storageKind != BlockStorageKind.HEAP) {
                alloc = NioBytesStorageAllocator.allocator();
            } else {
                alloc = JFSStorageAllocator.HEAP;
            }
            if (encoding != null) {
                alloc = alloc.withEncoding(encoding);
            }
            JFS result = new JFS(alloc, listener, locale);
            if (!classpath.isEmpty()) {
                result.setClasspathTo(classpath);
            }
            return result;
        }

        /**
         * Set the locale - this is used by the delegate StandardFileManager
         * which manages the classpath.
         *
         * @param locale A locale
         * @return this
         */
        public JFSBuilder withLocale(Locale locale) {
            this.locale = locale;
            return this;
        }

        /**
         * If set, use the experimental off-heap allocator which is backed by a
         * memory-mapped file. Note that instances created with this set
         * <i>must</i> have <code>close()</code> called on them, or they will
         * leave large amounts of memory ampped.
         *
         * @return this
         */
        public JFSBuilder useBlockStorage(BlockStorageKind kind) {
            this.storageKind = kind;
            return this;
        }

        /**
         * Add a listener which can be notified when files are added, for
         * debugging purposes.
         *
         * @param listener A consumer
         * @return this
         */
        public JFSBuilder withListener(BiConsumer<Location, FileObject> listener) {
            assert listener != null;
            if (this.listener != null) {
                this.listener = this.listener.andThen(listener);
            } else {
                this.listener = listener;
            }
            return this;
        }
    }

    private Collection<File> currentClasspath = Collections.emptyList();

    public String currentClasspath() {
        StringBuilder sb = new StringBuilder();
        currentClasspath.forEach((f) -> {
            if (sb.length() > 0) {
                sb.append(":");
            }
            sb.append(f.getPath());
        });
        return sb.toString();
    }

    public Collection<? extends File> classpathContents() {
        return Collections.unmodifiableCollection(currentClasspath);
    }

    public void setClasspathTo(Collection<File> files) throws IOException {
        LOG.log(Level.FINEST, "Set classpath to {0}", files);
        currentClasspath = files;
        delegate.setLocation(StandardLocation.CLASS_PATH, new ArrayList<>(files));
    }

    public void clear(Location loc) throws IOException {
        JFSStorage stor = storageForLocation.remove(loc);
        if (stor != null) {
            stor.close();
        }
    }

    /**
     * Add JARs or folders to the compile classpath. Note: this delegates to the
     * StandardJavaFileManager and does not copy JARs or their contents into
     * memory.
     *
     * @param jars
     * @throws IOException
     */
    public void setClasspath(Path... jars) throws IOException {
        List<File> files = new ArrayList<>(jars.length);
        for (Path p : jars) {
            files.add(p.toFile());
        }
        delegate.setLocation(StandardLocation.CLASS_PATH, files);
    }

    public void setClasspath(URL... jars) throws IOException, URISyntaxException {
        List<File> files = new ArrayList<>(jars.length);
        for (URL up : jars) {
            files.add(new File(up.toURI()));
        }
        delegate.setLocation(StandardLocation.CLASS_PATH, files);
    }

    public void setClasspath(Collection<Path> jars) throws IOException {
        List<File> files = new ArrayList<>(jars.size());
        for (Path p : jars) {
            files.add(p.toFile());
        }
        delegate.setLocation(StandardLocation.CLASS_PATH, files);
    }

    JFSStorageAllocator<?> alloc() {
        return allocator;
    }

    public String id() {
        return fsid;
    }

    boolean is(String id) {
        return id().equals(id);
    }

    JFSStorage storageForLocation(Location loc, boolean create) {
        JFSStorage result = storageForLocation.get(loc);
        if (result == null && create) {
            // Ensure we don't have a cached merged storage that does
            // not contain all locations but could be used to resolve
            // urls
            storageForLocation.remove(JFSStorage.MERGED_LOCATION);
            result = new JFSStorage(loc, this, allocator, listener);
            storageForLocation.put(loc, result);
        }
        return result;
    }

    /**
     * Retrieve the number of bytes used by all file objects in this JFS.
     *
     * @return The size
     */
    public long size() {
        long result = 0;
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            result += e.getValue().size();
        }
        return result;
    }

    /**
     * Discard the bytes held in a particular location. Useful for reusing a JFS
     * for multiple invocations.
     *
     * @param locations
     * @throws IOException
     */
    public void closeLocations(Location... locations) throws IOException {
        for (Location loc : locations) {
            JFSStorage store = storageForLocation.get(loc);
            if (store != null) {
                store.close();
                storageForLocation.remove(loc);
            }
        }
    }

    /**
     * Create a classloader over a particular location.
     *
     * @param location
     * @return A classloader
     */
    @Override
    public JFSClassLoader getClassLoader(Location location) {
        JFSStorage storage = storageForLocation(location, false);
        try {
            return storage == null ? null
                    : storage.createClassLoader(delegate.getClassLoader(StandardLocation.CLASS_PATH));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Create a classloader specifying the parent classloader.
     *
     * @param location The location
     * @param parent The parent classloader
     * @return A classloader
     * @throws IOException
     */
    public JFSClassLoader getClassLoader(Location location, ClassLoader parent) throws IOException {
        JFSStorage storage = storageForLocation(location, false);
        return storage == null ? null : storage.createClassLoader(parent);
    }

    /**
     * Create a classloader subsuming multiple locations in this JFS, with
     * parents in order of precedence.
     *
     * @param includeEmptyLocations If true, create storage areas for locations
     * which have not yet been used, anticipating loading classes from them. If
     * false, this method may return null if this JFS is empty.
     *
     * @param parent The parent classloader
     * @param loc The location to create the initial classloader over
     * @param more Additional locations
     * @return A class loader
     * @throws IOException If something goes wrongF
     */
    public JFSClassLoader getClassLoader(boolean includeEmptyLocations,
            ClassLoader parent, Location loc, Location... more) throws IOException {
        if (more.length == 0) {
            return getClassLoader(loc, parent);
        }
        Set<Location> all = new LinkedHashSet<>();
        Location[] locs = new Location[more.length + 1];
        System.arraycopy(more, 0, locs, 1, more.length);
        locs[0] = loc;
        JFSClassLoader result = null;
        for (int i = 0; i < locs.length; i++) {
            Location l = locs[i];
            if (all.contains(l)) {
                continue;
            }
            all.add(l);
            JFSStorage storage = storageForLocation(l, includeEmptyLocations);
            if (storage == null) {
                continue;
            }
            if (result == null) {
                result = storage.createClassLoader(parent);
            } else {
                result = storage.createClassLoader(result);
            }
        }
        return result;
    }

    public int list(Location location, BiConsumer<Location, JFSFileObject> consumer) {
        JFSStorage stor = storageForLocation.get(location);
        if (stor == null) {
            return 0;
        }
        return stor.list(consumer);
    }

    /**
     * For debugging/logging purposes, create a string listing of some locations
     * in this JFS.
     *
     * @param first`The first location
     * @param more More locations
     * @return A string listing
     */
    public String list(Location first, Location... more) {
        StringBuilder sb = new StringBuilder();
        BiConsumer<Location, JFSFileObject> writer = (loc, file) -> {
            sb.append(" * ").append(loc).append(file.getName()).append('\n');
        };
        list(first, writer);
        for (Location l : more) {
            list(l, writer);
        }
        return sb.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        if (StandardLocation.PLATFORM_CLASS_PATH == location
                || StandardLocation.CLASS_PATH == location
                || StandardLocation.ANNOTATION_PROCESSOR_PATH == location) {
            Iterable<JavaFileObject> fos
                    = delegate.list(location, packageName, kinds, recurse);
            if (StandardLocation.CLASS_PATH == location) {
                // If we want the compiler to be able to load previously compiled classes in this JFS,
                // then the listing for CLASS_PATH needs to include those from CLASS_OUTPUT
                Iterable<JavaFileObject> localClasses = _list(StandardLocation.CLASS_OUTPUT, packageName, kinds, recurse);
                if (storageForLocation.containsKey(location)) {
                    return CollectionUtils.concatenate(localClasses, fos, _list(location, packageName, kinds, recurse));
                } else {
                    return CollectionUtils.concatenate(localClasses, fos);
                }
            }
            if (storageForLocation.containsKey(location)) {
                return CollectionUtils.concatenate(fos, _list(location, packageName, kinds, recurse));
            }
            return fos;
        }
        return _list(location, packageName, kinds, recurse);
    }

    public JFSFileObject find(String locationName, String path) throws FileNotFoundException {
        JFSStorage storage = storageForLocation(locationName);
        if (storage == null) {
            throw new FileNotFoundException("No storage for " + locationName + " when searching for " + path);
        }
        JFSFileObjectImpl fo = storage.find(Name.forFileName(path), false);
        if (fo == null) {
            throw new FileNotFoundException("No JFSFileObject " + path + " in " + locationName);
        }
        return fo;
    }

    private Iterable<JavaFileObject> _list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        JFSStorage stor = storageForLocation(location, false);
        if (stor == null) {
            return Collections.emptyList();
        }
        Iterable<JavaFileObject> result = stor.list(packageName, kinds, recurse);
        LOG.log(Level.FINEST, "List {0} pkg {1} of {2} gets {3}",
                new Object[]{location, packageName, kinds, result});
        return result;
    }

    public Map<JFSFileObject, Location> listAll() {
        Map<JFSFileObject, Location> all = new HashMap<>();
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            Location loc = e.getKey();
            JFSStorage s = e.getValue();
            for (JFSFileObject fo : s.listAll(EnumSet.allOf(JavaFileObject.Kind.class), true)) {
                all.put(fo, loc);
            }
        }
        return all;
    }

    public void listAll(BiConsumer<Location, JFSFileObject> cons) {
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            Location loc = e.getKey();
            JFSStorage s = e.getValue();
            for (JFSFileObject fo : s.listAll(EnumSet.allOf(JavaFileObject.Kind.class), true)) {
                cons.accept(loc, fo);
            }
        }
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof JFSFileObjectImpl) {
            Name name = ((JFSFileObjectImpl) file).name();
            return name.asClassName();
        }
        return delegate.inferBinaryName(location, file);
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        return a.equals(b);
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        return true;
    }

    @Override
    public boolean hasLocation(Location location) {
        return storageForLocation.containsKey(location)
                || delegate.hasLocation(location);
    }

    JFSStorage storageForLocation(String locationName) {
        JFSStorage result = null;
        if (result == null && JFSStorage.MERGED_LOCATION.getName().equals(locationName)) {
            result = JFSStorage.createMerged(fsid, storageForLocation.values());
            storageForLocation.put(JFSStorage.MERGED_LOCATION, result);
            return result;
        }
        Location loc = StandardLocation.locationFor(locationName);
        if (loc != null) {
            result = storageForLocation.get(loc);
            if (result != null) {
                return result;
            }
        }
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            if (e.getKey().getName().equals(locationName)) {
                if (e.getKey().isOutputLocation() == loc.isOutputLocation()) {
                    result = e.getValue();
                }
                break;
            }
        }
        return result;
    }

    @Override
    public JFSJavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        Name name = Name.forClassName(className, kind);
        JFSStorage stor = storageForLocation(location, false);
        if (stor == null) {
            throw new FileNotFoundException("No files in location " + location.getName());
        }
        JFSJavaFileObject result = stor.findJavaFileObject(name, false);
        if (result == null) {
            throw new FileNotFoundException("Did not find in " + location + ": " + name);
        }
        LOG.log(Level.FINEST, "getJavaFileForInput {0} {1} {2} gets {3}",
                new Object[]{location, className, kind, result});
        return result;
    }

    @Override
    public JFSJavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        Name name = Name.forClassName(className, kind);
        JFSStorage stor = storageForLocation(location, true);
        JFSJavaFileObject result = stor.findJavaFileObject(name, true);
        checkpoints.touch(result);
        LOG.log(Level.FINEST, "getJavaFileForOutput {0} {1} {2} gets {3}",
                new Object[]{location, className, kind, result});
        return result;
    }

    @Override
    public JFSFileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        new Exception("Get file for input " + packageName + " type " + relativeName).printStackTrace();
        Name name = Name.forFileName(packageName, relativeName);
        JFSStorage stor = storageForLocation(location, false);
        if (stor == null) {
            throw new FileNotFoundException("Nothing stored in location: " + location + " (looking up " + name + ")");
        }
        JFSFileObjectImpl obj = stor.find(name, false);
        if (obj == null) {
            throw new FileNotFoundException("Did not find in " + location + ": " + name);
        }
        LOG.log(Level.FINEST, "getFileForInput {0} {1} gets {2}",
                new Object[]{location, packageName, obj});
        return obj;
    }

    public JFSFileObject getFileForOutput(Location location, UnixPath filePath) throws IOException {
        Name nm = Name.forPath(filePath);
        return getFileForOutput(location, nm.packageName(), nm.getName(), null);
    }

    public JFSFileObject getSourceFileForOutput(String filePath) throws IOException {
        Name nm = Name.forFileName(filePath);
        return getFileForOutput(StandardLocation.SOURCE_PATH, nm.packageName(), nm.getName(), null);
    }

    public JFSFileObject getSourceFileForOutput(String filePath, Location loc) throws IOException {
        Name nm = Name.forFileName(filePath);
        return getFileForOutput(loc, nm.packageName(), nm.getName(), null);
    }

    /**
     * For debugging purposes, copy the contents of (some locations of) this JFS
     * to disk somewhere.
     *
     * @param dir A folder The destination folder the folder structure of this
     * JFS will be recreated in.
     * @param locs The set of locations to dump
     * @return The set of files written
     * @throws IOException If something goes wrong
     */
    public Set<Path> dumpToDisk(Path dir, Location... locs) throws IOException {
        assert Files.isDirectory(dir) : "Not a directory: " + dir;
        if (locs.length == 0) {
            return Collections.emptySet();
        }
        Set<Path> result = new HashSet<>();
        Set<Location> all = setOf(locs);
        for (Location loc : all) {
            JFSStorage stor = storageForLocation.get(loc);
            if (stor != null) {
                stor.forEach((name, file) -> {
                    Path up = dir.resolve(name.toPath().toNativePath());
                    if (!Files.exists(up.getParent())) {
                        Files.createDirectories(up.getParent());
                    }
                    Files.write(up, file.asBytes(), CREATE,
                            TRUNCATE_EXISTING, WRITE);
                    result.add(up);
                });
            }
        }
        return result;
    }

    @Override
    public JFSFileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
        Name name = Name.forFileName(packageName, relativeName);
        JFSStorage stor = storageForLocation(location, true);
        boolean java = false;
        switch (name.kind()) {
            case CLASS:
            case SOURCE:
            case HTML:
                java = true;
        }
        JFSFileObject result;
        if (java) {
            result = stor.findJavaFileObject(name, true);
        } else {
            result = stor.find(name, true, checkpoints);
        }
        if (result != null) {
            checkpoints.touch(result);
        }
        LOG.log(Level.FINEST, "getFileForOutput {0} {1} - {2} gets {3} kind {4}",
                new Object[]{location, packageName, name, result, name.kind()});
        return result;
    }

    public boolean isEmpty() {
        if (storageForLocation.isEmpty()) {
            return true;
        }
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            if (e.getValue().isClosed()) {
                continue;
            }
            if (!e.getValue().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convenience method for getting an existing JFSFileObject by path if it
     * exists.
     *
     * @param location The location
     * @param path The path
     * @return A file object or null
     */
    public JFSFileObject get(Location location, UnixPath path) {
        notNull("path", path);
        JFSStorage stor = storageForLocation(location, false);
        return stor == null ? null : stor.find(Name.forPath(path));
    }

    /**
     * Convenience method for getting an existing JFSFileObject, creating it if
     * it does not exist.
     *
     * @param location The location
     * @param path The path
     * @param create If true, create the file if it does not already exist
     * @return A file object or null
     */
    public JFSFileObject get(Location location, UnixPath path, boolean create) {
        JFSStorage stor = storageForLocation(location, create);
        return stor == null ? null : stor.find(Name.forPath(path), create);
    }

    /**
     * Does nothing in a JFS.
     *
     * @throws IOException
     */
    @Override
    public void flush() throws IOException {
        // do nothing
    }

    /**
     * Returns true if close() was called - if a live classloader exists over
     * some portion of this filesystem, this JFS may not be fully closed yet
     * (URLs produced by that classloader need still to be resolvable).
     *
     * @return True if close was called and closing any remaining classloaders
     * will delete the storage
     */
    public boolean closeCalled() {
        return closeCalled;
    }

    /**
     * Returns true if close() has been called and no classloader is holding
     * this JFS open - the contents have been discarded.
     *
     * @return
     */
    public boolean isReallyClosed() {
        return closeCalled && storageForLocation.isEmpty();
    }

    private volatile boolean closeCalled;
    private ThreadLocal<Boolean> inClose = ThreadLocal.withInitial(() -> false);

    /**
     * Close this JFS. Resources may not be released if a classloader still
     * exists which can create URLs into this JFS.
     */
    @Override
    public void close() throws IOException {
        if (inClose.get()) {
            return;
        }
        inClose.set(true);
        try {
            closeCalled = true;
            Set<Location> toRemove = new HashSet<>();
            for (Map.Entry<Location, JFSStorage> e : new ArrayList<>(storageForLocation.entrySet())) {
                if (e.getValue().close()) {
                    toRemove.add(e.getKey());
                } else {
                    LOG.log(Level.FINE, "JFS.close(): Will not close {0} in "
                            + "{1} - a live classloader over it exists",
                            new Object[]{e.getKey(), fsid});
                }
            }
            for (Location loc : toRemove) {
                LOG.log(Level.FINER, "JFS.close(): removing {0} from {1}", new Object[]{loc, fsid});
                storageForLocation.remove(loc);
            }
            if (storageForLocation.isEmpty()) {
                LOG.log(Level.FINER, "JFS.close(): empty and no live "
                        + "classloaders - unregistering {0}", fsid);
                JFSUrlStreamHandlerFactory.unregister(this);
                delegate.close();
                allocator.destroy();
            } else {
                LOG.log(Level.FINER, "JFS.close(): a classloader may still exist "
                        + "- not unregistering {0} until last classloader "
                        + "is closed", fsid);
            }
        } finally {
            inClose.set(false);
        }
    }

    void lastClassloaderClosed(Location loc) throws IOException {
        if (closeCalled) {
            storageForLocation.remove(loc);
            close();
        }
    }

    @Override
    public int isSupportedOption(String option) {
        return -1;
    }

    /**
     * Alias a file into this filesystem without reading its bytes or testing
     * its existence unless an attempt is made to read it, and asserting a
     * particular file encoding for the file which may be different than that of
     * this JFS. The resulting JFSFileObject cannot be written to.
     *
     * @param file The file's actual path on disk
     * @param loc The location to include it at
     * @param asPath The path that should be used locally
     * @param encoding The character set the file in question uses
     * @return A file object
     */
    public JFSFileObject masquerade(Path file, Location loc, UnixPath asPath, Charset encoding) {
        LOG.log(Level.FINEST, "JFS.masquerade(Path): Add {0} as {1} bytes to {2} in {3} with {4}",
                new Object[]{file, asPath, loc, fsid, encoding.name()});
        JFSFileObject fo = storageForLocation(loc, true).addRealFile(asPath, file, encoding);
        checkpoints.touch(fo);
        return fo;
    }

    /**
     * Alias a file into this filesystem without reading its bytes or testing
     * its existence unless an attempt is made to read it. The resulting
     * JFSFileObject cannot be written to.
     *
     * @param file The file's actual path on disk
     * @param loc The location to include it at
     * @param asPath The path that should be used locally
     * @return A file object
     */
    public JFSFileObject masquerade(Path file, Location loc, UnixPath asPath) {
        LOG.log(Level.FINEST, "JFS.masquerade(Path): Add {0} as {1} bytes to {2} in {3}",
                new Object[]{file, asPath, loc, fsid});
        JFSFileObject result = storageForLocation(loc, true).addRealFile(asPath, file);
        checkpoints.touch(result);
        return result;
    }

    /**
     * Alias a file into this filesystem without reading its bytes or testing
     * its existence unless an attempt is made to read it. The resulting
     * JFSFileObject cannot be written to.
     *
     * @param file The file's actual path on disk
     * @param loc The location to include it at
     * @param asPath The path that should be used locally
     * @return A file object
     */
    public JFSFileObject masquerade(Document doc, Location loc, UnixPath asPath) {
        LOG.log(Level.FINEST, "JFS.masquerade(Document): Add {0} as {1} bytes to {2} in {3}",
                new Object[]{doc, asPath, loc, fsid});
        JFSFileObject result = storageForLocation(loc, true).addDocument(asPath, doc);
        checkpoints.touch(result);
        return result;
    }

    /**
     * Mainly for testing, copy a folder full of files into storage.
     *
     * @param dir The folder
     * @throws IOException If something goes wrong
     */
    public void load(Path dir, boolean copy) throws IOException {
        LOG.log(Level.FINEST, "JFS.load(): Add children of {0} copying? {1} bytes to {2}",
                new Object[]{dir, copy, fsid});
        Files.walkFileTree(dir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (copy) {
                    copy(file, dir, StandardLocation.SOURCE_PATH);
                } else {
                    Path rel = dir.relativize(file);
                    masquerade(file, StandardLocation.SOURCE_PATH, UnixPath.get(rel));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Copy a single file into a location, giving it a path relative to a parent
     * file. This immediately reads the file and copies its bytes.
     *
     * @param file The file to copy
     * @param relativeTo A parent folder (it must be) of the file to copy
     * @param location The location to copy to
     * @return A file object
     * @throws IOException If the path is not relative, the file is not
     * readable, or something else goes wrong
     */
    public JFSFileObject copy(Path file, Path relativeTo, Location location) throws IOException {
        if (!file.startsWith(relativeTo)) {
            throw new IOException(file + " is not a child of " + relativeTo);
        }
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        Name name = Name.forPath(UnixPath.get(file), UnixPath.get(relativeTo));
        byte[] bytes = convertEncoding(Files.readAllBytes(file), file, name);

        LOG.log(Level.FINEST, "JFS.copy(): Copying file {0} as {1} to {2} in {3}",
                new Object[]{file, name, location, fsid});
        return copyBytes(name, bytes, location, lastModified);
    }

    /**
     * Copy a single file into a location, giving it a path relative to a parent
     * file. This immediately reads the file and copies its bytes.
     *
     * @param file The file to copy
     * @param relativeTo A parent folder (it must be) of the file to copy
     * @param location The location to copy to
     * @return A file object
     * @throws IOException If the path is not relative, the file is not
     * readable, or something else goes wrong
     */
    public JFSFileObject copy(Path file, Location location, UnixPath as) throws IOException {
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        Name name = Name.forPath(as);
        byte[] bytes = convertEncoding(Files.readAllBytes(file), file, name);
        LOG.log(Level.FINEST, "JFS.copy(): Copying file {0} as {1} to {2} in {3}",
                new Object[]{file, name, location, fsid});
        return copyBytes(name, bytes, location, lastModified);
    }

    public JFSFileObject copy(Path file, Charset fileEncoding, Location location, UnixPath as) throws IOException {
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        Name name = Name.forPath(as);
        byte[] bytes = convertEncoding(Files.readAllBytes(file), file, fileEncoding, name);
        LOG.log(Level.FINEST, "JFS.copy(): Copying file {0} as {1} to {2} in {3}",
                new Object[]{file, name, location, fsid});
        return copyBytes(name, bytes, location, lastModified);
    }

    /**
     * Shared implementation for copying an array of bytes into the filesystem
     * at a specific location.
     *
     * @param name A name
     * @param bytes Some btes
     * @param location The location
     * @param lastModified The last modified time
     * @return A file object
     * @throws IOException
     */
    private JFSFileObject copyBytes(Name name, byte[] bytes, Location location, long lastModified) throws IOException {
        JFSStorage storage = storageForLocation(location, true);
        boolean java = name.kind() == CLASS || name.kind() == SOURCE;
        JFSFileObjectImpl result = storage.allocate(name, java);
        result.setBytes(bytes, lastModified);
        checkpoints.touch(result);
        return result;
    }

    /**
     * Converts the character set of bytes being copied to that of this JFS.
     *
     * @param bytes The bytes
     * @param forFile The original file path
     * @param name The name to be used
     * @return A byte array, which may have been converted from the original
     */
    private byte[] convertEncoding(byte[] bytes, Path forFile, Name name) {
        if (name.kind() == JavaFileObject.Kind.CLASS) {
            return bytes;
        }
        // If we are copying in a file from disk, convert it to the encoding
        // this JFS is using
        Charset inputCharset = JFSUtilities.encodingFor(forFile);
        if (inputCharset != null && !inputCharset.equals(encoding())) {
            String in = new String(bytes, inputCharset);
            return in.getBytes(encoding());
        }
        return bytes;
    }

    private byte[] convertEncoding(byte[] bytes, Path forFile, Charset fileEncoding, Name name) {
        if (name.kind() == JavaFileObject.Kind.CLASS) {
            return bytes;
        }
        // If we are copying in a file from disk, convert it to the encoding
        // this JFS is using
        Charset inputCharset = fileEncoding;
        if (inputCharset != null && !inputCharset.equals(encoding())) {
            String in = new String(bytes, inputCharset);
            return in.getBytes(encoding());
        }
        return bytes;
    }

    /**
     * For testing, copy an array of bytes into a file in some location.
     *
     * @param path
     * @param location
     * @param bytes
     * @return
     * @throws IOException
     */
    public JFSFileObject create(UnixPath path, Location location, byte[] bytes) throws IOException {
        long lastModified = System.currentTimeMillis();
        Name name = Name.forPath(path);
        LOG.log(Level.FINEST, "JFS.create(): Add {0} with {1} bytes to {2} in {3}",
                new Object[]{name, bytes.length, location, fsid});
        JFSStorage storage = storageForLocation(location, true);
        boolean java = name.kind() == CLASS || name.kind() == SOURCE;
        JFSFileObjectImpl result = storage.allocate(name, java);
        result.setBytes(bytes, lastModified);
        checkpoints.touch(result);
        return result;
    }

    /**
     * Create a fileobject passing a string (will be stored as utf 8).
     *
     * @param path The path
     * @param location The location
     * @param string The string
     * @return A fileObject
     * @throws IOException if something goes wrong
     */
    public JFSFileObject create(UnixPath path, Location location, String string) throws IOException {
        return create(path, location, string.getBytes(allocator.encoding()));
    }
}

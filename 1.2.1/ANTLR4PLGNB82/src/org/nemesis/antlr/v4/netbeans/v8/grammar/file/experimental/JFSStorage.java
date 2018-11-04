package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import javax.swing.text.Document;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import org.openide.util.WeakSet;

/**
 * Manages bytes storage for JFS - allocates JFSFileObjectImpls and delegates to
 * the bytes storage allocator for actually creating backing buffers.
 *
 * A note on memory management: We keep a reference to the filesystem instance
 * entirely to hold it in memory (so it stays registered for URL resolution) via
 * the reference chain JFSClassLoader -> JFSStorage -> JFS -> (WeakReference in
 * JFSUrlStreamHandlerFactory's registry). That way a JFS can be "closed", so it
 * can be discarded entirely with all its storage once the classloader is
 * closed.
 *
 * @author Tim Boudreau
 */
final class JFSStorage {

    private final Location location;
    private final Map<Name, JFSFileObjectImpl> files = new ConcurrentHashMap<>();
    private final String fileSystemId;
    private final BiConsumer<Location, FileObject> listener;
    private final Set<ClassLoader> liveClassLoaders
            = Collections.synchronizedSet(new WeakSet<>());
    private final JFSStorageAllocator<?> alloc;
    private volatile boolean closePendingClassloadersGone;
    private volatile JFS filesystem;

    JFSStorage(Location location, JFS jfs, BiConsumer<Location, FileObject> listener) {
        this(location, jfs, jfs.alloc(), listener);
    }

    JFSStorage(Location location, JFS jfs, JFSStorageAllocator<?> alloc, BiConsumer<Location, FileObject> listener) {
        this.location = location;
        this.alloc = alloc;
        this.fileSystemId = jfs.id();
        this.filesystem = jfs;
        this.listener = listener;
    }

    boolean close() throws IOException {
        if (!liveClassLoaders.isEmpty()) {
            closePendingClassloadersGone = true;
            return false;
        } else {
            _close();
            return true;
        }
    }

    void classloaderClosed(JFSClassLoader cl) throws IOException {
        liveClassLoaders.remove(cl);
        if (closePendingClassloadersGone) {
            _close();
        }
    }

    private void _close() throws IOException {
        JFS fs = filesystem;
        if (fs != null) {
            fs.lastClassloaderClosed(location);
        }
        for (JFSFileObjectImpl fo : new HashSet<>(this.files.values())) {
            fo.delete();
        }
        if (this.files.isEmpty()) { // theoretically something could be added while we were iterating
            this.files.clear();
            // The only reason we keep a reference to the filesystem
            // is so that it cannot be garbage collected (breaking URL
            // resolution of FileObjects) while a classloader that might
            // use it is still alive
            filesystem = null;
        }
    }

    JFSClassLoader createClassLoader(ClassLoader parent) throws IOException {
        JFSClassLoader result = new JFSClassLoader(this, parent);
        closePendingClassloadersGone = false;
        liveClassLoaders.add(result);
        return result;
    }

    public Charset encoding() {
        return alloc.encoding();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.toString();
    }

    boolean isClosed() {
        return filesystem == null;
    }

    JFS jfs() {
        return filesystem;
    }

    static JFSStorage createMerged(String fsId, Iterable<JFSStorage> others) {
        if (!others.iterator().hasNext()) {
            throw new IllegalStateException("Empty");
        }
        JFS filesystem = null;
        for (JFSStorage s : others) {
            filesystem = s.jfs();
            if (filesystem != null) {
                break;
            }
        }
        if (filesystem == null) {
            throw new IllegalStateException("All storages are closed");
        }
        JFSStorageAllocator<?> readOnlyAlloc = filesystem
                .alloc().toReadOnlyAllocator();

        JFSStorage result = new JFSStorage(MERGED_LOCATION, filesystem,
                readOnlyAlloc, null);
        for (JFSStorage o : others) {
            JFS test = o.jfs();
            if (test != null && test != filesystem) {
                throw new IllegalStateException("Storage is not from this filesystem");
            }
            o.mergeInto(result);
        }
        return result;
    }

    Set<JFSFileObjectImpl> scan(JavaFileObject.Kind kind) {
        Set<JFSFileObjectImpl> result = new HashSet<>();
        for (Map.Entry<Name, JFSFileObjectImpl> e : files.entrySet()) {
            if (kind.equals(e.getKey().kind())) {
                result.add(e.getValue());
            }
        }
        return result;
    }

    void mergeInto(JFSStorage other) {
        other.files.putAll(files);
    }

    String id() {
        return fileSystemId;
    }

    Location location() {
        return location;
    }

    String urlPath() {
        return id() + '/' + location.getName();
    }

    Set<String> listPackageNames() {
        Set<String> result = new HashSet<>();
        for (Map.Entry<Name, JFSFileObjectImpl> e : files.entrySet()) {
            if (e.getValue() instanceof JFSJavaFileObjectImpl) {
                String nm = e.getKey().packageName();
                if (!nm.isEmpty()) {
                    result.add(nm);
                }
            }
        }
        return result;
    }

    public long size() {
        long result = 0;
        for (Map.Entry<Name, JFSFileObjectImpl> e : files.entrySet()) {
            result += e.getValue().length();
        }
        return result;
    }

    public boolean delete(Name name, JFSBytesStorage storage) {
        storage.discard();
        alloc.onDiscard(storage);
        return files.remove(name) != null;
    }

    JFSFileObjectImpl addRealFile(Path localName, Path realFile) {
        return addRealFile(localName, realFile, alloc.encoding());
    }

    JFSFileObjectImpl addRealFile(Path localName, Path realFile, Charset encoding) {
        Name name = Name.forPath(localName);
        boolean java = name.kind() == CLASS || name.kind() == SOURCE;
        FileBytesStorageWrapper wrapper = new FileBytesStorageWrapper(this, realFile);
        JFSFileObjectImpl fo
                = java ? new JFSJavaFileObjectImpl(wrapper, location, name, encoding)
                        : new JFSFileObjectImpl(wrapper, location, name, encoding);
        files.put(name, fo);
        if (listener != null) {
            listener.accept(location, fo);
        }
        return fo;
    }

    JFSFileObject addDocument(Path asPath, Document doc) {
        Name name = Name.forPath(asPath);
        boolean java = name.kind() == CLASS || name.kind() == SOURCE;
        DocumentBytesStorageWrapper wrapper = new DocumentBytesStorageWrapper(this, doc);
        JFSFileObjectImpl fo
                = java ? new JFSJavaFileObjectImpl(wrapper, location, name, encoding())
                        : new JFSFileObjectImpl(wrapper, location, name, encoding());
        files.put(name, fo);
        if (listener != null) {
            listener.accept(location, fo);
        }
        return fo;
    }

    JFSFileObjectImpl allocate(Name name, boolean java) {
        JFSBytesStorage storage = alloc.allocate(this, name, location);
        java |= name.kind() == CLASS || name.kind() == SOURCE;
        JFSFileObjectImpl fo
                = java ? new JFSJavaFileObjectImpl(storage, location, name, alloc.encoding())
                        : new JFSFileObjectImpl(storage, location, name, alloc.encoding());
        files.put(name, fo);
        if (listener != null) {
            listener.accept(location, fo);
        }
        return fo;
    }

    JFSFileObjectImpl find(Name name) {
        return find(name, false);
    }

    JFSFileObjectImpl find(Name name, boolean create) {
        JFSFileObjectImpl result = files.get(name);
        if (result == null && create) {
            result = allocate(name, false);
        }
        return result;
    }

    JFSJavaFileObjectImpl findJavaFileObject(Name name) {
        return findJavaFileObject(name, false);
    }

    JFSJavaFileObjectImpl findJavaFileObject(Name name, boolean create) {
        JFSFileObjectImpl result = find(name, create);
        if (result != null) {
            JFSJavaFileObjectImpl jfsFo = result.toJavaFileObject();
            if (jfsFo != result) {
                files.put(name, jfsFo);
            }
            return jfsFo;
        } else if (create) {
            return allocate(name, true).toJavaFileObject();
        }
        return null;
    }

    Iterable<JavaFileObject> list(String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) {
        Path path = Name.packageToPath(packageName);
        List<JavaFileObject> result = new ArrayList<>(files.size());
        for (Map.Entry<Name, JFSFileObjectImpl> e : files.entrySet()) {
            if (e.getKey().isPackage(path, recurse)) {
                if (e.getValue() instanceof JavaFileObject) {
                    result.add(e.getValue().toJavaFileObject());
                }
            }
        }
        return result;
    }

    Iterable<JFSFileObject> listAll(Set<JavaFileObject.Kind> kinds, boolean recurse) {
        List<JFSFileObject> result = new ArrayList<>(files.size());
        files.entrySet().forEach((e) -> {
            result.add(e.getValue().toJavaFileObject());
        });
        return result;
    }

    static final Location MERGED_LOCATION = new Location() {
        @Override
        public String getName() {
            return "merged";
        }

        @Override
        public boolean isOutputLocation() {
            return true;
        }

        public boolean equals(Object o) {
            return o == this;
        }

        public int hashCode() {
            return 1;
        }

        public String toString() {
            return getName();
        }
    };
}

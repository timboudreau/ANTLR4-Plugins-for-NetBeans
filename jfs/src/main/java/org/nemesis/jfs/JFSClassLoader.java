package org.nemesis.jfs;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileObject;
import org.nemesis.jfs.isolation.ExposedFindClass;
import org.nemesis.jfs.isolation.Lockless;

/**
 * Public so the close method is visible.
 *
 * @author Tim Boudreau
 */
@Lockless
public final class JFSClassLoader extends ClassLoader implements Closeable, AutoCloseable, ExposedFindClass {

    private final JFSStorage storage;
    private final Map<String, Class<?>> classes = new HashMap<>();
    private volatile Package[] packages;

    JFSClassLoader(JFSStorage storage) throws IOException {
        this(storage, ClassLoader.getSystemClassLoader());
    }

    JFSClassLoader(JFSStorage storage, ClassLoader ldr) throws IOException {
        super(ldr == null ? ClassLoader.getSystemClassLoader() : ldr);
        this.storage = storage;
        Set<Package> packages = new HashSet<>();
        String ver = "1." + Long.toString(System.currentTimeMillis());
        for (String pkg : storage.listPackageNames()) {
            packages.add(definePackage(pkg, pkg, ver, storage.id(),
                    "-", ver, "-", null));
        }
        this.packages = packages.toArray(new Package[packages.size()]);
        List<JFSFileObjectImpl> all = new ArrayList<>(storage.scan(JavaFileObject.Kind.CLASS));
        Set<String> names = new HashSet<>();
        // Poor man's dependency order - shuffle until it's right
        for (JFSFileObjectImpl file : all) {
            String nm = file.getName();
            // strip .class from the file name
            names.add(file.getName().substring(0, nm.length()-6));
        }
        while (!all.isEmpty()) {
            for (Iterator<JFSFileObjectImpl> iter = all.iterator(); iter.hasNext();) {
                JFSFileObjectImpl clazz = iter.next();
                try {
                    Class<?> type = defineClass(null, clazz.asByteBuffer(), null);
                    classes.put(type.getName(), type);
                    classes.put(clazz.name().asClassName(), type);
                    iter.remove();
                } catch (NoClassDefFoundError err) {
                    // Change the iteration order randomly
                    if (!names.contains(err.getMessage()) || all.size() == 1) {
                        throw err;
                    }
                    Collections.shuffle(all);
                    break;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        packages = new Package[0];
        classes.clear();
        storage.classloaderClosed(this);
    }

    @Override
    protected Object getClassLoadingLock(String className) {
        // None needed, we already defined all classes we ever will in our
        // constructor
        return new Object();
    }

    @Override
    public Class<?> lookupClass(String name) throws ClassNotFoundException {
        return findClass(name);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> type = classes.get(name);
        if (type != null) {
            return type;
        }
        return super.findClass(name);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Package getPackage(String name) {
        Package[] pkgs = packages;
        if (pkgs != null) { // in constructor
            for (Package p : pkgs) {
                if (name.equals(p.getName())) {
                    return p;
                }
            }
        }
        return super.getPackage(name);
    }

    @Override
    public URL getResource(String name) {
        JFSFileObjectImpl fo = storage.find(Name.forFileName(name));
        return fo != null ? fo.toURL() : super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        JFSFileObjectImpl fo = storage.find(Name.forFileName(name));
        if (fo != null) {
            try {
                return fo.openInputStream();
            } catch (IOException ex) {
                Logger.getLogger(JFSClassLoader.class.getName()).log(Level.SEVERE, "Could not get resource " + name, ex);
                return null;
            }
        }
        return super.getResourceAsStream(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        URL result = findResource(name);
        return result != null ? new SingletonEnumeration<>(result)
                : Collections.emptyEnumeration();
    }

    @Override
    protected URL findResource(String name) {
        JFSFileObjectImpl fo = storage.find(Name.forFileName(name));
        return fo == null ? null : fo.toURL();
    }
    
    static final class SingletonEnumeration<T> implements Enumeration<T> {
        private final T obj;
        private volatile boolean done;

        SingletonEnumeration(T obj) {
            this.obj = obj;
        }

        @Override
        public boolean hasMoreElements() {
            return !done;
        }

        @Override
        public T nextElement() {
            done = true;
            return obj;
        }
    }
}

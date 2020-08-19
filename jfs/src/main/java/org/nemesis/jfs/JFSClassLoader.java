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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
 * A classloader that can load classes from a JFS location or its parent. Note
 * that this classloader is lockless (it currently tries to preload all classes
 * in the storage aggresively on first use, shuffling initialization order until
 * dependencies are satisfied); if you use it in a multithreded environment,
 * take advantage of JFS.readLock() to and JFS.writeLock() to ensure thread-safe
 * concurrent access.
 *
 * @author Tim Boudreau
 */
@Lockless
public final class JFSClassLoader extends ClassLoader implements Closeable, AutoCloseable, ExposedFindClass {

    private static final Logger LOG = Logger.getLogger(JFSClassLoader.class.getName());
    private static final boolean debug = Boolean.getBoolean("unit.test")
            || Boolean.getBoolean("jfs.classloader.debug");
    private static String baseId = Long.toString(System.currentTimeMillis());
    private static volatile int ids;
    private Throwable closedAt;
    private final JFSStorage storage;
    private final Map<String, Class<?>> classes = new HashMap<>();
    private volatile Package[] packages;
    private volatile boolean initialized;
    private volatile boolean initializing;
    private final int id;
    private volatile boolean closed;

    JFSClassLoader(JFSStorage storage) throws IOException {
        this(storage, ClassLoader.getSystemClassLoader());
    }

    JFSClassLoader(JFSStorage storage, ClassLoader parent) throws IOException {
        super(parent == null ? ClassLoader.getSystemClassLoader() : parent);
        this.storage = storage;
        this.id = ids++;
        LOG.fine(() -> "Created JFSClassLoader-" + id + "-" + storage.location() + " over " + parent);
    }

    private String packageVersion() {
        return baseId + "." + id;
    }

    private synchronized boolean preloadClassesFromJFS() {
        if (closed) {
            String msg = "Reusing a closed JFSClassLoader-" + id + " "
                    + classes + ": " + this;
            if (debug) {
                throw new IllegalStateException(msg, closedAt);
            } else {
                LOG.log(Level.WARNING, msg);
            }
            return false;
        }
        if (!initialized) {
            initialized = true;
            LOG.log(Level.FINE, "Preload JFSClassLoader-{0}-{1}-{2}", new Object[]{id, storage.location(), storage.jfs().id()});
            Set<Package> packages = new HashSet<>();
            String ver = packageVersion();
            for (String pkg : storage.listPackageNames()) {
                packages.add(definePackage(pkg, pkg, ver, storage.id(),
                        storage.jfs().id(), ver, "-", null));
                LOG.log(Level.FINE, "Define-pkg : {0} in JFSClassLoader-{1}", new Object[]{pkg, id});
            }
            this.packages = packages.toArray(new Package[packages.size()]);
            List<JFSFileObjectImpl> all = new ArrayList<>(storage.scan(JavaFileObject.Kind.CLASS));
            assert all.size() == new HashSet(all).size() : "List of file objects contains duplicates: " + all;
            Set<String> names = new HashSet<>(all.size());
            // Poor man's dependency order - shuffle until it's right
            // Since there are usually only a small number of classes involved,
            // this is actually the cheapest option, and more importantly,
            // lets this classloader function without a classloader lock

            // Hmm, should maybe move this back into the constructor, and
            // set the system classloader
            // to the current context classloader before construction - we
            // run into linkage errors if we try to load a class that is
            // also defined in a running module, when editing, say, the
            // antlr grammar project
            for (JFSFileObjectImpl file : all) {
                String nm = file.getName();
                // strip .class from the file name
                names.add(file.getName().substring(0, nm.length() - 6));
            }

            long max = all.size() * all.size();
            for (int i = 0; !all.isEmpty() && i < max + 1; i++) {
                for (Iterator<JFSFileObjectImpl> iter = all.iterator(); iter.hasNext();) {
                    JFSFileObjectImpl clazz = iter.next();
                    String nm = clazz.name().asClassName();
                    if (classes.containsKey(nm)) {
                        continue;
                    }
                    try {
                        // We may have implicitly loaded a superclass or similar,
                        // so note it if so
                        Class<?> type = findLoadedClass(nm);
                        if (type == null) {
                            type = defineClass(null, clazz.asByteBuffer(), null);
                        }
                        classes.put(type.getName(), type);
                        classes.put(nm, type);
                        LOG.log(Level.FINEST, "JFSClassLoader-{0} preinit {1} as {2}", new Object[]{id, nm, type.getName()});
                        iter.remove();
                    } catch (NoClassDefFoundError err) {
                        // Change the iteration order randomly
                        if (!names.contains(err.getMessage()) || all.size() == 1) {
                            throw err;
                        }
                        Collections.shuffle(all);
                        break;
                    } catch (LinkageError err) {
                        boolean parentContainsIt = false;
                        try {
                            getParent().loadClass(nm);
                            parentContainsIt = true;
                        } catch (Exception | Error ex) {

                        }
                        LOG.log(Level.SEVERE, "Linkage error - double load or parent contains " + nm
                                + " already loaded " + classes.keySet() + " parent is " + getParent()
                                + " able to load from parent? " + parentContainsIt, err);
                        break;
                    } catch (IOException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }
            return true;
        }
        return false;
    }

    //@Override // XXX JDK9
    protected Class<?> findClass(String moduleName, String name) {
        preloadClassesFromJFS();
        Class<?> result = loadLocal(name, true);
        superFindClassJDK9(this, moduleName, name);
        if (result == null) {
            try {
                result = findClass(name);
            } catch (ClassNotFoundException ex) {
                LOG.log(Level.FINE, null, ex);
            }
        }
        return result;
    }

    static volatile Method findClassWithModuleNameMethod;
    static volatile boolean findClassWithModuleNameMethodLookupFailed;

    private static synchronized Class<?> superFindClassJDK9(JFSClassLoader ldr, String moduleName, String name) {
        if (findClassWithModuleNameMethod != null && !findClassWithModuleNameMethodLookupFailed) {
            try {
                return (Class<?>) findClassWithModuleNameMethod.invoke(ldr, moduleName, name);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                LOG.log(Level.INFO, null, ex);
            }
        } else if (!findClassWithModuleNameMethodLookupFailed) {
            try {
                findClassWithModuleNameMethod = ClassLoader.class.getDeclaredMethod("findClass", String.class, String.class);
                findClassWithModuleNameMethod.setAccessible(true);
            } catch (NoSuchMethodException | SecurityException ex) {
                LOG.log(Level.INFO, null, ex);
                findClassWithModuleNameMethodLookupFailed = true;
            }
        }
        if (findClassWithModuleNameMethod != null) {
            try {
                findClassWithModuleNameMethod.invoke(ldr, moduleName, name);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> result = loadLocal(name, resolve);
        return result == null ? super.loadClass(name, resolve) : result;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class<?> result = loadLocal(name, false);
        return result == null ? super.loadClass(name) : result;
    }

    /**
     * Determine if this classloader has been closed and should not be used.
     *
     * @return True if this classloader has been closed and its contents
     * discarded
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder("JFSClassLoader-");
        res.append('-').append(storage.location());
        res.append('-').append(storage.jfs().id());
        res.append('(');
        List<String> all = new ArrayList<>(classes.keySet());
        Collections.sort(all);
        for (Iterator<String> it = all.iterator(); it.hasNext();) {
            res.append(it.next());
            if (it.hasNext()) {
                res.append(", ");
            }
        }
        res.append(')');
        ClassLoader par = getParent();
        if (par != null && par != this) {
            res.append(" -> ").append(par);
        }
        return res.toString();
    }

    private String identifier() {
        return id + "-" + storage.location() + "-" + storage.id();
    }

    @Override
    public void close() throws IOException {
        if (debug && !closed) {
            closedAt = new Exception("Close JFSClassLoader-" + identifier());
//            if (LOG.isLoggable(Level.FINEST)) {
//                LOG.log(Level.FINEST, "Close JFSClassLoader-" + id, closedAt);
//            }
        }
        if (closed) {
            // double check nothing got loaded unexpectedly
            classes.clear();
            if (debug && LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, "Double-close", new Exception("Second close", closedAt));
            }
            LOG.log(Level.FINER, "Close called twice on {0}", this);
            return;
        }
        closed = true;
        LOG.log(Level.FINE, "Close JFSClassLoader-{0} with {1} loaded classes: {2}",
                new Object[]{identifier(), classes.size(), classes.keySet()});
        packages = new Package[0];
        classes.clear();
        storage.classloaderClosed(this);
        ClassLoader parent = this.getParent();
        if (parent instanceof JFSClassLoader && parent != this) {
            LOG.log(Level.FINER, "Also close parent {0}", ((JFSClassLoader) parent).classes.keySet());
            ((JFSClassLoader) parent).close();
        }
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
        Class<?> result = loadLocal(name, true);
        return result != null ? result : super.findClass(name);
    }

    private Class<?> loadLocal(String className, boolean resolve) {
        preloadClassesFromJFS();
        Class<?> result = findLoadedClass(className);
        if (result != null) {
            if (!classes.containsKey(className)) {
                classes.put(className, result);
                classes.put(result.getName(), result);
            }
            return result;
        }
        Class<?> type = classes.get(className);
        if (type != null) {
            if (resolve) {
                resolveClass(type);
            }
            return type;
        } else {
            // Generation now may be run *after* the first call to load some
            // JFS class
            type = tryPostInitLoadFromJFS(className);
            if (type != null) {
                classes.put(className, type);
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }
        return null;
    }

    private Class<?> tryPostInitLoadFromJFS(String className) {
        // XXX should probably use the JFS read-lock here to
        // avoid racing;  for now, simply assuming the caller got their
        // locking right, which in the case of the modules, it did.
        Name name = Name.forClassName(className, JavaFileObject.Kind.CLASS);
        JFSFileObjectImpl fo = storage.find(name, false);
        if (fo != null) {
            try {
                String pkName = name.packageName();
                // Need to define the package first, for this to work
                boolean addPackage = true;
                for (Package pk : packages) {
                    if (pkName.equals(pk.getName())) {
                        addPackage = false;
                        break;
                    }
                }
                if (addPackage) {
                    Set<Package> all = new HashSet<>(Arrays.asList(packages));
                    String ver = packageVersion();
                    for (String pkg : storage.listPackageNames()) {
                        all.add(definePackage(pkg, pkg, ver, storage.id(),
                                storage.jfs().id(), ver, "-", null));
                    }
                    packages = all.toArray(new Package[all.size()]);
                }
                Class<?> result = super.defineClass(null, fo.asByteBuffer(), null);
                LOG.log(Level.FINEST, "Post-init load {0} as {1} in JFSClassLoader-{2}",
                        new Object[]{className, result.getName(), id});
                return result;
            } catch (IOException ex) {
                Logger.getLogger(JFSClassLoader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Package getPackage(String name) {
        preloadClassesFromJFS();
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

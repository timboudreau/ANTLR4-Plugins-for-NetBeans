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

    private static final Logger LOG = Logger.getLogger(JFSClassLoader.class.getName());
    private static volatile int ids;
    private final JFSStorage storage;
    private final Map<String, Class<?>> classes = new HashMap<>();
    private volatile Package[] packages;
    private boolean initialized;
    private volatile boolean initializing;
    private final int id;

    JFSClassLoader(JFSStorage storage) throws IOException {
        this(storage, ClassLoader.getSystemClassLoader());
    }

    JFSClassLoader(JFSStorage storage, ClassLoader ldr) throws IOException {
        super(ldr == null ? ClassLoader.getSystemClassLoader() : ldr);
        this.storage = storage;
        this.id = ids++;
        LOG.fine(() -> "Created JFSClassLoader " + id);
    }

    private synchronized void preloadClassesFromJFS() {
        if (!initialized) {
            initialized = true;
            Set<Package> packages = new HashSet<>();
            String ver = "1." + Long.toString(System.currentTimeMillis());
            for (String pkg : storage.listPackageNames()) {
                packages.add(definePackage(pkg, pkg, ver, storage.id(),
                        "-", ver, "-", null));
            }
            this.packages = packages.toArray(new Package[packages.size()]);
            List<JFSFileObjectImpl> all = new ArrayList<>(storage.scan(JavaFileObject.Kind.CLASS));
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
                    } catch (IOException ex) {
                        Logger.getLogger(JFSClassLoader.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    }

    //@Override // XXX JDK9
    protected Class<?> findClass(String moduleName, String name) {
        preloadClassesFromJFS();
        Class<?> result = superFindClassJDK9(this, moduleName, name);
        if (result == null) {
            try {
                result = findClass(name);
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(JFSClassLoader.class.getName()).log(Level.FINE, null, ex);
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

        }
        return null;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        preloadClassesFromJFS();
        return super.loadClass(name, resolve); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        preloadClassesFromJFS();
        return super.loadClass(name);
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder("JFSClassLoader-");
        res.append(id).append('(');
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

    @Override
    public void close() throws IOException {
        LOG.log(Level.FINE, "Close a JFS classloader with {0}", classes.keySet());
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
        preloadClassesFromJFS();
        Class<?> type = classes.get(name);
        if (type != null) {
            return type;
        }
        return super.findClass(name);
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

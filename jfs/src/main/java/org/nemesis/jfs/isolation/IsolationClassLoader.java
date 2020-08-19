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
package org.nemesis.jfs.isolation;

import com.mastfrog.predicates.Predicates;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A general-purpose child-first classloader which can optionally take a
 * predicate that allows certain classes to be loaded from its parent.
 *
 * @author Tim Boudreau
 */
public final class IsolationClassLoader<T extends ClassLoader & ExposedFindClass & Closeable>
        extends ClassLoader implements AutoCloseable, Closeable, Supplier<IsolationClassLoader<?>> {

    private T childClassLoader;
    private final boolean lockless;
    private final boolean uncloseable;

    IsolationClassLoader(Function<ClassLoader, T> childForParent, boolean uncloseable) {
        this(Thread.currentThread().getContextClassLoader(), childForParent, uncloseable);
    }

    IsolationClassLoader(ClassLoader parent, Function<ClassLoader, T> childForParent,
            boolean uncloseable) {
        this(parent, new FindClassClassLoader(parent), childForParent, uncloseable);
    }

    private IsolationClassLoader(ClassLoader parent, FindClassClassLoader lfp,
            Function<ClassLoader, T> childForParent, boolean uncloseable) {
        super(parent);
        this.uncloseable = uncloseable;
        childClassLoader = childForParent.apply(lfp);
        lockless = childClassLoader.getClass().getAnnotation(Lockless.class) != null;
        lfp.lockless = lockless;
    }

    public static <T extends ClassLoader & ExposedFindClass & Closeable> Supplier<IsolationClassLoader<T>>
            lazyCreate(Function<ClassLoader, T> createDelegateClassloader) {
        return lazyCreate(createDelegateClassloader, false);
    }

    public static <T extends ClassLoader & ExposedFindClass & Closeable> Supplier<IsolationClassLoader<T>>
            lazyCreate(Function<ClassLoader, T> createDelegateClassloader, boolean uncloseable) {
        return () -> {
            return new IsolationClassLoader<>(createDelegateClassloader, uncloseable);
        };
    }

    public static <T extends ClassLoader & ExposedFindClass & Closeable> Supplier<IsolationClassLoader<T>>
            lazyCreate(ClassLoader parent, Function<ClassLoader, T> createDelegateClassloader) {
        return lazyCreate(parent, createDelegateClassloader, false);
    }

    public static <T extends ClassLoader & ExposedFindClass & Closeable> Supplier<IsolationClassLoader<T>>
            lazyCreate(ClassLoader parent, Function<ClassLoader, T> createDelegateClassloader, boolean uncloseable) {
        return () -> {
            return new IsolationClassLoader<>(parent, createDelegateClassloader, uncloseable);
        };
    }

    @Override
    public IsolationClassLoader<?> get() {
        return this;
    }

    @Override
    protected Object getClassLoadingLock(String className) {
        if (lockless) {
            return new Object();
        }
        return super.getClassLoadingLock(className);
    }

    @Override
    public String toString() {
        return "IsolationClassLoader{" + childClassLoader + "}";
    }

    /**
     * Create a simple isoloation classloader for some URLs.
     *
     * @param urls Some urls
     * @return
     */
    public static IsolationClassLoader<?> forURLs(URL[] urls) {
        return forURLs(false, notNull("urls", urls));
    }

    public static IsolationClassLoader<?> forURLs(boolean uncloseable, URL[] urls) {
        return forURLs(urls, Predicates.alwaysFalse()); // yes, lambdas, but this is cheaper
    }

    public static IsolationClassLoader<?> forURLs(URL[] urls, Predicate<String> forceLoadFromParent) {
        return forURLs(urls, forceLoadFromParent, false);
    }

    public static IsolationClassLoader<?> forURLs(URL[] urls, Predicate<String> forceLoadFromParent, boolean uncloseable) {
        return forURLs(Thread.currentThread().getContextClassLoader(), urls, forceLoadFromParent, uncloseable);
    }

    public static IsolationClassLoader<?> forURLs(ClassLoader parent, URL[] urls, Predicate<String> forceLoadFromParent) {
        return forURLs(parent, urls, forceLoadFromParent, false);
    }

    /**
     * Create an isolation classloader directly.
     *
     * @param parent The parent classloader (urls are searched first)
     * @param urls Some URLs to create URLClassLoaders over
     * @param forceLoadFromParent A predicate which, for class names it tests true for,
     * will ensure the class object is loaded fro the parent classloader, not any url
     * or other classloader present.
     * @param uncloseable If true, the <code>close()</code> method will do nothing, and
     * <code>reallyClose()</code> must be called to actually close the classloader - this
     * is useful if this classloader will be repeatedly used as the parent of classloaders
     * such as JFSClassLoader that try to close their parent when they are closed.
     * @return An isolation class loader
     */
    public static IsolationClassLoader<?> forURLs(ClassLoader parent, URL[] urls, Predicate<String> forceLoadFromParent, boolean uncloseable) {
        Function<ClassLoader, ChildURLClassLoader> createWithParent = par -> {
            return new ChildURLClassLoader(urls, par, forceLoadFromParent);
        };
        return new IsolationClassLoader<>(parent, createWithParent, uncloseable);
    }

    /**
     * Create a builder for IsolationClassLoader instances.
     *
     * @return An IsolationClassLoaderBuilder
     */
    public static IsolationClassLoaderBuilder builder() {
        return new IsolationClassLoaderBuilder();
    }

    @Override
    public synchronized void close() throws IOException {
        if (!uncloseable) {
            childClassLoader.close();
            childClassLoader = null;
        }
    }

    /**
     * Actually close this classloader, even if it is marked uncloseable.
     *
     * @throws IOException if something goes wrong
     */
    public synchronized void reallyClose() throws IOException {
        if (uncloseable) {
            childClassLoader.close();
            childClassLoader = null;
        }
    }

    @Override
    public String getName() {
        T ldr;
        synchronized (this) {
            ldr = childClassLoader;
        }
        String nm = IsolationClassLoader.class.getSimpleName();
        return nm + "{" + ldr + "}";
    }

    private static class FindClassClassLoader extends ClassLoader implements ExposedFindClass {

        private boolean lockless;

        public FindClassClassLoader(ClassLoader parent) {
            super(parent);
        }

        public String getName() {
            return "FindClassLoader{" + getParent() + "}";
        }

        @Override
        protected Object getClassLoadingLock(String className) {
            if (lockless) {
                return new Object();
            }
            return super.getClassLoadingLock(className);
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public Class<?> lookupClass(String name) throws ClassNotFoundException {
            return super.findClass(name);
        }

        public void close() {
            // do nothing
        }
    }

    /**
     * This class delegates (child then parent) for the findClass method for a
     * URLClassLoader. We need this because findClass is protected in
     * URLClassLoader
     */
    private static class ChildURLClassLoader extends URLClassLoader implements ExposedFindClass {

        private ClassLoader realParent;
        private final Map<String, Class<?>> found = new HashMap<>();
        private final Predicate<String> forceLoadFromParent;

        public ChildURLClassLoader(URL[] urls, ClassLoader realParent,
                Predicate<String> forceLoadFromParent) {
            super(urls, null);
            this.realParent = realParent;
            this.forceLoadFromParent = forceLoadFromParent;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("IsolationClassLoader.ChildURLClassLoader([");
            String urls = Strings.join(", ", Arrays.asList(super.getURLs()));
            sb.append(urls).append("] under ").append(realParent)
                    .append(" allowing ").append(forceLoadFromParent)
                    .append(" currently with ").append(Strings.join(", ",
                    new TreeSet<>(found.keySet())));
            return sb.toString();
        }

        @Override
        public synchronized void close() throws IOException {
            found.clear();
            super.close();
            realParent = null;
        }

        @Override
        public Class<?> lookupClass(String name) throws ClassNotFoundException {
            return findClass(name);
        }

        @Override
        protected synchronized Class<?> findClass(String name) throws ClassNotFoundException {
            if (realParent == null) {
                throw new IllegalStateException("Classloader closed");
            }
            if (forceLoadFromParent.test(name)) {
                return realParent.loadClass(name);
            }
            try {
                Class<?> previouslyFound = found.get(name);
                if (previouslyFound != null) {
                    return previouslyFound;
                }
                Class<?> result = super.findClass(name);
                found.put(name, result);
                return result;
            } catch (ClassNotFoundException e) {
                return realParent.loadClass(name);
            }
        }
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (childClassLoader == null) {
            throw new IllegalStateException("Classloader closed");
        }
        try {
            // first we try to find a class inside the child classloader
            return childClassLoader.lookupClass(name);
        } catch (ClassNotFoundException e) {
            // didn't find it, try the parent
            try {
                return super.loadClass(name, resolve);
            } catch (ClassNotFoundException cnfe) {
                throw new ClassNotFoundException("Could not find " + name + " in "
                        + this, cnfe);
            }
        }
    }
}

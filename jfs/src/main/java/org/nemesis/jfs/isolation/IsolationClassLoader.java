package org.nemesis.jfs.isolation;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A general-purpose child-first classloader which can optionally take
 * a predicate that allows certain classes to be loaded from its
 * parent.
 *
 * @author Tim Boudreau
 */
public final class IsolationClassLoader<T extends ClassLoader & ExposedFindClass & Closeable> extends ClassLoader implements AutoCloseable, Closeable, Supplier<IsolationClassLoader<?>> {

    private T childClassLoader;

    private final boolean lockless;

    IsolationClassLoader(Function<ClassLoader, T> childForParent) {
        this(Thread.currentThread().getContextClassLoader(), childForParent);
    }

    IsolationClassLoader(ClassLoader parent, Function<ClassLoader, T> childForParent) {
        this(parent, new FindClassClassLoader(parent), childForParent);
    }

    private IsolationClassLoader(ClassLoader parent, FindClassClassLoader lfp, Function<ClassLoader, T> childForParent) {
        super(parent);
        childClassLoader = childForParent.apply(lfp);
        lockless = childClassLoader.getClass().getAnnotation(Lockless.class) != null;
        lfp.lockless = lockless;
    }

    public static <T extends ClassLoader & ExposedFindClass & Closeable> Supplier<IsolationClassLoader<T>> lazyCreate(Function<ClassLoader, T> createDelegateClassloader) {
        return () -> {
            return new IsolationClassLoader<>(createDelegateClassloader);
        };
    }

    public static <T extends ClassLoader & ExposedFindClass & Closeable> Supplier<IsolationClassLoader<T>> lazyCreate(ClassLoader parent, Function<ClassLoader, T> createDelegateClassloader) {
        return () -> {
            return new IsolationClassLoader<>(parent, createDelegateClassloader);
        };
    }

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

    public String toString() {
        return "IsolationClassLoader{" + childClassLoader + "}";
    }

    static final class AlwaysFalse implements Predicate<String> {

        @Override
        public boolean test(String t) {
            return false;
        }
    }

    public static IsolationClassLoader<?> forURLs(URL[] urls) {
        return forURLs(urls, new AlwaysFalse()); // yes, lambdas, but this is cheaper
    }

    public static IsolationClassLoader<?> forURLs(URL[] urls, Predicate<String> forceLoadFromParent) {
        return forURLs(Thread.currentThread().getContextClassLoader(), urls, forceLoadFromParent);
    }

    public static IsolationClassLoader<?> forURLs(ClassLoader parent, URL[] urls, Predicate<String> forceLoadFromParent) {
        Function<ClassLoader, ChildURLClassLoader> createWithParent = par -> {
            return new ChildURLClassLoader(urls, par, forceLoadFromParent);
        };
        return new IsolationClassLoader<>(parent, createWithParent);
    }

    @Override
    public synchronized void close() throws IOException {
        childClassLoader.close();
        childClassLoader = null;
    }

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

        public String toString() {
            return getName();
        }

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
        public synchronized void close() throws IOException {
            found.clear();
            super.close();
            realParent = null;
        }

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

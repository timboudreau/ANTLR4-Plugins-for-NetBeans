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

import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.NullArgumentException;
import com.mastfrog.util.strings.Strings;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.nemesis.jfs.JFS;

/**
 *
 * @author Tim Boudreau
 */
public final class IsolationClassLoaderBuilder implements Supplier<ClassLoader> {

    private ClassLoader parent = Thread.currentThread().getContextClassLoader();

    private final Set<URLValue> including = new LinkedHashSet<>();
    private final Set<String> allowedClassNames = new HashSet<>();
    private final Set<String> allowedPackages = new HashSet<>();
    private final Set<String> allowedWildcardPackages = new HashSet<>();
    private final Set<JFS> includeFrom = new LinkedHashSet<>();
    private final Set<ClassLoader> alsoDelegateTo = new LinkedHashSet<>();
    private boolean uncloseable;

    IsolationClassLoaderBuilder() {
        // do nothing
    }

    /**
     * Parent the created classloader on ths system classloader.
     *
     * @return this
     */
    public IsolationClassLoaderBuilder usingSystemClassLoader() {
        parent = ClassLoader.getSystemClassLoader();
        return this;
    }

    /**
     * Add a type to the list that should be loaded from the parent classloader.
     *
     * @param typeName The class name
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder loadingFromParent(String typeName) {
        allowedClassNames.add(notNull("typeName", typeName));
        return this;
    }

    /**
     * Add a type to the list that should be loaded from the parent classloader.
     *
     * @param type The type as a Class object
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder loadingFromParent(Class<?> type) {
        return loadingFromParent(notNull("type", type).getName());
    }

    /**
     * Add an entire package to the list that should be loaded from the parent
     * classloader.
     *
     * @param packageName The package name
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder loadingPackageFromParent(String packageName) {
        allowedPackages.add(notNull("packageName", packageName));
        return this;
    }

    /**
     * Add the entire package of the passed class file to the list that should
     * be loaded from the parent classloader.
     *
     * @param of A class
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder loadingPackageFromParent(Class<?> of) {
        allowedPackages.add(notNull("of", of).getPackage().getName());
        return this;
    }

    /**
     * Add an entire package <i>and all subpackages of that it</i> to the list
     * that should be loaded from the parent classloader.
     *
     * @param of A package name
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder loadingPackageAndSubpackagesFromParent(String pkg) {
        allowedWildcardPackages.add(notNull("pkg", pkg));
        return this;
    }

    /**
     * Add an the entire package of the passed class file <i>and all subpackages
     * of that package</i> to the list that should be loaded from the parent
     * classloader.
     *
     * @param of A package name
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder loadingPackageAndSubpackagesFromParent(Class<?> of) {
        return loadingPackageAndSubpackagesFromParent(notNull("of", of).getPackage().getName());
    }

    /**
     * Set the parent class loader for the created classloader; if it has
     * already been set, the new value replaces the old. If unset, the context
     * classloader of the thread that created this builder at the time it was
     * instantiated is used.
     *
     * @param ldr A class loader
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder withParentClassLoader(ClassLoader ldr) {
        this.parent = notNull("ldr", ldr);
        return this;
    }

    /**
     * If this classloader is intended to be long-lived and the parent of
     * multiple shorter-lived classloaders which might implicitly close it (such
     * as JFSClassLoader), mark the classloader you are building as uncloseable,
     * and then call <code>reallyClose()</code> when you are absolutely sure it
     * will not be reused again.
     *
     * @return this
     */
    public IsolationClassLoaderBuilder uncloseable() {
        uncloseable = true;
        return this;
    }

    /**
     * Add the jar or codebase of the passed type to the created loader, which
     * will load any such classes from that jar with its own loader, rather than
     * the parent classloader (note that this means class objects for those
     * types will not == the same types in the code that created the loader -
     * those types that must be shared should be added as pass-throughs.
     *
     * @param type The type
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder includingJarOf(Class<?> type) {
        Path pth = jarPathFor(notNull("type", type));
        try {
            including.add(new URLValue(pth.toUri().toURL()));
        } catch (MalformedURLException ex) {
            throw new AssertionError(ex);
        }
        return this;
    }

    /**
     * Include a URL to a jar, file or whatever that this loader can load from.
     *
     * @param url A url
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder including(URL url) {
        including.add(new URLValue(notNull("url", url)));
        return this;
    }

    /**
     * Include a URI to a jar, file or whatever that this loader can load from.
     *
     * @param url A url
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder including(URI uri) {
        try {
            return IsolationClassLoaderBuilder.this.including(notNull("uri", uri).toURL());
        } catch (MalformedURLException ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * Include another parent classloader to delegate to (order of addition is
     * order of precedence, first-in called first) - for example, a <code>ClassLoader</code>
     * over the user's project in the IDE.
     *
     * @param ldr A class loader
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder alsoDelegateTo(ClassLoader ldr) {
        alsoDelegateTo.add(notNull("ldr", ldr));
        return this;
    }

    /**
     * Include the classpath location from the JFS's implementation of javac's
     * JavaFileSystem.
     *
     * @param jfs A JFS
     * @return this
     * @throws NullArgumentException if the argument is null
     */
    public IsolationClassLoaderBuilder includingClassPathOf(JFS jfs) {
        includeFrom.add(notNull("jfs", jfs));
        return this;
    }

    static Path jarPathFor(Class<?> includeCodeBaseOf) {
        try {
            URI uri = includeCodeBaseOf
                    .getProtectionDomain().getCodeSource()
                    .getLocation().toURI();
            uri = toFileURI(uri);
            return Paths.get(uri);
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    private static URI toFileURI(URI jarUri) throws URISyntaxException {
        String s = jarUri.toString();
        if (s.endsWith("!/") && s.startsWith("jar:file:")) {
            s = s.substring(4, s.length() - 2);
            return new URI(s);
        }
        return jarUri;
    }

    /**
     * Build a classloader from the configuration described by this builder.
     *
     * @return A class loader
     */
    public IsolationClassLoader<?> build() {
        ClassLoader workingParent = parent;
        if (!alsoDelegateTo.isEmpty()) {
            workingParent = new AggregatingClassLoader(workingParent, alsoDelegateTo);
        }
        return IsolationClassLoader.forURLs(workingParent, urls(), allowThrough(), uncloseable);
    }

    private URL[] urls() {
        if (!includeFrom.isEmpty()) {
            for (JFS jfs : includeFrom) {
                for (File f : jfs.classpathContents()) {
                    including(f.toURI());
                }
            }
        }
        URL[] urls = new URL[including.size()];
        int i = 0;
        for (Iterator<URLValue> it = including.iterator(); i < urls.length; i++) {
            urls[i] = it.next().url();
        }
        return urls;
    }

    private Predicate<String> allowThrough() {
        Predicate<String> result = null;
        if (!this.allowedClassNames.isEmpty()) {
            result = new ExactMatchPredicate(this.allowedClassNames);
        }
        if (!this.allowedPackages.isEmpty()) {
            if (result == null) {
                result = new ExactPackageMatchPredicate(this.allowedPackages);
            } else {
                result = result.or(new ExactPackageMatchPredicate(this.allowedPackages));
            }
        }
        if (!this.allowedWildcardPackages.isEmpty()) {
            if (result == null) {
                result = new WildcardPackageMatchPredicate(this.allowedWildcardPackages);
            } else {
                result = result.or(new WildcardPackageMatchPredicate(this.allowedWildcardPackages));
            }
        }
        if (result == null) {
            result = ignored -> false;
        }
        return result;
    }

    @Override
    public ClassLoader get() {
        return build();
    }

    private static Set<String> immutableSetIfPossible(Set<String> s) {
        try {
            return CollectionUtils.immutableSet(s);
        } catch (IllegalArgumentException ex) {
            // Theoretically, if two class names are not the same but have
            // the same hash code, ImmutableSet will throw an exception -
            // its advantage is an heavily optimized negative-test path,
            // which is well worth it
            return CollectionUtils.arraySetOf(s.toArray(new String[s.size()]));
        }
    }

    private static class ExactMatchPredicate implements Predicate<String> {

        private final Set<String> typeNames;

        public ExactMatchPredicate(Set<String> typeNames) {
            this.typeNames = immutableSetIfPossible(typeNames);
        }

        @Override
        public boolean test(String t) {
            return typeNames.contains(t);
        }

        @Override
        public Predicate<String> or(Predicate<? super String> other) {
            return new OrPredicate(this, other);
        }

        @Override
        public String toString() {
            return "class-names(" + Strings.join(", ", typeNames) + ")";
        }
    }

    private static class ExactPackageMatchPredicate implements Predicate<String> {

        private final Set<String> packages;

        public ExactPackageMatchPredicate(Set<String> packages) {
            this.packages = immutableSetIfPossible(packages);
        }

        @Override
        public boolean test(String t) {
            return packages.contains(stripClassName(t));
        }

        @Override
        public Predicate<String> or(Predicate<? super String> other) {
            return new OrPredicate(this, other);
        }

        @Override
        public String toString() {
            return "in-packages(" + Strings.join(", ", packages) + ")";
        }
    }

    private static class WildcardPackageMatchPredicate implements Predicate<String> {

        private final Set<String> wildcardPackages;

        public WildcardPackageMatchPredicate(Set<String> wildcardPackages) {
            this.wildcardPackages = immutableSetIfPossible(wildcardPackages);
        }

        @Override
        public boolean test(String t) {
            String stripped = stripClassName(t);
            for (String pkg : wildcardPackages) {
                if (pkg.equals(stripped)) {
                    return true;
                } else if (stripped.startsWith(pkg)) {
                    return stripped.charAt(pkg.length()) == '.';
                }
            }
            return false;
        }

        @Override
        public Predicate<String> or(Predicate<? super String> other) {
            return new OrPredicate(this, other);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(128)
                    .append("subpackages(");
            for (Iterator<String> it = wildcardPackages.iterator(); it.hasNext();) {
                sb.append(it.next()).append(".**");
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            return sb.append(')').toString();
        }
    }

    private static class OrPredicate implements Predicate<String> {

        private final List<Predicate<? super String>> all = new ArrayList();

        public OrPredicate(Predicate<? super String> a, Predicate<? super String> b) {
            all.add(a);
            all.add(b);
        }

        @Override
        public boolean test(String t) {
            for (Predicate<? super String> p : all) {
                if (p.test(t)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Predicate<String> or(Predicate<? super String> other) {
            all.add(other);
            return this;
        }

        @Override
        public String toString() {
            return "(" + Strings.join(" | ", all) + ")";
        }
    }

    private static String stripClassName(String s) {
        int ix = s.lastIndexOf('.');
        if (ix > 0 && ix < s.length() - 1) {
            return s.substring(0, ix);
        }
        return s;
    }

    /**
     * A wrapper for a URL that will not attempt a network connection to perform
     * an equality test.
     */
    private static final class URLValue {

        private final URL url;

        public URLValue(URL url) {
            this.url = url;
        }

        public URL url() {
            return url;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof URLValue && ((URLValue) o).toString().equals(toString());
        }

        @Override
        public int hashCode() {
            return 7 * url.toString().hashCode();
        }

        @Override
        public String toString() {
            return url.toString();
        }
    }
}

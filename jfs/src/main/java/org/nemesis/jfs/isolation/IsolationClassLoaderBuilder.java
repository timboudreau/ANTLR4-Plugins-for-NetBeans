package org.nemesis.jfs.isolation;

import static com.mastfrog.util.preconditions.Checks.notNull;
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

    IsolationClassLoaderBuilder() {
        // do nothing
    }

    public IsolationClassLoaderBuilder usingSystemClassLoader() {
        parent = ClassLoader.getSystemClassLoader();
        return this;
    }

    public IsolationClassLoaderBuilder loadingFromParent(String typeName) {
        allowedClassNames.add(typeName);
        return this;
    }

    public IsolationClassLoaderBuilder loadingFromParent(Class<?> type) {
        return loadingFromParent(notNull("type", type).getName());
    }

    public IsolationClassLoaderBuilder loadingPackageFromParent(String packageName) {
        allowedPackages.add(notNull("packageName", packageName));
        return this;
    }

    public IsolationClassLoaderBuilder loadingPackageFromParent(Class<?> of) {
        allowedPackages.add(notNull("of", of).getPackage().getName());
        return this;
    }

    public IsolationClassLoaderBuilder loadingPackageAndSubpackagesFromParent(String pkg) {
        allowedWildcardPackages.add(notNull("pkg", pkg));
        return this;
    }

    public IsolationClassLoaderBuilder loadingPackageAndSubpackagesFromParent(Class<?> of) {
        return loadingPackageAndSubpackagesFromParent(notNull("of", of).getPackage().getName());
    }

    public IsolationClassLoaderBuilder withParentClassLoader(ClassLoader ldr) {
        this.parent = notNull("ldr", ldr);
        return this;
    }

    public IsolationClassLoaderBuilder includingJarOf(Class<?> type) {
        Path pth = jarPathFor(notNull("type", type));
        try {
            including.add(new URLValue(pth.toUri().toURL()));
        } catch (MalformedURLException ex) {
            throw new AssertionError(ex);
        }
        return this;
    }

    public IsolationClassLoaderBuilder including(URL url) {
        including.add(new URLValue(url));
        return this;
    }

    public IsolationClassLoaderBuilder including(URI uri) {
        try {
            return IsolationClassLoaderBuilder.this.including(uri.toURL());
        } catch (MalformedURLException ex) {
            throw new AssertionError(ex);
        }
    }

    public IsolationClassLoaderBuilder includingClassPathOf(JFS jfs) {
        includeFrom.add(jfs);
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

    public IsolationClassLoader<?> build() {
        return IsolationClassLoader.forURLs(parent, urls(), allowThrough());
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

    private static class ExactMatchPredicate implements Predicate<String> {

        private final Set<String> typeNames;

        public ExactMatchPredicate(Set<String> typeNames) {
            this.typeNames = new HashSet<>(typeNames);
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
            this.packages = new HashSet<>(packages);
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
            this.wildcardPackages = new HashSet<>(wildcardPackages);
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

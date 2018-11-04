package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import javax.tools.JavaFileObject;

/**
 * A wrapper for file and class names that normalizes them according to the JLS
 * and provides ways of interconverting between files, packages and classes.
 * Internally represents the parent path as a java.io.file.Path, and separate
 * name and file extension strings.
 *
 * @author Tim Boudreau
 */
final class Name {

    private final Path prefix;
    private final String name;
    private final String extension;

    Name(Path prefix, String name, String ext) {
        this.prefix = prefix == null ? Paths.get("") : prefix;
        this.name = name;
        this.extension = ext;
    }

    static Name forFileName(String pkg, String fileName) {
        String[] nameExt = nameExt(Paths.get(fileName));
        return new Name(packageToPath(pkg), nameExt[0], nameExt[1]);
    }

    static Name forClassName(String className, JavaFileObject.Kind kind) {
        int ix = className.lastIndexOf('.');
        String pkg = "";
        if (ix > 0 && ix < className.length() - 1) {
            pkg = className.substring(0, ix);
            className = className.substring(ix + 1, className.length());
        }
        Path prefix = packageToPath(pkg);
        String ext = kind.extension;
        if (ext.length() > 0 && ext.charAt(0) == '.') {
            ext = ext.substring(1);
        }
        return new Name(prefix, className, ext);
    }

    static Path packageToPath(String packageName) {
        Path result;
        if (packageName != null && !packageName.isEmpty()) {
            result = Paths.get(packageName.replace('.', '/'));
        } else {
            return Paths.get("");
        }
        return result;
    }

    static Name forFileName(String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Empty name");
        }
        if (name.charAt(0) == '/') {
            name = name.substring(1);
        }
        Path asPath = Paths.get(name);
        String[] nameExt = nameExt(asPath);
        return new Name(asPath.getParent(), nameExt[0], nameExt[1]);
    }

    static Name forPath(Path path) {
        if (path.isAbsolute()) {
            JFS.LOG.log(Level.WARNING, "Absolute path passed to "
                    + "Name.forPath(): {0}. Converting to relative path",
                    path);
            path = Paths.get("/").relativize(path);
        }
        String[] nameExt = nameExt(path);
        return new Name(path.getParent(), nameExt[0], nameExt[1]);
    }

    static Name forPath(Path path, Path relativeTo) {
        Path realPath = relativeTo.relativize(path);
        String[] nameExt = nameExt(realPath);
        return new Name(realPath.getParent(), nameExt[0], nameExt[1]);
    }

    static String[] nameExt(Path path) {
        String fileName = path.getFileName().toString();
        String ext = "";
        int extIx = fileName.lastIndexOf('.');
        if (extIx > 0 && extIx < fileName.length() - 1) {
            ext = fileName.substring(extIx + 1);
            fileName = fileName.substring(0, extIx);
        }
        return new String[]{fileName, ext};
    }

    private JavaFileObject.Kind cachedKind;
    JavaFileObject.Kind kind() {
        if (cachedKind != null) {
            return cachedKind;
        }
        if (extension.isEmpty()) {
            return cachedKind = JavaFileObject.Kind.OTHER;
        }
        String dotExt = '.' + extension;
        for (JavaFileObject.Kind k : JavaFileObject.Kind.values()) {
            if (dotExt.equalsIgnoreCase(k.extension)) {
                return cachedKind = k;
            }
        }
        return cachedKind = JavaFileObject.Kind.OTHER;
    }

    boolean isJar() {
        return "jar".equals(extension);
    }

    String extension() {
        return extension;
    }

    boolean nameMatches(String rawName) {
        return name.equals(rawName);
    }

    boolean packageMatches(String pkg) {
        return packageName().equals(pkg);
    }

    boolean isPackage(Path pkgPath, boolean orSubpackage) {
        if (pkgPath == null || pkgPath.toString().isEmpty()) {
            boolean result = orSubpackage || prefix.toString().isEmpty();
            return result;
        }
        boolean result = prefix.equals(pkgPath);
        if (!result && orSubpackage) {
            result = prefix.startsWith(pkgPath);
        }
        return result;
    }

    String getName() {
        String nm = name;
        if (extension != null && !extension.isEmpty()) {
            nm += "." + extension;
        }
        return nm;
    }

    String asClassName() {
        String pn = packageName();
        return pn.isEmpty() ? name : pn + "." + name;
    }

    String packageName() {
        StringBuilder sb = new StringBuilder();
        int ct = prefix.getNameCount();
        for (int i = 0; i < ct; i++) {
            sb.append(prefix.getName(i));
            if (i != ct - 1) {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    public Path toPath() {
        return Paths.get(toString());
    }

    @Override
    public String toString() {
        return prefix.resolve(getName()).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof Name) {
            return toString().equals(o.toString());
        }
        return false;
    }

    int hashCode = 0;

    @Override
    public int hashCode() {
        if (hashCode != 0) {
            return hashCode;
        }
        return hashCode = prefix.hashCode()
                + (37 * name.hashCode())
                * (extension.hashCode() + 1);
    }

    String getNameBase() {
        return name;
    }
}

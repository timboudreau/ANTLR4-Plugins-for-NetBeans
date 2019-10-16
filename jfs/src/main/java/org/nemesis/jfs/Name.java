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

import com.mastfrog.util.path.UnixPath;
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

    private final UnixPath prefix;
    private final String name;
    private final String extension;

    Name(UnixPath prefix, String name, String ext) {
        this.prefix = prefix == null ? UnixPath.empty() : prefix;
        this.name = name;
        this.extension = ext;
    }

    static Name forFileName(String pkg, String fileName) {
        UnixPath pkgPth = packageToPath(pkg);
        UnixPath fn = UnixPath.get(fileName);
        return new Name(pkgPth, fn.rawName(), fn.extension());
    }

    static Name forClassName(String className, JavaFileObject.Kind kind) {
        int ix = className.lastIndexOf('.');
        String pkg = "";
        if (ix > 0 && ix < className.length() - 1) {
            pkg = className.substring(0, ix);
            className = className.substring(ix + 1, className.length());
        }
        UnixPath prefix = packageToPath(pkg);
        String ext = kind.extension;
        if (ext.length() > 0 && ext.charAt(0) == '.') {
            ext = ext.substring(1);
        }
        return new Name(prefix, className, ext);
    }

    static UnixPath packageToPath(String packageName) {
        UnixPath result;
        if (packageName != null && !packageName.isEmpty()) {
            result = UnixPath.get(packageName.replace('.', '/'));
        } else {
            return UnixPath.empty();
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
        UnixPath asPath = UnixPath.get(name).normalize();
        return new Name(asPath.getParent(), asPath.rawName(), asPath.extension());
    }

    static Name forPath(UnixPath path) {
        if (path.isAbsolute()) {
            JFS.LOG.log(Level.WARNING, "Absolute path passed to "
                    + "Name.forPath(): {0}. Converting to relative path",
                    path);
            path = path.toRelativePath();
        }
        path = path.normalize();
        return new Name(path.getParent(), path.rawName(), path.extension());
    }

    static Name forPath(UnixPath path, UnixPath relativeTo) {
        UnixPath realPath = relativeTo.normalize().relativize(path.normalize());
        return new Name(realPath.getParent(), realPath.rawName(), realPath.extension());
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

    boolean isPackage(UnixPath pkgPath, boolean orSubpackage) {
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
        return prefix.toString('.');
    }

    public UnixPath toPath() {
        return UnixPath.get(toString());
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

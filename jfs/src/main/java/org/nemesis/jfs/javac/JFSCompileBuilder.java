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
package org.nemesis.jfs.javac;

import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javax.tools.JavaFileManager.Location;
import org.nemesis.jfs.JFS;

/**
 *
 * @author Tim Boudreau
 */
public class JFSCompileBuilder {

    private final Set<ClasspathEntry> classpath = new LinkedHashSet<>();
    private final Supplier<JFS> jfs;
    private final JavacOptions options = new JavacOptions();
    private final Set<Location> locationsToCompile = new HashSet<>();
    private Writer compilerOutputWriter;

    public JFSCompileBuilder(JFS jfs) {
        this(() -> jfs);
    }

    public JFSCompileBuilder(Supplier<JFS> jfs) {
        this.jfs = jfs;
    }

    public String toString() {
        StringBuilder result = new StringBuilder("JFSCompileBuilder(")
                .append(jfs.get().id()).append("\nOptions: ")
                .append(options).append("\nCompile-loctions: ")
                .append(locationsToCompile).append("\nClasspath:\n");
        for (ClasspathEntry ce : classpath) {
            result.append(ce).append('\n');
        }
        return result.toString();
    }

    public JFSCompileBuilder addSourceLocation(Location location) {
        locationsToCompile.add(location);
        return this;
    }

    public JFSCompileBuilder addToClasspath(Class<?> type) {
        classpath.add(new ClassEntry(type));
        return this;
    }

    public JFSCompileBuilder addToClasspath(Path path) {
        return addToClasspath(path.toFile());
    }

    public JFSCompileBuilder addToClasspath(File file) {
        classpath.add(new FileEntry(file));
        return this;
    }

    public JFSCompileBuilder addToClasspath(URL url) {
        classpath.add(new URLEntry(url));
        return this;
    }

    public JFSCompileBuilder clearClasspath() {
        classpath.clear();
        return this;
    }

    public JFSCompileBuilder setOptions(JavacOptions options) {
        this.options.setFrom(options);
        return this;
    }

    public JFSCompileBuilder sourceAndTargetLevel(int tgt) {
        options.sourceAndTargetLevel(tgt);
        return this;
    }

    public JFSCompileBuilder sourceLevel(int level) {
        options.sourceLevel(level);
        return this;
    }

    public JFSCompileBuilder targetLevel(int level) {
        options.targetLevel(level);
        return this;
    }

    public JFSCompileBuilder withDebugInfo(JavacOptions.DebugInfo debug) {
        options.withDebugInfo(debug);
        return this;
    }

    public JFSCompileBuilder withMaxWarnings(int maxWarnings) {
        options.withMaxWarnings(maxWarnings);
        return this;
    }

    public JFSCompileBuilder withMaxErrors(int maxErrors) {
        options.withMaxErrors(maxErrors);
        return this;
    }

    public JFSCompileBuilder runAnnotationProcessors(boolean runAnnotationProcessors) {
        options.runAnnotationProcessors(runAnnotationProcessors);
        return this;
    }

    public JFSCompileBuilder verbose() {
        options.verbose();
        return this;
    }

    public JFSCompileBuilder nonIdeMode() {
        options.nonIdeMode();
        return this;
    }

    public JFSCompileBuilder compilerOutput(Writer writer) {
        this.compilerOutputWriter = writer;
        return this;
    }

    public JFSCompileBuilder abortOnBadClassFile() {
        options.abortOnBadClassFile();
        return this;
    }

    public JavacOptions options() {
        return options.copy();
    }

    private List<File> classpath() {
        List<File> allFiles = new ArrayList<>(classpath.size());
        for (ClasspathEntry e : classpath) {
            File f = e.toFile();
            if (f != null) {
                allFiles.add(f);
            }
        }
        return allFiles;
    }

    public CompileResult compileSingle(UnixPath path) throws IOException {
        JFS jfs = this.jfs.get();
        options.withCharset(jfs.encoding());
        CompileJavaSources compiler = new CompileJavaSources(options);
        jfs.setClasspathTo(classpath());
        return compiler.compile(compilerOutputWriter, jfs, path, locationsToCompile.toArray(
                new Location[locationsToCompile.size()]));
    }

    public CompileResult compile() throws IOException {
        JFS jfs = this.jfs.get();
        options.withCharset(jfs.encoding());
        CompileJavaSources compiler = new CompileJavaSources(options);
        jfs.setClasspathTo(classpath());
        return compiler.compile(compilerOutputWriter, jfs, locationsToCompile.toArray(
                new Location[locationsToCompile.size()]));
    }

    static abstract class ClasspathEntry {

        abstract File toFile();

        @Override
        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof ClasspathEntry) {
                return Objects.equals(toFile(), ((ClasspathEntry) o).toFile());
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(toFile());
        }

        @Override
        public final String toString() {
            return Objects.toString(toFile());
        }
    }

    static final class ClassEntry extends ClasspathEntry {

        private final Class<?> type;

        public ClassEntry(Class<?> type) {
            this.type = type;
        }

        @Override
        File toFile() {
            Path path = jarPathFor(type);
            if (path != null) {
                return path.toFile();
            }
            return null;
        }

    }

    static final class FileEntry extends ClasspathEntry {

        private final File file;

        public FileEntry(File file) {
            this.file = file;
        }

        @Override
        File toFile() {
            return file;
        }
    }

    static final class URLEntry extends ClasspathEntry {

        private final URL url;

        public URLEntry(URL url) {
            this.url = url;
        }

        @Override
        File toFile() {
            try {
                return new File(url.toURI());
            } catch (URISyntaxException ex) {
                return Exceptions.chuck(ex);
            }
        }

    }

    static Path jarPathFor(Class<?> includeCodeBaseOf) {
        if (includeCodeBaseOf == null) {
            return null;
        }
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

}

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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.Assert;
import org.nemesis.antlr.v4.netbeans.v8.project.ProjectTypeTest;
import org.openide.filesystems.FileUtil;

/**
 * Copies a grammar file into place in a unique test dir under /tmp and creates
 * build folders.
 */
public final class TestDir {

    final String name;
    final String packageName;
    final Path root;
    final Path antlrSources;
    final Path javaClasspathRoot;
    final Path outputPackage;
    final Path antlrSourceFile;
    final Path tmp;

    public TestDir(Path root, String name, String grammarStreamName, String packageName) throws IOException {
        this(root, name, grammarStreamName, packageName, null);
    }

    public TestDir(Path root, String name, String grammarStreamName, String packageName, Class<?> grammarStreamRelativeTo) throws IOException {
        this.name = name;
        this.packageName = packageName;
        this.root = root;
        if (grammarStreamRelativeTo == null) {
            grammarStreamRelativeTo = TestDir.class;
        }
        antlrSources = root.resolve(name);
        String fn = grammarStreamName;
        if (fn.endsWith("._g4")) {
            fn = fn.substring(0, fn.length() - 4) + ".g4";
        }
        antlrSourceFile = antlrSources.resolve(fn);
        System.out.println("antlrSourceFile is " + antlrSourceFile);
        javaClasspathRoot = antlrSources.resolve("output");
        outputPackage = javaClasspathRoot.resolve(packageName.replace('.', '/'));
        tmp = antlrSources.resolve("tmp");
        InputStream in = grammarStreamRelativeTo.getResourceAsStream(grammarStreamName);
        Assert.assertNotNull("Missing " + grammarStreamName + " on classpath adjacent to " + grammarStreamRelativeTo.getName(), in);
        Files.createDirectories(outputPackage);
        Files.createDirectories(tmp);
        try (final OutputStream out = Files.newOutputStream(antlrSourceFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileUtil.copy(in, out);
        }
    }

    public Path root() {
        return root;
    }

    public Path sources() {
        return antlrSources;
    }

    public Path grammar() {
        return antlrSourceFile;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            Files.walkFileTree(root, new FileVisitor<Path>() {
                int depth;

                char[] indent() {
                    char[] result = new char[depth * 2];
                    Arrays.fill(result, ' ');
                    return result;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    sb.append(indent()).append("> ").append(dir.getFileName()).append('\n');
                    depth++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    sb.append(indent()).append("- ").append(file.getFileName()).append('\n');
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    depth--;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        return sb.toString();
    }

    public static Path projectBaseDir() throws URISyntaxException {
        Path baseDir = Paths.get(ProjectTypeTest.class
                .getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getParent().getParent();
        return baseDir;
    }

    public static Path testResourcePath(Class<?> relativeTo, String name) throws URISyntaxException {
        Path base = projectBaseDir();
        return base.resolve(Paths.get("src", "test", "resources",
                relativeTo.getPackage().getName().replace('.', '/'), name));
    }

    public static String readFile(String name) throws URISyntaxException, IOException {
        return readFile(TestDir.class, name);
    }

    public static String readFile(Class<?> relativeTo, String name) throws URISyntaxException, IOException {
        Path p = testResourcePath(relativeTo, name);
        return new String(Files.readAllBytes(p), UTF_8);
    }

    public TestDir addImportFile(String name, Path orig) throws IOException {
        Path importDir = antlrSources.resolve("import");
        if (!Files.exists(importDir)) {
            Files.createDirectories(importDir);
        }
        Path dataFile = importDir.resolve(name);
        Files.copy(orig, dataFile);
        return this;
    }

    public TestDir addImportFile(String name, String data) throws IOException {
        Path importDir = antlrSources.resolve("import");
        if (!Files.exists(importDir)) {
            Files.createDirectories(importDir);
        }
        Path dataFile = importDir.resolve(name);
        Files.write(dataFile, data.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        return this;
    }

    public void cleanUp() throws IOException {
        Set<Path> paths = new HashSet<>();
        paths.add(antlrSources);
        Files.walkFileTree(antlrSources, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                paths.add(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                paths.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw new RuntimeException(exc);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        List<Path> toDelete = new ArrayList<>(paths);
        Collections.sort(toDelete, (a, b) -> {
            int ad = a.getNameCount();
            int bd = b.getNameCount();
            if (ad == bd) {
                return a.toString().compareTo(b.toString());
            }
            return ad < bd ? 1 : ad == bd ? 0 : -1;
        });
        for (Path p : toDelete) {
            if (RecompilationTest.reallyCleanup) {
                System.out.println("delete " + p);
                Files.delete(p);
            }
        }
    }

    @SuppressWarnings("StringEquality")
    public void modifyGrammar(Function<String, String> lineReceiver) throws IOException {
        List<String> newLines = new ArrayList<>(Files.readAllLines(antlrSourceFile));
        for (int i = 0; i < newLines.size(); i++) {
            String s = newLines.get(i);
            String revised = lineReceiver.apply(s);
            if (revised != s) {
                newLines.set(i, revised);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String ln : newLines) {
            sb.append(ln).append('\n');
        }
        ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
        try (final OutputStream out = Files.newOutputStream(antlrSourceFile, StandardOpenOption.WRITE)) {
            FileUtil.copy(in, out);
        }
    }

    public void replaceGrammar(String grammarStreamName) throws IOException {
        InputStream in = RecompilationTest.class.getResourceAsStream(grammarStreamName);
        Assert.assertNotNull("Missing " + grammarStreamName + " on classpath adjacent to " + RecompilationTest.class.getName(), in);
        try (final OutputStream out = Files.newOutputStream(antlrSourceFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileUtil.copy(in, out);
        }
    }
}

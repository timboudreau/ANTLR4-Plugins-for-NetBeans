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

    TestDir(Path root, String name, String grammarStreamName, String packageName) throws IOException {
        this.name = name;
        this.packageName = packageName;
        this.root = root;
        antlrSources = root.resolve(name);
        antlrSourceFile = antlrSources.resolve(grammarStreamName);
        javaClasspathRoot = antlrSources.resolve("output");
        outputPackage = javaClasspathRoot.resolve(packageName.replace('.', '/'));
        tmp = antlrSources.resolve("tmp");
        InputStream in = RecompilationTest.class.getResourceAsStream(grammarStreamName);
        Assert.assertNotNull("Missing " + grammarStreamName + " on classpath adjacent to " + RecompilationTest.class.getName(), in);
        Files.createDirectories(outputPackage);
        Files.createDirectories(tmp);
        try (final OutputStream out = Files.newOutputStream(antlrSourceFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileUtil.copy(in, out);
        }
    }

    public static Path projectBaseDir() throws URISyntaxException {
        Path baseDir = Paths.get(ProjectTypeTest.class
                .getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getParent().getParent().getParent().getParent();
        return baseDir;
    }

    public static Path testResourcePath(Class<?> relativeTo, String name) throws URISyntaxException {
        Path base = projectBaseDir();
        return base.resolve(Paths.get("test", "unit", "src",
                relativeTo.getPackage().getName().replace('.', '/'), name));
    }

    public TestDir addImportFile(Path relativePath, String name, String data) throws IOException {
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

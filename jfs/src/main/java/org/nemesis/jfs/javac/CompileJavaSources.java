package org.nemesis.jfs.javac;

import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.SOURCE_PATH;
import javax.tools.ToolProvider;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public class CompileJavaSources {

    private static final Logger LOG = Logger.getLogger(CompileJavaSources.class.getName());
    private final JavacOptions options;
    private final Class<?> includedCodeBaseOf;

    public CompileJavaSources() {
        this(new JavacOptions());
    }

    public CompileJavaSources(JavacOptions options) {
        this(options, null);
    }

    public CompileJavaSources(JavacOptions options, Class<?> includedCodeBaseOf) {
        this.options = options.copy();
        this.includedCodeBaseOf = includedCodeBaseOf;
    }

    public CompileJavaSources(Class<?> includedCodeBaseOf) {
        this.options = new JavacOptions();
        this.includedCodeBaseOf = includedCodeBaseOf;
    }

    private Iterable<JavaFileObject> sourceLocations(JFS jfs, Location... locations) throws IOException {
        if (locations.length == 0) {
            return jfs.list(SOURCE_PATH, "", EnumSet.of(SOURCE), true);
        }
        List<Iterable<JavaFileObject>> all = new ArrayList<>();
        for (Location loc : locations) {
            Iterable<JavaFileObject> nue = jfs.list(loc, "", EnumSet.of(SOURCE), true);
            all.add(nue);
        }
        return CollectionUtils.concatenate(all);
    }

    /**
     * Compile the sources in a JFS.
     *
     * @param jfs A JFS
     * @param sourceLocations The set of source locations to compile - if empty,
     * StandardLocation.SOURCE_PATH is used
     * @return A compile result
     */
    public CompileResult compile(JFS jfs, Location... sourceLocations) {
        return compile(jfs, null, sourceLocations);
    }

    private Iterable<JavaFileObject> singleSource(JFS jfs, Path loc, Location... sourceLocations) {
        JavaFileObject target;
        for (Location l : sourceLocations.length == 0 ? new Location[]{StandardLocation.SOURCE_PATH} : sourceLocations) {
            JFSFileObject o = jfs.get(l, loc);
            if (o != null) {
                if (o instanceof JavaFileObject) {
                    return Collections.singleton((JavaFileObject) o);
                } else {
                    return Collections.singleton(o.toJavaFileObject());
                }
            }
        }
        return Collections.emptySet();
    }

    public CompileResult compile(JFS jfs, Path singleSource, Location... sourceLocations) {
        CompileResult.Builder result = CompileResult.builder(Paths.get(""));
        result.setInitialFileStatus(jfs.status(setOf(sourceLocations)));
        try {
            if (includedCodeBaseOf != null) {
                throw new IllegalStateException("Cannot include the codebase of " + includedCodeBaseOf.getName()
                        + " when using JFS compilation - add it to the classpath in your JFSBuilder instead.");
            }
            L diagnosticListener = new L(result);
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            List<String> compilerOptions = this.options.copy().withCharset(jfs.encoding()).options(compiler);
            Iterable<JavaFileObject> toCompile = singleSource != null 
                    ? singleSource(jfs, singleSource, sourceLocations)
                    : sourceLocations(jfs, sourceLocations);
            JavaCompiler.CompilationTask task = compiler.getTask(null,
                    jfs, diagnosticListener, compilerOptions, null,
                    toCompile);
            List<Path> paths = new LinkedList<>();
            for (JavaFileObject jfo : toCompile) {
                LOG.log(Level.FINEST, "Compile {0}", jfo);
                paths.add(Paths.get(jfo.getName()));
                result.addSource(jfo);
            }
            long then = System.currentTimeMillis();
            boolean javacResult = task.call();
            result.withJavacResult(javacResult);
            long elapsed = System.currentTimeMillis() - then;
            result.withFiles(paths);
            result.elapsed(elapsed);
            LOG.log(Level.FINE, "Compile took {0}ms. Ok? {1}", new Object[]{elapsed, javacResult});
        } catch (Exception e) {
            LOG.log(Level.INFO, "Virtual compilation threw", e);
            result.thrown(e);
        }
        return result.build();
    }

    public CompileResult compile(Path sourceRoot, Path output, Path[] classpath) {
        return compile(UTF_8, sourceRoot, output, classpath);
    }

    public CompileResult compile(Charset charset, Path sourceRoot, Path output, Path[] classpath) {
        long then = System.currentTimeMillis();
        CompileResult.Builder result = CompileResult.builder(sourceRoot);
        Boolean callResult = null;
//        CompileMonitorThread monitor = new CompileMonitorThread(Thread.currentThread());
//        monitor.start();
        try {
            if (!Files.exists(sourceRoot)) {
                result.thrown(new IOException("Source root does not exist: " + sourceRoot));
                return result.build();
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

            L diagnosticListener = new L(result);

            StandardJavaFileManager m = compiler
                    .getStandardFileManager(diagnosticListener, Locale.getDefault(),
                            charset);

            List<Path> files = new ArrayList<>(10);
            Files.walkFileTree(sourceRoot, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
//            System.out.println("WILL COMPILE " + files);
            result.withFiles(files);

//        Iterable<? extends JavaFileObject> toCompile
//                = m.getJavaFileObjectsFromPaths(files);
            m.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(sourceRoot.toFile()));
            m.setLocation(StandardLocation.SOURCE_PATH, Collections.singleton(sourceRoot.toFile()));
            Set<File> classpathItems = new HashSet<>();
            // XXX this does not work at runtime
            Path moduleJar = jarPathFor(includedCodeBaseOf);
            if (moduleJar != null) {
                classpathItems.add(moduleJar.toFile());
            }
            for (Path path : classpath) {
                classpathItems.add(path.toFile());
            }
            m.setLocation(StandardLocation.CLASS_PATH, classpathItems);
            Set<File> fls = new HashSet<>();
            for (Path p : files) {
                fls.add(p.toFile());
            }
            Iterable<? extends JavaFileObject> fos = m.getJavaFileObjects(files.toArray(new File[files.size()]));
            for (JavaFileObject fo : fos) {
                result.addSource(fo);
            }

            Iterable<String> options = this.options.options(compiler);

            LOG.log(Level.FINER, "Compile {0} into {1} with classpath {2}",
                    new Object[]{files, sourceRoot, classpathItems});

            JavaCompiler.CompilationTask task = compiler.getTask(null,
                    m, diagnosticListener, options, null,
                    m.getJavaFileObjects(fls.toArray(new File[fls.size()])));

            callResult = task.call();
            LOG.log(Level.FINE, "Compile result: {0}", callResult);
            result.withJavacResult(callResult);
        } catch (Exception | Error err) {
            result.thrown(err);
            LOG.log(Level.FINE, "Compile threw", err);
        } finally {
            LOG.log(Level.FINE, "Compile took {0}ms. Ok? {1}", new Object[]{System.currentTimeMillis() - then, callResult});
//            monitor.interrupt();
        }
        return result.build();
    }

    public static Path jarPathFor(Class<?> includedCodeBaseOf) {
        if (includedCodeBaseOf == null) {
            return null;
        }
        try {
            URI uri = includedCodeBaseOf
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

    private static class L implements DiagnosticListener<JavaFileObject> {

        final CompileResult.Builder res;

        L(CompileResult.Builder res) {
            this.res = res;
        }

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            LOG.log(Level.FINEST, "javac: {0}", diagnostic);
            res.addDiagnostic(diagnostic);
        }
    }
}

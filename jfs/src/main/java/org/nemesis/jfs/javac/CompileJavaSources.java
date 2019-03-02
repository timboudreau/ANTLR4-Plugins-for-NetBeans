package org.nemesis.jfs.javac;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
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
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.SOURCE_PATH;
import javax.tools.ToolProvider;
import org.nemesis.jfs.JFS;

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

    static final class CompileMonitorThread extends Thread {
        // A debugging tool that just dumps the stack of
        // the compile thread - keep for now
        private final Thread toMonitor;

        CompileMonitorThread(Thread toMonitor) {
            this.toMonitor = toMonitor;
        }

        public void run() {
            for (int i = 0;; i++) {
                try {
                    Thread.sleep(1000);
                    System.out.println("Iter " + i);
                    for (StackTraceElement st : toMonitor.getStackTrace()) {
                        System.out.println(st);
                    }
                    if (Thread.interrupted()) {
                        break;
                    }
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }
    }

    public CompileResult compile(JFS jfs) {
        CompileResult.Builder result = CompileResult.builder(Paths.get(""));
        try {
            L diagnosticListener = new L(result);
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            List<String> options = this.options.copy().withCharset(jfs.encoding()).options(compiler);

            Iterable<JavaFileObject> toCompile = jfs.list(SOURCE_PATH, "", EnumSet.of(SOURCE), true);
            JavaCompiler.CompilationTask task = compiler.getTask(null,
                    jfs, diagnosticListener, options, null,
                    toCompile);
            List<Path> paths = new LinkedList<>();
            for (JavaFileObject jfo : toCompile) {
                LOG.log(Level.FINEST, "Compile {0}", jfo);
                paths.add(Paths.get(jfo.getName()));
            }
            long then = System.currentTimeMillis();
            boolean javacResult = task.call();
            result.withJavacResult(javacResult);
            long elapsed = System.currentTimeMillis() - then;
            result.withFiles(paths);
            LOG.log(Level.FINE, "Compile took {0}ms. Ok? {1}", new Object[]{elapsed, javacResult});
        } catch (Exception e) {
            LOG.log(Level.INFO, "Virtual compilation threw", e);
            result.thrown(e);
        }
        return result.build();
    }

    public CompileResult compile(Path sourceRoot, Path output, Path[] classpath) {
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
                            Charset.forName("UTF-8"));

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
                result.addClasspathItem(moduleJar);
            }
            for (Path path : classpath) {
                result.addClasspathItem(path);
                classpathItems.add(path.toFile());
            }
            m.setLocation(StandardLocation.CLASS_PATH, classpathItems);
            Set<File> fls = new HashSet<>();
            for (Path p : files) {
                fls.add(p.toFile());
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

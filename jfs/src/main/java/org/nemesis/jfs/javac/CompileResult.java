package org.nemesis.jfs.javac;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public class CompileResult {

    boolean callResult;
    private final List<JavacDiagnostic> diagnostics = new ArrayList<>();
    Throwable thrown;
    private final Path sourceRoot;
    private final List<Path> files = new ArrayList<>();
    private final List<Path> classpath = new ArrayList<>();

    CompileResult(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public static CompileResult.Builder builder(Path sourceRoot) {
        return new Builder(sourceRoot);
    }

    public static final class Builder {

        private final List<JavacDiagnostic> diagnostics = new ArrayList<>();
        private final Path sourceRoot;
        private final List<Path> files = new ArrayList<>();
        private final List<Path> classpath = new ArrayList<>();
        private Throwable thrown;
        private boolean callResult;

        public Builder(Path sourceRoot) {
            assert sourceRoot != null : "Source root null";
            this.sourceRoot = sourceRoot;
        }

        public Builder addClasspathItem(Path path) {
            classpath.add(path);
            return this;
        }

        public CompileResult build() {
            CompileResult res = new CompileResult(sourceRoot);
            res.diagnostics.addAll(diagnostics);
            res.files.addAll(files);
            res.classpath.addAll(classpath);
            res.thrown = thrown;
            res.callResult = callResult;
            return res;
        }

        public Builder addDiagnostic(JavacDiagnostic diag) {
            diagnostics.add(diag);
            return this;
        }

        public Builder withClasspath(Collection<? extends Path> classpath) {
            this.classpath.addAll(classpath);
            return this;
        }

        public Builder withFiles(Collection<? extends Path> files) {
            this.files.addAll(files);
            return this;
        }

        public Builder thrown(Throwable thrown) {
            this.thrown = thrown;
            return this;
        }

        public Builder withJavacResult(boolean callResult) {
            this.callResult = callResult;
            return this;
        }

        private String sourcePath(Diagnostic<? extends JavaFileObject> diag) {
            String path;
            if (diag.getSource() == null) { // annotation warnings
                path = sourceRoot.toString();
            } else {
                if (diag.getSource() instanceof JFSFileObject) {
                    path = ((JFSFileObject) diag.getSource()).toPath().toString();
                } else {
                    URI uri = diag.getSource().toUri();
                    try {
                        path = Paths.get(uri).toString();
                    } catch (Exception e) {
                        path = diag.getSource().toString();
                    }
                }
            }
            return path;
        }

        public Builder addDiagnostic(Diagnostic<? extends JavaFileObject> diag) {
            assert diag != null : "Diag null";
            JavacDiagnostic wrapper = JavacDiagnostic.create(sourcePath(diag), sourceRoot, diag);
            diagnostics.add(wrapper);
            return this;
        }
    }

    public Path sourceRoot() {
        return sourceRoot;
    }

    public Optional<Throwable> thrown() {
        if (thrown != null) {
            return Optional.of(thrown);
        }
        for (JavacDiagnostic d : diagnostics) {
            if (d.isError()) {
                return Optional.of(d.toThrowable());
            }
        }
        return Optional.empty();
    }

    public boolean isUsable() {
        boolean cr = callResult;
        boolean noThrown = thrown == null;
        boolean compileSucceeded = !compileFailed();
        return cr && noThrown && compileSucceeded;
    }

    public boolean compileFailed() {
        for (JavacDiagnostic d : diagnostics) {
            if (d.isError()) {
                return true;
            }
        }
        return false;
    }

    public List<Path> sources() {
        return files;
    }

    public List<Path> classpath() {
        return classpath;
    }

    public boolean ok() {
        return callResult;
    }

    public List<JavacDiagnostic> diagnostics() {
        return diagnostics;
    }

    void addClasspathItem(Path path) {
        classpath.add(path);
    }
}

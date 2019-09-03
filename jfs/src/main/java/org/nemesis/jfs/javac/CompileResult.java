package org.nemesis.jfs.javac;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.result.ProcessingResult;
import org.nemesis.jfs.result.UpToDateness;

/**
 *
 * @author Tim Boudreau
 */
public class CompileResult implements ProcessingResult {

    boolean callResult;
    private final List<JavacDiagnostic> diagnostics = new ArrayList<>();
    Throwable thrown;
    private final Path sourceRoot;
    private final List<Path> files = new ArrayList<>();
    private final Map<JavaFileObject, Long> sourceFilesToModifications = new HashMap<>();

    CompileResult(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    static CompileResult.Builder builder(Path sourceRoot) {
        return new Builder(sourceRoot);
    }

    @Override
    public UpToDateness currentStatus() {
        if (!isUsable() || sourceFilesToModifications.isEmpty()) {
            return UpToDateness.UNKNOWN;
        }
        for (Map.Entry<JavaFileObject, Long> e : sourceFilesToModifications.entrySet()) {
            if (e.getKey().getLastModified() > e.getValue()) {
                return UpToDateness.STALE;
            }
        }
        return UpToDateness.CURRENT;
    }

    static final class Builder {

        private final List<JavacDiagnostic> diagnostics = new ArrayList<>();
        private final Path sourceRoot;
        private final List<Path> files = new ArrayList<>();
        private Throwable thrown;
        private boolean callResult;
        private final Map<JavaFileObject, Long> sourceFilesToModifications = new HashMap<>();

        Builder(Path sourceRoot) {
            assert sourceRoot != null : "Source root null";
            this.sourceRoot = sourceRoot;
        }

        public Builder addSource(JavaFileObject fo) {
            sourceFilesToModifications.put(fo, fo.getLastModified());
            return this;
        }

        public CompileResult build() {
            CompileResult res = new CompileResult(sourceRoot);
            res.diagnostics.addAll(diagnostics);
            res.files.addAll(files);
            res.thrown = thrown;
            res.callResult = callResult;
            res.sourceFilesToModifications.putAll(sourceFilesToModifications);
            return res;
        }

        public Builder addDiagnostic(JavacDiagnostic diag) {
            diagnostics.add(diag);
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

    /**
     * Get the source root for compilation. Note that for JFS compilation this
     * will be a meaningless path of the empty string.
     *
     * @return The root directory for Java sources and packages
     */
    public Path sourceRoot() {
        return sourceRoot;
    }

    /**
     * Returns a throwable, if any, wrapped in an Optional. The throwable may be
     * one thrown during the compilation process, or it may be one which is
     * constructed from a diagnostic if a diagnostic of level ERROR is reported.
     * This is proxied to avoid leaking objects from the javac classloader.
     *
     * @return An optional which may contain a throwable
     */
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

    /**
     * Determine if the job completed successfully, no exceptions were thrown
     * and no error diagnostics were emitted during compilation. This is usually
     * the method you want if you intend to load and call compiled classes.
     *
     * @return Whether or not everything completed successfully
     */
    @Override
    public boolean isUsable() {
        boolean cr = callResult;
        boolean noThrown = thrown == null;
        boolean compileSucceeded = !compileFailed();
        return cr && noThrown && compileSucceeded;
    }

    /**
     * Returns true (<i>even if ok() reports true!</i>) if
     * <i>compilation</i> failed - if an error diagnostic was reported,
     * regardless of the overall status of the compilation job (you can tell
     * javac to ignore errors, in which case it will succeed, but succeed by a
     * definition of success that doesn't include it producing usable output).
     *
     * @return Whether or not compilation failed, as distinct from whether the
     * compilation <i>job</i> failed.
     */
    public boolean compileFailed() {
        for (JavacDiagnostic d : diagnostics) {
            if (d.isError()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The source files which were to be compiled.
     *
     * @return The source files
     */
    public List<Path> sources() {
        return files;
    }

    /**
     * Returns true if the Java <i>compilation</i> job reported success (this is
     * distinct from whether usable class files were generated). To find out
     * overall if the compilation job emitted class files that are complete and
     * safe to use, you should call <code>isUsable()</code>.
     *
     * @return True if the job reported success
     */
    public boolean ok() {
        return callResult;
    }

    /**
     * Get the list of any warnings, notes or errors emitted by the compiler or
     * annotation processors during the compilation process. These are
     * functionally equivalent to javac's Diagnostic class, but are proxied to
     * avoid leaking objects from javac's classloader.
     *
     * @return A list of diagnostics
     */
    public List<JavacDiagnostic> diagnostics() {
        return diagnostics;
    }
}

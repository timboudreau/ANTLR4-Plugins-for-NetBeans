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
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.result.ProcessingResult;
import org.nemesis.jfs.result.UpToDateness;

/**
 * Result of compilation with in-memory javac.
 *
 * @author Tim Boudreau
 */
public class CompileResult implements ProcessingResult {

    boolean callResult;
    long elapsed;
    private final List<JavacDiagnostic> diagnostics = new ArrayList<>();
    Throwable thrown;
    private final Path sourceRoot;
    private final List<Path> files = new ArrayList<>();
    private final JFSFileModifications filesState;

    CompileResult(Path sourceRoot, JFSFileModifications filesState) {
        this.sourceRoot = sourceRoot;
        this.filesState = filesState;
    }

    /**
     * Create a fake compilation result, for cases when compilation is not
     * needed because sources are either broken or up-to-date.  The
     * result will have an empty modification set and so will always
     * be up to date.
     *
     * @param success If true, the result will appear to be successful
     * compilation
     * @param sourceRoot The source root
     * @return A compiler result
     */
    public static CompileResult precompiled(boolean success, Path sourceRoot) {
        CompileResult result = new CompileResult(sourceRoot, JFSFileModifications.empty());
        result.callResult = success;
        return result;
    }

    /**
     * Create a fake compilation result, for cases when compilation is not
     * needed because sources are either broken or up-to-date.  The
     * result will have an empty modification set and so will always
     * be up to date.
     *
     * @param success If true, the result will appear to be successful
     * compilation
     * @param sourceRoot The source root
     * @return A compiler result
     */
    public static CompileResult precompiled(boolean success) {
        CompileResult result = new CompileResult(UnixPath.empty(), JFSFileModifications.empty());
        result.callResult = success;
        return result;
    }


    /**
     * Determine if specific errors (as determined by Diagnostic.sourceCode()) exist
     * in the set of diagnostics in this result.
     *
     * @param errors Some error codes
     * @return True if any of them are present
     */
    public boolean hasErrors(String... errors) {
        if (errors.length == 0) {
            return false;
        }
        if (diagnostics.isEmpty()) {
            return false;
        }
        Set<String> set = new HashSet<>(Arrays.asList(errors));
        for (JavacDiagnostic d : diagnostics) {
            if (set.contains(d.sourceCode())) {
                return true;
            }
        }
        return false;
    }

    public JFSFileModifications filesState() {
        return filesState;
    }

    static CompileResult.Builder builder(Path sourceRoot) {
        return new Builder(sourceRoot);
    }

    public void refreshFilesStatus() {
        filesState.refresh();
    }

    @Override
    public String toString() {
        return "CompileResult(completed " + callResult + " thrown " + thrown
                + " diagnostics " + diagnostics.size() + " sourceFiles "
                + files.size() + " elapsedMs " + elapsed + ")";
    }

    /**
     * Get the elapsed time spent in compilation to produce this result.
     *
     * @return the elapsed milliseconds
     */
    public long elapsedMillis() {
        return elapsed;
    }

    void elapsedMillis(long elapsedMillis) {
        this.elapsed = elapsedMillis;
    }

    @Override
    public UpToDateness currentStatus() {
        return filesState.changes().status();
    }

    static final class Builder {

        private final List<JavacDiagnostic> diagnostics = new ArrayList<>();
        private final Path sourceRoot;
        private final List<Path> files = new ArrayList<>();
        private Throwable thrown;
        private boolean callResult;
        long elapsed;
        private JFSFileModifications filesState;

        Builder(Path sourceRoot) {
            assert sourceRoot != null : "Source root null";
            this.sourceRoot = sourceRoot;
        }

        public Builder addSource(JavaFileObject fo) {
            return this;
        }

        void elapsed(long elapsed) {
            this.elapsed = elapsed;
        }

        public CompileResult build() {
            CompileResult res = new CompileResult(sourceRoot, filesState);
            res.diagnostics.addAll(diagnostics);
            res.files.addAll(files);
            res.thrown = thrown;
            res.callResult = callResult;
            res.elapsedMillis(elapsed);
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

        public void setInitialFileStatus(JFSFileModifications status) {
            this.filesState = status;
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

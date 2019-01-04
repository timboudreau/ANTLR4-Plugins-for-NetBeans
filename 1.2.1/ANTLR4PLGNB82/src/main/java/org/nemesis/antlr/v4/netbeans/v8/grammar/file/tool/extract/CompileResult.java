package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.JFSFileObject;
import org.openide.util.Parameters;

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

    void setFiles(List<Path> files) {
        this.files.addAll(files);
    }

    public void addDiagnostic(Diagnostic<? extends JavaFileObject> diag) {
        Parameters.notNull("diag", diag);
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
        JavacDiagnostic wrapper = new JavacDiagnostic(path, diag.getCode(), diag.getKind(), diag.getLineNumber(), diag.getColumnNumber(), diag.getPosition(), diag.getEndPosition(), diag.getMessage(Locale.getDefault()), sourceRoot);
        diagnostics.add(wrapper);
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

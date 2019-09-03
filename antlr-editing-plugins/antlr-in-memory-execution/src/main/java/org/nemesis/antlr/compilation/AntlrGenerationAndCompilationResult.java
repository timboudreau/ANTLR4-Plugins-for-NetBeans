package org.nemesis.antlr.compilation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.tools.JavaFileManager;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.jfs.result.UpToDateness;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.jfs.result.ProcessingResult;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrGenerationAndCompilationResult implements ProcessingResult {

    private final AntlrGenerationResult grammarGenerationResult;
    private final CompileResult compilationResult;
    private final Throwable thrown;
    private final Map<JFSFileObject, Long> touched;

    AntlrGenerationAndCompilationResult(AntlrGenerationResult grammarGenerationResult,
            CompileResult compilationResult, Throwable thrown,
            Map<JFSFileObject, Long> touched) {
        this.grammarGenerationResult = grammarGenerationResult;
        this.compilationResult = compilationResult;
        this.thrown = thrown;
        this.touched = touched;
    }

    public AntlrGenerationResult generationResult() {
        return grammarGenerationResult;
    }

    public JavaFileManager.Location javaSourceOutputLocation() {
        return grammarGenerationResult.javaSourceOutputLocation();
    }

    public String packageName() {
        return grammarGenerationResult.packageName();
    }

    public List<ParsedAntlrError> grammarGenerationErrors() {
        return grammarGenerationResult.errors();
    }

    public List<String> grammarGenerationInfoMessages() {
        return grammarGenerationResult.infoMessages();
    }

    public Grammar mainGrammar() {
        return grammarGenerationResult.mainGrammar();
    }

    public int grammarGenerationExitCode() {
        return grammarGenerationResult.exitCode();
    }

    @Override
    public UpToDateness currentStatus() {
        if (grammarGenerationResult == null || compilationResult == null || touched.isEmpty()) {
            return UpToDateness.UNKNOWN;
        }
        UpToDateness result = grammarGenerationResult.currentStatus();
        if (result.mayRequireRebuild()) {
            return result;
        }
        result = compilationResult.currentStatus();
        if (result.mayRequireRebuild()) {
            return result;
        }
        for (Map.Entry<JFSFileObject, Long> e : touched.entrySet()) {
            if (e.getKey().getLastModified() > e.getValue()) {
                return UpToDateness.STALE;
            }
        }
        return UpToDateness.CURRENT;
    }

    @Override
    public boolean isUsable() {
        return thrown == null
                && grammarGenerationResult.isUsable()
                && compilationResult.isUsable();
    }

    public JFS jfs() {
        return grammarGenerationResult.jfs();
    }

    public Optional<Grammar> findGrammar(String name) {
        return grammarGenerationResult.findGrammar(name);
    }

    public void rethrow() throws Throwable {
        Optional<Throwable> th = thrown();
        if (th.isPresent()) {
            throw th.get();
        }
    }

    public Path sourceRoot() {
        return compilationResult.sourceRoot();
    }

    @Override
    public Optional<Throwable> thrown() {
        if (this.thrown != null) {
            return Optional.of(this.thrown);
        }
        if (grammarGenerationResult != null && grammarGenerationResult.thrown().isPresent()) {
            return grammarGenerationResult.thrown();
        }
        if (compilationResult != null && compilationResult.thrown().isPresent()) {
            return compilationResult.thrown();
        }
        return Optional.empty();
    }

    public boolean compileFailed() {
        return compilationResult.compileFailed();
    }

    public List<Path> compiledSourceFiles() {
        return compilationResult.sources();
    }

    public List<JavacDiagnostic> javacDiagnostics() {
        return compilationResult.diagnostics();
    }

}

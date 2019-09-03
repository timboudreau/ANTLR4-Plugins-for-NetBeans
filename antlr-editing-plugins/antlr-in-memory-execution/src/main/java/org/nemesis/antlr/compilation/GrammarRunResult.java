package org.nemesis.antlr.compilation;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.antlr.v4.tool.Grammar;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.jfs.result.ProcessingResult;
import org.nemesis.jfs.result.UpToDateness;

/**
 *
 * @author Tim Boudreau
 */
public class GrammarRunResult<T> implements ProcessingResult, Supplier<T> {

    private T result;
    private final Throwable thrown;
    private final GenerationAndCompilationResult buildResult;
    private final GrammarRunResult<T> lastGood;
    private final AntlrGenerationAndCompilationResult genResult;

    GrammarRunResult(T result, Throwable thrown, GenerationAndCompilationResult buildResult, GrammarRunResult<T> lastGood) {
        this.result = result;
        this.thrown = thrown;
        this.buildResult = buildResult;
        this.lastGood = lastGood;
        genResult = buildResult.genResult();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GrammarRunResult(");
        sb.append("usable=").append(isUsable())
                .append(", result=").append(result).append("\n\n");
        sb.append("buildResult.usable=").append(buildResult.isUsable());
        sb.append(" buildResult.genAndCompileResult.usable=")
                .append(buildResult.genAndCompileResult.isUsable());
        sb.append(" buildResult.genAndCompileResult.usable=");
        sb.append(buildResult.genAndCompileResult.isUsable());
        sb.append(" buildResult.genAndCompileResult.grammarGenerationResult.usable=");
        sb.append(buildResult.genAndCompileResult.generationResult().isUsable());
        return sb.toString();
    }

    void disposeResult() {
        result = null;
    }

    public JFS jfs() {
        return genResult.jfs();
    }

    public Optional<Grammar> findGrammar(String name) {
        return genResult.findGrammar(name);
    }

    public Path sourceRoot() {
        return genResult.sourceRoot();
    }

    public boolean compileFailed() {
        return genResult.compileFailed();
    }

    public List<Path> sources() {
        return genResult.compiledSourceFiles();
    }

    public List<JavacDiagnostic> diagnostics() {
        return genResult.javacDiagnostics();
    }

    public GrammarRunResult<T> lastGood() {
        return lastGood;
    }

    public final AntlrGenerationAndCompilationResult genResult() {
        return buildResult.genResult();
    }

    @Override
    public boolean isUsable() {
        return buildResult.isUsable() && thrown == null;
    }

    @Override
    public Optional<Throwable> thrown() {
        if (thrown != null) {
            return Optional.of(thrown);
        }
        return buildResult.thrown();
    }

    public String generationOutput() {
        return buildResult.output();
    }

    @Override
    public UpToDateness currentStatus() {
        return buildResult.currentStatus();
    }

    @Override
    public T get() {
        return result;
    }
}

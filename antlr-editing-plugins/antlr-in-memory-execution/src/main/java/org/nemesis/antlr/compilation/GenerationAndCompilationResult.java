package org.nemesis.antlr.compilation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.nemesis.jfs.result.ProcessingResult;
import org.nemesis.jfs.result.UpToDateness;

/**
 *
 * @author Tim Boudreau
 */
final class GenerationAndCompilationResult implements ProcessingResult {

    final ByteArrayOutputStream output;
    final AntlrGenerationAndCompilationResult genAndCompileResult;

    public GenerationAndCompilationResult(ByteArrayOutputStream output, AntlrGenerationAndCompilationResult genAndCompileResult) {
        this.output = output;
        this.genAndCompileResult = genAndCompileResult;
    }

    public String output() {
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public final AntlrGenerationAndCompilationResult genResult() {
        return genAndCompileResult;
    }

    @Override
    public boolean isUsable() {
        return genAndCompileResult.isUsable();
    }

    @Override
    public Optional<Throwable> thrown() {
        return genAndCompileResult.thrown();
    }

    @Override
    public UpToDateness currentStatus() {
        return genAndCompileResult.currentStatus();
    }

}

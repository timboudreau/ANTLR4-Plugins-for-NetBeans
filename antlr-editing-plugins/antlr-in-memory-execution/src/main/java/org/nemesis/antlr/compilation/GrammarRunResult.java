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
    private final AntlrGenerationAndCompilationResult generationAndCompilationResult;
    private final GrammarRunResult<T> lastGood;
    private final long timestamp = System.currentTimeMillis();

    GrammarRunResult(T result, Throwable thrown, AntlrGenerationAndCompilationResult buildResult, GrammarRunResult<T> lastGood) {
        this.result = result;
        this.thrown = thrown;
        this.generationAndCompilationResult = buildResult;
        // Only need a last-good result if we are not; otherwise we
        // will leak a chain of GrammarRunResults back to the dawn of time.
        this.lastGood = buildResult.isUsable() ? null : lastGood;
    }

    public String grammarName() {
        if (generationAndCompilationResult != null && generationAndCompilationResult.generationResult() != null) {
            return generationAndCompilationResult.generationResult().grammarName;
        }
        if (lastGood != null && lastGood.generationAndCompilationResult != null && lastGood.generationAndCompilationResult.generationResult() != null) {
            return lastGood.generationAndCompilationResult.generationResult().grammarName;
        }
        if (generationAndCompilationResult != null && generationAndCompilationResult.mainGrammar() != null) {
            return generationAndCompilationResult.mainGrammar().name;
        }
        return "Unknown";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GrammarRunResult(");
        sb.append("usable=").append(isUsable())
                .append(", result=").append(result).append("\n\n");
        sb.append("buildResult.usable=").append(generationAndCompilationResult.isUsable());
        sb.append(" buildResult.grammarGenerationResult.usable=");
        sb.append(generationAndCompilationResult.generationResult().isUsable());
        return sb.toString();
    }

    void disposeResult() {
        result = null;
    }

    public long timestamp() {
        return timestamp;
    }

    public JFS jfs() {
        return generationAndCompilationResult.jfs();
    }

    public Optional<Grammar> findGrammar(String name) {
        return generationAndCompilationResult.findGrammar(name);
    }

    public Path sourceRoot() {
        return generationAndCompilationResult.sourceRoot();
    }

    public boolean compileFailed() {
        return generationAndCompilationResult.compileFailed();
    }

    public List<Path> sources() {
        return generationAndCompilationResult.compiledSourceFiles();
    }

    public List<JavacDiagnostic> diagnostics() {
        return generationAndCompilationResult.javacDiagnostics();
    }

    public GrammarRunResult<T> lastGood() {
        return lastGood;
    }

    public final AntlrGenerationAndCompilationResult genResult() {
        return generationAndCompilationResult;
    }

    @Override
    public boolean isUsable() {
        return generationAndCompilationResult.isUsable() && thrown == null;
    }

    @Override
    public Optional<Throwable> thrown() {
        if (thrown != null) {
            return Optional.of(thrown);
        }
        return generationAndCompilationResult.thrown();
    }

    @Override
    public UpToDateness currentStatus() {
        return generationAndCompilationResult.currentStatus();
    }

    @Override
    public T get() {
        return result;
    }
}

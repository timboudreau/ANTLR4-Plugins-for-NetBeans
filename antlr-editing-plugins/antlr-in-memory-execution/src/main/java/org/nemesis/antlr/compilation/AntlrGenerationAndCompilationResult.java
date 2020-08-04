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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.tools.JavaFileManager;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.jfs.result.UpToDateness;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileModifications;
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
    private final JFSFileModifications touched;

    AntlrGenerationAndCompilationResult(AntlrGenerationResult grammarGenerationResult,
            CompileResult compilationResult, Throwable thrown,
            JFSFileModifications touched) {
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

    public void refreshFileStatusForReuse() {
        if (grammarGenerationResult != null) {
            grammarGenerationResult.filesStatus.refresh();
        }
        if (compilationResult != null) {
            compilationResult.refreshFilesStatus();
        }
        touched.refresh();
    }

    @Override
    public UpToDateness currentStatus() {
        if (grammarGenerationResult == null || compilationResult == null) {
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
        result = touched.changes().status();
        if (!result.isUpToDate()) {
        }
        return result;
    }

    @Override
    public boolean isUsable() {
        return thrown == null
                && grammarGenerationResult.isUsable()
                && compilationResult.isUsable();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('(');
        sb.append("thrown=").append(thrown)
                .append(" grammarGenerationResult=")
                .append(grammarGenerationResult)
                .append(" compilationResult=")
                .append(compilationResult);
        return sb.append(')').toString();
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
        return compilationResult == null ? null : compilationResult.sourceRoot();
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
        return compilationResult == null ? false : compilationResult.compileFailed();
    }

    public List<Path> compiledSourceFiles() {
        return compilationResult == null ? Collections.emptyList() : compilationResult.sources();
    }

    public List<JavacDiagnostic> javacDiagnostics() {
        return compilationResult == null ? Collections.emptyList() : compilationResult.diagnostics();
    }

}

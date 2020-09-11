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
import java.util.function.Supplier;
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

    private AntlrGenerationResult grammarGenerationResult;
    private CompileResult compilationResult;
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

    public synchronized CompileResult compilationResult() {
        return compilationResult;
    }

    @Override
    public <T> T getWrapped(Class<T> type) {
        if (type == AntlrGenerationResult.class) {
            AntlrGenerationResult res;
            synchronized(this) {
                res = grammarGenerationResult;
            }
            if (res != null) {
                return type.cast(res);
            }
        }
        if (type == CompileResult.class) {
            CompileResult res;
            synchronized(this) {
                res = compilationResult;
            }
            if (res != null) {
                return type.cast(res);
            }
        }
        return ProcessingResult.super.getWrapped(type);
    }

    public synchronized AntlrGenerationResult generationResult() {
        return grammarGenerationResult;
    }

    public synchronized JavaFileManager.Location javaSourceOutputLocation() {
        return grammarGenerationResult.javaSourceOutputLocation();
    }

    public synchronized List<ParsedAntlrError> grammarGenerationErrors() {
        return grammarGenerationResult.errors();
    }

    public synchronized Grammar mainGrammar() {
        return grammarGenerationResult.mainGrammar();
    }

    public synchronized void refreshFileStatusForReuse() {
        if (grammarGenerationResult != null) {
            grammarGenerationResult = grammarGenerationResult.recycle();
        }
        if (compilationResult != null) {
            compilationResult = compilationResult.refresh();
        }
        touched.refresh();
    }

    @Override
    public UpToDateness currentStatus() {
        AntlrGenerationResult generate;
        CompileResult compile;
        Supplier<JFS> jfsSupplier;
        synchronized (this) {
            generate = this.grammarGenerationResult;
            compile = this.compilationResult;
            jfsSupplier = generate.jfsSupplier;
        }
        if (generate == null || compile == null || !generate.isUsable() || !compile.isUsable() || jfsSupplier == null) {
            return UpToDateness.UNKNOWN;
        }
        JFS jfs = jfsSupplier.get();
        if (!generate.areOutputFilesUpToDate(generate.grammarFile.path(), jfs)) {
            return UpToDateness.STALE;
        }
        if (!compile.areOutputFilesPresentIn(jfs) || !compile.areClassesUpToDateWithSources(jfs)) {
            return UpToDateness.STALE;
        }
        long compileStamp = compile.timestamp();
        long genStamp = generate.timestamp;
        if (genStamp > compileStamp) {
            return UpToDateness.STALE;
        }
        return UpToDateness.CURRENT;
    }

    @Override
    public synchronized boolean isUsable() {
        return thrown == null
                && grammarGenerationResult != null
                && compilationResult != null
                && grammarGenerationResult.isUsable()
                && compilationResult.isUsable();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('(');
        sb.append("thrown=").append(thrown)
                .append(" grammarGenerationResult=")
                .append(grammarGenerationResult)
                .append(" compilationResult=")
                .append(compilationResult);
        return sb.append(')').toString();
    }

    public synchronized JFS jfs() {
        return grammarGenerationResult.jfs();
    }

    public synchronized Optional<Grammar> findGrammar(String name) {
        return grammarGenerationResult.findGrammar(name);
    }

    public void rethrow() throws Throwable {
        Optional<Throwable> th = thrown();
        if (th.isPresent()) {
            throw th.get();
        }
    }

    @Override
    public Optional<Throwable> thrown() {
        if (this.thrown != null) {
            return Optional.of(this.thrown);
        }
        synchronized (this) {
            if (grammarGenerationResult != null && grammarGenerationResult.thrown().isPresent()) {
                return grammarGenerationResult.thrown();
            }
            if (compilationResult != null && compilationResult.thrown().isPresent()) {
                return compilationResult.thrown();
            }
        }
        return Optional.empty();
    }

    public synchronized boolean compileFailed() {
        return compilationResult == null ? false : compilationResult.compileFailed();
    }

    public synchronized List<Path> compiledSourceFiles() {
        return compilationResult == null ? Collections.emptyList() : compilationResult.sources();
    }

    public synchronized List<JavacDiagnostic> javacDiagnostics() {
        return compilationResult == null ? Collections.emptyList() : compilationResult.diagnostics();
    }
}

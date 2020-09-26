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

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.antlr.v4.tool.Grammar;
import org.nemesis.debug.api.Trackables;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.jfs.result.ProcessingResult;
import org.nemesis.jfs.result.UpToDateness;

/**
 *
 * @author Tim Boudreau
 */
public class GrammarRunResult<T> implements ProcessingResult, Supplier<T> {

    private WeakReference<T> result;
    private final Throwable thrown;
    private AntlrGenerationAndCompilationResult generationAndCompilationResult;
    private GrammarRunResult<T> lastGood;
    private final long timestamp = System.currentTimeMillis();
    private static final AtomicLong ids = new AtomicLong();
    public final long id = ids.getAndIncrement();

    // XXX tests are the only thing that still expect GrammarRunResult to have a
    // reference to T - get rid of that and get rid of the generic type, which
    // is a headache in other places

    GrammarRunResult(T result, Throwable thrown, AntlrGenerationAndCompilationResult buildResult, GrammarRunResult<T> lastGood) {
        this.result = result == null ? null : new WeakReference<>(result);
        this.thrown = thrown;
        this.generationAndCompilationResult = buildResult;
        // Only need a last-good result if we are not; otherwise we
        // will leak a chain of GrammarRunResults back to the dawn of time.
        this.lastGood = buildResult.isUsable() ? null : lastGood;
        if (lastGood != null) {
            lastGood.clearLastGood();
        }
        Trackables.track(GrammarRunResult.class, this);
    }

    @Override
    public <W> W getWrapped(Class<W> type) {
        if (AntlrGenerationAndCompilationResult.class == type) {
            if (generationAndCompilationResult != null) {
                return type.cast(generationAndCompilationResult);
            }
        }
        if (generationAndCompilationResult != null) {
            W res = generationAndCompilationResult.getWrapped(type);
            if (res != null) {
                return res;
            }
        }
        T res = get();
        if (res != null && type.isInstance(res)) {
            return type.cast(res);
        }
        if (GrammarRunResult.class == type && lastGood != null) {
            return type.cast(lastGood);
        }
        return ProcessingResult.super.getWrapped(type);
    }

    void clearLastGood() {
        lastGood = null;
        result = null;
        generationAndCompilationResult = null;
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
        StringBuilder sb = new StringBuilder("GRR(");
        sb.append(id).append(" usable=").append(isUsable())
                .append(", result=").append(result).append(" ");
        sb.append("buildResult.usable=").append(generationAndCompilationResult == null
                ? "no-result-present" : generationAndCompilationResult.isUsable());
        sb.append(" buildResult.grammarGenerationResult.usable=");
        sb.append(generationAndCompilationResult == null
                || generationAndCompilationResult.generationResult() == null
                ? "no-result"
                : generationAndCompilationResult.generationResult().isUsable());
        return sb.toString();
    }

    void disposeResult() {
        result = null;
    }

    public long timestamp() {
        return timestamp;
    }

    public JFS jfs() {
        return generationAndCompilationResult == null
                ? null : generationAndCompilationResult.jfs();
    }

    public Optional<Grammar> findGrammar(String name) {
        return generationAndCompilationResult == null ? Optional.empty()
                : generationAndCompilationResult.findGrammar(name);
    }

    public boolean compileFailed() {
        return generationAndCompilationResult == null ? true : generationAndCompilationResult.compileFailed();
    }

    public List<Path> sources() {
        return generationAndCompilationResult == null ? Collections.emptyList() : generationAndCompilationResult.compiledSourceFiles();
    }

    public List<JavacDiagnostic> diagnostics() {
        return generationAndCompilationResult == null ? Collections.emptyList() : generationAndCompilationResult.javacDiagnostics();
    }

    public GrammarRunResult<T> lastGood() {
        return lastGood;
    }

    public final AntlrGenerationAndCompilationResult genResult() {
        return generationAndCompilationResult;
    }

    @Override
    public boolean isUsable() {
        return generationAndCompilationResult != null && generationAndCompilationResult.isUsable() && thrown == null;
    }

    @Override
    public Optional<Throwable> thrown() {
        if (thrown != null) {
            return Optional.of(thrown);
        }
        return generationAndCompilationResult == null ? Optional.empty()
                : generationAndCompilationResult.thrown();
    }

    @Override
    public UpToDateness currentStatus() {
        return generationAndCompilationResult == null ? UpToDateness.UNKNOWN
                : generationAndCompilationResult.currentStatus();
    }

    @Override
    public T get() {
        WeakReference<T> ref = result;
        return ref == null ? null : ref.get();
    }
}

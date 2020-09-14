/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.execution.impl;

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Obj;
import com.mastfrog.function.throwing.io.IOPetaFunction;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.compilation.AntlrGenerationAndCompilationResult;
import org.nemesis.antlr.compilation.AntlrGeneratorAndCompiler;
import org.nemesis.antlr.compilation.AntlrRunBuilder;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.compilation.WithGrammarRunner;
import org.nemesis.antlr.live.execution.InvocationRunner;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class InvocationEnvironment<T, R> {

    private static final Logger LOG = Logger.getLogger(
            InvocationEnvironment.class.getName());
    private final InvocationRunnerLookupKey<T> key;
    private final InvocationRunner<T, R> runner;
    private final FileObject file;
    private final AtomicReference<EnvironmentState> state
            = new AtomicReference<>(new EnvironmentState());
    private Supplier<JFS> jfsSupplier;

    InvocationEnvironment(InvocationRunnerLookupKey<T> key, InvocationRunner<T, R> runner, FileObject file) {
        this.key = notNull("key", key);
        this.runner = notNull("runner", runner);
        this.file = notNull("file", file);
    }

    synchronized void maybeInitializeFrom(AntlrGenerationEvent initialState, FileObject file) {
        if (jfsSupplier == null && initialState.res != null && initialState.res.jfsSupplier != null) {

            this.jfsSupplier = initialState.res.jfsSupplier;
        }
    }

    GrammarRunResult<T> run(AntlrGenerationEvent event, FileObject grammarFile) throws IOException {
        assert grammarFile.equals(file) : "Passed event for wrong file: " + grammarFile + " expected " + file;
        AntlrGenerationResult genResult = event.res;
        if (genResult.isUsable()) {
            JFS jfs = jfsSupplier.get();
            return doBuild(event, grammarFile, genResult, jfs);
        }
        return null;
    }

    private GrammarRunResult<T> doBuild(AntlrGenerationEvent event, FileObject grammarFile,
            AntlrGenerationResult genResult, JFS jfs) throws IOException {
        EnvironmentState state = this.state.get();
        if (state.isUnchanged(event, jfs)) {
            return null;
        }
        // need some locking and an atomic to test if while we are in here, another
        // thread completes and updates the environment
        return performGenerationAndCompilation(state, event, grammarFile, genResult, jfs,
                (bldr, compileResult, csc, regenerated, arg) -> {
                    AntlrGeneratorAndCompiler compiler = AntlrGeneratorAndCompiler.fromResult(
                            genResult, bldr, compileResult);

                    AntlrGenerationAndCompilationResult agcr = null;
                    if (state.lastRunner != null) {
                        agcr = state.lastRunner.lastGenerationResult();
                    }
                    AntlrRunBuilder runBuilder = AntlrRunBuilder
                            .fromGenerationPhase(compiler)
                            .withLastGenerationResult(agcr)
                            .isolated();

                    if (csc.classloaderSupplier != null) {
                        runBuilder.withParentClassLoader(csc.classloaderSupplier);
                    } else {
                        runBuilder.withParentClassLoader(() -> {
                            try {
                                return jfs.getClassLoader(true, Thread.currentThread().getContextClassLoader(),
                                        StandardLocation.CLASS_OUTPUT, StandardLocation.CLASS_PATH);
                            } catch (IOException ex) {
                                throw new IllegalStateException(ex);
                            }
                        });
                    }

                    WithGrammarRunner wgr = runBuilder
                            .build(event.extraction.source().name());

//                            wgr.
                    return null;
                });
    }

    static class BuildAnalyzer extends CompilerRunnerAndAnalyzer<AntlrGenerationResult> {

        public BuildAnalyzer(Supplier<JFS> jfses, Path originalFilePath) {
            super(jfses, AntlrLoggers.STD_TASK_COMPILE_ANALYZER, originalFilePath);
        }

        @Override
        protected boolean onFailure(AntlrGenerationResult res, CompileResult compileResult, int attempt, boolean regenerated) {
            if (attempt > 1) {
                res.rebuild();
            }
            return true;
        }

        @Override
        protected void logInfo(AntlrGenerationResult res, Consumer<String> pw) {
            pw.accept("GEN PACKAGE: " + res.packageName);
            pw.accept("GRAMMAR SRC LOC: " + res.grammarSourceLocation);
            pw.accept("SOURCE OUT LOC: " + res.javaSourceOutputLocation);
            pw.accept("ORIG FILE: " + res.originalFilePath);
            pw.accept("FULL JFS LISTING:");
        }
    }

    private GrammarRunResult<T> performGenerationAndCompilation(EnvironmentState state, AntlrGenerationEvent event,
            FileObject grammarFile, AntlrGenerationResult genResult, JFS jfs,
            IOPetaFunction<JFSCompileBuilder, CompileResult, ClassloaderSupplierConsumer, Bool, R, GrammarRunResult<T>> c) throws IOException {
        BuildAnalyzer runAndAnalyze = new BuildAnalyzer(jfsSupplier, genResult.originalFilePath);
        Obj<CompileResult> compileResultHolder = Obj.create();
        Obj<R> arg = Obj.create();
        Bool regenerated = Bool.create();
        JFSCompileBuilder bldr = new JFSCompileBuilder(jfs);
        Obj<JFSCompileBuilder> compileBuilder = Obj.create();
        ClassloaderSupplierConsumer csc = new ClassloaderSupplierConsumer();
        try {
            return jfs.whileLockedWithWithLockDowngrade(() -> {
                compileResultHolder.set(runAndAnalyze.withCompilerOutputWriter(jfs, genResult, bldr, (Writer writer, JFSCompileBuilder b) -> {
                    Obj<UnixPath> singleSource = Obj.create();
                    arg.set(runner.configureCompilation(event.tree, genResult, event.extraction, jfs,
                            bldr, genResult.packageName(), csc, singleSource));
                    compileBuilder.set(bldr);
                    return runAndAnalyze.performCompilation(jfs, singleSource, b, writer, genResult, regenerated);
                }));
            }, () -> {
                if (compileResultHolder.isSet()) {
                    CompileResult cr = compileResultHolder.get();
                    return c.apply(compileBuilder.get(), compileResultHolder.get(), csc, regenerated, arg.get());
                }
                return null;
            });
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    class EnvironmentState {

        GrammarRunResult<T> lastResult;
        AntlrGenerationResult lastGenerationResult;
        CompileResult lastCompilationResult;
        R lastArg;
        WithGrammarRunner lastRunner;

        synchronized void update(AntlrGenerationEvent evt, CompileResult res, WithGrammarRunner runner, R arg, String grammarTokensHash) {
            AntlrGenerationAndCompilationResult agci = runner.lastGenerationResult();

        }

        /**
         * Do validity and up-to-date checks; if we return true, then there is
         * no need to perform compilation because we would generate class files
         * that are already present in the JFS and can just be reused.
         *
         * @param evt The generation event
         * @param jfs The JFS we would work against
         * @return true if nothing has changed (i.e. grammar sources may have
         * been regenerated, but they hash to the same bytes as when we last
         * compiled, and all compiler output is still present)
         */
        boolean isUnchanged(AntlrGenerationEvent evt, JFS jfs) {
            // last run was not successful
            if (lastResult == null || lastGenerationResult == null || lastArg == null || lastCompilationResult == null) {
                return false;
            }
            if (lastArg != null) {
                if (!runner.isStillValid(lastArg)) {
                    return false;
                }
            }
            // JFS was unused for a long time and was replaced - may not
            // contain anything at all yet
            if (!jfs.id().equals(lastGenerationResult.jfs.id())) {
                return false;
            }
            // Grammar files were really modified (uses JFSModifications which hashes the contents)
            if (!lastGenerationResult.isReusable()) {
                return false;
            }
            // If compiled files have been deleted, either CLASS_OUTPUT was cleaned, or
            // something like that
            if (!lastCompilationResult.areOutputFilesPresentIn(jfs)) {
                return false;
            }
            // The set of channel-0 tokens lexed from the grammar file by the
            // Antlr language grammar when parsing in the IDE has changed
            if (!lastGenerationResult.tokensHash.equals(evt.extraction.tokensHash())) {
                return false;
            }
            return true;
        }
    }

    static class ClassloaderSupplierConsumer implements Consumer<Supplier<ClassLoader>> {

        private Supplier<ClassLoader> classloaderSupplier;

        @Override
        public void accept(Supplier<ClassLoader> t) {
            this.classloaderSupplier = t;
        }

        Optional<ClassLoader> loader() {
            if (classloaderSupplier != null) {
                return Optional.ofNullable(classloaderSupplier.get());
            }
            return Optional.empty();
        }
    }

}

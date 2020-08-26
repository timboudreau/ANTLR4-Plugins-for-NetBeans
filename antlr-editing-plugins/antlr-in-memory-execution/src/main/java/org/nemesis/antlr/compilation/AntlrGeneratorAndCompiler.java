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

import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import static org.nemesis.antlr.memory.spi.AntlrLoggers.STD_TASK_GENERATE_ANTLR;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.jfs.javac.JavacOptions;

/**
 * Thing which takes an Antlr Generator and a JFSCompileBuilder, and can run
 * both of them to produce a result of both generation and compilation.
 *
 * @author Tim Boudreau
 */
public final class AntlrGeneratorAndCompiler {

    static final Logger LOG = Logger.getLogger(AntlrGeneratorAndCompiler.class.getName());

    private final Supplier<JFS> jfs;
    private final JFSCompileBuilder compileBuilder;
    private final AntlrGenerator generator;
    private AntlrGenerationResult lastGenerationResult;
    private CompileResult lastCompileResult;
    private static final boolean ANTLR_GENERATOR_VERBOSE_COMPILE = Boolean.getBoolean("antlr.gen.verbose");

    AntlrGeneratorAndCompiler(Supplier<JFS> jfs, JFSCompileBuilder compileBuilder, AntlrGenerator generator) {
        this.jfs = notNull("jfs", jfs);
        this.compileBuilder = notNull("compileBuilder", compileBuilder);
        this.generator = notNull("runner", generator);
        compileBuilder
                .addSourceLocation(generator.sourceLocation())
                .addSourceLocation(generator.outputLocation())
                .runAnnotationProcessors(false)
                .sourceAndTargetLevel(8);
        if (!ANTLR_GENERATOR_VERBOSE_COMPILE) {
            compileBuilder.withMaxErrors(1).setOptions(
                    new JavacOptions()
                            .withDebugInfo(JavacOptions.DebugInfo.NONE)
                            .onlyRebuildNewerSources()
            );
        } else {
            compileBuilder.setOptions(
                    new JavacOptions()
                            .withDebugInfo(JavacOptions.DebugInfo.LINES)
                            .onlyRebuildNewerSources(true)
                            .verbose()
                            .withMaxErrors(10)
                            .withMaxWarnings(10)
            );

        }
        LOG.log(Level.FINEST, "Create a {0} over jfs {1} for {2} with {3}", new Object[]{
            getClass().getSimpleName(), jfs,
            generator.originalFile() == null
            ? "no-file" // unit tests
            : generator.originalFile().getFileName(), compileBuilder});
    }

    public Path originalFile() {
        return generator.originalFile();
    }

    public String tokensHash() {
        // XXX is this preserving the tokens hash from first use,
        // or is it getting recreated?
        if (lastGenerationResult != null && lastGenerationResult.tokensHash != null) {
            return lastGenerationResult.tokensHash;
        }
        return generator.tokensHash();
    }

    public JFS jfs() {
        return jfs.get();
    }

    public AntlrGenerationAndCompilationResult compile(String grammarFileName, GrammarProcessingOptions... opts) {
        return compile(grammarFileName, GrammarProcessingOptions.setOf(opts));
    }

    public static AntlrGeneratorAndCompiler fromResult(AntlrGenerationResult lastResult, 
            JFSCompileBuilder compileBuilder, CompileResult lastCompileResult) {
        AntlrGeneratorAndCompiler result = new AntlrGeneratorAndCompiler(lastResult.jfsSupplier,
                compileBuilder, lastResult.toGenerator());
        result.lastGenerationResult = lastResult;
        result.lastCompileResult = lastCompileResult;
        return result;
    }

    // XXX sync with RebuildSubscriptions.TASK
    public AntlrGenerationAndCompilationResult compile(String grammarFileName,
            Set<GrammarProcessingOptions> opts) {
        JFS jfs = this.jfs.get();
        return Debug.runObject(this, "compile " + grammarFileName, () -> {
            return "Opts " + opts + "\nGenerator: " + generator + "\nJFS: " + jfs;
        }, () -> {
            AntlrGenerationResult run = null;
            try {
                if (opts.contains(GrammarProcessingOptions.REGENERATE_GRAMMAR_SOURCES)
                        || (lastGenerationResult == null || !lastGenerationResult.isUsable())) {
                    Debug.message("Rerun " + generator + " - regenerate passed or last unusable.", () -> {
                        return "Opts: " + opts + "\nLG: " + lastGenerationResult;
                    });
                    LOG.log(Level.FINER, "Grammar processing options contains regenerate for {0}: {1}", new Object[]{generator.originalFile().getFileName(), opts});
                    try (PrintStream logStream = AntlrLoggers.getDefault().printStream(generator.originalFile(), STD_TASK_GENERATE_ANTLR)) {
                        logStream.println("Regenerate Antlr Sources " + LocalDateTime.now() + " " + generator.originalFile());
                        run = jfs.whileWriteLocked(() -> (lastGenerationResult = generator.run(grammarFileName, logStream, true)));
                    }
                }
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Rebuilding " + originalFile().getFileName(), ex);
                Debug.thrown(ex);
                return new AntlrGenerationAndCompilationResult(run,
                        CompileResult.precompiled(false, UnixPath.empty()), ex,
                        JFSFileModifications.empty());
            }
            if (run == null) {
                run = generator.createFailedResult(null);
            }
            if (!run.isUsable()) {
                LOG.log(Level.FINE, "Run result not usable for {0}: {1}",
                        new Object[] { generator.originalFile().getFileName(), run});
                return new AntlrGenerationAndCompilationResult(run,
                        CompileResult.precompiled(true, UnixPath.empty()), null,
                        JFSFileModifications.empty());
            }
            boolean shouldBuild = false;
            if (opts.contains(GrammarProcessingOptions.REBUILD_JAVA_SOURCES)) {
                LOG.log(Level.FINER, "Rebuild {0} due to GrammarProcessingOptions {1} in {2}",
                        new Object[]{originalFile().getFileName(), opts, this});
                shouldBuild = true;
            } else if (lastCompileResult == null || !lastCompileResult.isUsable()) {
                LOG.log(Level.FINER, "Rebuild {0} due to last result null or unusable: {1} in {2}",
                        new Object[]{originalFile().getFileName(), lastCompileResult, this});
                shouldBuild = true;
            } else if (lastCompileResult != null && !lastCompileResult.filesState()
                    .changes()
                    .filter(AntlrGeneratorAndCompiler::isJavaSource).isUpToDate()) {
                LOG.log(Level.FINER, "Rebuild {0} due to last result out of date: {1} in {2}",
                        new Object[]{originalFile().getFileName(), lastCompileResult.filesState().changes(), this});
                shouldBuild = true;
            } else {
                LOG.log(Level.FINER, "Reusing output of previous compile");
            }
            CompileResult res;
            if (shouldBuild) {
                try {
                    synchronized (this) {
                        res = lastCompileResult = Debug.runObjectThrowing(this, "javac-compile " + grammarFileName, () -> {
                            return generator.sourcePath().toString();
                        }, () -> {
                            try (Writer writer = AntlrLoggers.getDefault()
                                    .writer(generator.originalFile(), AntlrLoggers.STD_TASK_COMPILE_GRAMMAR)) {

                                writer.append("Compile Antlr " + LocalDateTime.now() + " " + generator.originalFile() + "\n");

                                LOG.log(Level.FINE, "Rebuild {0}", originalFile().getFileName());
                                // XXX if we get cannot find symbol, we should wipe the Java sources,
                                // regenerate and try again
//                                compileBuilder.verbose().nonIdeMode().withMaxErrors(5).withMaxWarnings(10);
                                return compileBuilder.compilerOutput(writer).compile();
                            }
                        });
                        if (res.isUsable()) {
                            Debug.success("good compile", res::toString);
                        } else {
                            Debug.failure("bad compile", res::toString);
                        }
                        if (res.thrown().isPresent()) {
                            Debug.thrown(res.thrown().get());
                        }
                    }
                } catch (Exception ex) {
                    LOG.log(Level.FINE, generator.originalFile().getFileName().toString(), ex);
                    return new AntlrGenerationAndCompilationResult(run, CompileResult.precompiled(false, UnixPath.empty()),
                            ex, JFSFileModifications.empty());
                }
            } else {
                LOG.log(Level.FINEST, "Using last compile result");
                res = lastCompileResult;
            }
            return new AntlrGenerationAndCompilationResult(run, res, null,
                    jfs.status(AntlrGeneratorAndCompiler::isJavaSource,
                            StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT));
        });
    }

    static Supplier<String> info(CompileResult cr) {
        Supplier<String> info = () -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Elapsed ms: ").append(cr.elapsedMillis()).append("\n\n");
            sb.append("Compile failed: ").append(cr.compileFailed()).append('\n');
            for (JavacDiagnostic diag : cr.diagnostics()) {
                sb.append(diag).append('\n');
            }
            sb.append("\nSources:");
            for (Path p : cr.sources()) {
                sb.append(p).append('\n');
            }
            return sb.toString();
        };
        return info;
    }

    static boolean isJavaSource(Path path) {
        return path.getFileName().toString().endsWith(".java");
    }

    static boolean isClassFile(Path path) {
        return path.getFileName().toString().endsWith(".class");
    }
}

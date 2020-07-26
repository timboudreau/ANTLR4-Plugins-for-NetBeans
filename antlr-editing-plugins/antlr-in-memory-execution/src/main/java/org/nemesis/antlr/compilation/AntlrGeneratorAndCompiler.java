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
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;
import javax.tools.StandardLocation;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacDiagnostic;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrGeneratorAndCompiler {

    private final JFS jfs;
    private final JFSCompileBuilder compileBuilder;
    private final AntlrGenerator generator;

    AntlrGeneratorAndCompiler(JFS jfs, JFSCompileBuilder compileBuilder, AntlrGenerator runner) {
        this.jfs = jfs;
        this.compileBuilder = compileBuilder;
        compileBuilder.addSourceLocation(runner.sourceLocation()).addSourceLocation(runner.outputLocation());
        compileBuilder.runAnnotationProcessors(false);
        compileBuilder.sourceAndTargetLevel(8).withMaxErrors(1);
        this.generator = runner;
    }

    public JFS jfs() {
        return jfs;
    }

    public AntlrGenerationAndCompilationResult compile(String grammarFileName, PrintStream logStream, GrammarProcessingOptions... opts) {
        return compile(grammarFileName, logStream, GrammarProcessingOptions.setOf(opts));
    }

    public static AntlrGeneratorAndCompiler fromResult(AntlrGenerationResult lastResult) {
        return fromResult(lastResult, new JFSCompileBuilder(lastResult.jfs()));
    }

    public static AntlrGeneratorAndCompiler fromResult(AntlrGenerationResult lastResult, JFSCompileBuilder compileBuilder) {
        AntlrGeneratorAndCompiler result = new AntlrGeneratorAndCompiler(lastResult.jfs(),
                compileBuilder, lastResult.toGenerator());
        return result;
    }

    private AntlrGenerationResult lastGenerationResult;
    private CompileResult lastCompileResult;

    public AntlrGenerationAndCompilationResult compile(String grammarFileName, PrintStream logStream,
            Set<GrammarProcessingOptions> opts) {
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
                    run = jfs.whileWriteLocked(() -> (lastGenerationResult = generator.run(grammarFileName, logStream, true)));
                }
            } catch (Exception ex) {
                Debug.thrown(ex);
                return new AntlrGenerationAndCompilationResult(run, 
                        CompileResult.precompiled(false, UnixPath.empty()), ex,
                        JFSFileModifications.empty());
            }
            if (run == null || !run.isUsable()) {
                AntlrGenerationResult cr = run;
                Debug.failure("failed or unusable", () -> {
                    return cr == null ? "null result" : cr.toString();
                });
                if (cr.thrown().isPresent()) {
                    Debug.thrown(cr.thrown().get());
                }
                return new AntlrGenerationAndCompilationResult(run, 
                        CompileResult.precompiled(true, UnixPath.empty()), null,
                        JFSFileModifications.empty());
            }
            boolean shouldBuild = false;
            if (opts.contains(GrammarProcessingOptions.REBUILD_JAVA_SOURCES)) {
                shouldBuild = true;
            } else if (lastCompileResult == null || !lastCompileResult.isUsable()) {
                shouldBuild = true;
            } else if (lastCompileResult != null && !lastCompileResult.filesState()
                    .changes()
                    .filter(AntlrGeneratorAndCompiler::isJavaSource).isUpToDate()) {
                shouldBuild = true;
            }
            CompileResult res;
            if (shouldBuild) {
                try {
                    synchronized (this) {
                        res = lastCompileResult = Debug.runObjectThrowing(this, "javac-compile " + grammarFileName, () -> {
                            return generator.sourcePath().toString();
                        }, compileBuilder::compile);
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
                    return new AntlrGenerationAndCompilationResult(run, CompileResult.precompiled(false, UnixPath.empty()),
                            ex, JFSFileModifications.empty());
                }
            } else {
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

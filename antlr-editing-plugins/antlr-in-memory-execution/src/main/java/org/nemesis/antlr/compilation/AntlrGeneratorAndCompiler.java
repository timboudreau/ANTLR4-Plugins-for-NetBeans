/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.compilation;

import java.io.PrintStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static javax.tools.JavaFileObject.Kind.CLASS;
import javax.tools.StandardLocation;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;

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
        AntlrGeneratorAndCompiler result = new AntlrGeneratorAndCompiler(lastResult.jfs(), compileBuilder, lastResult.toGenerator());
        return result;
    }

    private AntlrGenerationResult lastGenerationResult;
    private CompileResult lastCompileResult;

    public AntlrGenerationAndCompilationResult compile(String grammarFileName, PrintStream logStream, Set<GrammarProcessingOptions> opts) {
        AntlrGenerationResult run = null;
        try {
            if (opts.contains(GrammarProcessingOptions.REGENERATE_GRAMMAR_SOURCES) || (lastGenerationResult == null || !lastGenerationResult.isUsable())) {
                run = (lastGenerationResult = generator.run(grammarFileName, logStream, true));
            }
        } catch (Exception ex) {
            return new AntlrGenerationAndCompilationResult(run, null, ex, Collections.emptyMap());
        }
        if (run == null || !run.isUsable()) {
            return new AntlrGenerationAndCompilationResult(run, null, null, Collections.emptyMap());
        }
        Map<JFSFileObject, Long> classFiles = new HashMap<>();
        Map<JFSFileObject, Long> touched = new HashMap<>();
        CompileResult res = null;
        if (opts.contains(GrammarProcessingOptions.REBUILD_JAVA_SOURCES) || (lastCompileResult == null || !lastCompileResult.isUsable())) {
            try {
                jfs.list(StandardLocation.CLASS_OUTPUT, "", EnumSet.of(CLASS), true)
                        .forEach(fo -> {
                            classFiles.put((JFSFileObject) fo, fo.getLastModified());
                        });
                res = lastCompileResult = compileBuilder.compile();
                jfs.list(StandardLocation.CLASS_OUTPUT, "", EnumSet.of(CLASS), true)
                        .forEach(fo -> {
                            @SuppressWarnings("element-type-mismatch")
                            Long oldLastModified = classFiles.get(fo);
                            long currLastModified = fo.getLastModified();
                            if (oldLastModified == null || oldLastModified < currLastModified) {
                                touched.put((JFSFileObject) fo, currLastModified);
                            }
                        });
            } catch (Exception ex) {
                return new AntlrGenerationAndCompilationResult(run, res, ex, touched);
            }
        } else {
            res = lastCompileResult;
        }
        return new AntlrGenerationAndCompilationResult(run, res, null, touched);
    }

}

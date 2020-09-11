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
import com.mastfrog.function.throwing.io.IOBiFunction;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacDiagnostic;
import org.nemesis.jfs.javac.JavacOptions;
import org.openide.util.Exceptions;

/**
 * This class just exists to isolate a bunch of spaghetti-like diagnostics
 * logging.
 *
 * @author Tim Boudreau
 */
final class CompilerRunnerAndAnalyzer {

    private static final String JAVAC_ERROR_SOURCE_ABSENT = "compiler.err.cant.resolve.location";
    private static final Logger LOG = Logger.getLogger(
            InvocationEnvironment.class.getName());

    private final Supplier<JFS> jfsSupplier;

    public CompilerRunnerAndAnalyzer(Supplier<JFS> jfses) {
        this.jfsSupplier = jfses;
    }

    CompileResult performCompilation(Obj<UnixPath> singleSource, JFSCompileBuilder bldr, Writer writer, AntlrGenerationResult genResult, Bool regenerated) throws IOException {
        CompileResult cr;
        if (!singleSource.isSet()) {
            cr = bldr.compile();
        } else {
            bldr.addSourceLocation(StandardLocation.CLASS_OUTPUT);
            cr = bldr.compile();
        }
        writer.write("Compile took " + cr.elapsedMillis() + "ms");

        if (!cr.isUsable()) {
            writer.write("Compile result not usable. If the error "
                    + "looks like it could be stale sources, will retry.\n");
            // Detect if we had a cannot find symbol or
            // cannot find location, and if present, wipe and
            // rebuild again
            cr = analyzeAndLogCompileFailureAndMaybeRetry(writer, cr, genResult, bldr, true, regenerated);

            if (!cr.isUsable()) {

                writer.write("Compile failed on clean retry.\n");
                analyzeAndLogCompileFailureAndMaybeRetry(writer, cr, genResult, bldr, false, regenerated);
                onCompileFailure(cr, genResult);
                LOG.log(Level.FINE, "Unusable second compile result {0}", cr);
            }
        }
        return cr;
    }

    CompileResult withCompilerOutputWriter(JFS jfs, AntlrGenerationResult res, IOBiFunction<Writer, JFSCompileBuilder, CompileResult> compilerRunner) throws IOException {
        JFSCompileBuilder bldr = configureCompileBuilder(jfs);
        try (Writer writer = AntlrLoggers.getDefault().writer(res.originalFilePath, AntlrLoggers.STD_TASK_COMPILE_GRAMMAR)) {
            bldr.compilerOutput(writer);
            return compilerRunner.apply(writer, bldr);
        }
    }

    private CompileResult analyzeAndLogCompileFailureAndMaybeRetry(final Writer writer, CompileResult cr, AntlrGenerationResult res, JFSCompileBuilder bldr, boolean rebuild, Bool regenerated) throws IOException {
        Consumer<String> pw = new PrintWriter(writer)::println;
        if (AntlrLoggers.isActive(writer)) {
            pw.accept("COMPILE RESULT: " + cr);
            pw.accept("GEN PACKAGE: " + res.packageName);
            pw.accept("GRAMMAR SRC LOC: " + res.grammarSourceLocation);
            pw.accept("SOURCE OUT LOC: " + res.javaSourceOutputLocation);
            pw.accept("ORIG FILE: " + res.originalFilePath);
            pw.accept("FULL JFS LISTING:");
            res.jfs.listAll((loc, fo) -> {
                pw.accept(" * " + loc + "\t" + fo);
            });
        }
        boolean foundCantResolveLocation = cr.hasErrors(JAVAC_ERROR_SOURCE_ABSENT);
        for (JavacDiagnostic diag : cr.diagnostics()) {
            printOneDiagnostic(pw, diag, res);
        }
        if (rebuild && foundCantResolveLocation) {
            if (AntlrLoggers.isActive(writer)) {
                pw.accept("");
                pw.accept("Error may be due to old source files obsoleted by grammar changes.  Deleting all generated files and retrying.");
                LOG.log(Level.FINE, "Compiler could not resolve files.  Deleting "
                        + "generated code, regenerating and recompiling: {0}", cr.diagnostics());
            }
            JFS jfs = res.jfsSupplier.get();
            if (!jfs.id().equals(res.jfs.id())) {
                LOG.log(Level.FINE, "JFS {0} has been replaced with new JFS {1}", new Object[]{res.jfs.id(), jfs.id()});
            }
            bldr.setOptions(bldr.options().rebuildAllSources());

            cr = jfs.whileWriteLocked(() -> {
                res.rebuild();
                regenerated.set(true);
                return bldr.compile();
            });
            writer.write("Compile took " + cr.elapsedMillis() + "ms");
        }
        return cr;
    }

    private void printOneDiagnostic(Consumer<String> pw, JavacDiagnostic diag, AntlrGenerationResult res) {
        pw.accept(diag.message() + " at " + diag.lineNumber() + ":" + diag.columnNumber() + " in " + diag.fileName());
        JFSFileObject fo = res.jfs().get(StandardLocation.SOURCE_OUTPUT, UnixPath.get(diag.sourceRootRelativePath()));
        if (fo == null) {
            fo = res.jfs().get(StandardLocation.SOURCE_PATH, UnixPath.get(diag.sourceRootRelativePath()));
        }
        if (fo != null) {
            pw.accept(diag.context(fo));
        }
    }

    private void onCompileFailure(CompileResult crFinal, AntlrGenerationResult res) {
        Debug.failure("Unusable compilation result", () -> {
            StringBuilder sb = new StringBuilder();
            sb.append(crFinal).append('\n');
            sb.append("failed ").append(crFinal.compileFailed());
            sb.append("diags ").append(crFinal.diagnostics());
            boolean hasFatal = false;
            if (crFinal.diagnostics() != null && !crFinal.diagnostics().isEmpty()) {
                for (JavacDiagnostic diag : crFinal.diagnostics()) {
                    sb.append("\n").append(diag.kind()).append(' ')
                            .append(diag.message()).append("\n  at")
                            .append(diag.lineNumber()).append(':')
                            .append(diag.columnNumber()).append("\n")
                            .append(diag.sourceCode()).append('\n');
                    hasFatal |= diag.isError();
                }
            }
            Optional<Throwable> th = crFinal.thrown();
            if (th != null && th.isPresent()) {
                sb.append(Strings.toString(th.get()));
            }
            if (hasFatal) {
                for (Path pth : crFinal.sources()) {
                    JFSFileObject jfo = res.jfs.get(StandardLocation.SOURCE_PATH, UnixPath.get(pth));
                    if (jfo == null) {
                        jfo = res.jfs.get(StandardLocation.SOURCE_OUTPUT, UnixPath.get(pth));
                    }
                    if (jfo == null) {
                        jfo = res.jfs.get(StandardLocation.CLASS_OUTPUT, UnixPath.get(pth));
                    }
                    if (jfo != null && jfo.getName().endsWith("Extractor.java")) {
                        try {
                            sb.append(jfo.getCharContent(true));
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            }
            return sb.toString();
        });
    }

    private JFSCompileBuilder configureCompileBuilder(JFS jfs) {
        return new JFSCompileBuilder(jfsSupplier)
                .withMaxErrors(1).setOptions(
                JavacOptions.fastDefaults()
                        .withDebugInfo(JavacOptions.DebugInfo.LINES)
                        .runAnnotationProcessors(false)
                        .withCharset(jfs.encoding())
        ).runAnnotationProcessors(false)
                .addSourceLocation(StandardLocation.SOURCE_PATH)
                .addSourceLocation(StandardLocation.SOURCE_OUTPUT);
    }

}

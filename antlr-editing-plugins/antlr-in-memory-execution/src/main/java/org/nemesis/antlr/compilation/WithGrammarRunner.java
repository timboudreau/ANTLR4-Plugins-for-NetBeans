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

import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.util.strings.Strings;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.javac.CompileResult;

/**
 *
 * @author Tim Boudreau
 */
public final class WithGrammarRunner {

    private static final Logger LOG = Logger.getLogger(
            WithGrammarRunner.class.getName());
    private final String grammarFileName;
    private final AntlrGeneratorAndCompiler compiler;
    private static final Map<Object, GrammarRunResult<?>> lastGoodResults
            = new WeakHashMap<>();
    private final Supplier<ClassLoader> classLoaderSupplier;

    WithGrammarRunner(String grammarFileName, AntlrGeneratorAndCompiler compiler, Supplier<ClassLoader> classLoaderSupplier) {
        this.grammarFileName = grammarFileName;
        this.compiler = compiler;
        this.classLoaderSupplier = classLoaderSupplier;
    }

    public AntlrGeneratorAndCompiler generatorCompiler() {
        return compiler;
    }

    public String grammarTokensHash() {
        return compiler == null ? "-no-compiler-" : compiler.tokensHash();
    }

    private AntlrGenerationAndCompilationResult generateAndCompile(String sourceFileName, Set<GrammarProcessingOptions> options) {
        return compiler.compile(sourceFileName, options);
    }

    public <T> RunResults<T> run(ThrowingSupplier<T> reflectiveRunner, GrammarProcessingOptions... options) {
        return run(reflectiveRunner, reflectiveRunner, options);
    }

    public <T> RunResults<T> run(Object key, ThrowingSupplier<T> reflectiveRunner, GrammarProcessingOptions... options) {
        return run(reflectiveRunner, GrammarProcessingOptions.setOf(options));
    }

    public <T> RunResults<T> run(ThrowingSupplier<T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        return run(reflectiveRunner, reflectiveRunner, options);
    }

    public <T, A> RunResults<T> run(A arg, ThrowingFunction<A, T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        return runWithArg(arg, reflectiveRunner, arg, options);
    }

    public <T, A> RunResults<T> run(Object key, A arg, ThrowingFunction<A, T> reflectiveRunner, GrammarProcessingOptions... options) {
        return run(key, arg, reflectiveRunner, GrammarProcessingOptions.setOf(options));
    }

    public <T, A> RunResults<T> run(Object key, A arg, ThrowingFunction<A, T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        return runWithArg(key, reflectiveRunner, arg, options);
    }

    private <T> GrammarRunResult<T> lastGood(Object key) {
        GrammarRunResult<?> res = lastGoodResults.get(key);
        return (GrammarRunResult<T>) res;
    }

    AntlrGenerationAndCompilationResult lastGenerationResult;

    public AntlrGenerationAndCompilationResult lastGenerationResult() {
        return lastGenerationResult;
    }

    public <A, T> RunResults<T> runWithArg(Object key, ThrowingFunction<A, T> reflectiveRunner,
            A arg, Set<GrammarProcessingOptions> options) {
        ThrowingSupplier<T> supp = () -> {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            ClassLoader ldr = classLoaderSupplier.get();
            if (ldr != null) {
                Thread.currentThread().setContextClassLoader(ldr);
            }
            try {
                return reflectiveRunner.apply(arg);
            } catch (LinkageError err) {
                LOG.log(Level.WARNING, "Likely to double-load some class in " + ldr, err);
                throw err;
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        };
        return run(key, supp, options);
    }

    public void resetFileModificationStatusForReuse() {
        AntlrGenerationAndCompilationResult r = lastGenerationResult;
        if (r != null) {
            r.refreshFileStatusForReuse();
        }
    }

    @SuppressWarnings("FinallyDiscardsException")
    public <T> RunResults<T> run(Object key, ThrowingSupplier<T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        // - results checks if stale or not
        // - force re-run or not
        // - optionally return the last good result if there is one
        //    - Maybe a closure that takes both?
        // - clean method on results
//            GenerationAndCompilationResult res;
        AntlrGenerationAndCompilationResult res;

        if (options.contains(GrammarProcessingOptions.REBUILD_JAVA_SOURCES) || (lastGenerationResult == null || (lastGenerationResult != null && !lastGenerationResult.isUsable())
                && !lastGenerationResult.currentStatus().mayRequireRebuild())) {
            Debug.message("regenerate-and-compile");
            res = generateAndCompile(grammarFileName, options);
        } else {
            Debug.message("use-last-generation-result");
            res = lastGenerationResult;
            if (res != null && res.generationResult() != null) {
                AntlrGenerationResult gr = res.generationResult();
                if (!gr.isUpToDate()) {
                    Set<GrammarProcessingOptions> opts = EnumSet.copyOf(options);
                    CompileResult cr = res.compilationResult();
                    if (cr != null && (!cr.isUsable() || !cr.areClassesUpToDateWithSources(res.jfs()))
                            || cr.timestamp() < gr.timestamp) {
                        opts.add(GrammarProcessingOptions.REBUILD_JAVA_SOURCES);
                    }
                    res = generateAndCompile(grammarFileName, opts);
                }
            }
        }
        if (!res.isUsable()) {
            if (lastGenerationResult != null && !lastGenerationResult.isUsable()) {
                lastGenerationResult = null;
            }
            Debug.failure("unusable-result", res::toString);
            if (options.contains(GrammarProcessingOptions.RETURN_LAST_GOOD_RESULT_ON_FAILURE)) {
                GrammarRunResult<T> lg = (GrammarRunResult<T> ) lastGood(key);
                if (lg != null && lg.lastGood() != null) {
                    lg = lg.lastGood();
                }
                return new RunResults<>(new GrammarRunResult<>(lg == null ? null : lg.get(), null, res, lg), lg == null ? null : lg.get());
            }
            return new RunResults<>(new GrammarRunResult<>(null, null, res, (GrammarRunResult<T>) lastGood(key)), null);
        }
        lastGenerationResult = res;
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        GrammarRunResult<T> grr = null;
        try {
            ClassLoader ldr = classLoaderSupplier.get();
            if (ldr != null) {
                Thread.currentThread().setContextClassLoader(ldr);
            }
            LOG.log(Level.FINEST, "Run {0} in {1}", new Object[]{grammarFileName, ldr});
            T result = reflectiveRunner.get();
            grr = new GrammarRunResult<>(result, null, res, (GrammarRunResult<T>) lastGoodResults.get(key));
            if (grr.isUsable()) {
                lastGoodResults.put(key, grr);
            }
            return new RunResults<>(grr, result);
        } catch (ClassNotFoundException ex) {
            StringBuilder sb = new StringBuilder();
            compiler.jfs().list(StandardLocation.SOURCE_OUTPUT, (loc, fo) -> {
                sb.append('\n').append(loc).append('\t').append(fo.getName());
            });
            compiler.jfs().list(StandardLocation.CLASS_OUTPUT, (loc, fo) -> {
                sb.append('\n').append(loc).append('\t').append(fo.getName());
            });
            ClassNotFoundException ex2 = new ClassNotFoundException("Failed in " + reflectiveRunner
                    + " JFS listing: " + sb, ex);
            LOG.log(Level.INFO, "Failed in " + reflectiveRunner, ex2);
            return new RunResults<>(new GrammarRunResult<T>(null, ex, res, lastGood(key)), null);
        } catch (Exception | Error ex) {
            Thread.currentThread().setContextClassLoader(oldLoader);
            Debug.failure(ex.toString(), () -> {
                return Strings.toString(ex);
            });
            if (ex instanceof Error) {
                LOG.log(Level.SEVERE, "Failed in " + reflectiveRunner, ex);
                throw (Error) ex;
            } else {
                LOG.log(Level.INFO, "Failed in " + reflectiveRunner, ex);
            }
            return new RunResults<T>(new GrammarRunResult<>(null, ex, res, (GrammarRunResult<T>) lastGood(key)), null);
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
//                Object o = grr;
//                Debug.success("new-grammar-run-result", () -> {
//                    return o == null ? "null" : o.toString();
//                });
        }
    }
}

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
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.debug.api.Debug;
import org.nemesis.debug.api.Trackables;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSClassLoader;

/**
 *
 * @author Tim Boudreau
 */
public final class WithGrammarRunner {

    private final String grammarFileName;
    private final AntlrGeneratorAndCompiler compiler;
    private final Map<Object, GrammarRunResult<?>> lastGoodResults
            = new WeakHashMap<>();
    private final Supplier<ClassLoader> classLoaderSupplier;

    WithGrammarRunner(String grammarFileName, AntlrGeneratorAndCompiler compiler, Supplier<ClassLoader> classLoaderSupplier) {
        this.grammarFileName = grammarFileName;
        this.compiler = compiler;
        this.classLoaderSupplier = classLoaderSupplier;
    }

    private AntlrGenerationAndCompilationResult generateAndCompile(String sourceFileName, Set<GrammarProcessingOptions> options) {
        return compiler.compile(sourceFileName, options);
    }

    public <T> GrammarRunResult<T> run(ThrowingSupplier<T> reflectiveRunner, GrammarProcessingOptions... options) {
        return run(reflectiveRunner, reflectiveRunner, options);
    }

    public <T> GrammarRunResult<T> run(Object key, ThrowingSupplier<T> reflectiveRunner, GrammarProcessingOptions... options) {
        return run(reflectiveRunner, GrammarProcessingOptions.setOf(options));
    }

    public <T> GrammarRunResult<T> run(ThrowingSupplier<T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        return run(reflectiveRunner, reflectiveRunner, options);
    }

    public <T, A> GrammarRunResult<T> run(A arg, ThrowingFunction<A, T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        return runWithArg(arg, reflectiveRunner, arg, options);
    }

    public <T, A> GrammarRunResult<T> run(Object key, A arg, ThrowingFunction<A, T> reflectiveRunner, GrammarProcessingOptions... options) {
        return run(key, arg, reflectiveRunner, GrammarProcessingOptions.setOf(options));
    }

    public <T, A> GrammarRunResult<T> run(Object key, A arg, ThrowingFunction<A, T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        return runWithArg(key, reflectiveRunner, arg, options);
    }

    private <T> GrammarRunResult<T> lastGood(Object key) {
        GrammarRunResult<?> res = lastGoodResults.get(key);
        return (GrammarRunResult<T>) res;
    }

    private AntlrGenerationAndCompilationResult lastGenerationResult;

    private JFSClassLoader createClassLoader() throws IOException {
        JFS jfs = compiler.jfs();
        Debug.message("create-classloader", jfs::currentClasspath);
        JFSClassLoader result = jfs.getClassLoader(true, classLoaderSupplier.get(), StandardLocation.CLASS_OUTPUT, StandardLocation.CLASS_PATH);
        String nm = jfs.id() + "-" + grammarFileName;
        Trackables.track(JFSClassLoader.class, result, () -> {
            return "JFSClassLoader-" + nm;
        });
        return result;
    }

    public <A, T> GrammarRunResult<T> runWithArg(Object key, ThrowingFunction<A, T> reflectiveRunner, A arg, Set<GrammarProcessingOptions> options) {
        ThrowingSupplier<T> supp = () -> {
            return reflectiveRunner.apply(arg);
        };
        return run(key, supp, options);
    }

    public void resetFileModificationStatusForReuse() {
        AntlrGenerationAndCompilationResult r = lastGenerationResult;
        if (r != null) {
            r.refreshFileStatusForReuse();
        }
    }

    public <T> GrammarRunResult<T> run(Object key, ThrowingSupplier<T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        // XXX
        // - results checks if stale or not
        // - force re-run or not
        // - optionally return the last good result if there is one
        //    - Maybe a closure that takes both?
        // - clean method on results
        return Debug.runObject(this, "with-grammar-runner-run " + grammarFileName, () -> {
//            GenerationAndCompilationResult res;
            AntlrGenerationAndCompilationResult res;
            if (options.contains(GrammarProcessingOptions.REBUILD_JAVA_SOURCES) || (lastGenerationResult == null || (lastGenerationResult != null && !lastGenerationResult.isUsable())
                    && !lastGenerationResult.currentStatus().mayRequireRebuild())) {
                Debug.message("regenerate-and-compile");
                res = generateAndCompile(grammarFileName, options);
            } else {
                Debug.message("use-last-generation-result");
                res = lastGenerationResult;
            }
            if (!res.isUsable()) {
                if (lastGenerationResult != null && !lastGenerationResult.isUsable()) {
                    lastGenerationResult = null;
                }
                Debug.failure("unusable-result", res::toString);
                if (options.contains(GrammarProcessingOptions.RETURN_LAST_GOOD_RESULT_ON_FAILURE)) {
                    GrammarRunResult<Object> lg = lastGood(key);
                    return new GrammarRunResult<>(lg == null ? null : lg.get(), null, res, lg);
                }
                return new GrammarRunResult<>(null, null, res, lastGood(key));
            }
            lastGenerationResult = res;
            ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
            GrammarRunResult<T> grr = null;
            try {
                try (JFSClassLoader nue = createClassLoader()) {
                    Thread.currentThread().setContextClassLoader(nue);
                    T result = reflectiveRunner.get();
                    grr = new GrammarRunResult(result, null, res, null);
                    if (grr.isUsable()) {
                        lastGoodResults.put(key, grr);
                    }
                    return grr;
                }
            } catch (Exception | Error ex) {
                Thread.currentThread().setContextClassLoader(oldLoader);
                Debug.failure(ex.toString(), () -> {
                    return Strings.toString(ex);
                });
                if (ex instanceof Error) {
                    Logger.getLogger(WithGrammarRunner.class.getName())
                            .log(Level.SEVERE, "Failed in " + reflectiveRunner, ex);
                    throw (Error) ex;
                } else {
                    Logger.getLogger(WithGrammarRunner.class.getName())
                            .log(Level.INFO, "Failed in " + reflectiveRunner, ex);
                }
                return new GrammarRunResult(null, ex, res, lastGood(key));
            } finally {
                Thread.currentThread().setContextClassLoader(oldLoader);
                Object o = grr;
                Debug.success("new-grammar-run-result", () -> {
                    return o == null ? "null" : o.toString();
                });
            }
        });
    }
}

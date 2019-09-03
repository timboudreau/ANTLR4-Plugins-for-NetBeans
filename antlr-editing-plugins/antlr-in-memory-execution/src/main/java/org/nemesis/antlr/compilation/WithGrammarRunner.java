package org.nemesis.antlr.compilation;

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.tools.StandardLocation;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSClassLoader;

/**
 *
 * @author Tim Boudreau
 */
public final class WithGrammarRunner {

    private final String grammarFileName;
    private final AntlrGeneratorAndCompiler compiler;
    private final boolean isolated;
    private final Map<Object, GrammarRunResult<?>> lastGoodResults
            = new WeakHashMap<>();

    WithGrammarRunner(String grammarFileName, AntlrGeneratorAndCompiler compiler, boolean isolated) {
        this.grammarFileName = grammarFileName;
        this.compiler = compiler;
        this.isolated = isolated;
    }

    private GenerationAndCompilationResult generateAndCompile(String sourceFileName, Set<GrammarProcessingOptions> options) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream outputTo = new PrintStream(output);
        return new GenerationAndCompilationResult(output, compiler.compile(sourceFileName, outputTo, options));
    }

    public <T> GrammarRunResult<T> run(ThrowingSupplier<T> reflectiveRunner, GrammarProcessingOptions... options) {
        return run(reflectiveRunner, reflectiveRunner, options);
    }

    @SuppressWarnings("ManualArrayToCollectionCopy")
    public <T> GrammarRunResult<T> run(Object key, ThrowingSupplier<T> reflectiveRunner, GrammarProcessingOptions... options) {
        return run(reflectiveRunner, GrammarProcessingOptions.setOf(options));
    }

    public <T> GrammarRunResult<T> run(ThrowingSupplier<T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        return run(reflectiveRunner, reflectiveRunner, options);
    }

    private <T> GrammarRunResult<T> lastGood(Object key) {
        GrammarRunResult<?> res = lastGoodResults.get(key);
        return (GrammarRunResult<T>) res;
    }

    private GenerationAndCompilationResult lastGenerationResult;

    private JFSClassLoader createClassLoader() throws IOException {
        JFS jfs = compiler.jfs();
        ClassLoader parent;
        if (isolated) {
            parent = ClassLoader.getSystemClassLoader();
        } else {
            parent = Thread.currentThread().getContextClassLoader();
        }
        return jfs.getClassLoader(true, parent, StandardLocation.CLASS_OUTPUT, StandardLocation.CLASS_PATH);
    }

    public <T> GrammarRunResult<T> run(Object key, ThrowingSupplier<T> reflectiveRunner, Set<GrammarProcessingOptions> options) {
        // XXX
        // - results checks if stale or not
        // - force re-run or not
        // - optionally return the last good result if there is one
        //    - Maybe a closure that takes both?
        // - clean method on results
        GenerationAndCompilationResult res;
        if (options.contains(GrammarProcessingOptions.REBUILD_JAVA_SOURCES) || (lastGenerationResult == null || (lastGenerationResult != null && !lastGenerationResult.isUsable())
                && !lastGenerationResult.currentStatus().mayRequireRebuild())) {
            res = generateAndCompile(grammarFileName, options);
        } else {
            res = lastGenerationResult;
        }
        if (!res.genAndCompileResult.isUsable()) {
            lastGenerationResult = null;
            return new GrammarRunResult<>(null, null, res, lastGood(key));
        }
        lastGenerationResult = res;
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        try {
            try (JFSClassLoader nue = createClassLoader()) {
                Thread.currentThread().setContextClassLoader(nue);
                T result = reflectiveRunner.get();
                GrammarRunResult<T> grr = new GrammarRunResult(result, null, res, null);
                if (grr.isUsable()) {
                    lastGoodResults.put(key, grr);
                }
                return grr;
            }
        } catch (Exception ex) {
            return new GrammarRunResult(null, ex, res, lastGood(key));
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }
}

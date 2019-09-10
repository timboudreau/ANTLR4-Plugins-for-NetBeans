package org.nemesis.antlr.live.execution;

import com.mastfrog.function.throwing.ThrowingFunction;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacOptions;

/**
 *
 * @author Tim Boudreau
 */
public abstract class InvocationRunner<T, A> implements ThrowingFunction<A, T> {

    private final Class<T> type;

    protected InvocationRunner(Class<T> type) {
        this.type = type;
    }

    public Class<T> type() {
        return type;
    }

    final A configureCompilation(ANTLRv4Parser.GrammarFileContext tree, AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr, String packageName, Consumer<Supplier<ClassLoader>> classloaderSupplierConsumer) throws IOException {
        bldr.runAnnotationProcessors(false);
        bldr.sourceAndTargetLevel(8);
        bldr.setOptions(new JavacOptions().withCharset(jfs.encoding()).withMaxErrors(1));
        return onBeforeCompilation(tree, res, extraction, jfs, bldr, packageName, classloaderSupplierConsumer);
    }

    protected abstract A onBeforeCompilation(ANTLRv4Parser.GrammarFileContext tree, AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr, String grammarPackageName, Consumer<Supplier<ClassLoader>> classloaderSupplierConsumer) throws IOException;
}

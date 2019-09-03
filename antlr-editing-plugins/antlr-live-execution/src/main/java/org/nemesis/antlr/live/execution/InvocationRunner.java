package org.nemesis.antlr.live.execution;

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.io.IOException;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacOptions;

/**
 *
 * @author Tim Boudreau
 */
public abstract class InvocationRunner<T> implements ThrowingSupplier<T> {

    private final Class<T> type;

    protected InvocationRunner(Class<T> type) {
        this.type = type;
    }

    public Class<T> type() {
        return type;
    }

    final void configureCompilation(JFS jfs, JFSCompileBuilder bldr, String packageName) throws IOException {
        bldr.runAnnotationProcessors(false);
        bldr.sourceAndTargetLevel(8);
        bldr.setOptions(new JavacOptions().withCharset(jfs.encoding()).withMaxErrors(1));
        onBeforeCompilation(jfs, bldr, packageName);
    }

    protected abstract void onBeforeCompilation(JFS jfs, JFSCompileBuilder bldr, String grammarPackageName) throws IOException;

}

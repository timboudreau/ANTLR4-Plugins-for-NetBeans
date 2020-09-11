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
package org.nemesis.antlr.live.execution;

import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacOptions;
import org.openide.filesystems.FileObject;

/**
 * Generic abstraction for a thing which will generate some source code into a
 * JFS, configure a compiler to compile it, configure a (most likely isolated)
 * and classloader to invoke it.
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

    protected void onDisposed(FileObject fo) {

    }

    public boolean isStillValid(A a) {
        return true;
    }

    public final A configureCompilation(ANTLRv4Parser.GrammarFileContext tree, AntlrGenerationResult res,
            Extraction extraction, JFS jfs, JFSCompileBuilder bldr, String packageName,
            Consumer<Supplier<ClassLoader>> classloaderSupplierConsumer,
            Consumer<UnixPath> singleJavaSourceConsumer) throws IOException {
        bldr.runAnnotationProcessors(false);
        bldr.sourceAndTargetLevel(8);
        bldr.setOptions(new JavacOptions().withCharset(jfs.encoding()).withMaxErrors(1));
        return onBeforeCompilation(tree, res, extraction, jfs, bldr, packageName,
                classloaderSupplierConsumer, singleJavaSourceConsumer);
    }

    protected abstract A onBeforeCompilation(ANTLRv4Parser.GrammarFileContext tree, AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr, String grammarPackageName, Consumer<Supplier<ClassLoader>> classloaderSupplierConsumer, Consumer<UnixPath> singleJavaSourceConsumer) throws IOException;
}

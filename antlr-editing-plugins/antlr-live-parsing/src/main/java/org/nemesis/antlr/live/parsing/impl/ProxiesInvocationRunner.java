/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.parsing.impl;

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.antlr.runtime.misc.IntArray;
import org.antlr.v4.Tool;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.live.execution.InvocationRunner;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.ExtractionCodeGenerationResult;
import org.nemesis.antlr.live.parsing.extract.ExtractionCodeGenerator;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner.GenerationResult;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.isolation.IsolationClassLoader;
import org.nemesis.jfs.isolation.IsolationClassLoaderBuilder;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;
import org.stringtemplate.v4.Interpreter;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = InvocationRunner.class, path
        = "antlr/invokers/org/nemesis/antlr/live/parsing/impl/EmbeddedParser")
public class ProxiesInvocationRunner extends InvocationRunner<EmbeddedParser, GenerationResult> {

    private static final Logger LOG = Logger.getLogger(
            ProxiesInvocationRunner.class.getName());
    private static final IsolatingClassLoaderSupplier CLASSLOADER_FACTORY
            = new IsolatingClassLoaderSupplier();

    static {
        LOG.setLevel(Level.ALL);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    public ProxiesInvocationRunner() {
        super(EmbeddedParser.class);
        LOG.log(Level.FINE, "Created {0}", this);
    }

    @Override
    protected GenerationResult onBeforeCompilation(ANTLRv4Parser.GrammarFileContext tree, AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr, String grammarPackageName, Consumer<Supplier<ClassLoader>> csc) throws IOException {
        GrammarKind kind = GrammarKind.forTree(tree);
        Optional<Path> realSourceFile = extraction.source().lookup(Path.class);
        if (realSourceFile.isPresent()) {
            Path path = realSourceFile.get();
            ExtractionCodeGenerationResult genResult = ExtractionCodeGenerator.saveExtractorSourceCode(path, jfs, res.packageName, res.grammarName());
            LOG.log(Level.FINER, "onBeforeCompilation for {0} kind {1} generation result {2}", new Object[]{path, kind, genResult});
            bldr.addToClasspath(AntlrProxies.class);
            bldr.addToClasspath(ANTLRErrorListener.class);
            bldr.addToClasspath(Tool.class);
            bldr.addToClasspath(IntArray.class);
            bldr.addToClasspath(Interpreter.class);
            bldr.addToClasspath(AdhocMimeTypes.class);
//            bldr.addToClasspath(CharSequenceCharStream.class);
            bldr.addSourceLocation(StandardLocation.SOURCE_PATH);
            bldr.addSourceLocation(StandardLocation.SOURCE_OUTPUT);
            csc.accept(CLASSLOADER_FACTORY);
            return new GenerationResult(genResult, res.packageName, path, res.grammarName);
        } else {
            LOG.log(Level.WARNING, "No source file for extraction {0}", extraction.logString());
        }
        return null;
    }

    @Override
    public EmbeddedParser apply(GenerationResult res) throws Exception {
        if (!res.res.isSuccess()) {
            LOG.log(Level.WARNING, "Failed to generate extractor: {0}", res);
            return new DeadEmbeddedParser(res.grammarPath, res.grammarName);
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        PreservedInvocationEnvironment env = new PreservedInvocationEnvironment(loader, res.packageName);
        return env;
    }

    static class GenerationResult {

        final ExtractionCodeGenerationResult res;
        final String packageName;
        final Path grammarPath;
        final String grammarName;

        public GenerationResult(ExtractionCodeGenerationResult res, String packageName, Path grammarPath, String grammarName) {
            this.res = res;
            this.packageName = packageName;
            this.grammarPath = grammarPath;
            this.grammarName = grammarName;
        }
    }

    static final class EnvRef extends WeakReference<PreservedInvocationEnvironment> implements Runnable {

        private final ClassLoader ldr;
        private volatile boolean discarded;

        public EnvRef(PreservedInvocationEnvironment referent) {
            super(referent, Utilities.activeReferenceQueue());
            // Run method will be called by activeReferenceQueue once garbage
            // collected
            this.ldr = referent.ldr;
        }

        public String toString() {
            return "EnvRef(" + ldr.getClass().getSimpleName() + "@"
                    + System.identityHashCode(ldr) + " discarded " + discarded + ")";
        }

        @Override
        public void run() {
            if (discarded) {
                return;
            }
            discarded = true;
            LOG.log(Level.FINEST, "Discard a classloader {0}", ldr);
            try {
                if (ldr instanceof AutoCloseable) {
                    ((AutoCloseable) ldr).close();
                } else if (ldr instanceof Closeable) {
                    ((Closeable) ldr).close();
                }
            } catch (Exception ex) {
                LOG.log(Level.INFO, "Exception closing classloader " + ldr, ex);
            }
        }
    }

    static final class IsolatingClassLoaderSupplier implements Supplier<ClassLoader> {

        private final IsolationClassLoaderBuilder builder = IsolationClassLoader.builder()
                .includingJarOf(ANTLRErrorListener.class)
                .includingJarOf(Tool.class)
                .includingJarOf(IntArray.class)
                .includingJarOf(Interpreter.class)
                .loadingFromParent(AntlrProxies.class)
                .loadingFromParent(AntlrProxies.ParseTreeBuilder.class)
                .loadingFromParent(AntlrProxies.ParseTreeElement.class)
                .loadingFromParent(AntlrProxies.ParseTreeElementKind.class)
                .loadingFromParent(AntlrProxies.ParseTreeProxy.class)
                .loadingFromParent(AntlrProxies.ProxyDetailedSyntaxError.class)
                .loadingFromParent(AntlrProxies.ProxyException.class)
                .loadingFromParent(AntlrProxies.ProxySyntaxError.class)
                .loadingFromParent(AntlrProxies.ProxyToken.class)
                .loadingFromParent(AntlrProxies.ProxyTokenType.class)
                // XXX, we should move the mime type guesswork to something
                // with a smaller footprint and omit this
                .loadingFromParent(AdhocMimeTypes.class);

        @Override
        public ClassLoader get() {
            return builder.build();
        }
    }

    /**
     * This is really just a holder for the classloader created over the JFS,
     * which is set before calling into generated classes.
     */
    private static final class PreservedInvocationEnvironment implements EmbeddedParser {

        private final ClassLoader ldr;
        private final String typeName;
        private EnvRef ref;

        public PreservedInvocationEnvironment(ClassLoader ldr, String pkgName) {
            this.ldr = ldr;
            typeName = pkgName + "." + ExtractionCodeGenerator.PARSER_EXTRACTOR;
            ref = new EnvRef(this);
        }

        @Override
        public String toString() {
            return super.toString() + "(" + ref + ")";
        }

        <T> T clRun(ThrowingSupplier<T> th) throws Exception {
            return Debug.runObjectThrowing(this, "enter-embedded-parser " + typeName + " " + ref, () -> {
                return ref.toString(); // ldr.toString().replace(',', '\n');
            }, () -> {
                ClassLoader old = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(ldr);
                try {
                    return th.get();
                } finally {
                    if (old != ldr) {
                        Thread.currentThread().setContextClassLoader(old);
                    }
                }
            });
        }

        @Override
        public AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body) throws Exception {
            AntlrProxies.ParseTreeProxy[] prex = new AntlrProxies.ParseTreeProxy[1];
            return Debug.runObjectThrowing(this, "embedded-parse for " + logName, () -> {
                StringBuilder sb = new StringBuilder("************* BODY ***************\n");
                sb.append(body);
                sb.append("\n******************* PROXY *********************\n");
                sb.append(prex[0]);
                return "";
            }, () -> {
                return clRun(() -> {
                    prex[0] = reflectively(typeName, new Class<?>[]{CharSequence.class}, body);
                    Debug.message("" + prex[0].grammarPath());
                    if (prex[0].isUnparsed()) {
                        Debug.failure("unparsed result", () -> {
                            return prex[0].grammarName() + " = " + prex[0].grammarPath();
                        });
                    } else {
                        Debug.success("parsed", prex[0].tokenCount() + " tokens");
                    }
                    return prex[0];
                });
            });
        }

        @Override
        public AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body, int ruleNo) throws Exception {
            return clRun(() -> {
                return reflectively(typeName, new Class<?>[]{CharSequence.class, int.class}, body, ruleNo);
            });
        }

        @Override
        public AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body, String ruleName) throws Exception {
            return clRun(() -> {
                return reflectively(typeName, new Class<?>[]{CharSequence.class, String.class}, body, ruleName);
            });
        }

        private static AntlrProxies.ParseTreeProxy reflectively(String cl,
                Class<?>[] params, Object... args)
                throws ClassNotFoundException, NoSuchMethodException,
                IllegalAccessException, IllegalArgumentException,
                InvocationTargetException {
            assert params.length == args.length;
            ClassLoader ldr = Thread.currentThread().getContextClassLoader();
            Class<?> c = ldr.loadClass(cl);
            Method m = c.getMethod("extract", params);
            return (AntlrProxies.ParseTreeProxy) m.invoke(null, args);
        }

        @Override
        public synchronized void onDiscard() {
            ref.run();
        }
    }
}

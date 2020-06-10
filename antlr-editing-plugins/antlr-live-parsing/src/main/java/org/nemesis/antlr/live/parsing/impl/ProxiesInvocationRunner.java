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
package org.nemesis.antlr.live.parsing.impl;

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.antlr.runtime.misc.IntArray;
import org.antlr.v4.Tool;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.tool.Grammar;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.live.execution.InvocationRunner;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.ExtractionCodeGenerationResult;
import org.nemesis.antlr.live.parsing.extract.ExtractionCodeGenerator;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner.GenerationResult;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
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

    @SuppressWarnings("LeakingThisInConstructor")
    public ProxiesInvocationRunner() {
        super(EmbeddedParser.class);
        LOG.log(Level.FINE, "Created {0}", this);
    }

    private static Supplier<String> compileMessage(AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr, String grammarPackageName) {
        return () -> {
            StringBuilder sb = new StringBuilder("GrammarPackage: ").append(grammarPackageName)
                    .append("\nExtraction Source:").append(extraction.source())
                    .append("\nJFS:\n");
            jfs.listAll((loc, fo) -> {
                sb.append(loc).append(": ").append(fo.getName()).append('\n');
            });
            if (res.infoMessages != null && !res.infoMessages.isEmpty()) {
                sb.append("\n").append("Generation info:\n");
                for (String msg : res.infoMessages) {
                    sb.append(msg).append('\n');
                }
            }
            sb.append("\n").append("ExitCode: ").append(res.code);
            if (res.errors != null && !res.errors.isEmpty()) {
                sb.append("\nAntlr Generation Errors:\n");
                for (ParsedAntlrError err : res.errors) {
                    sb.append(err).append("\n");
                }
            }
            if (res.allGrammars != null && !res.allGrammars.isEmpty()) {
                sb.append("\nGrammars:\n");
                for (Grammar g : res.allGrammars) {
                    sb.append("Grammar " + g.name + " @ " + g.fileName);
                }
            }
            if (res.thrown != null) {
                Debug.thrown(res.thrown);
            }
            sb.append("CompileBuilder: ").append(bldr);
            return sb.toString();
        };
    }

    public static Grammar findLexerGrammar(AntlrGenerationResult res) {
        Grammar main = res.mainGrammar;
        if (main.implicitLexer != null) {
            return main.implicitLexer;
        }
        if (main.importedGrammars != null) {
            for (Grammar imp : main.importedGrammars) {
                if (imp.isLexer()) {
                    return imp;
                }
            }
        }
        // If it was imported via tokenVocab, it may not be in
        // importedGrammars, but we ensure it is here:
        for (Grammar g : res.allGrammars) {
            if (g.isLexer()) {
                return g;
            }
        }
        return null;
    }

    @Override
    protected GenerationResult onBeforeCompilation(ANTLRv4Parser.GrammarFileContext tree, AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr, String grammarPackageName, Consumer<Supplier<ClassLoader>> csc) throws IOException {
        return Debug.runObjectIO(this, "onBeforeCompilation", compileMessage(res, extraction, jfs, bldr, grammarPackageName), () -> {
            GrammarKind kind = GrammarKind.forTree(tree);
            Optional<Path> realSourceFile = extraction.source().lookup(Path.class);
            if (realSourceFile.isPresent()) {
                Path path = realSourceFile.get();
                Grammar lexerGrammar = findLexerGrammar(res);

                ExtractionCodeGenerationResult genResult = ExtractionCodeGenerator.saveExtractorSourceCode(path, jfs,
                        res.packageName, res.grammarName(), lexerGrammar == null ? null : lexerGrammar.name);
                LOG.log(Level.FINER, "onBeforeCompilation for {0} kind {1} generation result {2}", new Object[]{path, kind, genResult});
                bldr.addToClasspath(AntlrProxies.class);
                bldr.addToClasspath(AntlrProxies.Ambiguity.class);
                bldr.addToClasspath(ANTLRErrorListener.class);
                bldr.addToClasspath(Tool.class);
                bldr.addToClasspath(IntArray.class);
                bldr.addToClasspath(Interpreter.class);
                bldr.addToClasspath(AdhocMimeTypes.class);
                bldr.addSourceLocation(StandardLocation.SOURCE_PATH);
                bldr.addSourceLocation(StandardLocation.SOURCE_OUTPUT);
                return Debug.runObject(this, "Generate extractor source", () -> {;
                    csc.accept(CLASSLOADER_FACTORY);
                    GenerationResult gr = new GenerationResult(genResult, res.packageName, path, res.grammarName);
                    Debug.message("Generation result", gr::toString);
                    return gr;
                });
            } else {
                Debug.message("No source file for extraction {0}", extraction.logString());
                LOG.log(Level.WARNING, "No source file for extraction {0}", extraction.logString());
            }
            return null;
        });
    }

    @Override
    public EmbeddedParser apply(GenerationResult res) throws Exception {
        return Debug.runObjectThrowing(this, "Create new embedded parser", () -> "", () -> {
            if (!res.res.isSuccess()) {
                Debug.message("Generation Failure", () -> {
                    return res.res.toString();
                });
                LOG.log(Level.WARNING, "Failed to generate extractor: {0}", res);
                Debug.message("Return a dead embedded parser for " + res.grammarPath);
                return new DeadEmbeddedParser(res.grammarPath, res.grammarName);
            }
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            PreservedInvocationEnvironment env = new PreservedInvocationEnvironment(loader, res.packageName);
            Debug.message("Use new PreservedInvocationEnvironment", () -> {
                return env.toString();
            });
            return env;
        });
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

        public String toString() {
            return "ProxiesInvocationRunner.GenerationResult("
                    + packageName + " " + grammarName + " on " + grammarPath + ": "
                    + res + ")";
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
            Debug.run(this, "Discard classloader", () -> {
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
            });
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
            return Debug.runObjectThrowing(this, "enter-embedded-parser-classloader " + typeName + " " + ref, ref::toString,
                    () -> {
                        ClassLoader old = Thread.currentThread().getContextClassLoader();
                        Thread.currentThread().setContextClassLoader(ldr);
                        T result = null;
                        Object[] res = new Object[1];
                        try {
                            result = th.get();
                            res[0] = result;
                        } finally {
                            if (old != ldr) {
                                Thread.currentThread().setContextClassLoader(old);
                            }
                            Debug.message("Invocation result", Objects.toString(res[0]));
                        }
                        return result;
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

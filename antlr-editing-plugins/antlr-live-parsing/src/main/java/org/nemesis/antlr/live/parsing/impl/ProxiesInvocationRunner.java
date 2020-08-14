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
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;
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
import org.nemesis.antlr.memory.spi.AntlrLoggers;
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
 * Factory that replaces the inner EmbeddedParser implementation wrapped by the
 * persistent instances of EmbeddedAntlrParser returned by EmbeddedAntlrParsers;
 * this class is called when an Antlr grammar has been rebuild in its JFS and
 * the parser may need to recompile its ParserExptractor to work against the
 * revised grammar.
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

    public String toString() {
        return "ProxiesInvocationRunner<EmbeddedParser, GenerationResult>";
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
        if (main.implicitLexer != null && main.implicitLexer.name != null) {
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

    private static String findLexerGrammarName(AntlrGenerationResult res) {
        if (res.mainGrammar.isLexer()) {
            return res.mainGrammar.getRecognizerName();
        }
        Grammar g = findLexerGrammar(res);
        if (g != null) {
            return g.getRecognizerName();
        }
        return res.mainGrammar.getOptionString("tokenVocab");
    }

    private static String findTargetName(AntlrGenerationResult res) {
        String result = res.mainGrammar.getRecognizerName();
        return result == null ? res.grammarName() : result;
    }

    @Override
    protected GenerationResult onBeforeCompilation(ANTLRv4Parser.GrammarFileContext tree,
            AntlrGenerationResult res, Extraction extraction, JFS jfs, JFSCompileBuilder bldr,
            String grammarPackageName, Consumer<Supplier<ClassLoader>> csc) throws IOException {
        return Debug.runObjectIO(this, "onBeforeCompilation", compileMessage(res, extraction, jfs, bldr, grammarPackageName), () -> {
            GrammarKind kind = GrammarKind.forTree(tree);
            Path path = res.originalFilePath;
            try (PrintStream info = AntlrLoggers.getDefault().printStream(path, AntlrLoggers.STD_TASK_GENERATE_ANALYZER)) {
                Grammar lexerGrammar = findLexerGrammar(res);
                String lexerName = lexerGrammar == null ? findLexerGrammarName(res) : lexerGrammar.name;

                info.println("Generate Live Analysis code for " + kind + " grammar " + path);
                if (lexerName != null) {
                    info.println("Lexer name from grammar:\t" + lexerName);
                }
                info.println("Grammar tokens hash:\t" + res.tokensHash);
                info.println("Tokens hash from extraction:\t" + (extraction == null ? "(no extraction)" : extraction.tokensHash()));
                info.println();

                ExtractionCodeGenerationResult genResult = ExtractionCodeGenerator.saveExtractorSourceCode(kind, path, jfs,
                        res.packageName, findTargetName(res), lexerName, info, extraction.tokensHash(), res.hints);

                LOG.log(Level.FINER, "onBeforeCompilation for {0} kind {1} generation result {2}"
                        + " tokens hash {3}",
                        new Object[]{
                            path,
                            kind,
                            genResult,
                            (extraction == null ? "" : extraction.tokensHash())
                        });

                bldr.verbose().withMaxErrors(10).withMaxWarnings(10).nonIdeMode().abortOnBadClassFile();

                bldr.addToClasspath(AntlrProxies.class);
//                bldr.addToClasspath(AntlrProxies.Ambiguity.class);
                bldr.addToClasspath(ANTLRErrorListener.class);
                bldr.addToClasspath(Tool.class);
                bldr.addToClasspath(IntArray.class);
                bldr.addToClasspath(Interpreter.class);
                bldr.addToClasspath(AdhocMimeTypes.class);
                bldr.addSourceLocation(StandardLocation.SOURCE_PATH);
                bldr.addSourceLocation(StandardLocation.SOURCE_OUTPUT);
                return Debug.runObject(this, "Generate extractor source", () -> {;
                    csc.accept(CLASSLOADER_FACTORY);
                    GenerationResult gr = new GenerationResult(genResult, res.packageName, path, res.grammarName, jfs);
                    LOG.log(Level.FINER, "Generation result {0}", gr);
                    Debug.message("Generation result", gr::toString);
                    return gr;
                });
            }
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
            PreservedInvocationEnvironment env = new PreservedInvocationEnvironment(loader, res.packageName, res);
            LOG.log(Level.FINER, "New environment created for embedded parser: {0} and {1}", new Object[]{env, res});
            Debug.message("Use new PreservedInvocationEnvironment", env::toString);
            return env;
        });
    }

    static class GenerationResult {

        final ExtractionCodeGenerationResult res;
        final String packageName;
        final Path grammarPath;
        final String grammarName;
        private final JFS jfs;

        public GenerationResult(ExtractionCodeGenerationResult res, String packageName, Path grammarPath, String grammarName, JFS jfs) {
            this.res = res;
            this.packageName = packageName;
            this.grammarPath = grammarPath;
            this.grammarName = grammarName;
            this.jfs = jfs;
        }

        public boolean isUsable() {
            return res != null && res.isSuccess();
        }

        public boolean clean() {
            return res != null && res.clean(jfs);
        }

        @Override
        public String toString() {
            return "ProxiesInvocationRunner.GenerationResult("
                    + packageName + " " + grammarName + " on " + grammarPath + ": "
                    + res + " over " + jfs + ")";
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
                .loadingFromParent(AntlrProxies.Ambiguity.class)
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
        private final EnvRef ref;
        private final CharSequence genInfo;
        private final Runnable cleaner;

        private PreservedInvocationEnvironment(ClassLoader ldr, String pkgName, GenerationResult res) {
            this.ldr = ldr;
            typeName = pkgName + "." + res.res.generatedClassName();
            ref = new EnvRef(this);
            this.genInfo = res.res.generationInfo();
            cleaner = res::clean;
        }

        @Override
        public void clean() {
            cleaner.run();
        }

        @Override
        public String toString() {
            return super.toString() + "(" + ref + " - " + typeName + " generationInfo: "
                    + genInfo.toString().replace("\n", "; ") + ")";
        }

        <T> T clRun(ThrowingSupplier<T> th) throws Exception {
            return Debug.runObjectThrowing(this, "enter-embedded-parser-classloader "
                    + typeName + " " + ref, ref::toString,
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
                            Debug.message("Invocation result", () -> Objects.toString(res[0]));
                        }
                        return result;
                    });
        }

        @Override
        public AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body) throws Exception {
            LOG.log(Level.FINER, "Initiating parse in {0}", logName);
//            if (LOG.isLoggable(Level.FINEST)) {
//                LOG.log(Level.FINEST, "Parse culprit " + logName + " in PIE " + System.identityHashCode(this), new Exception());
//            }
            AntlrProxies.ParseTreeProxy[] prex = new AntlrProxies.ParseTreeProxy[1];
            return Debug.runObjectThrowing(this, "embedded-parse for " + logName, () -> {
                StringBuilder sb = new StringBuilder("************* BODY ***************\n");
                sb.append(body);
                sb.append("\n******************* PROXY *********************\n");
                sb.append(prex[0]);
                sb.append("\n******************* GEN INFO ******************\n");
                return sb.toString();
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

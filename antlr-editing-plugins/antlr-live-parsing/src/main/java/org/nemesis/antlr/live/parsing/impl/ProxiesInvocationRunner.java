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
import com.mastfrog.function.throwing.ThrowingTriFunction;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.MapFactories;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.tools.StandardLocation;
import org.antlr.runtime.misc.IntArray;
import org.antlr.v4.Tool;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.tool.Grammar;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.live.execution.InvocationRunner;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.ExtractionCodeGenerationResult;
import org.nemesis.antlr.live.parsing.extract.ExtractionCodeGenerator;
import org.nemesis.antlr.live.parsing.impl.ProxiesInvocationRunner.GenerationResult;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSClassLoader;
import org.nemesis.jfs.isolation.IsolationClassLoader;
import org.nemesis.jfs.isolation.IsolationClassLoaderBuilder;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
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

    /**
     * A shared classloader that can be used as the parent of every
     * JFSClassLoader we create, since once loaded, its contents never change.
     */
    private static final IsolationClassLoaderBuilder isolatedParentClassLoader = IsolationClassLoader
            .builder()
            // Mark it uncloseable, or closing the JFSClassLoader will inadvertently
            // close it as well
//            .uncloseable()
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
//            .loadingFromParent(ProxiesInvocationRunner.class.getName())
            // XXX, we should move the mime type guesswork to something
            // with a smaller footprint and omit this
            .loadingFromParent(AdhocMimeTypes.class);

    @SuppressWarnings("LeakingThisInConstructor")
    public ProxiesInvocationRunner() {
        super(EmbeddedParser.class);
        LOG.log(Level.FINE, "Created {0}", this);
    }

    @Override
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
                    sb.append("Grammar ").append(g.name).append(" @ ").append(g.fileName);
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
                return Debug.runObject(this, "Generate extractor source", () -> {
                    csc.accept(new JFSClassLoaderFactory(res.jfsSupplier));
                    GenerationResult gr = new GenerationResult(genResult, res.packageName, path, res.grammarName, jfs,
                            res, bldr, csc, tree);
                    LOG.log(Level.FINER, "Generation result {0}", gr);
                    Debug.message("Generation result", gr::toString);
                    return gr;
                });
            }
        });
    }

    static class JFSClassLoaderFactory implements Supplier<ClassLoader> {

        private final Supplier<JFS> jfsSupplier;

        public JFSClassLoaderFactory(Supplier<JFS> jfsSupplier) {
            this.jfsSupplier = jfsSupplier;
        }

        @Override
        public ClassLoader get() {
            try {
                JFS jfs = jfsSupplier.get();
                return jfs.getClassLoader(true, isolatedParentClassLoader.build(),
                        StandardLocation.CLASS_OUTPUT, StandardLocation.CLASS_PATH);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
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
            PreservedInvocationEnvironment env = new PreservedInvocationEnvironment(loader, res.packageName, res,
                    (logName, thrown, text) -> repairEnvironment(logName, thrown, text, res));
            LOG.log(Level.FINER, "New environment created for embedded parser: {0} and {1}", new Object[]{env, res});
            Debug.message("Use new PreservedInvocationEnvironment", env::toString);
            return env;
        });
    }

    /**
     * A last-ditch attempt to rebuild the ENTIRE environment
     *
     * @param res
     * @return
     * @throws IOException
     */
    private synchronized ParseTreeProxy repairEnvironment(String logName, Throwable thrown, CharSequence text, GenerationResult res) throws IOException {
        if (res.compiler == null) {
            // Already attempted
            return AntlrProxies.forUnparsed(res.grammarPath, res.grammarName, text);
        }
        // Called on a ClassNotFoundException which indicates it is fairly likely
        // the the generated class file was deleted
        return res.jfs.whileWriteLocked(() -> {
            LOG.log(Level.WARNING, "Got ClassNotFoundException in {0} jfs/classloader.  Will try "
                    + "rebuilding the ENTIRE JFS contents for {1} and re-running.  This may not work.", new Object[]{logName, res.grammarName});
            try {
                JFS jfs = res.generationResult.jfsSupplier.get();
                Path path = res.generationResult.originalFilePath;
                assert path != null : "Grammar path from generation result null: " + res.generationResult;
                File file = res.grammarPath.toFile();
                org.openide.filesystems.FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
                if (fo == null) {
                    // file no longer exists
                    return AntlrProxies.forUnparsed(res.grammarPath, res.grammarName, text);
                }
                EditorCookie ck = DataObject.find(fo).getLookup().lookup(EditorCookie.class);
                Document doc = ck == null ? null : ck.getDocument();
                NbAntlrUtils.invalidateSource(fo);
                Extraction ext = doc == null ? NbAntlrUtils.extractionFor(fo) : NbAntlrUtils.extractionFor(doc);
                if (ext != null) {
                    try (PrintStream info = AntlrLoggers.getDefault().printStream(
                            res.grammarPath, AntlrLoggers.STD_TASK_GENERATE_ANALYZER)) {
                        GrammarKind kind = GrammarKind.forTree(res.tree);
                        Grammar lexerGrammar = findLexerGrammar(res.generationResult);
                        String lexerName = lexerGrammar == null
                                ? findLexerGrammarName(res.generationResult) : lexerGrammar.name;

                        AntlrGenerationResult newGenResult = res.generationResult.rebuild();

                        LOG.finest(() -> "Rebuild regen result " + newGenResult);

                        ExtractionCodeGenerationResult genResult = ExtractionCodeGenerator.saveExtractorSourceCode(
                                kind, path, jfs,
                                res.packageName, findTargetName(newGenResult),
                                lexerName, info, ext.tokensHash(), newGenResult.hints);

                        LOG.finest(() -> "Rebuild generation result: " + genResult);

                        res.compiler.addSourceLocation(StandardLocation.SOURCE_PATH);
                        res.compiler.addSourceLocation(StandardLocation.SOURCE_OUTPUT);

                        try (Writer compileOutput = AntlrLoggers.getDefault().writer(path, AntlrLoggers.STD_TASK_COMPILE_GRAMMAR)) {
                            res.compiler.compilerOutput(compileOutput);
                            CompileResult compileResult = res.compiler.compile();
                            LOG.finest(() -> "Rebuild compile result " + compileResult);
                        }
//                        res.csc.accept(CLASSLOADER_FACTORY);

                        GenerationResult newG = new GenerationResult(genResult, res.packageName, path,
                                newGenResult.grammarName, newGenResult.jfs, newGenResult,
                                res.compiler, res.csc, res.tree);

                        JFSClassLoader workingLoader = jfs.getClassLoader(StandardLocation.CLASS_OUTPUT,
                                isolatedParentClassLoader.build());

                        PreservedInvocationEnvironment pie = new PreservedInvocationEnvironment(workingLoader,
                                newG.packageName, newG, (String lgName, Throwable originallyThrown, CharSequence ignored) -> {
                                    originallyThrown.addSuppressed(thrown);
                                    Exception ex = new Exception("Already tried rebuilding once; will not loop endlessly."
                                            + " JFS contents: " + listJFS(jfs, new StringBuilder()), originallyThrown);
                                    LOG.log(Level.FINE, "Environment rebuild failed", ex);
                                    return AntlrProxies.forUnparsed(res.grammarPath, res.grammarName, text);
                                });
                        boolean success = false;
                        try {
                            ParseTreeProxy result = pie.parse(logName, text);
                            if (result != null) {
                                success = true;
                            }
                            return result;
                        } finally {
                            pie.onDiscard();
                            workingLoader.close();
                            if (success) {
                                newG.discardLeakables();
                            }
                        }
                    }
                }
                return AntlrProxies.forUnparsed(res.grammarPath, res.grammarName, text);
            } catch (Exception ex) {
                return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
            }
        });
    }

    static StringBuilder listJFS(JFS jfs, StringBuilder sb) {
        jfs.list(StandardLocation.SOURCE_OUTPUT, (loc, fo) -> {
            sb.append('\n').append(loc).append('\t').append(fo.path());
        });
        jfs.list(StandardLocation.CLASS_OUTPUT, (loc, fo) -> {
            sb.append('\n').append(loc).append('\t').append(fo.path());
        });
        return sb;
    }

    static class GenerationResult {

        final ExtractionCodeGenerationResult res;
        final String packageName;
        final Path grammarPath;
        final String grammarName;
        private final JFS jfs;
        private JFSCompileBuilder compiler;
        private AntlrGenerationResult generationResult;
        private Consumer<Supplier<ClassLoader>> csc;
        private GrammarFileContext tree;

        GenerationResult(ExtractionCodeGenerationResult genResult, String packageName, Path grammarPath,
                String grammarName, JFS jfs, AntlrGenerationResult res, JFSCompileBuilder compiler,
                Consumer<Supplier<ClassLoader>> csc, GrammarFileContext tree) {
            this.res = genResult;
            this.packageName = packageName;
            this.grammarPath = grammarPath;
            this.grammarName = grammarName;
            this.jfs = jfs;
            this.compiler = compiler;
            this.generationResult = res;
            this.csc = csc;
            this.tree = tree;
        }

        void discardLeakables() {
            // These are needed to deal with the case of an emergency
            // rebuild, if files are missing from the JFS.
            tree = null;
            csc = null;
            generationResult = null;
            compiler = null;
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

        EnvRef(PreservedInvocationEnvironment referent) {
            super(referent, Utilities.activeReferenceQueue());
            // Run method will be called by activeReferenceQueue once garbage
            // collected
            this.ldr = referent.ldr;
        }

        @Override
        public String toString() {
            return "EnvRef(" + (ldr == null ? "null" : ldr.getClass().getSimpleName())
                    + " " + (ldr == null ? 0 : System.identityHashCode(ldr))
                    + " discarded " + discarded + ")";
        }

        @Override
        public void run() {
            if (discarded) {
                return;
            }
            discarded = true;
            Debug.run(this, "Discard classloader", () -> {
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
        private final ThrowingTriFunction<String, Throwable, CharSequence, ParseTreeProxy> onEnvironmentCorrupted;
        private final String grammarName;
        private final Path grammarPath;
        private final JFS jfs;
        private volatile Runnable onFirst;

        private PreservedInvocationEnvironment(ClassLoader ldr, String pkgName, GenerationResult res,
                ThrowingTriFunction<String, Throwable, CharSequence, ParseTreeProxy> onEnvironmentCorrupted) {
            ref = new EnvRef(this);
            this.ldr = notNull("ldr", ldr);
            typeName = pkgName + "." + res.res.generatedClassName();
            this.jfs = notNull("res.jfs", res.jfs);
            this.grammarPath = notNull("res.grammarPath", res.grammarPath);
            this.grammarName = notNull("res.grammarName", res.grammarName);
            this.genInfo = res.res.generationInfo();
            cleaner = res::clean;
            this.onEnvironmentCorrupted = onEnvironmentCorrupted;
            onFirst = res::discardLeakables;
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
        public ParseTreeProxy parse(String logName, CharSequence body) throws Exception {
            LOG.log(Level.FINER, "Initiating parse in {0}", logName);
            return Debug.runObjectThrowing(this, "embedded-parse for " + logName, () -> {
                StringBuilder sb = new StringBuilder("************* BODY ***************\n");
                sb.append(body);
                sb.append("\n******************* GEN INFO ******************\n");
                return sb.toString();
            }, () -> {
                try {
                    ParseTreeProxy result = doClRun(body);
                    Runnable of = onFirst;
                    if (of != null) {
                        // After the first success, the classloader will contain all
                        // needed classes; so ensure this object does not hold a
                        // bucket of objects from the parse that are no longer
                        // needed;  the are used if we need to regenerate the
                        // environment
                        onFirst = null;
                        of.run();
                    }
                    return result;
                } catch (ClassNotFoundException ex) {
                    StringBuilder sb = new StringBuilder(120);
                    sb.append("Environment probably corrupted for ")
                            .append(this.typeName).append(" for grammar ")
                            .append(grammarName).append(" of ")
                            .append(grammarPath)
                            .append("; will retry once, for ")
                            .append(logName).append(" - ").append(":");
                    if (LOG.isLoggable(Level.FINE)) {
                        listJFS(this.jfs, sb);
                        LOG.log(Level.FINE, sb.toString(), ex);
                    }
                    String msg = sb.toString();
                    try {
                        ClassNotFoundException cnfe = new ClassNotFoundException(msg, ex);
                        ParseTreeProxy result = onEnvironmentCorrupted.apply(logName, cnfe, body);
                        if (result.isUnparsed()) {
                            LOG.log(Level.FINEST, msg, cnfe);
                        }
                        return result;
                    } catch (Exception | Error ex1) {
                        ex1.addSuppressed(ex);
                        LOG.log(Level.WARNING, "Attempt to recreate environment "
                                + "for " + grammarName + " failed", ex1);
                        return AntlrProxies.forUnparsed(grammarPath, grammarName, body);
                    }
                }
            });
        }

        ParseTreeProxy doClRun(CharSequence body) throws Exception {
            return clRun(() -> {
                ParseTreeProxy result = reflectively(typeName, new Class<?>[]{CharSequence.class}, body);
//                Debug.message("" + result.grammarPath());
//                if (result.isUnparsed()) {
//                    Debug.failure("unparsed result", () -> {
//                        return result.grammarName() + " = " + result.grammarPath();
//                    });
//                } else {
//                    Debug.success("parsed", result.tokenCount() + " tokens");
//                }
                return result;
            });
        }

        @Override
        public AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body, int ruleNo) throws Exception {
            // XXX should have same retry logic; currently unused since there is no way to have
            // the starting parser rule not be the first one
            return clRun(() -> {
                return reflectively(typeName, new Class<?>[]{CharSequence.class, int.class}, body, ruleNo);
            });
        }

        @Override
        public AntlrProxies.ParseTreeProxy parse(String logName, CharSequence body, String ruleName) throws Exception {
            // XXX should have same retry logic; currently unused since there is no way to have
            // the starting parser rule not be the first one
            return clRun(() -> {
                return reflectively(typeName, new Class<?>[]{CharSequence.class, String.class}, body, ruleName);
            });
        }

        private static AntlrProxies.ParseTreeProxy reflectively(String cl,
                Class<?>[] params, Object... args)
                throws ClassNotFoundException, NoSuchMethodException,
                IllegalAccessException, IllegalArgumentException,
                InvocationTargetException {
            try {
                assert params.length == args.length;
//                ClassLoader ldr = Thread.currentThread().getContextClassLoader();
//                Class<?> c = ldr.loadClass(cl);
                Class<?> c = cachedClass(cl);
                Method m = c.getMethod("extract", params);
                return (AntlrProxies.ParseTreeProxy) m.invoke(null, args);
            } catch (ClassNotFoundException cnfe) {
                throw new ClassNotFoundException(Thread.currentThread().getContextClassLoader().toString(), cnfe);
            }
        }

        @Override
        public synchronized void onDiscard() {
            ref.run();
        }
    }

    static final Map<String, Map<ClassLoader, Class<?>>> typeForNameAndLoader
            = CollectionUtils.concurrentSupplierMap(() -> MapFactories.WEAK_KEYS_AND_VALUES.createMap(16, true));

    static Class<?> cachedClass(String name) throws ClassNotFoundException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Map<ClassLoader, Class<?>> map = typeForNameAndLoader.get(name);
        Class<?> result = map.get(cl);
        if (result == null) {
//            result = cl.loadClass(name);
            result = Class.forName(name, true, cl);
            map.put(cl, result);
        }
        return result;
    }
}

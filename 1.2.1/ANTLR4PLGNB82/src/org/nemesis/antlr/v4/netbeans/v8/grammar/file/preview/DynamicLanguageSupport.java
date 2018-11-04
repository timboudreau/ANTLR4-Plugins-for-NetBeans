package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.loggableMimeType;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.mimeTypeForPath;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrRunOption;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.InMemoryAntlrSourceGenerationBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParseProxyBuilder;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.grammarFilePathForMimeType;

/**
 *
 * @author Tim Boudreau
 */
public class DynamicLanguageSupport {

    private static final DynamicLanguageSupport INSTANCE = new DynamicLanguageSupport();
    static final Logger LOG = Logger.getLogger(DynamicLanguageSupport.class.getName());

    public static ParseTreeProxy proxyFor(String mimeType) {
        GrammarRegistration reg = registrations.get(mimeType);
        if (reg == null) {
            Path path = grammarFilePathForMimeType(mimeType);
            if (path != null) {
                FileObject fo = FileUtil.toFileObject(path.toFile());
                if (fo != null) {
                    reg = INSTANCE._registerGrammar(fo, null, true, null);
                    return reg.lastParseResult;
                }
            }
        }
        if (reg == null) {
            LOG.log(Level.WARNING, "Could not derive a grammar file path from mime type {0};"
                    + " known types are {1}",
                    new Object[]{loggableMimeType(mimeType), registrations.keySet()});
            return AntlrProxies.forUnparsed(Paths.get("/invalid-" + mimeType), mimeType, mimeType);
        }
        return reg.lastParseResult;
    }

    private static final Map<String, GrammarRegistration> registrations
            = new ConcurrentHashMap<>(20, 0.88f);

    private static Path pathFor(FileObject grammarFile) {
        File file = FileUtil.toFile(grammarFile);
        if (file != null) {
            Path path = file.getAbsoluteFile().toPath();
            return path;
        }
        return Paths.get(grammarFile.getPath()); // virtual file
    }

    public static boolean isRegistered(String mimeType) {
        return registrations.containsKey(mimeType);
    }

    private GrammarRegistration _registerGrammar(FileObject grammarFile, String initialText, boolean build, Consumer<String> statusMonitor) {
        if (Boolean.TRUE.equals(grammarFile.getAttribute("example"))) {
            return null;
        }
        if (!grammarFile.isValid() || !grammarFile.isData() || !grammarFile.canRead()) {
            return null;
        }
        Path path = pathFor(grammarFile);
        String mimeType = mimeTypeForPath(path);
        LOG.log(Level.FINE, "Register grammar {0} with initial text of {1} length build {2}"
                + " as mime type {3}",
                new Object[]{path, initialText == null ? 0 : initialText.length(), build, loggableMimeType(mimeType)});
        GrammarRegistration reg = registrations.get(mimeType);
        if (reg != null) {
            LOG.log(Level.FINE, "Registration already exists");
            return reg;
        }
        ParseTreeProxy proxy = AntlrProxies.forUnparsed(path, initialText, mimeType);
        return _registerGrammar(proxy, grammarFile, build, statusMonitor);
    }

    private GrammarRegistration _registerGrammar(ParseTreeProxy proxy, FileObject grammarFile, boolean build, Consumer<String> statusMonitor) {
        GrammarRegistration reg = new GrammarRegistration(grammarFile, proxy, build, statusMonitor);
        registrations.put(proxy.mimeType(), reg);
        return reg;
    }

    public static GrammarRegistration registerGrammar(FileObject fo) {
        return registerGrammar(mimeTypeForPath(pathFor(fo)), null, null);
    }

    public static GrammarRegistration registerGrammar(String mimeType, String text) {
        return registerGrammar(mimeType, text, null);
    }

    private static GrammarRegistration registerGrammar(String mimeType, String text, Consumer<String> statusMonitor) {
        GrammarRegistration result = registrations.get(mimeType);
        if (result == null) {
            Path grammarFile = grammarFilePathForMimeType(mimeType);
            if (grammarFile == null) {
                throw new IllegalStateException("No grammar file path in mime type '" + mimeType + "'");
            }
            FileObject fo = FileUtil.toFileObject(grammarFile.toFile());
            if (fo == null) {
                LOG.log(Level.SEVERE, "Grammar file does not exist: {0} from {1}",
                        new Object[]{grammarFile, loggableMimeType(mimeType)});
                return null;
            }
            return INSTANCE._registerGrammar(fo, text, text != null ? true : false, statusMonitor);
        }
        return result;
    }

    public static ParseTreeProxy parseImmediately(String mimeType, String text) {
        return parseImmediately(mimeType, text, null);
    }

    public static ParseTreeProxy parseImmediately(String mimeType, String text, Consumer<String> statusMonitor) {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, "parseImmediately ''{0}''", truncated(text));
        }
        return INSTANCE._parseImmediately(mimeType, text, statusMonitor);
    }

    private ParseTreeProxy _parseImmediately(String mimeType, String txt, Consumer<String> statusMonitor) {
        GrammarRegistration reg = registerGrammar(mimeType, txt);
        if (reg != null) {
            if (txt == null) {
                return reg.lastParseResult;
            } else {
                return reg.get(txt, statusMonitor);
            }
        } else {
            Path path = grammarFilePathForMimeType(mimeType);
            LOG.log(Level.SEVERE, "Grammar file does not exist: {0} - returning "
                    + "dummy parse result with all text in a single token", path);
            return AntlrProxies.forUnparsed(path, mimeType, txt == null ? "" : txt);
        }
    }

    private AdhocMimeDataProvider mimeData() {
        return Lookup.getDefault().lookup(AdhocMimeDataProvider.class);
    }

    private void updateParsingEnvironment(GenerateBuildAndRunGrammarResult buildResult, ParseTreeProxy lastParseResult) {
        LOG.log(Level.FINE, "Update parsing envioronment for {0}", loggableMimeType(lastParseResult.mimeType()));
        mimeData().updateMimeType(buildResult, lastParseResult);
        try {
            AdhocColoringsRegistry.getDefault().update(lastParseResult.mimeType());
        } catch (ParseException | IOException | BadLocationException ex) {
            LOG.log(Level.WARNING, "Exception updating colorings", ex);
        }
    }

    private void registerInParsingEnvironment(GenerateBuildAndRunGrammarResult buildResult, ParseTreeProxy parseResult) {
        mimeData().addMimeType(buildResult, parseResult);
    }

    public static Set<String> mimeTypes() {
        return Collections.unmodifiableSet(registrations.keySet());
    }

    public static Parser parser(String mimeType) {
        AdhocParserFactory pf = INSTANCE.mimeData().getLookup(mimeType).lookup(AdhocParserFactory.class);
        if (pf == null) {
            return null;
        }
        return pf.newParser();
    }

    private static final ThreadLocal<String> FORCED_LANGUAGE = new ThreadLocal<>();

    public static void forceLanguage(String lang, Runnable run) {
        String old = FORCED_LANGUAGE.get();
        try {
            FORCED_LANGUAGE.set(lang);
            run.run();
        } finally {
            if (old == null) {
                FORCED_LANGUAGE.remove();
            } else {
                FORCED_LANGUAGE.set(old);
            }
        }
    }

    static String currentMimeType(String passed) {
        String result = FORCED_LANGUAGE.get();
        return result == null ? passed : result;
    }

    public static ParseTreeProxy lastParseResult(String mimeType, String text) {
        GrammarRegistration reg = registrations.get(mimeType);
        if (reg != null) {
            ParseTreeProxy last = reg.lastParseResult;
            if (last.text().equals(text)) {
                return last;
            } else {
                return reg.get(text, null);
            }
        }
        return null;
    }

    public static GenerateBuildAndRunGrammarResult lastBuildResult(String mimeType, String text) {
        GrammarRegistration reg = registrations.get(mimeType);
        if (reg != null) {
            GenerateBuildAndRunGrammarResult last = reg.lastBuildResult;
            if (last.text().equals(text)) {
                return last;
            } else {
                reg.get(text, null);
                return reg.lastBuildResult;
            }
        }
        return null;
    }

    static String truncated(String txt) {
        txt = txt.trim().replace('\n', ' ');
        int len = txt.length();
        if (len > 20) {
            txt = txt.substring(0, 20) + "...";
        }
        return txt;
    }

    static final class GrammarRegistration {

        private ParseTreeProxy lastParseResult;
        final ParseProxyBuilder runner;
        private final FileObject grammarFile;
        GenerateBuildAndRunGrammarResult lastBuildResult;

        GrammarRegistration(FileObject fo, ParseTreeProxy prox, boolean enqueueCompile, Consumer<String> statusMonitor) {
            lastParseResult = prox;
            Path path = pathFor(fo);
            this.grammarFile = fo;
            Optional<Path> imports = AntlrFolders.IMPORT.getPath(ProjectHelper.getProject(path), Optional.of(path));
            runner = InMemoryAntlrSourceGenerationBuilder.forAntlrSource(path)
                    //                    .withAntlrLibrary(library)
                    .withImportDir(imports)
                    .withRunOptions(AntlrRunOption.GENERATE_LEXER, AntlrRunOption.GENERATE_VISITOR)
                    .toParseAndRunBuilder();
            if (prox.isUnparsed()) {
                get(null, null);
            }
            INSTANCE.registerInParsingEnvironment(lastBuildResult, lastParseResult);
        }

        public ParseTreeProxy get(String text, Consumer<String> statusMonitor) {
            Path path = pathFor(grammarFile);
            GenerateBuildAndRunGrammarResult parse = null;
            try {
                parse = lastBuildResult = runner.parse(text, statusMonitor);
                LOG.log(Level.FINER, "Result of parse {0} usable {1}", new Object[]{loggableMimeType(lastParseResult.mimeType()),
                    parse.isUsable()});
                LOG.log(Level.FINEST, "Gen/build/parse result {0}", parse);
//                System.out.println("\n*************** PARSE RESULT *****************");
//                System.out.println(parse);
//                System.out.println("***********************************************\n");
                if (parse.parseResult().isPresent()) {
                    if (parse.parseResult().get().parseTree().isPresent()) {
                        ParseTreeProxy result = parse.parseResult().get().parseTree().get();
                        LOG.log(Level.FINE, "Parse result {0}", result.summary());
//                        update(result, parse.wasCompiled());
                        update(parse, result, true);
                        return result;
                    }
                }
                if (parse.thrown().isPresent()) {
                    LOG.log(Level.INFO, "Error building/parsing {0}: {1}",
                            new Object[]{grammarFile.getPath(), parse.thrown().get()});
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            LOG.log(Level.INFO, "Error building/parsing {0}; using dummy result",
                    new Object[]{grammarFile.getPath()});
            ParseTreeProxy result = AntlrProxies.forUnparsed(path, ParseTreeCache.grammarName(path), text);
            update(parse == null ? lastBuildResult : parse, result, true); // XXX could check if previous result was same and thrash less
            return result;
        }

        private synchronized void update(GenerateBuildAndRunGrammarResult buildResult, ParseTreeProxy res, boolean env) {
            lastParseResult = res;
            if (env) {
                INSTANCE.updateParsingEnvironment(buildResult, lastParseResult);
            }
        }

    }

    /*
    static final class GrammarRegistration extends FileChangeAdapter {

        private final FileObject grammarFile;
        private final ParseProxyBuilder runner;
        private ParseTreeProxy lastParseResult;
        private final ParseTreeCache cache;
        private static final int EXPIRE_LIVE = 60000;
        private static final int EXPIRE_OBSOLETE = 20000;

        GrammarRegistration(FileObject fo, ParseTreeProxy prox, boolean enqueueCompile, Consumer<String> statusMonitor) {
            this.grammarFile = fo;
            this.lastParseResult = prox;
            fo.addFileChangeListener(FileUtil.weakFileChangeListener(this, fo));
            Path path = pathFor(fo);
            Optional<Path> imports = AntlrFolders.IMPORT.getPath(ProjectHelper.getProject(path), Optional.of(path));
//            AntlrLibrary library = AntlrLibrary.forOwnerOf(path);
            runner = InMemoryAntlrSourceGenerationBuilder.forAntlrSource(path)
                    //                    .withAntlrLibrary(library)
                    .withImportDir(imports)
                    .withRunOptions(AntlrRunOption.GENERATE_LEXER, AntlrRunOption.GENERATE_VISITOR)
                    .toParseAndRunBuilder();
            this.cache = new ParseTreeCache(pathFor(fo), EXPIRE_OBSOLETE, EXPIRE_LIVE,
                    RequestProcessor.getDefault(), this::_build);
            if (enqueueCompile) {
                lastParseResult = cache.get(lastParseResult.text(), statusMonitor);
            }
            INSTANCE.registerInParsingEnvironment(lastParseResult);
        }

        private ParseTreeProxy _build(String txt, Consumer<String> statusMonitor) {
            Path path = pathFor(grammarFile);
            System.out.println("ENTER BUILD FOR " + path);
            try {
                GenerateBuildAndRunGrammarResult parse = runner.parse(txt, statusMonitor);
                LOG.log(Level.FINER, "Result of parse {0} usable {1}", new Object[]{loggableMimeType(lastParseResult.mimeType()),
                    parse.isUsable()});
                System.out.println("\n*************** PARSE RESULT *****************");
                System.out.println(parse);
                System.out.println("***********************************************\n");
                if (parse.parseResult().isPresent()) {
                    if (parse.parseResult().get().parseTree().isPresent()) {
                        ParseTreeProxy result = parse.parseResult().get().parseTree().get();
                        LOG.log(Level.FINE, "Parse result {0}", result.summary());
                        update(result, parse.wasCompiled());
                        return result;
                    }
                }
                System.out.println("Did not have a parse tree in it.");
                if (parse.thrown().isPresent()) {
                    LOG.log(Level.INFO, "Error building/parsing {0}: {1}",
                            new Object[]{grammarFile.getPath(), parse.thrown().get()});
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            LOG.log(Level.INFO, "Error building/parsing {0}; using dummy result",
                    new Object[]{grammarFile.getPath()});
            ParseTreeProxy result = AntlrProxies.forUnparsed(path, ParseTreeCache.grammarName(path), txt);
            update(result, true); // XXX could check if previous result was same and thrash less
            return result;
        }

        public ParseTreeProxy getIfAvailable(String text) {
            ParseTreeProxy res = cache.getIfPresent(text);
            return res == null && text.equals(lastParseResult.text()) ? lastParseResult
                    : res;
        }

        public ParseTreeProxy get(String text, Consumer<String> statusMonitor) {
            System.out.println("calling cache.get() for '" + truncated(text) + "'");
            return cache.get(text, statusMonitor);
        }

        @Override
        public void fileChanged(FileEvent fe) {
            LOG.log(Level.FINER, "File change in {0} triggering recompile of {1}",
                    new Object[]{fe.getFile().getPath(), loggableMimeType(lastParseResult.mimeType())});
            runner.regenerateAntlrCodeOnNextCall();
            cache.get(lastParseResult.text(), null);
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            fe.getFile().removeFileChangeListener(this);
            INSTANCE.mimeData().removeMimeType(lastParseResult.mimeType());
            registrations.remove(lastParseResult.mimeType());
        }

        private synchronized void update(ParseTreeProxy res, boolean env) {
            lastParseResult = res;
            if (env) {
                INSTANCE.updateParsingEnvironment(lastParseResult);
            }
        }

        private ParseTreeProxy setResult(GenerateBuildAndRunGrammarResult parse) {
            LOG.log(Level.FINER, "Result of parse {0} usable {1}", new Object[]{loggableMimeType(lastParseResult.mimeType()),
                parse.isUsable()});
            ParseTreeProxy[] results = new ParseTreeProxy[1];
            boolean succeeded = parse.onSuccess(new ParseConsumer() {
                @Override
                public void accept(AntlrSourceGenerationResult genResult, CompileResult compileResult, ParserRunResult parserRunResult) {
                    if (parserRunResult == null) {
                        // Was a compile, no text was passed - preview initialization
                        LOG.log(Level.FINE, "Missing results gen={0}, compile={1}, parse={2}", new Object[]{genResult, compileResult, parserRunResult});
                        return;
                    }
                    LOG.log(Level.FINE, "Parse result {0}", parserRunResult.parseTree().isPresent() ? parserRunResult.parseTree().get().summary() : "-");
                    if (parserRunResult.parseTree().isPresent()) {
                        ParseTreeProxy result = parserRunResult.parseTree().get();
                        update(result, parse.wasCompiled());
                        results[0] = result;
                    } else {
                        update(results[0] = AntlrProxies.forUnparsed(pathFor(grammarFile),
                                parse.generationResult().grammarName(), parse.text()), true);
                    }
                }
            });
            if (!succeeded) {
                if (parse.thrown().isPresent()) {
                    LOG.log(Level.INFO, "Error building/parsing {0}: {1}",
                            new Object[]{grammarFile.getPath(), parse.thrown().get()});
                }
                ParseTreeProxy res = AntlrProxies.forUnparsed(pathFor(grammarFile),
                        parse.generationResult().grammarName(), parse.text());
                update(res, false);
                results[0] = res;
            }
            return results[0];
        }
    }
     */
}

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
package org.nemesis.antlr.memory.tool;

import org.nemesis.antlr.memory.tool.epsilon.EpsilonAnalysis;
import org.nemesis.antlr.memory.tool.ext.EpsilonRuleInfo;
import com.mastfrog.util.path.UnixPath;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaFileManager.Location;
import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.misc.IntArray;
import org.antlr.runtime.tree.Tree;
import org.antlr.v4.Tool;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ErrorManager;
import org.antlr.v4.tool.ErrorSeverity;
import org.antlr.v4.tool.ErrorType;
import static org.antlr.v4.tool.ErrorType.CANNOT_FIND_TOKENS_FILE_REFD_IN_GRAMMAR;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.GrammarTransformPipeline;
import org.antlr.v4.tool.LexerGrammar;
import org.antlr.v4.tool.ast.GrammarAST;
import org.antlr.v4.tool.ast.GrammarRootAST;
import static org.nemesis.antlr.memory.tool.ToolContext.currentFile;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.stringtemplate.v4.ST;

/**
 *
 * @author Tim Boudreau
 */
public final class MemoryTool extends Tool {

    private static final String OUTPUT_WINDOW_FRIENDLY_ERROR_FORMAT = "vs2005";
    private final boolean initialized;
    private List<EpsilonRuleInfo> epsilonIssues;

    MemoryTool(ToolContext ctx, String... args) {
        super(args);
        if (ctx.jfs.get(ctx.inputLocation, ctx.dir) != null) {
            throw new IllegalArgumentException("Input directory is actually "
                    + "a JFS file, not its parent: " + ctx.dir);
        }
        // We cannot serialize UnixPath, or ST4's Interpreter will
        // go into an endless loop
        this.msgFormat = OUTPUT_WINDOW_FRIENDLY_ERROR_FORMAT;
        this.errMgr = new ErrM(this);
        errMgr.setFormat(msgFormat);
        grammarEncoding = ctx.jfs.encoding().name();
        initialized = true;
    }

    void initLog(ToolContext ctx) {
        if (ctx.logStream != null) {
            ctx.logStream.println("Init " + getClass().getName() + " " + versionInfo());
            ctx.logStream.println("Over JFS with\n" + list());
        }
    }

    public static MemoryTool create(UnixPath dir, JFS jfs, Location inputLocation, Location outputLocation, PrintStream logStream, String... args) {
        return ToolContext.create(dir, jfs, inputLocation, outputLocation, logStream, args);
    }

    private UnixPath dir() {
        return ToolContext.get(this).dir;
    }

    public void error(ANTLRMessage msg) {
        // Avoid the tool logging about non existent directories in the
        // super constructor when we are not actually using the filesystem
        if (initialized) {
            Path currFile = currentFile.get();
            if (currFile != null) {
                // Ensure error messages have the complete (relative)
                // file path, so we can map them easily in error highlighting
                msg.fileName = currFile.toString();
            }
            super.error(msg);
        }
    }

    public static String versionInfo() {
        StringBuilder sb = new StringBuilder(VERSION)
                .append(" (").append(Tool.class.getProtectionDomain().getCodeSource().getLocation())
                .append(", ").append(RuntimeMetaData.class.getProtectionDomain().getCodeSource().getLocation())
                .append(", ").append(IntArray.class.getProtectionDomain().getCodeSource().getLocation())
                .append(",");

        return sb.toString();
    }

    JFS jfs() {
        return ToolContext.get(this).jfs;
    }

    private int errorsFilledIn = -1;

    public List<ParsedAntlrError> errors() {
        List<ParsedAntlrError> result = new ArrayList<>(((ErrM) this.errMgr).errors);
        if (errorsFilledIn != result.size()) {
            fillInErrors(result);
            convertEpsilonIssuesToErrors(result);
            errorsFilledIn = result.size();
        }
        Collections.sort(result);
        if (!result.isEmpty()) {
            result = coalesceSyntaxErrors(result);
        }
        return result;
    }

    private List<ParsedAntlrError> coalesceSyntaxErrors(List<ParsedAntlrError> errors) {
        // Antlr will emit individual syntax errors for each character in a sequence
        // of characters that are not letters or numbers - better to coalesce them
        // into a single error for saner error handling
        List<ParsedAntlrError> result = new ArrayList<>();
        ParsedAntlrError prev = errors.get(0);
        result.add(prev);
        for (int i = 1; i < errors.size(); i++) {
            ParsedAntlrError curr = errors.get(i);
            if (curr.code() == 50 && prev.code() == curr.code() && prev.lineNumber() == curr.lineNumber() && prev.lineOffset() + prev.length() == curr.lineOffset() && prev.path().equals(curr.path())) {
                result.remove(result.size() - 1);
//                System.out.println("coalesce " + prev + " and " + curr);
                ParsedAntlrError nue = new ParsedAntlrError(prev.isError() || curr.isError(),
                        curr.code(), curr.path(), curr.lineNumber(), prev.lineOffset(), "Syntax error");
                if (curr.hasFileOffset() && prev.hasFileOffset()) {
                    int start = prev.fileOffset();
                    int end = curr.endOffset();
                    nue.setFileOffsetAndLength(start, end - start);
                }
                result.add(nue);
                prev = nue;
            } else {
                result.add(curr);
                prev = curr;
            }
        }
        return result;
    }

    boolean hasEpsilonIssues() {
        return ((ErrM) this.errMgr).hasEpsilonIssues;
    }

    private void convertEpsilonIssuesToErrors(List<ParsedAntlrError> errors) {
//        System.out.println("HAVE " + (epsilonIssues == null ? "null" : epsilonIssues.size()) + " EPSILON ISSUES");
        if (epsilonIssues != null) {
            boolean hasSelfViolations = false;
            boolean anyConverted = false;
            Collections.sort(epsilonIssues);
            Set<String> seen = new HashSet<>();
            Path path = null;
            for (EpsilonRuleInfo e : epsilonIssues) {
                boolean isSelfViolation = e.isSelfViolation();
                hasSelfViolations |= isSelfViolation;
                if (!isSelfViolation) {
                    String key = e.victimRuleName() + ":" + e.culpritRuleName();
                    if (seen.contains(key)) {
//                        System.out.println("  Seen - skip " + e);
                        continue;
                    }
                    seen.add(key);
                    anyConverted = true;
                    for (Iterator<ParsedAntlrError> it = errors.iterator(); it.hasNext();) {
                        ParsedAntlrError pae = it.next();
                        if (path == null) {
                            path = pae.path();
                        }
                        if (pae.code() == e.kind().antlrErrorCode()
                                && e.victimLine() == pae.lineNumber()
                                && e.victimLineOffset() == pae.lineOffset()) {
                            it.remove();
                        }
                    }
                    ParsedAntlrError replacement
                            = new ParsedAntlrError(false, e.kind().antlrErrorCode(),
                                    path, e.victimLine(), e.victimLineOffset(),
                                    e.victimErrorMessage());

                    replacement.setInfo(e);
                    replacement.setFileOffsetAndLength(e.victimStart(), e.victimEnd() - e.victimStart());

                    errors.add(replacement);

                    ParsedAntlrError culpritError
                            = new ParsedAntlrError(true, e.kind().antlrErrorCode(),
                                    path, e.culpritLine(), e.culpritLineOffset(),
                                    e.culpritErrorMessage());

                    culpritError.setFileOffsetAndLength(e.culpritStart(),
                            e.culpritEnd() - e.culpritStart());

                    errors.add(culpritError);
                } else {
                    String key = e.victimRuleName() + ":" + e.culpritRuleName();
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);
                    anyConverted = true;
                    for (Iterator<ParsedAntlrError> it = errors.iterator(); it.hasNext();) {
                        ParsedAntlrError pae = it.next();
                        if (path == null) {
                            path = pae.path();
                        }
                        if (pae.code() == e.kind().antlrErrorCode()
                                && e.victimLine() == pae.lineNumber()
                                && e.victimLineOffset() == pae.lineOffset()) {
                            it.remove();
                        }
                    }
                    ParsedAntlrError replacement
                            = new ParsedAntlrError(false, e.kind().antlrErrorCode(),
                                    path, e.victimLine(), e.victimLineOffset(),
                                    e.culpritErrorMessage());
                    errors.add(replacement);
                    replacement.setInfo(e);
                    replacement.setFileOffsetAndLength(e.culpritStart(),
                            e.culpritEnd() - e.culpritStart());
                }
            }
            if (!anyConverted && hasSelfViolations) {
                Collections.sort(errors);
            }
        }
    }

    private List<String> customLineSplit(CharSequence seq) {
        List<String> result = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int max = seq.length();
        for (int i = 0; i < max; i++) {
            char c = seq.charAt(i);
            currentLine.append(c);
            if (c == '\n') {
                result.add(currentLine.toString());
                currentLine.setLength(0);
            }
        }
        return result;
    }

    Location inputLocation() {
        return ToolContext.get(this).inputLocation;
    }

    Location outputLocation() {
        return ToolContext.get(this).outputLocation;
    }

    private List<String> lines(Path path) {
        Set<LoadAttempt> attempts = new HashSet<>();
        JFSFileObject fo = getFO(inputLocation(), UnixPath.get(path), attempts, ToolContext.get(this));
        if (fo != null) {
            try {
                CharSequence content = fo.getCharContent(true);
                return customLineSplit(content);
//                return Arrays.asList(Pattern.compile("\r?\n").split(content));
            } catch (IOException ex) {
                Logger.getLogger(MemoryTool.class.getName()).log(Level.INFO,
                        "Failed reading " + path, ex);
            }
        }
        return Collections.emptyList();
    }

    private void fillInErrors(List<ParsedAntlrError> l) {
        ParsedAntlrError.computeFileOffsets(l, this::lines);
    }

    public List<String> infoMessages() {
        return ((ErrM) this.errMgr).infos;
    }

    @Override
    public void log(String msg) {
        // do nothing
//        ToolContext ctx = ToolContext.get(this);
//        if (ctx.logStream != null) {
//            ctx.logStream.println(msg);
//        }
    }

    @Override
    public void log(String component, String msg) {
        ToolContext ctx = ToolContext.get(this);
        if (ctx.logStream != null) {
            ctx.logStream.println(component + ": " + msg);
        }
    }

    @Override
    public File getOutputDirectory(String fileNameWithPath) {
        return fileNameWithPath == null
                ? new File(System.getProperty("java.io.tmpdir"))
                : new File(fileNameWithPath);
    }

    UnixPath resolveRelativePath(UnixPath rel) {
        return dir().resolve(rel);
    }

    @Override
    public Writer getOutputFileWriter(Grammar g, String fileName) throws IOException {
        ToolContext ctx = ToolContext.get(this);
        Path pth = ctx.dir.resolve(fileName);
        // PENDING:  In Maven, .tokens file always end up in the default package.
        // Do that here?
        JFSFileObject fo = ctx.jfs.getSourceFileForOutput(pth.toString(),
                ctx.outputLocation);
        return fo.openWriter();
    }

    void withCurrentPath(Path path, Runnable r) {
        Path old = currentFile.get();
        try {
            currentFile.set(path);
            r.run();
        } finally {
            currentFile.set(old);
        }
    }

    public void withCurrentPathThrowing(Path path, Thrower r) throws IOException {
        Path old = currentFile.get();
        try {
            currentFile.set(path);
            r.run();
        } finally {
            currentFile.set(old);
        }
    }

    public <T> T withCurrentPath(Path path, Supplier<T> supp) {
        Path old = currentFile.get();
        try {
            currentFile.set(path);
            return supp.get();
        } finally {
            currentFile.set(old);
        }
    }

    public <T> T withCurrentPathThrowing(Path path, IOSupp<T> supp) throws IOException {
        Path old = currentFile.get();
        try {
            currentFile.set(path);
            return supp.get();
        } finally {
            currentFile.set(old);
        }
    }

    public List<EpsilonRuleInfo> epsilonIssues() {
        return epsilonIssues == null ? Collections.emptyList() : epsilonIssues;
    }

    public Grammar loadGrammar(String fileName, Consumer<JFSFileObject> c) {
        ToolContext ctx = ToolContext.get(this);
        JFSFileObject fo = ctx.jfs.get(ctx.inputLocation, UnixPath.get(fileName));
        Set<LoadAttempt> attemptedPaths = new HashSet<>(3);
        if (fo == null) {
            fo = getFO(ctx.inputLocation, dir().resolve(fileName), attemptedPaths, ctx);
        }
        if (fo == null) {
            fo = getFO(ctx.inputLocation, dir(), attemptedPaths, ctx);
        }
        if (fo != null) {
            JFSFileObject finalFile = fo;
            Grammar result = withCurrentPath(UnixPath.get(fo.getName()), () -> {
                c.accept(finalFile);
                return readOneGrammar(finalFile, fileName, ctx);
            });
            ErrM errm = (ErrM) this.errMgr;
            if (errm.hasEpsilonIssues) {
                result.originalTokenStream.seek(0);
                result.originalTokenStream.getTokenSource();
                List<EpsilonRuleInfo> issues = EpsilonAnalysis.analyze(result);
                for (EpsilonRuleInfo epsilonIssue : issues) {
                    String implicitLexerGrammarName = result.implicitLexer != null
                            ? result.implicitLexer.name : null;
                    if (epsilonIssue.grammarName().equals(result.name) || epsilonIssue.grammarName().equals(implicitLexerGrammarName)) {
                        if (epsilonIssues == null) {
                            epsilonIssues = new ArrayList<>(issues.size());
                        }
                        epsilonIssues.add(epsilonIssue);
//                    } else {
//                        System.out.println("WRONG GRAMMAR: " + epsilonIssue
//                                + " grammar name " + epsilonIssue.grammarName());
                    }
                }
            }
            return result;
        } else {
            log("Could not load primary grammar " + fileName + " from any of " + attemptedPaths);
        }
        return null;
    }

    public Grammar readOneGrammar(JFSFileObject finalFile, String fileName, ToolContext ctx) {
        try {
            CharSequence seq;
            try {
                seq = finalFile.getCharContent(true);
            } catch (ClosedByInterruptException ex) {
                if (ctx.logStream != null) {
                    ex.printStackTrace(ctx.logStream);
                }
                Thread.interrupted();
                seq = finalFile.getCharContent(true);
            }
            CharSequenceCharStream str = new CharSequenceCharStream(seq);
            return doReadOneGrammar(finalFile, fileName, ctx, str);
        } catch (IOException oi) {
            oi.printStackTrace();
            errMgr.toolError(ErrorType.CANNOT_OPEN_FILE, oi, fileName);
            return null;
        }
    }

    private Grammar doReadOneGrammar(JFSFileObject finalFile, String fileName, ToolContext ctx, org.antlr.runtime.CharStream stream) throws IOException {
        GrammarRootAST grammarRootAST = parse(fileName, stream);
        final Grammar g = createGrammar(grammarRootAST);
        if (g != null) {
            g.fileName = fileName;
            log("Loaded primary grammar " + g.name + " file "
                    + g.fileName + " from " + ctx.inputLocation
                    + ":" + finalFile);
            process(g, false);
        }
        return g;
    }

    @Override
    public Grammar loadGrammar(String fileName) {
        return loadGrammar(fileName, ignored -> {
        });
    }

    private Map<String, Grammar> importedGrammars = null;

    void noteImportedGrammar(String name, Grammar grammar) {
        Map<String, Grammar> all = importedGrammars();
        all.put(name, grammar);
    }

    public Collection<? extends Grammar> allGrammars() {
        try {
            return importedGrammars().values();
        } catch (IllegalStateException ex) {
            Logger.getLogger(MemoryTool.class.getName()).log(Level.WARNING, "Failed to look up imported grammars", ex);
            return Collections.emptySet();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Grammar> importedGrammars() {
        if (importedGrammars != null) {
            return importedGrammars;
        }
        try {
            Field f = Tool.class.getDeclaredField("importedGrammars");
            f.setAccessible(true);
            return importedGrammars = (Map<String, Grammar>) f.get(this);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            throw new IllegalStateException("Could not look up imported grammars field", ex);
        }
    }

    public JFSFileObject getImportedGrammarFileObject(Grammar g, String fileName) {
        ToolContext ctx = ToolContext.get(this);
        Set<LoadAttempt> failures = new HashSet<>(7);
        JFSFileObject result = getImportedGrammarFileObject(g, fileName, ctx.inputLocation, failures, ctx);
        if (result == null) {
            result = getImportedGrammarFileObject(g, fileName, ctx.outputLocation, failures, ctx);
        }
        if (result == null) {
            log("Could not find imported " + fileName + " from " + g.name + " in any of "
                    + failures);
        }
        return result;
    }

    private JFSFileObject getImportedGrammarFileObject(Grammar g, String fileName, Location loc, Set<? super LoadAttempt> failures, ToolContext ctx) {
        JFSFileObject fo = ctx.jfs.get(loc, ctx.dir.resolve(fileName));
        if (fo == null) {
            UnixPath nxt = UnixPath.get(fileName);
            fo = ctx.jfs.get(loc, nxt);
            if (fo == null && nxt.getParent() != null) {
                fo = getFO(loc, nxt.getParent().resolve(fileName), failures, ctx);
            }
            if (fo == null) {
                fo = getFO(loc, UnixPath.get("import").resolve(fileName), failures, ctx);
            }
            if (fo == null) {
                fo = getFO(loc, UnixPath.get("imports").resolve(fileName), failures, ctx);
            }
            if (fo == null) {
                fo = getFO(loc, UnixPath.get("import").resolve(g.fileName), failures, ctx);
            }
            if (fo == null) {
                fo = getFO(loc, UnixPath.get("imports").resolve(g.fileName), failures, ctx);
            }
            if (fo == null && libDirectory != null) {
                fo = getFO(loc, UnixPath.get(libDirectory).resolve(g.fileName), failures, ctx);
            }
        }
        if (fo != null) {
            log("Loaded imported " + fileName + " from " + g.name + " as " + loc + ":" + fo);
        }
        return fo;
    }

    JFSFileObject getFO(Location loc, UnixPath path, Set<? super LoadAttempt> attempted, ToolContext ctx) {
        JFSFileObject fo = ctx.jfs.get(loc, path);
        if (fo == null) {
            attempted.add(new LoadAttempt(path, loc));
//            log("Failed loading " + path + " from " + loc);
        } else {
            log("Loaded " + path + " as " + fo + " from " + loc);
        }
        return fo;
    }

    private String list() {
        ToolContext ctx = ToolContext.get(this);
        StringBuilder sb = new StringBuilder("Input location: " + ctx.inputLocation + "\n")
                .append("Output location: ").append(ctx.outputLocation)
                .append('\n');
        for (Map.Entry<JFSFileObject, Location> e : ctx.jfs.listAll().entrySet()) {
            sb.append(e.getValue()).append('\t').append(e.getKey().getName()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public Grammar loadImportedGrammar(Grammar g, GrammarAST nameNode) throws IOException {
        String name = nameNode.getText();
        if (name.equals(g.name)) {
            // Avoid a StaackOverflowError if the grammar imports itself
            this.errMgr.emit(ErrorType.INVALID_IMPORT, new ANTLRMessage(ErrorType.INVALID_IMPORT, nameNode.token, name, name));
            return null;
        }
        Grammar imported = importedGrammars().get(name);
        if (imported == null) {
            g.tool.log("grammar", "load " + name + " from " + g.fileName);
            JFSFileObject importedFile = null;
            for (String extension : ALL_GRAMMAR_EXTENSIONS) {
                importedFile = getImportedGrammarFileObject(g, name + extension);
                if (importedFile != null) {
                    break;
                }
            }
            if (importedFile != null) {
                JFSFileObject fo = importedFile;
                return withCurrentPathThrowing(UnixPath.get(importedFile.getName()), () -> {
                    try (Reader reader = fo.openReader(true)) {
                        org.antlr.runtime.CharStream charStream = new org.antlr.runtime.ANTLRReaderStream(reader, fo.length(), 512);
                        GrammarRootAST grammarRootAST = parse(fo.getName(), charStream);
                        final Grammar gg = createGrammar(grammarRootAST);
                        if (gg != null) {
                            gg.fileName = Paths.get(fo.getName()).getFileName().toString();
                            process(gg, false);
                        }
                        return gg;
                    }
                });
            } else {
                errMgr.grammarError(ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR, g.fileName, nameNode.getToken(), name);
            }
        }
        return imported;
    }

    public Grammar loadDependentGrammar(String name, JFSFileObject fo) throws IOException {
        return withCurrentPathThrowing(Paths.get(fo.getName()), () -> {
            try (InputStream inStream = fo.openInputStream()) {
                ANTLRInputStream in = new ANTLRInputStream(inStream, fo.length());
                GrammarRootAST root = parse(name, in);
                Grammar result = createGrammar(root);
                result.fileName = name;
                return result;
            }
        });
    }

    public Grammar createGrammar(GrammarRootAST ast) {
        final Grammar g;
        if (ast == null) { // no such source inputLocation or file
            return null;
        }
        ast = elide(ast);
        if (ast.grammarType == ANTLRParser.LEXER) {
            g = new LexerGrammar(this, ast);
        } else {
            g = new AlternateTokenLoadingGrammar(this, ast);
        }
        // ensure each node has pointer to surrounding grammar
        GrammarTransformPipeline.setGrammarPtr(g, ast);
        return g;
    }

    private GrammarRootAST elide(GrammarRootAST ast) {
        GrammarRootAST result = new GrammarASTElider(MemoryTool::shouldElide).elide(ast);
        if (result == null) {
            throw new IllegalStateException("Elider produced null result");
        }
        return result;
    }

    private static boolean shouldElide(Tree tree) {
        return tree instanceof GrammarAST && "returns".equals(tree.getText());
    }

    @Override
    public void panic() {
        throw AttemptedExit.panic();
    }

    @Override
    public void exit(int exitCode) {
        throw new AttemptedExit(exitCode);
    }

    public static int attemptedExitCode(Throwable thrown) {
        int result = 0;
        if (thrown instanceof AttemptedExit) {
            result = ((AttemptedExit) thrown).code();
        } else if (thrown != null && thrown.getCause() != null) {
            if (thrown.getCause() instanceof AttemptedExit) {
                result = ((AttemptedExit) thrown.getCause()).code();
            }
        }
        return result;
    }

    static final class AttemptedExit extends RuntimeException {

        private final int code;

        AttemptedExit(int code) {
            super(code == -1 ? "Antlr panic" : "Attempted exit with " + code);
            this.code = code;
        }

        public static AttemptedExit panic() {
            return new AttemptedExit(-1);
        }

        public boolean isPanic() {
            return code == -1;
        }

        public int code() {
            return code;
        }
    }

    static final class ErrM extends ErrorManager {

        private final List<ParsedAntlrError> errors = new ArrayList<>(13);
        private final List<String> infos = new ArrayList<>(3);
        boolean hasEpsilonIssues;

        public ErrM(Tool tool) {
            super(tool);
        }

        private String replaceWithPath(String name) {
            Path p = currentFile.get();
            if (p != null) {
                return p.toString();
            }
            return name;
        }

        @Override
        public void grammarError(ErrorType etype, String fileName, Token token, Object... args) {
            super.grammarError(etype, replaceWithPath(fileName), token, args);
        }

        @Override
        public void syntaxError(ErrorType etype, String fileName, Token token, RecognitionException antlrException, Object... args) {
            super.syntaxError(etype, replaceWithPath(fileName), token,
                    antlrException, args);
        }

        private String toString(ANTLRMessage msg) {
            ST msgST = getMessageTemplate(msg);
            String outputMsg = msgST.render();
            if (tool.errMgr.formatWantsSingleLineMessage()) {
                outputMsg = outputMsg.replace('\n', ' ');
            }
            return outputMsg;
        }

        @Override
        public void emit(ErrorType etype, ANTLRMessage msg) {
            switch (etype) {
                case EPSILON_TOKEN:
                case EPSILON_CLOSURE:
                case EPSILON_OPTIONAL:
                case EPSILON_LR_FOLLOW:
                    hasEpsilonIssues = true;
                    break;
            }
            if (etype == CANNOT_FIND_TOKENS_FILE_REFD_IN_GRAMMAR) {
                new Exception("Bingo " + msg).printStackTrace();
            }
            msg.fileName = replaceWithPath(msg.fileName);
            Path supplied = currentFile.get();
            if (msg.fileName == null && supplied == null || (msg.fileName != null && !msg.fileName.contains("/"))) {
                new Exception("No qualified filename in " + msg.fileName + " - " + msg.getClass().getName()
                        + " - " + msg).printStackTrace();
            }
            Path pth = msg.fileName == null ? supplied == null
                    ? UnixPath.get("_no-file_") : supplied : UnixPath.get(msg.fileName);
            int charPositionInLine = msg.charPosition;
            int line = msg.line;
            int startOffset = -1;
            int stopOffset = -1;
            if (msg.offendingToken instanceof CommonToken) {
                CommonToken ct = (CommonToken) msg.offendingToken;
                startOffset = ct.getStartIndex();
                stopOffset = ct.getStopIndex();
            } else if (msg.offendingToken instanceof org.antlr.runtime.CommonToken) {
                org.antlr.runtime.CommonToken ct = (org.antlr.runtime.CommonToken) msg.offendingToken;
                startOffset = ct.getStartIndex();
                stopOffset = ct.getStopIndex();
            } else if (msg.offendingToken != null) {
                String txt = msg.offendingToken.getText();
                if (txt != null) {
                    startOffset = msg.offendingToken.getInputStream().index();
                    stopOffset = startOffset + txt.length() - 1;
                }
            }
            boolean isError = etype.severity == ErrorSeverity.ERROR
                    || etype.severity == ErrorSeverity.ERROR_ONE_OFF
                    || etype.severity == ErrorSeverity.FATAL;

            String message = toString(msg);
            Matcher m = ERRNUM.matcher(message);
            if (m.find() && m.group(1).length() > 0) {
                message = m.group(1);
            }
            ParsedAntlrError pae = new ParsedAntlrError(isError, etype.code, pth, line, charPositionInLine, message);
            if (startOffset != -1 && stopOffset != -1) {
                pae.setFileOffsetAndLength(startOffset, (stopOffset - startOffset) + 1);
            }
            errors.add(pae);
            super.emit(etype, msg);
        }

        // 'nbantlr/xtr175w/sesjzw8xtoe/run1/Rust.g4(19,0) : error 51 : rule compilation_unit redefinition; previous at line 16'
        static final Pattern ERRNUM = Pattern.compile("^.*\\(\\d+,\\d+\\)\\s+:\\s+error \\d+\\s+:\\s+(\\S.*)$", Pattern.DOTALL);

        @Override
        public void info(String msg) {
            infos.add(msg);
            super.info(msg);
        }

        @Override
        public void resetErrorState() {
            errors.clear();
            infos.clear();
            super.resetErrorState();
        }
    }

    /**
     * We could use mastfrog IOSupplier, but we want to limit the number of
     * classes that need to be exposed through the isolating classloader.
     *
     * @param <T>
     */
    public interface IOSupp<T> {

        public T get() throws IOException;
    }

    public interface Thrower {

        void run() throws IOException;
    }
}

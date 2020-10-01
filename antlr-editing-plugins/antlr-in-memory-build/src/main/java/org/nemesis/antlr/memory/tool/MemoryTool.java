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

import com.mastfrog.function.throwing.ThrowingFunction;
import org.nemesis.antlr.memory.tool.epsilon.EpsilonAnalysis;
import org.nemesis.antlr.memory.tool.ext.EpsilonRuleInfo;
import com.mastfrog.util.path.UnixPath;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
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
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.GrammarTransformPipeline;
import org.antlr.v4.tool.LexerGrammar;
import org.antlr.v4.tool.ast.GrammarAST;
import org.antlr.v4.tool.ast.GrammarRootAST;
import org.nemesis.antlr.memory.JFSPathHints;
import static org.nemesis.antlr.memory.tool.ToolContext.currentFile;
import static org.nemesis.antlr.memory.tool.ToolContext.isLoggable;
import com.mastfrog.util.streams.stdio.ThreadMappedStdIO;
import org.antlr.v4.tool.Rule;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
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
    private final Map<JFSCoordinates, Set<JFSCoordinates>> outputFiles = new HashMap<>(32);
    private final Map<String, Set<JFSCoordinates>> inputFiles = new HashMap<>(8);
    private final Map<String, JFSCoordinates> primaryInputFileForGrammarName = new HashMap<>(8);
    private final Map<JFSCoordinates, Set<JFSCoordinates>> dependencies = new HashMap<>(8);
    public JFSPathHints hints;

    MemoryTool(ToolContext ctx, String... args) {
        super(appendInternalArgs(args));
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

    private static String[] appendInternalArgs(String[] args) {
        String[] result = Arrays.copyOf(args, args.length + 2);
        result[result.length - 2] = "contextSuperClass";
        result[result.length - 1] = "org.antlr.v4.runtime.RuleContextWithAltNum";
        return result;
    }

    void initLog(ToolContext ctx) {
        if (ctx.logStream != null) {
            ctx.logStream.println("Init " + getClass().getName() + " " + versionInfo());
            ctx.logStream.println("Context-dir:\t" + ctx.dir);
            ctx.logStream.println("Input:\t" + this.inputDirectory);
            ctx.logStream.println("Imports:\t" + this.libDirectory);
            ctx.logStream.println("Grammar-files:\t" + this.grammarFiles);
            ctx.logStream.println("Output:\t" + this.outputDirectory);
            ctx.logStream.println("Package:\t" + this.genPackage);
            ctx.logStream.println("Encoding:\t" + this.grammarEncoding);
            ctx.logStream.println("Exact-output:\t" + this.exact_output_dir);
            ctx.logStream.println("Args:\t" + Arrays.toString(this.args));
            ctx.logStream.println("Over JFS with\n" + list());
        }
    }

    public static <T> T run(UnixPath dir, JFS jfs, Location inputLocation, Location outputLocation, PrintStream logStream, String[] args, ThrowingFunction<MemoryTool, T> func) throws Exception {
        return ThreadMappedStdIO.enter(logStream, () -> {
            MemoryTool tool = create(dir, jfs, inputLocation, outputLocation, logStream, args);
            return func.apply(tool);
        });
    }

    public static MemoryTool create(UnixPath dir, JFS jfs, Location inputLocation, Location outputLocation, PrintStream logStream, String... args) {
        return ToolContext.create(dir, jfs, inputLocation, outputLocation, logStream, args);
    }

    private UnixPath dir() {
        return ToolContext.get(this).dir;
    }

    @Override
    public void error(ANTLRMessage msg) {
        // Avoid the tool logging about non existent directories in the
        // super constructor when we are not actually using the filesystem
        if (initialized) {
            ErrM em = (ErrM) errMgr;
            JFSCoordinates currFile = currentFile.get();
            if (currFile != null) {
                // Ensure error messages have the complete (relative)
                // file path, so we can map them easily in error highlighting
                msg.fileName = currFile.path().toString();
            }
            // There are a few cases in Antlr sources where
            // a direct call is made to tool.error() or tool.warning()
            // while bypassing the error manager - this means we
            // would fail to capture these messages - so, in the case
            // we're not making a recursive call, force them through
            // ErrorManager
            if (!em.isEmitting()) {
                em.emit(msg.getErrorType(), msg, true);
            }
            super.error(msg);
        }
    }

    @Override
    public void warning(ANTLRMessage msg) {
        if (initialized) {
            ErrM em = (ErrM) errMgr;

            JFSCoordinates currFile = currentFile.get();
            if (currFile != null) {
                // Ensure error messages have the complete (relative)
                // file path, so we can map them easily in error highlighting
                msg.fileName = currFile.path().toString();
            }
            // There are a few cases in Antlr sources where
            // a direct call is made to tool.error() or tool.warning()
            // while bypassing the error manager - this means we
            // would fail to capture these messages - so, in the case
            // we're not making a recursive call, force them through
            // ErrorManager
            if (!em.isEmitting()) {
                em.emit(msg.getErrorType(), msg, true);
            }
            super.warning(msg);
        }
    }

    public static String versionInfo() {
        return new StringBuilder(VERSION)
                .append('(').append(Tool.class.getProtectionDomain().getCodeSource().getLocation())
                .append(", ").append(RuntimeMetaData.class.getProtectionDomain().getCodeSource().getLocation())
                .append(", ").append(IntArray.class.getProtectionDomain().getCodeSource().getLocation())
                .append(')').toString();
    }

    JFS jfs() {
        return ToolContext.get(this).jfs;
    }

    private int errorsFilledIn = -1;

    public List<ParsedAntlrError> errors() {
        List<ParsedAntlrError> result = new ArrayList<>(((ErrM) this.errMgr).errorsEncountered);
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

    public int originalErrorCount() {
        return (((ErrM) this.errMgr).errorsEncountered.size());
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
            } catch (IOException ex) {
                ex.printStackTrace(ToolContext.get(this).logStream);
                Logger.getLogger(MemoryTool.class.getName()).log(Level.INFO,
                        "Failed reading " + path, ex);
            }
        }
        return Collections.emptyList();
    }

    private void fillInErrors(List<ParsedAntlrError> l) {
        ParsedAntlrError.computeFileOffsets(l, this::lines);
    }

    @Override
    public void log(String msg) {
        ToolContext ctx = ToolContext.get(this);
        if (ctx.logStream != null) {
            ctx.logStream.println(msg);
        }
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

    public Map<String, Set<JFSCoordinates>> inputFiles() {
        return Collections.unmodifiableMap(inputFiles);
    }

    public Map<JFSCoordinates, Set<JFSCoordinates>> outputFiles() {
        return Collections.unmodifiableMap(outputFiles);
    }

    public Map<String, JFSCoordinates> primaryInputFiles() {
        return Collections.unmodifiableMap(primaryInputFileForGrammarName);
    }

    public Map<JFSCoordinates, Set<JFSCoordinates>> dependencies() {
        return Collections.unmodifiableMap(dependencies);
    }

    public void notePrimaryInput(Grammar g, JFSCoordinates path) {
        assert g != null : "no grammar";
        assert g.name != null : "no grammar name";
        primaryInputFileForGrammarName.put(g.name, path);
    }

    public void noteOutputFile(Grammar g, String filename, JFSCoordinates path) {
        assert currentFile != null;
        JFSCoordinates curr = currentFile.get();
        if (curr != null) {
            Set<JFSCoordinates> output = outputFiles.get(curr);
            if (output == null) {
                output = new HashSet<>(16);
                outputFiles.put(curr, output);
            }
            output.add(path);
        }
    }

    public void noteInputFile(Grammar g, String filename, JFSCoordinates path) {
        Set<JFSCoordinates> input = inputFiles.get(filename);
        if (input == null) {
            input = new HashSet<>();
            inputFiles.put(g.name, input);
        }
        JFSCoordinates currentPath = currentFile.get();
        if (currentPath != null && currentPath.equals(path)) {
            Set<JFSCoordinates> deps = dependencies.get(currentPath);
            if (deps == null) {
                deps = new HashSet<>(8);
                dependencies.put(currentPath, deps);
            }
            deps.add(path);
        }
        input.add(path);
    }

    @Override
    public Writer getOutputFileWriter(Grammar g, String fileName) throws IOException {
        ToolContext ctx = ToolContext.get(this);
        UnixPath pth = ctx.dir.resolve(fileName);
        ctx.logStream.println("output: " + g.name + " -> " + g.fileName + " -> " + pth.toString());
        // PENDING:  In Maven, .tokens file always end up in the default package.
        // Do that here?
        JFSFileObject fo = ctx.jfs.getSourceFileForOutput(pth.toString(),
                ctx.outputLocation);
        noteOutputFile(g, fileName, fo.toCoordinates());
        return fo.openWriter();
    }

    void addDependency(JFSCoordinates from, JFSCoordinates to) {
        if (from != null && !from.equals(to)) {
            Set<JFSCoordinates> set = dependencies.get(from);
            if (set == null) {
                set = new LinkedHashSet<>();
                dependencies.put(from, set);
            }
            set.add(to);
        }
    }

    void withCurrentPath(JFSCoordinates path, Runnable r) {
        JFSCoordinates old = currentFile.get();
        try {
            currentFile.set(path);
            r.run();
            addDependency(old, path);
        } finally {
            currentFile.set(old);
        }
    }

    public void withCurrentPathThrowing(JFSCoordinates path, Thrower r) throws IOException {
        JFSCoordinates old = currentFile.get();
        try {
            currentFile.set(path);
            r.run();
            addDependency(old, path);
        } finally {
            currentFile.set(old);
        }
    }

    public <T> T withCurrentPath(JFSCoordinates path, Supplier<T> supp) {
        JFSCoordinates old = currentFile.get();
        try {
            currentFile.set(path);
            T result = supp.get();
            addDependency(old, path);
            return result;
        } finally {
            currentFile.set(old);
        }
    }

    public <T> T withCurrentPathThrowing(JFSCoordinates path, IOSupp<T> supp) throws IOException {
        JFSCoordinates old = currentFile.get();
        try {
            currentFile.set(path);
            T result = supp.get();
            addDependency(old, path);
            return result;
        } finally {
            currentFile.set(old);
        }
    }

    public List<EpsilonRuleInfo> epsilonIssues() {
        return epsilonIssues == null ? Collections.emptyList() : epsilonIssues;
    }

    public Grammar loadGrammar(String fileName, Consumer<JFSFileObject> c) {
        ToolContext ctx = ToolContext.get(this);
        JFSFileObject fo = null;
        Set<LoadAttempt> attemptedPaths = new HashSet<>(12);
        if (hints != null) {
            String rawName = UnixPath.get(fileName).rawName();
            if (currentFile.get() != null) {
                JFSCoordinates coords = hints.forFileName(rawName,
                        currentFile.get().path(), "g4", "g");
                if (coords != null) {
                    attemptedPaths.add(new LoadAttempt(coords.path(), ctx.inputLocation));
                    fo = ctx.jfs.get(ctx.inputLocation, coords.path());
                    if (fo == null & coords.location() != ctx.inputLocation) {
                        fo = ctx.jfs.get(coords.location(), coords.path());
                        attemptedPaths.add(new LoadAttempt(coords.path(), coords.location()));
                        if (isLoggable(Level.FINEST)) {
                            ctx.logExternal(Level.FINEST, "A. Found {0} at {1}",
                                    new Object[]{fileName, fo.path()});
                        }
                    } else if (fo != null && isLoggable(Level.FINEST)) {
                        ctx.logExternal(Level.FINEST, "B. Found {0} at {1}",
                                new Object[]{fileName, fo.path()});
                    }
                }
            }
            if (fo == null) {
                UnixPath path = hints.firstPathForRawName(fileName, "g4", "g");
                if (path != null) {
                    attemptedPaths.add(new LoadAttempt(path, ctx.inputLocation));
                    fo = ctx.jfs.get(ctx.inputLocation, path);
                    if (isLoggable(Level.FINEST)) {
                        ctx.logExternal(Level.FINEST, "C. Found {0} at {1}",
                                new Object[]{fileName, fo.path()});
                    }
                }
            }
        }
        if (fo == null) {
            String[] possibleNames;
            if (fileName.lastIndexOf('.') < 0) {
                possibleNames = new String[]{fileName + ".g4", fileName + ".g"};
            } else {
                possibleNames = new String[]{fileName};
            }
            for (String name : possibleNames) {
                if (fo == null) {
                    UnixPath path = UnixPath.get(name);
                    attemptedPaths.add(new LoadAttempt(path, ctx.inputLocation));
                    fo = ctx.jfs.get(ctx.inputLocation, UnixPath.get(fileName));
                    if (fo != null && isLoggable(Level.FINEST)) {
                        ctx.logExternal(Level.FINEST, "D. Found {0} at {1}",
                                new Object[]{name, fo.path()});
                    }
                }
                if (fo == null) {
                    fo = getFO(ctx.inputLocation, dir().resolve(name), attemptedPaths, ctx);
                    if (fo != null && isLoggable(Level.FINEST)) {
                        ctx.logExternal(Level.FINEST, "E. Found {0} at {1}",
                                new Object[]{name, fo.path()});
                    }
                }
            }
        }
        if (fo != null) {
            JFSFileObject finalFile = fo;
            JFSCoordinates grammarPath = fo.toCoordinates();
            Grammar result = withCurrentPath(grammarPath, () -> {
                c.accept(finalFile);
                Grammar g = readOneGrammar(finalFile, fileName, ctx);
                if (g != null) {
                    notePrimaryInput(g, grammarPath);
                    noteInputFile(g, fileName, grammarPath);
                }
                return g;
            });
            ErrM errm = (ErrM) this.errMgr;
            if (errm.hasEpsilonIssues) {
                result.originalTokenStream.seek(0);
                result.originalTokenStream.getTokenSource();
                List<EpsilonRuleInfo> issues = EpsilonAnalysis.analyze(result);
                for (EpsilonRuleInfo epsilonIssue : issues) {
                    String implicitLexerGrammarName = result.implicitLexer != null
                            ? result.implicitLexer.name : null;
                    if (epsilonIssue.grammarName().equals(result.name)
                            || epsilonIssue.grammarName().equals(implicitLexerGrammarName)) {
                        if (epsilonIssues == null) {
                            epsilonIssues = new ArrayList<>(issues.size());
                        }
                        epsilonIssues.add(epsilonIssue);
                    }
                }
            }
            return result;
        } else {
            if (grammarFiles != null && !grammarFiles.isEmpty()) {
                ctx.logExternal(Level.FINEST, "Failover scanning known grammars: {0}",
                        grammarFiles);

                for (String gf : this.grammarFiles) {
                    if (gf.endsWith("/" + fileName) && !gf.equals(fileName)) {
                        ctx.logExternal(Level.WARNING, "PATH 6 {0}", fo);
                        Grammar result = loadGrammar(gf);
                        if (result != null) {
                            ctx.logExternal(Level.FINEST, "G. Failover file lookup existing{0} at {1}",
                                    new Object[]{fileName, result.fileName});
                            return result;
                        }
                    }
                }
            }
            ctx.logExternal(Level.FINE, "Could not load primary grammar {0} from any of {1} under {2}",
                    new Object[]{fileName, attemptedPaths, ctx.dir});
            log("Could not load primary grammar " + fileName + " from any of " + attemptedPaths
                    + " underneath " + ctx.dir);
        }
        return null;
    }

    public Grammar readOneGrammar(JFSFileObject finalFile, String fileName, ToolContext ctx) {
        if ("tokens".equals(finalFile.path().extension())) {
            new IllegalStateException("Trying to parse a tokens file as a g4 file: "
                    + fileName + " " + finalFile.getName())
                    .printStackTrace();
        }
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
            System.out.println("CTX SUPER " + g.getOptionString("contextSuperClass"));
            g.fileName = finalFile.path().toString();
            log("Loaded primary grammar " + g.name + " file "
                    + g.fileName + " from " + ctx.inputLocation
                    + ":" + finalFile);
            try {
                JFSCoordinates path = finalFile.toCoordinates();
                notePrimaryInput(g, path);
                noteInputFile(g, fileName, path);
                process(g, false);
            } catch (Circularity cir) {
                errMgr.toolError(ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR, cir.attemptedLoad, cir.from.toString());
                cir.printStackTrace();
            } catch (NullPointerException ex) {
                /*
                A parser grammar with an illegal labeled element referencing
                a non-set will produce an NPE - e.g.,
                foo=(This? That)
                causes
                java.lang.NullPointerException
                        at org.antlr.v4.automata.ParserATNFactory.elemList(ParserATNFactory.java:467)
                        at org.antlr.v4.automata.ParserATNFactory.alt(ParserATNFactory.java:456)
                        at org.antlr.v4.parse.ATNBuilder.alternative(ATNBuilder.java:559)
                        at org.antlr.v4.parse.ATNBuilder.ruleBlock(ATNBuilder.java:293)
                        at org.antlr.v4.automata.ParserATNFactory._createATN(ParserATNFactory.java:162)
                        at org.antlr.v4.automata.ParserATNFactory.createATN(ParserATNFactory.java:108)
                        at org.antlr.v4.Tool.processNonCombinedGrammar(Tool.java:396)
                        at org.antlr.v4.Tool.process(Tool.java:369)

                 */
                ex.printStackTrace(ctx.logStream);
                errMgr.toolError(ErrorType.INTERNAL_ERROR, ex);
            }
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

    private static final UnixPath IMPORT = UnixPath.get("import");
    private static final UnixPath IMPORTS = UnixPath.get("imports");

    private JFSFileObject getImportedGrammarFileObject(Grammar g, String fileName, Location loc, Set<? super LoadAttempt> failures, ToolContext ctx) {
        JFSFileObject fo = null;
        fo = ctx.jfs.get(loc, ctx.dir.resolve(fileName));
        if (fo == null) {
            UnixPath nxt = UnixPath.get(fileName);
            fo = ctx.jfs.get(loc, nxt);
            if (fo == null && nxt.getParent() != null) {
                fo = getFO(loc, nxt.getParent().resolve(fileName), failures, ctx);
            }
            if (fo == null) {
                fo = getFO(loc, IMPORT.resolve(fileName), failures, ctx);
            }
            if (fo == null) {
                fo = getFO(loc, IMPORTS.resolve(fileName), failures, ctx);
            }
            if (fo == null && libDirectory != null) {
                if (libDirectory.isEmpty()) {
                    fo = getFO(loc, UnixPath.get(fileName), failures, ctx);
                } else {
                    fo = getFO(loc, UnixPath.get(libDirectory).resolve(fileName), failures, ctx);
                }
                if (fo == null && !libDirectory.isEmpty()) {
                    fo = getFO(loc, UnixPath.get(libDirectory).resolve(IMPORTS).resolve(fileName), failures, ctx);
                }
                if (fo == null && !libDirectory.isEmpty()) {
                    fo = getFO(loc, UnixPath.get(libDirectory).resolve(IMPORT).resolve(fileName), failures, ctx);
                }
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
        ctx.jfs.list(StandardLocation.SOURCE_PATH, (loc, file) -> {
            sb.append(" * ").append(loc.getName()).append('\t').append(file).append('\n');
        });
        ctx.jfs.list(StandardLocation.SOURCE_OUTPUT, (loc, file) -> {
            sb.append(" * ").append(loc.getName()).append('\t').append(file).append('\n');
        });
        return sb.toString();
    }

    private static final ThreadLocal<LinkedList<String>> loading = ThreadLocal.withInitial(LinkedList::new);

    private boolean isSameGrammar(Grammar imported, String name) {
        if (imported == null || name == null) {
            return false;
        }
        if (imported.fileName != null) {
            if (imported.fileName.equals(name)) {
                return true;
            }
            if (UnixPath.get(imported.fileName).rawName().equals(name)) {
                return true;
            }
        }
        if (imported.name != null) {
            if (imported.name.equals(name)) {
                return true;
            }
            if (imported.name.equals(UnixPath.get(name).rawName()));
        }
        return false;
    }

    static class Circularity extends RuntimeException {

        final String attemptedLoad;
        final JFSCoordinates from;
        final LinkedHashSet<String> loading;

        Circularity(List<String> loading, String attemptedLoad, JFSCoordinates from) {
            super("Circular load of '" + attemptedLoad + " when already loading " + loading + " from " + from);
            this.attemptedLoad = attemptedLoad;
            this.from = from;
            this.loading = new LinkedHashSet<>(loading);
        }
    }

    @Override
    public Grammar loadImportedGrammar(Grammar g, GrammarAST nameNode) throws IOException {
        String name = nameNode.getText();
        if (name.equals(g.name)) {
            // Avoid a StaackOverflowError if the grammar imports itself
            this.errMgr.emit(ErrorType.INVALID_IMPORT, new ANTLRMessage(ErrorType.INVALID_IMPORT, nameNode.token, nameNode.getToken(), name));
            return null;
        }
        LinkedList<String> currentlyLoading = loading.get();
        if (currentlyLoading.contains(name)) {
            errMgr.grammarError(ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR, g.fileName, nameNode.getToken(), name);
            throw new Circularity(currentlyLoading, name, currentFile.get());
        }
        Set<String> prevCurrentlyLoading = new HashSet<>(currentlyLoading);
        currentlyLoading.push(name);
        try {
            Grammar imported = importedGrammars().get(name);
            if (imported == g) {
                return null;
            }
            if (isSameGrammar(imported, name)) {
                return imported;
            }
            if (imported == null && !prevCurrentlyLoading.contains(name)) {
                JFSFileObject importedFile = null;
                for (String extension : ALL_GRAMMAR_EXTENSIONS) {
                    importedFile = getImportedGrammarFileObject(g, name + extension);
                    if (importedFile != null) {
                        break;
                    }
                }
                log("grammar", "load " + name + " from " + g.fileName + ": " + importedFile);
                if (importedFile != null) {
                    JFSFileObject fo = importedFile;
                    JFSCoordinates path = fo.toCoordinates();
                    return withCurrentPathThrowing(path, () -> {

                        CharSequence chars = fo.getCharContent(true);
                        try { // (InputStream in = fo.openInputStream()) {
                            CharSequenceCharStream charStream = new CharSequenceCharStream(chars);
                            GrammarRootAST grammarRootAST = parse(fo.getName(), charStream);
                            final Grammar gg = createGrammar(grammarRootAST);
//                            importedGrammars.put(name, gg);
                            if (gg != null) {
                                gg.fileName = path.toString();
                                try {
                                    process(gg, false);
                                } catch (Circularity circ) {
                                    // we don't want to abort THIS grammar, just not
                                    // try to load an import that circles back
                                    circ.printStackTrace();
                                }
                                notePrimaryInput(gg, path);
                                noteInputFile(gg, gg.fileName, path);
                            }
                            return gg;
                        } catch (Exception ex) {
                            IOException ex1 = new IOException("Exception reading " + path
                                    + " JFS FO " + fo + " length " + fo.length()
                                    + " isr with chars length " + chars.length(), ex);
                            ToolContext.get(this).logExternal(Level.WARNING, listJFS(), ex1);
                            throw ex1;
                        }
                    });
                } else {
                    errMgr.grammarError(ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR, g.fileName, nameNode.getToken(), name);
                }
            }
            return imported;
        } finally {
            currentlyLoading.pop();
        }
    }

    private final Map<Grammar, Boolean> alreadyLoadedImportedGrammars = new WeakHashMap<>();

    private void loadImportedGrammars(Grammar g) {
        if (alreadyLoadedImportedGrammars.containsKey(g)) {
            return;
        }
        alreadyLoadedImportedGrammars.put(g, true);
        g.loadImportedGrammars();
    }

    @Override
    public void process(Grammar g, boolean gencode) {
        // A missing file will cause endless cycling, as the attempt to
        // load grammars is repeated every time the grammar is traversed,
        // so we have to override process() to avoid attempting to load
        // imported grammars more than once
        loadImportedGrammars(g);
        ToolContext.get(this).logStream.println("process " + g.name + " generate? " + gencode);

        GrammarTransformPipeline transform = new GrammarTransformPipeline(g, this);
        transform.process();

        LexerGrammar lexerg;
        GrammarRootAST lexerAST;
        if (g.ast != null && g.ast.grammarType == ANTLRParser.COMBINED
                && !g.ast.hasErrors) {
            lexerAST = transform.extractImplicitLexer(g); // alters g.ast
            if (lexerAST != null) {
                if (grammarOptions != null) {
                    lexerAST.cmdLineOptions = grammarOptions;
                }
                lexerg = new LexerGrammar(this, lexerAST);
                lexerg.fileName = g.fileName;
                lexerg.originalGrammar = g;
                g.implicitLexer = lexerg;
                lexerg.implicitLexerOwner = g;
                JFSCoordinates gFile = primaryInputFileForGrammarName.get(g.name);
                notePrimaryInput(lexerg, gFile);
                processNonCombinedGrammar(lexerg, gencode);
            }
        }
        if (g.implicitLexer != null) {
            g.importVocab(g.implicitLexer);
        }
        processNonCombinedGrammar(g, gencode);
    }

    private String listJFS() {
        StringBuilder sb = new StringBuilder("Input JFS:");
        ToolContext.get(this).jfs.list(ToolContext.get(this).inputLocation, (loc, jfo) -> {
            sb.append('\n').append(jfo.getName()).append(" len ").append(jfo.length());
        });
        return sb.toString();
    }

    public Grammar loadDependentGrammar(String name, JFSFileObject fo) throws IOException {
        JFSCoordinates grammarPath = fo.toCoordinates();
        log("LOAD DEP GRAMMAR " + grammarPath);
        return withCurrentPathThrowing(grammarPath, () -> {
            CharSequenceCharStream in = new CharSequenceCharStream(fo.getCharContent(true));
            GrammarRootAST root = parse(name, in);
            Grammar result = createGrammar(root);
            result.fileName = grammarPath.toString();
            notePrimaryInput(result, grammarPath);
            noteInputFile(result, name, grammarPath);
            return result;
        });
    }

    @Override
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
        if (thrown == null) {
            return 0;
        }
        int result = 0;
        if (thrown instanceof AttemptedExit) {
            result = ((AttemptedExit) thrown).code();
        } else if (thrown.getCause() != null) {
            if (thrown.getCause() instanceof AttemptedExit) {
                result = ((AttemptedExit) thrown.getCause()).code();
            }
        }
        if (result == 0 && thrown.getSuppressed() != null
                && thrown.getSuppressed().length > 0) {
            for (Throwable t : thrown.getSuppressed()) {
                if (t instanceof AttemptedExit) {
                    result = ((AttemptedExit) t).code();
                }
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

        private final List<ParsedAntlrError> errorsEncountered = new ArrayList<>(13);
        boolean hasEpsilonIssues;

        public ErrM(Tool tool) {
            super(tool);
        }

        private String replaceWithPath(String name) {
            JFSCoordinates p = currentFile.get();
            if (p != null) {
                return p.path().toString();
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
        public void leftRecursionCycles(String fileName, Collection<? extends Collection<Rule>> cycles) {
            for (ANTLRMessage msg : createLeftRecusionCyclesMessages(fileName, cycles)) {
                tool.error(msg);
            }
        }

        @Override
        public void emit(ErrorType etype, ANTLRMessage msg) {
            emit(etype, msg, false);
        }

        boolean isEmitting() {
            return inEmit;
        }

        private boolean inEmit;

        void emit(ErrorType etype, ANTLRMessage msg, boolean fromTool) {
            if (inEmit) {
                // Avoid recursive reentry
                return;
            }
            inEmit = true;
            try {
                ToolContext ctx = ToolContext.get((MemoryTool) tool);
                switch (etype) {
                    case EPSILON_TOKEN:
                    case EPSILON_CLOSURE:
                    case EPSILON_OPTIONAL:
                    case EPSILON_LR_FOLLOW:
                        hasEpsilonIssues = true;
                        break;
                    case INTERNAL_ERROR:
                        if (msg.getCause() != null) {
                            ctx.logExternal(
                                    Level.INFO, etype + ": " + msg.getMessageTemplate(true).render(), msg.getCause());
                            msg.getCause().printStackTrace(ctx.logStream);
                        }
                }
                msg.fileName = replaceWithPath(msg.fileName);
                JFSCoordinates supplied = currentFile.get();
                UnixPath pth = msg.fileName == null ? supplied == null
                        ? UnixPath.get("_no-file_") : supplied.path() : UnixPath.get(msg.fileName);
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
                    Token t = msg.offendingToken;
                    if (txt != null) {
                        // XXX this is garbage
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
                errorsEncountered.add(pae);
                if (!fromTool) {
                    super.emit(etype, msg);
                }
                ctx.logStream.println(etype.severity + ": " + message);
            } finally {
                inEmit = false;
            }
        }

        // 'nbantlr/xtr175w/sesjzw8xtoe/run1/Rust.g4(19,0) : error 51 : rule compilation_unit redefinition; previous at line 16'
        static final Pattern ERRNUM = Pattern.compile("^.*\\(\\d+,\\d+\\)\\s+:\\s+error \\d+\\s+:\\s+(\\S.*)$", Pattern.DOTALL);

        @Override
        public void info(String msg) {
            ToolContext ctx = ToolContext.get((MemoryTool) tool);
            ctx.logStream.println("INFO: " + msg);
            super.info(msg);
        }

        @Override
        public void resetErrorState() {
//            errorsEncountered.clear();
            super.resetErrorState();
        }
    }

    static List<ANTLRMessage> createLeftRecusionCyclesMessages(String fileName, Collection<? extends Collection<Rule>> cycles) {
        List<ANTLRMessage> result = new ArrayList<>(16);
        for (Collection<Rule> collection : cycles) {
            for (Rule rule : collection) {
                if (rule.ast != null) {
                    int line = rule.ast.getLine();
                    int cp = rule.ast.getCharPositionInLine();
                    Token tok = rule.ast.getToken();
                    if (tok.getCharPositionInLine() == -1) {
                        // This is a synthetic U/DOWN token - not useful for
                        // getting meaningful information from
                        tok = rule.ast.g.originalTokenStream.get(rule.ast.getTokenStartIndex());
                    }
                    List<Rule> withCurrentAtFront = new ArrayList<>(collection);
                    if (withCurrentAtFront.indexOf(rule) != 0) {
                        withCurrentAtFront.remove(rule);
                        withCurrentAtFront.add(0, rule);
                    }
                    ANTLRMessage msg = new ANTLRMessage(ErrorType.LEFT_RECURSION_CYCLES, tok,
                            Arrays.asList(withCurrentAtFront));
                    msg.line = line;
                    msg.charPosition = cp;
                    msg.g = rule.g;
                    result.add(msg);
                }
            }
        }
        return result;
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

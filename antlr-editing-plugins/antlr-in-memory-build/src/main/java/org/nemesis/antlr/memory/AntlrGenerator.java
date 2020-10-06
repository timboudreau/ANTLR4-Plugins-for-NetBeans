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
package org.nemesis.antlr.memory;

import org.nemesis.antlr.memory.alt.AlternativesAnalyzer;
import org.nemesis.antlr.memory.alt.AlternativesInfo;
import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Lng;
import com.mastfrog.function.state.Obj;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.io.PrintStream;
import org.nemesis.antlr.memory.tool.MemoryTool;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.Checkpoint;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.spi.JFSUtilities;

/**
 * Can run Antlr in-memory over an instance of JFS.
 *
 * @author Tim Boudreau
 */
public final class AntlrGenerator {

    private static final Logger LOG = Logger.getLogger(AntlrGenerator.class.getName());
    private final Charset grammarEncoding;
    private final boolean generateAll;
    private final String packageName;
    final Supplier<JFS> jfs;
    private final JavaFileManager.Location grammarSourceLocation;
    private final UnixPath virtualSourcePath;
    private final UnixPath virtualImportDir;
    private final Set<AntlrGenerationOption> opts = EnumSet.noneOf(AntlrGenerationOption.class);
    private final JavaFileManager.Location outputLocation;
    private final Path originalFile;
    private final String originalTokensHash;
    private JFSPathHints pathHints;
    final RerunInterceptor interceptor;
    private boolean analyzeAlts;

    static AntlrGenerator fromResult(AntlrGenerationResult result) {
        return new AntlrGenerator(AntlrGeneratorBuilder.fromResult(result));
    }

    public AntlrGenerator(Charset grammarEncoding, boolean generateAll, String packageName,
            Supplier<JFS> jfs, JavaFileManager.Location grammarSourceLocation, UnixPath virtualSourcePath,
            UnixPath virtualImportDir, JavaFileManager.Location outputLocation,
            Path originalFile, String originalTokensHash, JFSPathHints pathHints,
            RerunInterceptor interceptor, boolean analyzeAlts) {
        this.grammarEncoding = grammarEncoding;
        this.generateAll = generateAll;
        this.packageName = packageName;
        this.jfs = jfs;
        this.grammarSourceLocation = grammarSourceLocation;
        this.virtualSourcePath = virtualSourcePath;
        this.virtualImportDir = virtualImportDir;
        this.outputLocation = outputLocation;
        this.originalFile = notNull("originalFile", originalFile);
        this.originalTokensHash = originalTokensHash;
        this.pathHints = pathHints == null ? JFSPathHints.NONE : pathHints;
        this.interceptor = interceptor;
        this.analyzeAlts = analyzeAlts;
    }

    public JFSPathHints hints() {
        return pathHints == null ? JFSPathHints.NONE : pathHints;
    }

    public Path originalFile() {
        return originalFile;
    }

    public String tokensHash() {
        return originalTokensHash;
    }

    public AntlrGenerator withFileInfo(Path originalPath, String tokensHash) {
        if (Objects.equals(originalFile, originalPath) && Objects.equals(tokensHash, originalTokensHash)) {
            return this;
        }
        return new AntlrGenerator(grammarEncoding, generateAll,
                packageName, jfs, grammarSourceLocation, virtualSourcePath,
                virtualImportDir, outputLocation, originalFile,
                originalTokensHash, pathHints, interceptor, analyzeAlts);
    }

    public JavaFileManager.Location sourceLocation() {
        return grammarSourceLocation;
    }

    public JavaFileManager.Location outputLocation() {
        return outputLocation;
    }

    public String packageName() {
        return packageName;
    }

    public Charset encoding() {
        return grammarEncoding;
    }

    public UnixPath importDir() {
        return virtualImportDir();
    }

    public UnixPath sourcePath() {
        return virtualSourcePath;
    }

    /**
     * If running using classloader isolation, these are the packages that
     * directly touch Antlr (which may be a different version than on the module
     * classpath).
     *
     * @return
     */
    public static String[] antlrPackages() {
        return new String[]{MemoryTool.class.getPackage().getName()};
    }

    /**
     * If running using classloader isolation, these are the packages which need
     * to be visible to both the Antlr classloader and the parent classloader.
     *
     * @return
     */
    public static String[] accessiblePackagesFromParentClassloader() {
        return new String[]{
            ParsedAntlrError.class.getPackage().getName(),
            AntlrGenerator.class.getPackage().getName(),
            JFS.class.getPackage().getName(),
            "org.nemesis.jfs.javac",
            "org.nemesis.jfs.nio",
            "org.nemesis.jfs.spi",
            JFSUtilities.getDefault().getClass().getPackage().getName()
        };
    }

    public static AntlrGenerator create(AntlrGeneratorBuilder<?> bldr) {
        return new AntlrGenerator(bldr);
    }

    AntlrGenerator(AntlrGeneratorBuilder<?> b) {
        this.jfs = b.jfs;
        if (b.genListener) {
            opts.add(AntlrGenerationOption.GENERATE_LISTENER);
        }
        if (b.genVisitor) {
            opts.add(AntlrGenerationOption.GENERATE_VISITOR);
        }
        if (b.generateATNDot) {
            opts.add(AntlrGenerationOption.GENERATE_ATN);
        }
        if (b.genDependencies) {
            opts.add(AntlrGenerationOption.GENERATE_DEPENDENCIES);
        }
        if (b.longMessages) {
            opts.add(AntlrGenerationOption.LONG_MESSAGES);
        }
        if (b.log) {
            opts.add(AntlrGenerationOption.LOG);
        }
        if (b.forceAtn) {
            opts.add(AntlrGenerationOption.FORCE_ATN);
        }
        this.generateAll = b.generateAll;
        this.grammarEncoding = b.grammarEncoding;
        this.grammarSourceLocation = b.grammarSourceInputLocation;
        this.outputLocation = b.javaSourceOutputLocation;
        this.virtualSourcePath = b.sourcePath;
        this.packageName = b.packageName;
        this.virtualImportDir = b.importDir;
        this.originalTokensHash = b.tokensHash;
        this.originalFile = notNull("b.originalFile", b.originalFile);
        this.pathHints = b.pathHints;
        this.interceptor = b.interceptor;
        this.analyzeAlts = b.analyzeAlts;
    }

    public static <T> AntlrGeneratorBuilder<T> builder(Supplier<JFS> jfs, Function<? super AntlrGeneratorBuilder<T>, T> func) {
        return new AntlrGeneratorBuilder<>(jfs, func);
    }

    public static AntlrGeneratorBuilder<AntlrGenerator> builder(Supplier<JFS> jfs) {
        return builder(jfs, AntlrGenerator::new);
    }

    public JFS jfs() {
        return jfs.get();
    }

    public Supplier<JFS> jfsSupplier() {
        return jfs;
    }

    public UnixPath packagePath() {
        return UnixPath.get(packageName.replace('.', '/'));
    }

    public JFSCoordinates grammarFilePath(String fileName) {
        JFS jfs = jfs();
        String baseName = fileName;
        if (fileName.indexOf('.') < 0) {
            fileName += ".g4";
        }
        String rawName = UnixPath.get(baseName).rawName();
        UnixPath result = pathHints.firstPathForRawName(rawName, "g4", "g");
        if (result == null) {
            result = packagePath().resolve(fileName);
        }
        JFSFileObject fo = jfs.get(sourceLocation(), result);
        if (fo == null) {
            result = packagePath().resolve(UnixPath.get(baseName).rawName() + ".g");
            fo = jfs.get(sourceLocation(), result);
        }
        if (fo == null) {
            System.out.println("NO GRAMMAR PATH FOR " + fileName + " rawName "
                    + rawName + " baseName " + baseName
                    + " hints " + pathHints + " jfs " + jfs.id()
                    + " listing " + jfs.list(SOURCE_PATH, SOURCE_OUTPUT, CLASS_OUTPUT));
        }
        return fo == null ? null : fo.toCoordinates();
    }

    private UnixPath resolveSourcePath(String grammarFileName) {
        // In the case of grammars in the default package, there may be
        // some issues with null parents and resolving siblings, because of
        // how UnixPath works.
        UnixPath result = virtualSourcePath;
        if (result == null || result.toString().isEmpty()) {
            JFSCoordinates fromHints = grammarFilePath(grammarFileName);
            if (fromHints != null) {
                result = fromHints.path();
            }
        }
        return result;
    }

    private String listJFS() {
        return jfs.get().list(SOURCE_PATH, SOURCE_OUTPUT, CLASS_OUTPUT);
    }

    public interface ReRunner {

        AntlrGenerationResult run(String grammarFileName, PrintStream logStream, boolean generate);
    }

    public interface RerunInterceptor {

        AntlrGenerationResult rerun(String grammarFileName, PrintStream logStream, boolean generate, AntlrGenerator originator, ReRunner localRerunner);
    }

    public AntlrGenerationResult run(String grammarFileName, PrintStream logStream, boolean generate) {
        if (interceptor == null) {
            return internalRun(grammarFileName, logStream, generate);
        } else {
            return interceptor.rerun(grammarFileName, logStream, generate, this, this::internalRun);
        }
    }

    public AntlrGenerationResult createFailedResult(Throwable thrown) {
        return createFailedResult(null, thrown);
    }

    public AntlrGenerationResult createFailedResult(String grammarName, Throwable thrown) {
        UnixPath grammarFile = grammarName != null ? UnixPath.get(grammarName) : UnixPath.get(originalFile.getFileName());
        if (virtualSourcePath != null && !virtualSourcePath.isEmpty()) {
            grammarFile = virtualSourcePath.resolve(grammarFile);
        }
        JFSCoordinates coords = JFSCoordinates.create(StandardLocation.SOURCE_PATH, grammarFile);
        return new AntlrGenerationResult(false, 1000, thrown, grammarFile.rawName(),
                null, Collections.emptyList(), coords, System.currentTimeMillis(), Collections.emptySet(),
                jfs.get(), grammarSourceLocation, outputLocation, packageName, virtualSourcePath, virtualImportDir,
                generateAll, opts, grammarEncoding, originalTokensHash, originalFile, jfs,
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0,
                pathHints, interceptor, Collections.emptySet(), Collections.emptySet(), JFSFileModifications.empty(),
                JFSFileModifications.empty(), null, false);
    }

    private Set<JFSCoordinates> filter(Set<JFSCoordinates> set, Predicate<JFSCoordinates> pred) {
        Set<JFSCoordinates> result = new HashSet<>(set.size());
        int ct = 0;
        for (JFSCoordinates c : set) {
            if (pred.test(c)) {
                ct++;
                result.add(c);
            }
        }
        return ct == set.size() ? set : ct == 0 ? Collections.emptySet() : CollectionUtils.immutableSet(result);
    }

    private Set<JFSCoordinates> coalesce(Map<JFSCoordinates, Set<JFSCoordinates>> all) {
        if (all.isEmpty()) {
            return Collections.emptySet();
        }
        Set<JFSCoordinates> result = new HashSet<>(all.size() + 1);
        for (Map.Entry<JFSCoordinates, Set<JFSCoordinates>> e : all.entrySet()) {
            result.add(e.getKey());
            result.addAll(e.getValue());
        }
        return CollectionUtils.immutableSet(result);
    }

    private Set<JFSCoordinates> coalesceNameMap(Map<String, Set<JFSCoordinates>> all) {
        if (all.isEmpty()) {
            return Collections.emptySet();
        }
        Set<JFSCoordinates> result = new HashSet<>(all.size() + 1);
        for (Map.Entry<String, Set<JFSCoordinates>> e : all.entrySet()) {
            result.addAll(e.getValue());
        }
        return CollectionUtils.immutableSet(result);
    }

    private AntlrGenerationResult internalRun(String grammarFileName, PrintStream logStream, boolean generate) {
//        System.out.println("RUN " + grammarFileName);
//        System.out.println(listJFS());
        return Debug.runObject(this, "Generate " + grammarFileName + " - " + generate, () -> {
            logStream.println("Begin generation of '" + grammarFileName + "' generated=" + generate
                    + " generateAll? " + generateAll);
            List<ParsedAntlrError> errors = new ArrayList<>();
            Throwable thrown = null;
            int code = -1;
//            Map<JFSFileObject, Long> modificationDates = new HashMap<>();
//            Set<JFSCoordinates.Resolvable> files = new HashSet<>();
//            JFSFileObject[] grammarFile = new JFSFileObject[1];
//            long[] grammarFileLastModified = new long[]{0};
            Set<Grammar> grammars = new HashSet<>(12);
            Obj<Grammar> mainGrammar = Obj.create();
//            Grammar[] mainGrammar = new Grammar[1];
//            boolean[] success = new boolean[]{true};
            Bool success = Bool.create(true);
            Lng grammarFileLastModified = Lng.create();
            Obj<Map<JFSCoordinates, Set<JFSCoordinates>>> outputFiles = Obj.of(Collections.emptyMap());
            Obj<Map<JFSCoordinates, Set<JFSCoordinates>>> dependencies = Obj.of(Collections.emptyMap());
            Obj<Map<String, Set<JFSCoordinates>>> inputFiles = Obj.of(Collections.emptyMap());
            Obj<Map<String, JFSCoordinates>> primaryInputFileForGrammarName = Obj.of(Collections.emptyMap());
            Obj<JFSCoordinates> grammarFile = Obj.create();
            Obj<String> gn = Obj.of("--");
            Lng timestamp = Lng.of(System.currentTimeMillis());
            Obj<AlternativesInfo> altPositions = Obj.create();
            JFS jfs = this.jfs.get();
            Checkpoint checkpoint = jfs.newCheckpoint();
            try {
                String[] args = AntlrGenerationOption.toAntlrArguments(
                        resolveSourcePath(grammarFileName),
                        opts,
                        grammarEncoding,
                        packageName,
                        virtualImportDir());
                MemoryTool.run(virtualSourcePath, jfs, grammarSourceLocation,
                        outputLocation, logStream, args, tool -> {
                            if (this.pathHints != null) {
                                tool.hints = this.pathHints;
                            }
                            JFSCoordinates grammarFilePath = grammarFilePath(grammarFileName);
                            if (grammarFilePath == null) {
                                success.set(false);
                                throw new IOException("Could not resolve grammar file " + grammarFileName
                                    + " in " + jfs.list(SOURCE_PATH, SOURCE_OUTPUT, CLASS_OUTPUT));
                            }
                            String grammarName = "--";
                            tool.generate_ATN_dot = opts.contains(AntlrGenerationOption.GENERATE_ATN);
                            tool.grammarEncoding = grammarEncoding.name();
                            tool.gen_dependencies = opts.contains(AntlrGenerationOption.GENERATE_DEPENDENCIES);
                            tool.longMessages = opts.contains(AntlrGenerationOption.LONG_MESSAGES);
                            tool.log = opts.contains(AntlrGenerationOption.LOG);
                            tool.force_atn = opts.contains(AntlrGenerationOption.FORCE_ATN);
                            tool.genPackage = packageName;
                            logStream.println("Grammar File Path:\t" + grammarFilePath);

                            timestamp.set(System.currentTimeMillis());
                            mainGrammar.set(tool.withCurrentPath(grammarFilePath, () -> {
                                Grammar result = tool.loadGrammar(grammarFileName, fo -> {
                                    grammarFileLastModified.set(fo.getLastModified());
                                    grammarFile.set(fo.toCoordinates());
                                });
                                logStream.println("loaded main grammar " + (result == null ? "null"
                                        : (result.name + " / " + result.fileName + " " + result.getTypeString())));
                                if (result != null) {
                                    grammars.add(result);
                                }
                                if (generateAll && result != null) {
                                    tool.withCurrentPath(grammarFilePath, () -> {
                                        generateAllGrammars(tool, result, new HashSet<>(), generate, grammars, jfs);
                                        return null;
                                    });
                                }
                                if (analyzeAlts && result != null) {
                                    altPositions.set(AlternativesAnalyzer.collectAlternativesOffsets(result));
                                }
                                return result;
                            }));
                            if (mainGrammar.isSet()) {
                                grammarName = mainGrammar.get().name;
                                gn.set(grammarName);
                                Debug.success("Generated " + grammarName, this::toString);
                            } else {
                                Debug.failure("Not-generated " + grammarFileName, this::toString);
                                success.set(false);
                            }
                            outputFiles.set(tool.outputFiles());
                            inputFiles.set(tool.inputFiles());
                            primaryInputFileForGrammarName.set(tool.primaryInputFiles());
                            dependencies.set(tool.dependencies());
                            List<ParsedAntlrError> errs = tool.errors();
                            errors.addAll(errs);
                            logStream.println("Raw error count " + tool.originalErrorCount()
                                    + " with coalesce/epsilon processing " + errs.size());
                            return null;
                        });
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Error loading grammar " + grammarFileName, ex);
                ex.printStackTrace(logStream);
                thrown = ex;
                success.set(false);
            }
            try {
                if (!errors.isEmpty()) {
                    LOG.log(Level.FINE, "Errors generating virtual Antlr sources");
                    if (LOG.isLoggable(Level.FINEST)) {
                        for (ParsedAntlrError e : errors) {
                            LOG.log(Level.FINEST, "Antlr error: {0}", e);
                        }
                    }
                }
                if (success.get() && !errors.isEmpty()) {
                    for (ParsedAntlrError e : errors) {
                        if (e.isError()) {
                            success.set(false);
                            break;
                        }
                    }
                }
                code = MemoryTool.attemptedExitCode(thrown);
            } catch (Exception ex) {
                thrown = ex;
            }
            Set<JFSCoordinates> touchedFiles = checkpoint.updatedFiles();
            // It is possible for the user to type while generation is running,
            // and generate a spurious modification of a grammar file
            // that will be captured by the checkpoint - so filter out
            // grammar files
            Set<JFSCoordinates> modifiedFiles = filter(touchedFiles, tst -> {
                UnixPath path = tst.path();
                String ext = path.extension();
                if ("g4".equals(ext) && "g".equals(ext)) {
                    return false;
                }
                return true;
            });
            Set<JFSCoordinates> allInputFiles = coalesceNameMap(inputFiles.get());
            JFSFileModifications inputFileModifications = JFSFileModifications.of(jfs, allInputFiles);
            Set<JFSCoordinates> allOutputFiles = coalesce(outputFiles.get());
            JFSFileModifications outputFileModifications = JFSFileModifications.of(jfs, allOutputFiles);

            return new AntlrGenerationResult(success.getAsBoolean(), code, thrown, gn.get(),
                    mainGrammar.get(), errors, grammarFile.get(),
                    grammarFileLastModified.getAsLong(),
                    grammars, jfs,
                    grammarSourceLocation, outputLocation, packageName,
                    virtualSourcePath, virtualImportDir, this.generateAll,
                    this.opts, this.grammarEncoding, originalTokensHash,
                    originalFile, this.jfs, outputFiles.get(), inputFiles.get(),
                    primaryInputFileForGrammarName.get(), dependencies.get(),
                    timestamp.get(), pathHints, interceptor, modifiedFiles, allInputFiles, outputFileModifications,
                    inputFileModifications, altPositions.get(), analyzeAlts);
        });
    }

    private static String keyFor(Grammar g) {
        return g.name + ":" + g.getTypeString();
    }

    private UnixPath virtualImportDir() {
        return virtualImportDir == null ? UnixPath.get("imports") : virtualImportDir;
    }

    private void generateAllGrammars(MemoryTool tool, Grammar g,
            Set<String> seen, boolean generate, Set<Grammar> grammars, JFS jfs) {
        if (g != null && !seen.contains(keyFor(g))) {
            LOG.log(Level.FINEST, "MemoryTool generating {0}", g.fileName);
            seen.add(keyFor(g));
            if (g.implicitLexer != null) {
                tool.process(g.implicitLexer, generate);
            }
            try {
                tool.process(g, generate);
            } catch (RuntimeException ex) {
                if ("set is empty".equals(ex.getMessage())) {
                    // bad source - a partially written
                    // character set, e.g. fragment FOO : [\p{...];
                    LOG.log(Level.INFO, "Bad character set", ex);
                    tool.errMgr.emit(ErrorType.ERROR_READING_IMPORTED_GRAMMAR,
                            new ANTLRMessage(ErrorType.ERROR_READING_IMPORTED_GRAMMAR, ex, null));
                }
            }
            if (g.isCombined()) {
                String suffix = Grammar.getGrammarTypeToFileNameSuffix(ANTLRParser.LEXER);
                String lexer = g.name + suffix + ".g4";
                UnixPath srcPath = packagePath().resolve(lexer);
                JFSFileObject lexerFo = jfs.get(grammarSourceLocation, srcPath);
                if (lexerFo == null) {
                    pathHints.firstPathForRawName(g.name, "g4", "g");
                }
                if (lexerFo == null) {
                    lexer = g.name + suffix + ".g";
                    srcPath = packagePath().resolve(lexer);
                    lexerFo = jfs.get(grammarSourceLocation, srcPath);
                }
                if (lexerFo == null) {
                    srcPath = virtualImportDir().resolve(lexer);
                    lexerFo = jfs.get(grammarSourceLocation, srcPath);
                }
                if (lexerFo != null) {
                    try {
                        JFSFileObject finalLexerFo = lexerFo;
                        Grammar lexerGrammar = tool.withCurrentPathThrowing(lexerFo.toCoordinates(), () -> {
                            Grammar result = tool.loadDependentGrammar(g.name, finalLexerFo);
                            LOG.log(Level.FINEST, "Generate lexer {0}", result.fileName);
                            return result;
                        });
                        grammars.add(lexerGrammar);
                        generateAllGrammars(tool, lexerGrammar, seen, generate, grammars, jfs);
                    } catch (IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                }
            }
            grammars.addAll(tool.allGrammars());
        }
    }

    @Override
    public String toString() {
        return "AntlrRunner{" + "grammarEncoding=" + grammarEncoding
                + ", generateAll=" + generateAll + ", packageName="
                + packageName + ", sourceLocation=" + grammarSourceLocation
                + ", virtualSourcePath=" + virtualSourcePath
                + ", virtualImportDir=" + virtualImportDir()
                + ", opts=" + opts
                + ", outputLocation=" + outputLocation + '}';
    }
}

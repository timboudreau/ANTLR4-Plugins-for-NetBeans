package org.nemesis.antlr.memory;

import java.io.IOException;
import java.io.PrintStream;
import org.nemesis.antlr.memory.tool.MemoryTool;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileManager;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.JFS;
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
    private final JFS jfs;
    private final JavaFileManager.Location grammarSourceLocation;
    private final Path virtualSourcePath;
    private final Path virtualImportDir;
    private final Set<AntlrGenerationOption> opts = EnumSet.noneOf(AntlrGenerationOption.class);
    private final JavaFileManager.Location outputLocation;

    static AntlrGenerator fromResult(AntlrGenerationResult result) {
        return new AntlrGenerator(AntlrGeneratorBuilder.fromResult(result));
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

    public Path importDir() {
        return virtualImportDir();
    }

    public Path sourcePath() {
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

    public AntlrGeneratorBuilder<AntlrGenerationResult> toBuilder() {
        return AntlrGeneratorBuilder.fromGenerator(this);
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
    }

    public static <T> AntlrGeneratorBuilder<T> builder(JFS jfs, Function<? super AntlrGeneratorBuilder<T>, T> func) {
        return new AntlrGeneratorBuilder<>(jfs, func);
    }

    public static AntlrGeneratorBuilder<AntlrGenerator> builder(JFS jfs) {
        return builder(jfs, AntlrGenerator::new);
    }

    public JFS jfs() {
        return jfs;
    }

    public Path packagePath() {
        return Paths.get(packageName.replace('.', '/'));
    }

    public Path grammarFilePath(String fileName) throws IOException {
        if (fileName.indexOf('.') < 0) {
            fileName += ".g4";
        }
        return packagePath().resolve(fileName);
    }

    public AntlrGenerationResult run(String grammarFileName, PrintStream logStream, boolean generate) {
        return Debug.runObject(this, "Generate " + grammarFileName + " - " + generate, () -> {
            List<ParsedAntlrError> errors = new ArrayList<>();
            List<String> infos = new ArrayList<>();
            String[] args = AntlrGenerationOption.toAntlrArguments(
                    virtualSourcePath,
                    opts,
                    grammarEncoding,
                    packageName,
                    virtualImportDir());

            boolean success = true;
            Throwable thrown = null;
            int code = -1;
            Map<JFSFileObject, Long> modificationDates = new HashMap<>();
            Set<JFSFileObject> files = new HashSet<>();
            JFSFileObject[] grammarFile = new JFSFileObject[1];
            long[] grammarFileLastModified = new long[]{0};
            Path grammarFilePath;
            Set<Grammar> grammars = new HashSet<>();
            String grammarName = "--";
            Grammar mainGrammar = null;
            try {
                MemoryTool tool = MemoryTool.create(virtualSourcePath, jfs, grammarSourceLocation,
                        outputLocation, logStream, args);
                tool.generate_ATN_dot = opts.contains(AntlrGenerationOption.GENERATE_ATN);
                tool.grammarEncoding = grammarEncoding.name();
                tool.gen_dependencies = opts.contains(AntlrGenerationOption.GENERATE_DEPENDENCIES);
                tool.longMessages = opts.contains(AntlrGenerationOption.LONG_MESSAGES);
                tool.log = opts.contains(AntlrGenerationOption.LOG);
                tool.force_atn = opts.contains(AntlrGenerationOption.FORCE_ATN);
                tool.genPackage = packageName;
                jfs.listAll().entrySet().stream().filter((e) -> {
                    return grammarSourceLocation.equals(e.getValue()) || outputLocation.equals(e.getValue());
                }).forEach((f) -> {
                    files.add(f.getKey());
                    modificationDates.put(f.getKey(), f.getKey().getLastModified());
                });
                grammarFilePath = grammarFilePath(grammarFileName);
                mainGrammar = tool.withCurrentPath(grammarFilePath, () -> {
                    Grammar result = tool.loadGrammar(grammarFileName, fo -> {
                        grammarFileLastModified[0] = fo.getLastModified();
                        grammarFile[0] = fo;
                    });
                    if (result != null) {
                        grammars.add(result);
                        if (generateAll) {
                            generateAllGrammars(tool, result, new HashSet<>(), generate, grammars);
                        }
                    }
                    return result;
                });
                if (mainGrammar != null) {
                    grammarName = mainGrammar.name;
                    Debug.success("Generated " + grammarName, this::toString);
                } else {
                    Debug.failure("Not-generated " + grammarFileName, this::toString);
                    success = false;
                }
                errors.addAll(tool.errors());
                infos.addAll(tool.infoMessages());
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Error loading grammar " + grammarFileName, ex);
                thrown = ex;
                success = false;
                LOG.log(Level.SEVERE, grammarName, ex);
            }
            if (!errors.isEmpty()) {
                LOG.log(Level.INFO, "Errors generating virtual Antlr sources");
                if (LOG.isLoggable(Level.FINEST)) {
                    for (ParsedAntlrError e : errors) {
                        LOG.log(Level.FINEST, "Antlr error: {0}", e);
                    }
                }
            }
            success &= errors.isEmpty();
            Set<JFSFileObject> postFiles = new HashSet<>();
            Map<JFSFileObject, Long> touchedLastModified = new HashMap<>();
            jfs.listAll().entrySet().stream().filter((e) -> {
                return grammarSourceLocation.equals(e.getValue()) || outputLocation.equals(e.getValue());
            }).forEach((f) -> {
                JFSFileObject file = f.getKey();
                if (!files.contains(file)) {
                    LOG.log(Level.FINEST, "Generated in {0}: {1}",
                            new Object[]{f.getKey(), f.getValue()});
                }
                Long mod = modificationDates.get(f.getKey());
                long currentLastModified = f.getKey().getLastModified();
                if (mod == null) {
                    touchedLastModified.put(file, currentLastModified);
                } else if (mod < currentLastModified) {
                    touchedLastModified.put(file, currentLastModified);
                } else if (file.storageKind().isMasqueraded()) {
                    touchedLastModified.put(file, currentLastModified);
                }
                postFiles.add(f.getKey());
            });
            postFiles.removeAll(files);
            code = MemoryTool.attemptedExitCode(thrown);
            return new AntlrGenerationResult(success, code, thrown, grammarName,
                    mainGrammar, errors, grammarFile[0], grammarFileLastModified[0],
                    infos, postFiles, touchedLastModified, grammars, jfs,
                    grammarSourceLocation, outputLocation, packageName,
                    virtualSourcePath, virtualImportDir, this.generateAll,
                    this.opts, this.grammarEncoding);
        });
    }

    private static String keyFor(Grammar g) {
        return g.name + ":" + g.getTypeString();
    }

    private Path virtualImportDir() {
        return virtualImportDir == null ? Paths.get("imports") : virtualImportDir;
    }

    private void generateAllGrammars(MemoryTool tool, Grammar g, Set<String> seen, boolean generate, Set<Grammar> grammars) {
        if (!seen.contains(keyFor(g))) {
            LOG.log(Level.FINEST, "MemoryTool generating {0}", g.fileName);
            seen.add(keyFor(g));
            if (g.implicitLexer != null) {
                tool.process(g.implicitLexer, generate);
            }
            tool.process(g, generate);
            if (g.isCombined()) {
                String suffix = Grammar.getGrammarTypeToFileNameSuffix(ANTLRParser.LEXER);
                String lexer = g.name + suffix + ".g4";
                Path srcPath = packagePath().resolve(lexer);
                JFSFileObject lexerFo = jfs.get(grammarSourceLocation, srcPath);
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
                        Grammar lexerGrammar = tool.withCurrentPathThrowing(Paths.get(lexerFo.getName()), () -> {
                            Grammar result = tool.loadDependentGrammar(g.name, finalLexerFo);
                            LOG.log(Level.FINEST, "Generate lexer {0}", result.fileName);
                            return result;
                        });
                        grammars.add(lexerGrammar);
                        generateAllGrammars(tool, lexerGrammar, seen, generate, grammars);
                    } catch (IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                }
            }
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

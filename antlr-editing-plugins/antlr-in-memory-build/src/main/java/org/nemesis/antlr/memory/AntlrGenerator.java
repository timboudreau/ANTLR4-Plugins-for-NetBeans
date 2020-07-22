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

import com.mastfrog.util.path.UnixPath;
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
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ErrorType;
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
    private final UnixPath virtualSourcePath;
    private final UnixPath virtualImportDir;
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

    public UnixPath packagePath() {
        return UnixPath.get(packageName.replace('.', '/'));
    }

    public UnixPath grammarFilePath(String fileName) {
        if (fileName.indexOf('.') < 0) {
            fileName += ".g4";
        }
        return packagePath().resolve(fileName);
    }

    private UnixPath resolveSourcePath(String grammarFileName) {
        // In the case of grammars in the default package, there may be
        // some issues with null parents and resolving siblings, because of
        // how UnixPath works.
        UnixPath result = virtualSourcePath;
        if (result == null || result.toString().isEmpty()) {
            return grammarFilePath(grammarFileName);
        }
        return result;
    }

    private String listJFS() {
        StringBuilder sb = new StringBuilder("Input JFS:");
        jfs.list(sourceLocation(), (loc, jfo) -> {
            sb.append('\n').append(jfo.getName()).append(" len ").append(jfo.length());
        });
        return sb.toString();
    }


    public AntlrGenerationResult run(String grammarFileName, PrintStream logStream, boolean generate) {
        System.out.println("RUN " + grammarFileName);
        System.out.println(listJFS());
        return Debug.runObject(this, "Generate " + grammarFileName + " - " + generate, () -> {
            List<ParsedAntlrError> errors = new ArrayList<>();
            List<String> infos = new ArrayList<>();
            Throwable thrown = null;
            int code = -1;
            Map<JFSFileObject, Long> modificationDates = new HashMap<>();
            Set<JFSFileObject> files = new HashSet<>();
            JFSFileObject[] grammarFile = new JFSFileObject[1];
            long[] grammarFileLastModified = new long[]{0};
            Set<Grammar> grammars = new HashSet<>();
            Grammar[] mainGrammar = new Grammar[1];
            boolean[] success = new boolean[]{true};

            String[] gn = new String[]{"--"};
            try {
                String[] args = AntlrGenerationOption.toAntlrArguments(
                        resolveSourcePath(grammarFileName),
                        opts,
                        grammarEncoding,
                        packageName,
                        virtualImportDir());

                MemoryTool.run(virtualSourcePath, jfs, grammarSourceLocation,
                        outputLocation, logStream, args, tool -> {
                            Path grammarFilePath;
                            String grammarName = "--";
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
                            mainGrammar[0] = tool.withCurrentPath(grammarFilePath, () -> {
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
                            if (mainGrammar[0] != null) {
                                grammarName = mainGrammar[0].name;
                                gn[0] = grammarName;
                                Debug.success("Generated " + grammarName, this::toString);
                            } else {
                                Debug.failure("Not-generated " + grammarFileName, this::toString);
                                success[0] = false;
                            }
                            errors.addAll(tool.errors());
                            infos.addAll(tool.infoMessages());
                            return null;
                        });
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Error loading grammar " + grammarFileName, ex);
                thrown = ex;
                success[0] = false;
                LOG.log(Level.SEVERE, gn[0], ex);
            }
            if (!errors.isEmpty()) {
                LOG.log(Level.FINE, "Errors generating virtual Antlr sources");
                if (LOG.isLoggable(Level.FINEST)) {
                    for (ParsedAntlrError e : errors) {
                        LOG.log(Level.FINEST, "Antlr error: {0}", e);
                    }
                }
            }
            if (success[0] && !errors.isEmpty()) {
                for (ParsedAntlrError e : errors) {
                    if (e.isError()) {
                        success[0] = false;
                        break;
                    }
                }
            }
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
            return new AntlrGenerationResult(success[0], code, thrown, gn[0],
                    mainGrammar[0], errors, grammarFile[0], grammarFileLastModified[0],
                    infos, postFiles, touchedLastModified, grammars, jfs,
                    grammarSourceLocation, outputLocation, packageName,
                    virtualSourcePath, virtualImportDir, this.generateAll,
                    this.opts, this.grammarEncoding);
        });
    }

    private static String keyFor(Grammar g) {
        return g.name + ":" + g.getTypeString();
    }

    private UnixPath virtualImportDir() {
        return virtualImportDir == null ? UnixPath.get("imports") : virtualImportDir;
    }

    private void generateAllGrammars(MemoryTool tool, Grammar g, Set<String> seen, boolean generate, Set<Grammar> grammars) {
        if (!seen.contains(keyFor(g))) {
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
                    tool.error(new ANTLRMessage(ErrorType.ERROR_READING_IMPORTED_GRAMMAR, ex, null));
                }
            }
            if (g.isCombined()) {
                String suffix = Grammar.getGrammarTypeToFileNameSuffix(ANTLRParser.LEXER);
                String lexer = g.name + suffix + ".g4";
                UnixPath srcPath = packagePath().resolve(lexer);
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

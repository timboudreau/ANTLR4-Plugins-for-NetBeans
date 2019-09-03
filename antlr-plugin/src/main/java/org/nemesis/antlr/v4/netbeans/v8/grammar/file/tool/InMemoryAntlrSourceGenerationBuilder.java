package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.IOException;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.toolext.MemoryTool;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.toolext.MemoryTool.AttemptedExit;
import org.nemesis.jfs.javac.CompileJavaSources;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ExtractionCodeGenerator;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.InMemoryParseProxyBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParseProxyBuilder;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import static org.nemesis.antlr.v4.netbeans.v8.util.RandomPackageNames.newPackageName;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.Task;
import org.openide.util.TaskListener;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
public final class InMemoryAntlrSourceGenerationBuilder implements AntlrSourceGenerationBuilder {

    private static final Logger LOG = Logger.getLogger(InMemoryAntlrSourceGenerationBuilder.class.getName());

    private final JFS jfs;
    private final Set<Path> classpath = new LinkedHashSet<>();
    private AtomicBoolean cancellation = new AtomicBoolean();
    private String pkg = newPackageName();
    private final Set<AntlrRunOption> options = EnumSet.noneOf(AntlrRunOption.class);
    private volatile boolean initialBuildDone;
    private final Path sourceFile;
    private Path importDir;
    volatile boolean stale = true;
    private AntlrLibrary lib;
    private DocumentAndFileListener listener = new DocumentAndFileListener();
    private final Set<Path> additionalSourceDirMasquerades = new HashSet<>();
    private final Set<Path> additionalSourceDirCopies = new HashSet<>();
    private Consumer<AntlrSourceGenerationResult> onRegenerate;
    private String virtualGrammarBody;

    InMemoryAntlrSourceGenerationBuilder(Path sourceFile) {
        try {
            jfs = JFS.builder().withCharset(UTF_8).build();
        } catch (IOException ex) {
            throw new IllegalStateException(ex); // won't happen
        }
        this.sourceFile = sourceFile;
    }

    public synchronized void fullReset() {
        initialBuildDone = false;
        listener = null;
        stale = true;
        listener = new DocumentAndFileListener();
        cancellation.set(false);
        for (JFSFileObject fo : antlrGeneratedFiles) {
            fo.delete();
        }
        antlrGeneratedFiles.clear();
    }

    public InMemoryAntlrSourceGenerationBuilder replacingAntlrGrammarWith(String body) throws IOException {
        if (!Objects.equals(virtualGrammarBody, body)) {
            stale = true;
            this.virtualGrammarBody = body;
            if (initialBuildDone) {
                JFSFileObject file = jfs.get(SOURCE_PATH, virtualSourceFile());
                if (file != null) {
                    file.delete();
                }
                fullReset();
                jfs.create(virtualSourceFile(), SOURCE_PATH, virtualGrammarBody);
            }
        }
        return this;
    }

    public static InMemoryAntlrSourceGenerationBuilder forAntlrSource(Path sourceFile) {
        return new InMemoryAntlrSourceGenerationBuilder(sourceFile);
    }

    public InMemoryAntlrSourceGenerationBuilder mapIntoSourcePackage(Path path) {
        additionalSourceDirMasquerades.add(path);
        return this;
    }

    public InMemoryAntlrSourceGenerationBuilder copyIntoSourcePackage(Path path) {
        additionalSourceDirCopies.add(path);
        return this;
    }

    public InMemoryAntlrSourceGenerationBuilder setAntlrLibrary(AntlrLibrary lib) {
        this.lib = lib;
        return this;
    }

    private Path virtualImportDir() {
        return Paths.get("import");
    }

    private Path virtualImportPath(Path filename) {
        return virtualImportDir().resolve(filename.getFileName());
    }

    private synchronized void initialBuild() throws IOException {
        LOG.log(Level.FINE, "Initial build of {0}", sourceFile);
        AntlrLibrary lib = this.lib != null ? this.lib : AntlrLibrary.forOwnerOf(sourceFile);
        List<Path> classpath = new ArrayList<>(Arrays.asList(lib.paths()));
        classpath.add(CompileJavaSources.jarPathFor(AntlrLibrary.class));
        jfs.setClasspath(classpath);
        Set<Path> masqueraded = new HashSet<>();
        if (importDir == null) {
            AntlrFolders.IMPORT.getPath(ProjectHelper.getProject(this.sourceFile), Optional.of(sourceFile));
        }
        if (importDir != null) {
            LOG.log(Level.FINER, "Map .g4 files in import dir {0}", importDir);
            if (Files.exists(importDir)) {
                try {
                    Files.list(importDir).filter(p -> {
                        return p.getFileName().toString().endsWith(".g4");
                    }).forEach(pth -> {
                        Path vip = virtualImportPath(pth);
                        LOG.log(Level.FINEST, "Map {0} to virtual {1}", new Object[]{pth, vip});
                        masqueraded.add(pth);
                        masqueradeFile(pth, FileUtil.toFileObject(pth.toFile()), virtualImportPath(pth));
                    });
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        if (!classpath.isEmpty()) {
            LOG.log(Level.FINER, "Set classpath to {0}", classpath);
            jfs.setClasspath(classpath);
        }
        for (Path pth : additionalSourceDirMasquerades) {
            Path vip = virtualSourcePath().resolve(pth.getFileName());
            boolean alreadyDone = masqueraded.contains(pth);
            LOG.log(Level.FINEST, "Additional src masquerade {0} as {1} needed? {2}", new Object[]{pth, vip, !alreadyDone});
            if (!alreadyDone) {
                masqueradeFile(pth, FileUtil.toFileObject(pth.toFile()), vip);
            }
        }
        for (Path pth : additionalSourceDirCopies) {
            Path vip = virtualSourcePath().resolve(pth.getFileName());
            boolean alreadyDone = masqueraded.contains(pth);
            LOG.log(Level.FINEST, "Additional src COPY {0} as {1} needed? {2}", new Object[]{pth, vip, !alreadyDone});
            if (!alreadyDone) {
                jfs.copy(pth, SOURCE_PATH, vip);
            }
//            masqueradeFile(pth, FileUtil.toFileObject(pth.toFile()), virtualSourcePath().resolve(pth.getFileName()));
        }
        Path vsf = virtualSourceFile();
        if (this.virtualGrammarBody != null) {
            LOG.log(Level.FINER, "Substituting virtual grammar body of length {0}", virtualGrammarBody.length());
            jfs.create(vsf, SOURCE_PATH, virtualGrammarBody);
        } else {
            LOG.log(Level.FINER, "Masq {0} as {1}", new Object[]{sourceFile, vsf});
            masqueradeFile(sourceFile, FileUtil.toFileObject(sourceFile.toFile()),
                    virtualSourceFile());
        }
        // For complex grammars, map any siblings that might be imported.
        // Really, we should get this from the semantic parser
        Files.list(sourceFile.getParent()).filter(p -> {
            return !p.equals(sourceFile) && p.getFileName().toString().endsWith(".g4");
        }).forEach(sibling -> {
            boolean alreadyDone = masqueraded.contains(sibling) || sibling.getFileName().equals(sourceFile.getFileName());
            if (!alreadyDone) {
                Path vsib = virtualSourcePath().resolve(sibling.getFileName());
                LOG.log(Level.FINEST, "Masq src sibling {0} as {1} needed? {2}", new Object[]{sibling, vsib, !alreadyDone});
                masqueradeFile(sourceFile, FileUtil.toFileObject(sourceFile.toFile()),
                        vsib);
            }
        });
        initialBuildDone = true;
//        System.out.println(list());
    }

    private String list() {
        StringBuilder sb = new StringBuilder("---------------- JFS CONTENTS ----------------- \n");
        for (Map.Entry<JFSFileObject, JavaFileManager.Location> e : jfs.listAll().entrySet()) {
            sb.append(e.getKey().getName()).append('\t').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    public Path sourceFile() {
        return sourceFile;
    }

    private Path virtualSourcePath() {
        return Paths.get(pkg.replace('.', '/'));
    }

    private Path virtualSourceFile() {
        return virtualSourcePath().resolve(sourceFile.getFileName());
    }

    @Override
    public ParseProxyBuilder toParseAndRunBuilder() {
        return new InMemoryParseProxyBuilder(this);
    }

    @Override
    public AntlrSourceGenerationResult build() throws IOException {
        return build(true);
    }

    AntlrSourceGenerationResult lastResult;

    private Set<JFSFileObject> antlrGeneratedFiles = new HashSet<>();

    private PrintStream toolLogStream;

    public InMemoryAntlrSourceGenerationBuilder setToolLogStream(PrintStream stream) {
        toolLogStream = stream;
        return this;
    }

    public synchronized AntlrSourceGenerationResult build(boolean generate) throws IOException {
        if (!initialBuildDone) {
            initialBuild();
        } else if (!stale && lastResult != null) {
            return lastResult;
        }
        Set<JFSFileObject> files = new HashSet<>();
        jfs.listAll().entrySet().stream().filter(e -> {
            return e.getValue() == StandardLocation.SOURCE_PATH && e.getKey().getName().endsWith(".java");
        }).forEach(f -> {
            files.add(f.getKey());
        });

        String[] args = AntlrRunOption.toAntlrArguments(virtualSourceFile(), options, jfs.encoding(), virtualSourcePath(), pkg,
                importDir == null ? null : virtualImportDir());
        Exception thrown = null;
        List<ParsedAntlrError> errors = new ArrayList<>();
        List<String> infos = new ArrayList<>();
        boolean success = false;
        String grammarName = "--";
        try {
            MemoryTool tool = new MemoryTool(jfs, SOURCE_PATH, virtualSourcePath(), args);
            tool.setLogStream(toolLogStream);
            tool.gen_listener = true;
            tool.gen_visitor = true;
//        tool.generate_ATN_dot = true;
            tool.grammarEncoding = jfs.encoding().name();
            tool.gen_dependencies = true;
            tool.longMessages = true;

            Grammar g = tool.loadGrammar(sourceFile.getFileName().toString());
            grammarName = g.name;
            generateAllGrammars(tool, g, new HashSet<>(), generate);
            errors.addAll(tool.errors());
            infos.addAll(tool.infoMessages());
            if (!errors.isEmpty()) {
                LOG.log(Level.INFO, "Errors generating virtual Antlr sources");
                if (LOG.isLoggable(Level.FINEST)) {
                    for (ParsedAntlrError e : errors) {
                        LOG.log(Level.FINEST, "Antlr error: {0}", e);
                    }
                }
            }
            success = errors.isEmpty();
        } catch (Exception ex) {
            thrown = ex;
            success = false;
            LOG.log(Level.FINE, "Exception generating from source file " + sourceFile.getFileName(), ex);
        }
        Set<JFSFileObject> postFiles = new HashSet<>();
        jfs.listAll().entrySet().stream().filter(e -> {
            return e.getValue() == StandardLocation.SOURCE_PATH && e.getKey().getName().endsWith(".java");
        }).forEach(f -> {
            if (!files.contains(f.getKey())) {
                LOG.log(Level.FINEST, "Generated in {0}: {1}", new Object[]{f.getKey(), f.getValue()});
            }
            postFiles.add(f.getKey());
        });
        postFiles.removeAll(files);
        Set<Path> generatedFiles = new HashSet<>();
        antlrGeneratedFiles.forEach((fo) -> {
            generatedFiles.add(fo.toPath());
        });
        if (success) {
            JFSFileObject extractor = jfs.get(SOURCE_PATH, packagePath().resolve("ParserExtractor.java"));
            if (extractor == null && grammarName != null) {
                extractor = ExtractionCodeGenerator.saveExtractorSourceCode(sourceFile, jfs, pkg, grammarName);
            }
        }
        Integer code = thrown instanceof AttemptedExit ? ((AttemptedExit) thrown).code() : null;
        AntlrSourceGenerationResult result = new AntlrSourceGenerationResult(sourceFile, Paths.get(""), packagePath(), pkg, success,
                Optional.ofNullable(thrown), Optional.ofNullable(code), errors, generatedFiles, null, grammarName,
                cancellation);
        synchronized (this) {
            lastResult = result;
            antlrGeneratedFiles.addAll(files);
            stale = false;
        }
        if (onRegenerate != null) {
            onRegenerate.accept(result);
        }
        return result;
    }

    public void onRegenerate(Consumer<AntlrSourceGenerationResult> onRegenerate) {
        this.onRegenerate = onRegenerate;
    }

    private Path packagePath() {
        return Paths.get(pkg.replace('.', '/'));
    }

    private static String keyFor(Grammar g) {
        return g.name + ":" + g.getTypeString();
    }

    private void generateAllGrammars(MemoryTool tool, Grammar g, Set<String> seen, boolean generate) {
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
                JFSFileObject lexerFo = jfs.get(SOURCE_PATH, srcPath);
                if (lexerFo == null) {
                    lexer = g.name + suffix + ".g";
                    srcPath = packagePath().resolve(lexer);
                    lexerFo = jfs.get(SOURCE_PATH, srcPath);
                }
                if (lexerFo == null) {
                    srcPath = virtualImportDir().resolve(lexer);
                    lexerFo = jfs.get(SOURCE_PATH, srcPath);
                }
                if (lexerFo != null) {
                    try {
                        Grammar lexg = tool.loadDependentGrammar(g.name, lexerFo);
                        LOG.log(Level.FINEST, "Generate lexer {0}", lexg.fileName);
                        generateAllGrammars(tool, lexg, seen, generate);
                    } catch (IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                }
            }
        }
    }

    @Override
    public InMemoryAntlrSourceGenerationBuilder addToClasspath(Path p, Path... more) {
        classpath.add(p);
        classpath.addAll(Arrays.asList(more));
        return this;
    }

    @Override
    public InMemoryAntlrSourceGenerationBuilder checkCancellationOn(AtomicBoolean bool) {
        this.cancellation = bool;
        return this;
    }

    private JFSFileObject masqueradeDocument(Path pth, Document doc, Path as) {
        JFSFileObject result = jfs.masquerade(doc, SOURCE_PATH, as);
        doc.addDocumentListener(WeakListeners.document(listener, doc));
        return result;
    }

    private void masqueradeFile(Path pth, FileObject file, Path as) {
        LOG.log(Level.FINEST, "Listen on {0} virtually {1}", new Object[]{pth, as});
        // Try to masquerade the actual *document* being edited, so that
        // we parse against live changes, saved or not.  If not open
        // in the editor, use the file path and we will update when
        // it is saved
        if (file == null) {
            jfs.masquerade(pth, SOURCE_PATH, as);
        }
        try {
            DataObject dob = DataObject.find(file);
            EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
            Document doc = ck.getDocument();
            if (doc != null) {
                masqueradeDocument(pth, doc, as);
            } else {
                // start by masquerading the file, try to load the doc async
                // and if it is created, replace the file masquerade with
                // that
                JFSFileObject masquerade = jfs.masquerade(pth, SOURCE_PATH, as);
                Task t = ck.prepareDocument();
                t.addTaskListener(new TaskListener() {
                    @Override
                    public void taskFinished(Task task) {
                        Document d = ck.getDocument();
                        if (d != null) {
                            masquerade.delete();
                            // will replace the previous file version
                            LOG.log(Level.FINEST, "Masq document for {0} instead", pth);
                            JFSFileObject fo = masqueradeDocument(pth, d, as);
                            LOG.log(Level.FINEST, "Created jfsFo {0}", fo);
                        }
                    }
                });
            }
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public InMemoryAntlrSourceGenerationBuilder withImportDir(Path importDir) {
        this.importDir = importDir;
        return this;
    }

    @Override
    public InMemoryAntlrSourceGenerationBuilder withPackage(String pkg) {
        this.pkg = pkg;
        return this;
    }

    @Override
    public InMemoryAntlrSourceGenerationBuilder withRunOptions(AntlrRunOption option, AntlrRunOption... more) {
        this.options.add(option);
        for (AntlrRunOption m : more) {
            this.options.add(m);
        }
        return this;
    }

    public void touch() {
        stale = true;
    }

    public String pkg() {
        return pkg;
    }

    class DocumentAndFileListener extends FileChangeAdapter implements DocumentListener {

        @Override
        public void fileChanged(FileEvent fe) {
            touch();
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            // Probably good
            touch();
        }

        @Override
        public void fileRenamed(FileRenameEvent fe) {
            // XXX need per file listeners to detect WHAT got renamed.
            // Then some complicated updating / remapping.
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            touch();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            touch();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            touch();
        }
    }

    public boolean isStale() {
        return stale;
    }

    public JFS jfs() {
        return jfs;
    }

}

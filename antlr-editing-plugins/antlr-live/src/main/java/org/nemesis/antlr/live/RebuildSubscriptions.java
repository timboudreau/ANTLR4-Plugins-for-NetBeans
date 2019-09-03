package org.nemesis.antlr.live;

import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.tools.StandardLocation;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.project.Folders;
import static org.nemesis.antlr.project.Folders.ANTLR_GRAMMAR_SOURCES;
import static org.nemesis.antlr.project.Folders.ANTLR_IMPORTS;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.ParseResultHook;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
public final class RebuildSubscriptions {

    private static final Logger LOG = Logger.getLogger(RebuildSubscriptions.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }
    private final JFSMapping mapping = new JFSMapping();
    private static final RequestProcessor RP = new RequestProcessor("rebuild-antlr-subscriptions", 2, true);

    private final Map<Project, Generator> generatorForMapping
            = new WeakHashMap<>();

    private static RebuildSubscriptions INSTANCE;

    JFS jfsFor(Project project) { // for tests
        return mapping.getIfPresent(project);
    }

    public static int liveSubscriptions() {
        if (INSTANCE == null) {
            return 0;
        }
        return INSTANCE.generatorForMapping.size();
    }

    static synchronized RebuildSubscriptions instance() {
        if (INSTANCE == null) {
            INSTANCE = new RebuildSubscriptions();
        }
        return INSTANCE;
    }

    public static String info() {
        StringBuilder sb = new StringBuilder();
        instance().generatorForMapping.entrySet().forEach((e) -> {
            sb.append(e.getKey().getProjectDirectory().getName()).append('\n')
                    .append(e.getValue());
        });
        return sb.toString();
    }

    public static Runnable subscribe(FileObject fo, Subscriber sub) {
        return instance()._subscribe(fo, sub);
    }

    // XXX need general subscribe to mime type / all
    private Runnable _subscribe(FileObject fo, Subscriber sub) {
        LOG.log(Level.FINE, "Subscribe {0} to rebuilds of {1}", new Object[]{sub, fo});
        if (!"text/x-g4".equals(fo.getMIMEType())) {
            throw new IllegalArgumentException(fo.getNameExt() + " is not "
                    + "an antlr grammar file - its mime type is " + fo.getMIMEType());
        }
        File foFile = FileUtil.toFile(fo);
        if (foFile == null) {
            System.err.println(fo.getPath() + " is not a disk file, cannot subscribe");
            return null;
        }
        Project project = FileOwnerQuery.getOwner(fo);
        if (project == null) {
            System.err.println("Cannot subscribe to an Antlr file not in a project");
            return null;
        }
        File file = FileUtil.toFile(project.getProjectDirectory());
        if (file == null) {
            System.err.println("Project " + project + " dir cannot resolve to "
                    + "a file - not a real disk file?");
        }
        // XXX use JFS MAPPING to enable cleanup
        Generator generator = generatorForMapping.get(project);
        try {
            if (generator == null) {
                generator = new Generator(fo, project, mapping);
                LOG.log(Level.FINEST, "Create new generator for {0} in {1} for {2}",
                        new Object[]{fo.getPath(), project.getProjectDirectory().getPath(), sub});
                generatorForMapping.put(project, generator);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
        generator.hook.subscribe(sub, fo);
        Generator finalGen = generator;
        return () -> {
            boolean allUnsubscribed = finalGen.hook.unsubscribe(sub);
            if (allUnsubscribed) {
                generatorForMapping.remove(project);
            }
        };
    }

    static volatile int ids;

    static class Generator extends FileChangeAdapter {

        private final Set<Mapping> mappings = new HashSet<>();
        private final RebuildHook hook;
        private final JFSMapping mapping;
        private final Charset encoding;
        private final FileObject initialFile;
        private final int id = ids++;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Generator-")
                    .append(id)
                    .append("(").append(initialFile.getPath())
                    .append(' ').append(encoding).append(' ');
            for (Mapping m : mappings) {
                sb.append("\n  ").append(m);
            }
            return sb.append(')').toString();
        }

        @SuppressWarnings("LeakingThisInConstructor")
        Generator(FileObject subscribeTo, Project in, JFSMapping mapping) throws IOException {
            hook = new RebuildHook();
            this.mapping = mapping;
            this.initialFile = subscribeTo;
            encoding = FileEncodingQuery.getEncoding(subscribeTo);
            Set<FileObject> subscribed = new HashSet<>();
            LOG.log(Level.FINEST, "Create Generator {0} for {1}", new Object[]{id, subscribeTo.getPath()});
            Folders.ANTLR_GRAMMAR_SOURCES.allFileObjects(in)
                    .forEach(fo -> {
                        // Antlr imports may be underneath antlr sources,
                        // so don't map twice
                        Folders owner = Folders.ownerOf(fo);
                        mappings.add(map(fo, owner));
                        subscribed.add(fo);
                    });
            if (!subscribed.contains(subscribeTo)) {
                Folders owner = Folders.ownerOf(subscribeTo);
                mappings.add(map(subscribeTo, owner));
                subscribed.add(subscribeTo);
            }
            Set<FileObject> listeningToDirs = new HashSet<>();
            for (Folders f : new Folders[]{ANTLR_GRAMMAR_SOURCES, ANTLR_IMPORTS}) {
                for (FileObject dir : f.findFileObject(subscribeTo)) {
                    LOG.log(Level.FINEST, "Generator {0} recursive listen to {1}", new Object[]{id, dir.getPath()});
                    FileUtil.addRecursiveListener(this, FileUtil.toFile(dir));
                    listeningToDirs.add(dir);
                }
            }
            Folders owner = Folders.ownerOf(subscribeTo);
            if (owner != ANTLR_IMPORTS && owner != ANTLR_GRAMMAR_SOURCES) {
                for (FileObject dir : owner.findFileObject(subscribeTo)) {
                    if (!listeningToDirs.contains(dir)) {
                        FileUtil.addRecursiveListener(this, FileUtil.toFile(dir));
                        listeningToDirs.add(dir);
                    }
                }
            }
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            FileObject deleted = fe.getFile();
            new HashSet<>(mappings).stream().filter((m) -> (m.fo.equals(deleted))).forEach((m) -> {
                mappings.remove(m);
            });
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            FileObject fo = fe.getFile();
            if (fo.isData() && "text/x-g4".equals(fo.getMIMEType())) {
                Folders owner = Folders.ownerOf(fo);
                Path relativePath = Folders.ownerRelativePath(fo);
                if (owner != null && relativePath != null) {
                    map(fo, owner);
                }
            }
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
        }

        Mapping map(FileObject fo, Folders owner) {
            Path relativePath = owner == Folders.ANTLR_IMPORTS
                    ? Paths.get("imports/" + fo.getNameExt()) : Folders.ownerRelativePath(fo);
            try {
                DataObject dob = DataObject.find(fo);
                EditorCookie.Observable obs = dob.getLookup().lookup(EditorCookie.Observable.class);
                return new Mapping(fo, obs, relativePath);
            } catch (DataObjectNotFoundException ex) {
                LOG.log(Level.SEVERE, "No data object for " + fo.getPath(), ex);
                return new Mapping(fo, null, relativePath);
            } finally {
                ParseResultHook.register(fo, hook);
            }
        }

        final class Mapping extends FileChangeAdapter implements PropertyChangeListener {

            private final Path targetPath;
            private EditorCookie.Observable obs;
            private final FileObject fo;
            private MappingMode mode;

            @SuppressWarnings("LeakingThisInConstructor")
            Mapping(FileObject fo, EditorCookie.Observable obs, Path targetPath) {
                this.fo = fo;
                this.obs = obs;
                this.targetPath = targetPath;
//                if (obs != null && obs.getOpenedPanes().length > 0) {
                if (obs != null && obs.getDocument() != null) {
                    setMappingMode(MappingMode.MAP_DOCUMENT);
                } else {
                    setMappingMode(MappingMode.MAP_FILE);
                }
                LOG.log(Level.FINER, "Generator-{0} map {1} as {2}", new Object[]{id, fo.getPath(), mode});
                if (obs != null) {
                    obs.addPropertyChangeListener(WeakListeners.propertyChange(this, obs));
                } else {
                    System.err.println("no EditorCookie.Observable in " + fo.getPath());
                }
                fo.addFileChangeListener(FileUtil.weakFileChangeListener(this, fo));
            }

            public String toString() {
                return fo.getPath() + " -> " + targetPath + " as " + mode;
            }

            synchronized void initMapping(JFS jfs) {
                switch (mode) {
                    case MAP_DOCUMENT: {
                        if (obs == null) {
                            try {
                                obs = DataObject.find(fo).getLookup().lookup(EditorCookie.Observable.class);
                                if (obs != null) {
                                    obs.addPropertyChangeListener(WeakListeners.propertyChange(this, obs));
                                }
                            } catch (DataObjectNotFoundException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                        try {
                            if (obs != null) {
                                Document doc = obs.openDocument();
                                JFSFileObject nue = jfs.masquerade(doc, StandardLocation.SOURCE_PATH, targetPath);
                                break;
                            }
                        } catch (IOException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    }
                    // fallthrough in case of exception
                    case MAP_FILE:
                        this.mode = MappingMode.MAP_FILE;
                        File file = FileUtil.toFile(fo);
                        if (file != null) {
                            jfs.masquerade(file.toPath(), StandardLocation.SOURCE_PATH, targetPath);
                        } else {
                            System.err.println("Cannot map " + fo
                                    + " into JFS - not a disk file");
                        }
                        break;
                    default:
                        throw new AssertionError(mode);
                }
            }

            synchronized void setMappingMode(MappingMode mode) {
                if (mode != this.mode) {
                    LOG.log(Level.FINER, "Mapping mode to {1} for {2} in Generator {0}", new Object[]{id,
                        mode,
                        fo.getName()});
                    this.mode = mode;
                    Project project = FileOwnerQuery.getOwner(fo);
                    if (project != null) {
                        LOG.log(Level.FINER, "Init JFS mappings for Generator {0}", new Object[]{id,
                            project.getProjectDirectory().getName()});
                        JFS jfs = mapping.getIfPresent(project);
                        if (jfs != null) {
                            initMapping(jfs);
                        }
                    } else {
                        LOG.log(Level.FINER, "No project for Generator {0}", new Object[]{id,
                            project.getProjectDirectory().getName()});
                    }
                }
            }

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
//                System.out.println("GENERATOR " + id + " got cookie change " + fo.getName()
//                        + " " + evt.getPropertyName() + " -> " + evt.getNewValue());

                LOG.log(Level.FINEST, "Generator-{0} got cookie change {1} from {2}", new Object[]{id,
                    evt.getNewValue(), fo.getName()});
                EditorCookie.Observable ec = (EditorCookie.Observable) evt.getSource();
                if (EditorCookie.Observable.PROP_OPENED_PANES.equals(evt.getPropertyName())) {
                    JTextComponent[] comp = (JTextComponent[]) evt.getNewValue();
                    if (comp == null || comp.length == 0) {
                        setMappingMode(MappingMode.MAP_FILE);
                    } else {
                        setMappingMode(MappingMode.MAP_DOCUMENT);
                    }
                } else if (EditorCookie.Observable.PROP_DOCUMENT.equals(evt.getPropertyName())) {
                    if (evt.getNewValue() != null) {
                        setMappingMode(MappingMode.MAP_DOCUMENT);
                    } else {
                        setMappingMode(MappingMode.MAP_FILE);
                    }
                }
            }
        }

        static enum MappingMode {
            MAP_FILE,
            MAP_DOCUMENT;
        }

        class RebuildHook extends ParseResultHook<GrammarFileContext> {

            AntlrGenerator gen;
            private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

            public RebuildHook() {
                super(GrammarFileContext.class);
            }

            void subscribe(Subscriber subscribe, FileObject to) {
                subscribers.add(subscribe);
                // Trigger a parse immediately
                if (subscribers.size() == 1) {
                    Source src = Source.create(to);
                    LOG.log(Level.FINE, "Force parse of {0} for initial subscriber in Generator {1}",
                            new Object[]{to.getName(), id});
                    Runnable doParse = () -> {
                        try {
                            ParserManager.parse(Collections.singleton(src), new UserTask() {
                                @Override
                                public void run(ResultIterator resultIterator) throws Exception {
                                    resultIterator.getParserResult();
                                }
                            });
                        } catch (ParseException ex) {
                            LOG.log(Level.INFO, "Exception parsing " + to.getPath(), ex);
                        }
                    };
                    if (EventQueue.isDispatchThread()) {
                        RP.post(doParse);
                    } else {
                        doParse.run();
                    }
                } else {
                    LOG.log(Level.FINE, "Add subscriber {0} to {1}", new Object[]{subscribe, to.getPath()});
                }
            }

            boolean unsubscribe(Subscriber subscribe) {
                LOG.log(Level.FINEST, "Unsubscribe {0} from {1} for {2}",
                        new Object[]{subscribe, id, initialFile.getPath()});
                subscribers.remove(subscribe);
                return subscribers.isEmpty();
            }

            private void initMappings(JFS jfs) {
                mappings.forEach((m) -> {
                    m.initMapping(jfs);
                });
            }

            private synchronized AntlrGenerator gen(Extraction ext) throws IOException {
                FileObject fo = ext.source().lookup(FileObject.class).get();
                Project project = FileOwnerQuery.getOwner(fo);
                JFS jfs = mapping.forProject(project);
                if (gen != null) {
                    if (jfs != gen.jfs()) {
                        gen = null;
                        LOG.log(Level.FINE, "Got a different JFS, reinit mappings for generator {0}", id);
                        initMappings(jfs);
                    } else {
                        return gen;
                    }
                } else {
                    initMappings(jfs);
                }

                Folders owner = Folders.ownerOf(fo);
                LOG.log(Level.FINER, "Create JFS for {0} owned by {1} in generator ", new Object[]{fo.getName(), owner, id});
                Path relPath = owner == ANTLR_IMPORTS ? Paths.get("imports/" + fo.getNameExt())
                        : Folders.ownerRelativePath(fo);
                return gen = AntlrGenerator.builder(jfs)
                        .building(relPath.getParent() == null ? Paths.get("") : relPath.getParent(), Paths.get("imports"));
            }

            @Override
            protected void onReparse(GrammarFileContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) throws Exception {

                AntlrGenerator gen = gen(extraction);
                AntlrGenerationResult result = mapping.whileLocked(gen.jfs(), () -> {
                    return gen.run(extraction.source().name(), System.err, true);
                });

                LOG.log(Level.WARNING, "Reparse received by genertor {0} for {1} placeholder {2} result usable {3} run result {4} send to {5} subscribers",
                        new Object[]{id, extraction.source().name(),
                            extraction.isPlaceholder(), result.isUsable(), result, subscribers.size()});

                subscribers.forEach((s) -> {
                    try {
                        s.onRebuilt(tree, mimeType, extraction, result, populate, fixes);
                    } catch (Exception e) {
                        Logger.getLogger(RebuildHook.class.getName()).log(Level.SEVERE,
                                "Exception processing parse result of "
                                + extraction.source(), e);
                    }
                });
            }
        }
    }
}

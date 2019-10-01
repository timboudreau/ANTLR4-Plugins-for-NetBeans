package org.nemesis.antlr.live;

import com.mastfrog.util.collections.CollectionUtils;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.tools.StandardLocation;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.memory.AntlrGeneratorBuilder;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.antlr.project.Folders;
import static org.nemesis.antlr.project.Folders.ANTLR_GRAMMAR_SOURCES;
import static org.nemesis.antlr.project.Folders.ANTLR_IMPORTS;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.ParseResultHook;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.misc.utils.CachingSupplier;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.queries.FileEncodingQuery;
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
import org.openide.util.WeakSet;

/**
 * Allows callers to pass a callback which will be triggered whenever a new
 * parser result for an Antlr file is being constructed, and process it (for
 * example, compiling it in-memory and running something against the compiled
 * classes in an isolating classloader, or adding hints, or whatever).
 *
 * @author Tim Boudreau
 */
public final class RebuildSubscriptions {

    private static final Logger LOG = Logger.getLogger(RebuildSubscriptions.class.getName());
    private final JFSMapping mapping = new JFSMapping();
    private static final RequestProcessor RP = new RequestProcessor("rebuild-antlr-subscriptions", 2, true);
    private final Map<Project, Generator> generatorForMapping
            = new WeakHashMap<>();

    static Supplier<RebuildSubscriptions> INSTANCE_SUPPLIER
            = CachingSupplier.of(RebuildSubscriptions::new);

    JFS jfsFor(Project project) { // for tests
        return mapping.getIfPresent(project);
    }

    public static int liveSubscriptions() {
        return INSTANCE_SUPPLIER.get().generatorForMapping.size();
    }

    static RebuildSubscriptions instance() {
        return INSTANCE_SUPPLIER.get();
    }

    public static String info() { // debug stuff, deleteme
        StringBuilder sb = new StringBuilder();
        instance().generatorForMapping.entrySet().forEach((e) -> {
            sb.append(e.getKey().getProjectDirectory().getName()).append('\n')
                    .append(e.getValue());
        });
        return sb.toString();
    }

    /**
     * Subscribe the passed subscriber to re-parses of the passed file object.
     * If this is the first call to subscribe for this file, a parse will be
     * triggered.
     *
     * @param fo A fileobject for an Antlr grammar file
     * @param sub A subscriber that will be invoked on reparse
     * @return A runnable which will unsubscribe the subscriber; the surrounding
     * plumbing that enables the subscription is strongly referenced <i>by</i>
     * the unsubscriber - allow that to be garbage collected and you may not get
     * notified of changes.
     */
    public static Runnable subscribe(FileObject fo, Subscriber sub) {
        return instance()._subscribe(fo, sub);
    }

    /**
     * Get the most recent last modified time for any grammar file in the
     * grammar source folders of the project that owns the passed file,
     * preferring the most recent (saved or not) <i>Document</i> edit times
     * where those are available, since we JFS-masquerade documents when
     * present, and listen and update a timestamp for them. If no subscribers
     * are registered for the project, returns the file timestamps.
     *
     * @param fo A file object
     * @return The most recent last modified time, or -1 if it cannot be
     * determined (grammar file deleted, etc.)
     * @throws IOException If something goes wrong
     */
    public static long mostRecentGrammarLastModifiedInProjectOf(FileObject fo) throws IOException {
        String mime = fo.getMIMEType();
        Project p = FileOwnerQuery.getOwner(fo);
        if (p == null) {
            return -1;
        }
        Generator g = instance().generatorForMapping.get(p);
        if (g == null) {
            return mostRecentGrammarLastModifiedInProjectTheHardWay(mime, fo, p);
        }
        return g.newestGrammarLastModified();
    }

    private static long mostRecentGrammarLastModifiedInProjectTheHardWay(String mime, FileObject fo, Project project) throws IOException {
        Iterable<FileObject> files = CollectionUtils.concatenate(
                Folders.ANTLR_GRAMMAR_SOURCES.findFileObject(project, fo),
                Folders.ANTLR_IMPORTS.findFileObject(project, fo));
        long result = -1;
        for (FileObject curr : files) {
            if ("text/x-g4".equals(curr.getMIMEType())) {
                result = Math.max(result, curr.lastModified().getTime());
            }
        }
        return result == -1 ? fo.lastModified().getTime() : result;
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
            LOG.log(Level.WARNING, "Not a disk file, cannot subscribe to {0}", fo.getPath());
            return null;
        }
        Project project = FileOwnerQuery.getOwner(fo);
        if (project == null) {
            LOG.log(Level.WARNING, "Not a disk file, cannot subscribe to {0}", fo.getPath());
            return null;
        }
        File file = FileUtil.toFile(project.getProjectDirectory());
        if (file == null) {
            LOG.log(Level.WARNING, "Project dir for {0} not a disk file"
                    + ", cannot subscribe to {1}", new Object[]{project, fo.getPath()});
            return null;
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
            // Create the callback that will be invoked
            hook = new RebuildHook();
            this.mapping = mapping;
            this.initialFile = subscribeTo;
            encoding = FileEncodingQuery.getEncoding(subscribeTo);
            Set<FileObject> subscribed = new HashSet<>();
            LOG.log(Level.FINEST, "Create Generator {0} for {1}", new Object[]{id, subscribeTo.getPath()});
            // Find every file under the sources and imports dirs, and map them
            // into the JFS - masquerading documents if a document exists for
            // the file (it's open in the editor), otherwise masquerade the file,
            // and listen for document creation to replace the masquerade as
            // needed
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
            // Listen on folders to ensure we detect new file creation
            // and set up mapping for newly created files
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

        public long newestGrammarLastModified() throws IOException {
            long result = Long.MIN_VALUE;
            JFS jfs = mapping.forProject(FileOwnerQuery.getOwner(initialFile));
            for (Mapping mapping : mappings) {
                if (mapping.isGrammarFile()) {
                    long val = mapping.lastModified(jfs);
                    if (val > 0) {
                        result = Math.max(result, val);
                    }
                }
            }
            return result;
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
                    LOG.log(Level.WARNING, "No EditorCookie.Observable for {0}", fo.getPath());
                }
                fo.addFileChangeListener(FileUtil.weakFileChangeListener(this, fo));
            }

            public boolean isGrammarFile() {
                return fo.isValid() && "text/x-g4".equals(fo.getMIMEType());
            }

            public long lastModified(JFS jfs) {
                JFSFileObject jfo = jfs.get(StandardLocation.SOURCE_PATH, targetPath);
                if (jfo == null) {
                    return fo.lastModified().getTime();
                }
                return jfo.getLastModified();
            }

            @Override
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
                            LOG.log(Level.WARNING, "Cannot map {0} into JFS - non-disk", fo);
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
                LOG.log(Level.FINEST, "Generator-{0} got cookie change {1} from {2}", new Object[]{id,
                    evt.getNewValue(), fo.getName()});
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
                LOG.log(Level.FINEST, "RebuildHook subscribe {0} to {1} count {2}",
                        new Object[]{subscribe, to.getPath(), subscribers.size()});
                // Trigger a parse immediately
                if (subscribers.size() == 1) {
                    LOG.log(Level.FINE, "Force parse of {0} for initial subscriber in "
                            + "Generator {1} for {2}",
                            new Object[]{to.getName(), id, subscribe});
                    Runnable doParse = () -> {
                        try {
                            ParsingUtils.parse(to);
                        } catch (Exception ex) {
                            LOG.log(Level.INFO, "Exception parsing " + to.getPath(), ex);
                        } catch (Error e) {
                            LOG.log(Level.SEVERE, "Error parsing " + to, e);
                            throw e;
                        }
                    };
                    // Try to keep this stuff off the event dispatch thread
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
                AntlrGeneratorBuilder<AntlrGenerator> agb = AntlrGenerator.builder(jfs);
                if (relPath.getParent() != null) {
                    String pkg = relPath.getParent().toString().replace('/', '.');
                    agb.generateIntoJavaPackage(pkg);
                }
                return gen = agb
                        .building(relPath.getParent() == null ? Paths.get("") : relPath.getParent(), Paths.get("imports"));
            }

            private PrintStream outputFor(Extraction ext) {
                Path p = Paths.get(initialFile.getPath());
                return AntlrLoggers.getDefault().forPath(p);
            }

            private String lastHash;
            private Object lastLock = new Object();
            private Set<Subscriber> subscribersAtLastParse;

            @Override
            protected void onReparse(GrammarFileContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) throws Exception {
                LOG.log(Level.FINER, "onReparse {0}", extraction.source());
                List<Subscriber> targets = subscribers;
                synchronized(lastLock) {
                    // avoid notifying for "casual" reparses where the
                    // content has not changed
                    // XXX this will block updates when an imported grammar
                    // changes
                    String hash = extraction.tokensHash();
                    if (Objects.equals(lastHash, hash)) {
                        targets = new ArrayList<>(targets);
                        targets.removeAll(subscribersAtLastParse);
                        if (targets.isEmpty()) {
                            return;
                        }
                    } else {
                        lastHash = hash;
                        subscribersAtLastParse = new WeakSet<>(subscribers);
                    }
                }
                AntlrGenerator gen = gen(extraction);
                try {
                    // The parsing plumbing interrupts the parse thread in the event
                    // of cancellation (e.g. user typed a character).  This can wreak
                    // unholy havoc with I/O since it may cause a failure at any
                    // point that manifests as an IOException or some other failure,
                    // so try to detect the situation and bail out
                    if (Thread.interrupted()) {
                        return;
                    }
                    PrintStream output = outputFor(extraction);
                    // Build the message here so the UI doesn't hold a reference to the extraction
                    // indefinitiely
                    String msg = "Regenerate Antlr Grammar " + mimeType + " for " + extraction.source().lookup(Path.class);
                    AntlrGenerationResult result = Debug.runObjectThrowing(this, "generate " + extraction.source(), () -> {
                        return msg;
                    }, () -> {
                        return mapping.whileLocked(gen.jfs(), () -> {
                            try {
                                return gen.run(extraction.source().name(), output, true);
                            } catch (Error eiie) {
                                handleEiiE(eiie, gen.jfs());
                                throw eiie;
                            }
                        });
                    });
                    if (Thread.interrupted()) {
                        return;
                    }

                    LOG.log(Level.FINER, "Reparse received by generator {0} for "
                            + "{1} placeholder {2} result usable {3} run result "
                            + "{4} send to {5} subscribers",
                            new Object[]{id, extraction.source().name(),
                                extraction.isPlaceholder(), result.isUsable(), result, subscribers.size()});

                    subscribers.forEach((s) -> {
                        try {
                            LOG.log(Level.FINEST, "Call onRebuild() on {0} for {1}",
                                    new Object[]{s, extraction.source()});
                            s.onRebuilt(tree, mimeType, extraction, result, populate, fixes);
                        } catch (Exception e) {
                            LOG.log(Level.SEVERE,
                                    "Exception processing parse result of "
                                    + extraction.source(), e);
                        } catch (Error e) {
                            handleEiiE(e, gen.jfs());
                            throw e;
                        }
                    });
                } catch (Error ex) {
                    // This will otherwise be swallowed
                    handleEiiE(ex, gen.jfs());
                    throw ex;
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, "Exception thrown running generator "
                            + "for " + extraction.source(), ex);
                }
            }
        }
    }

    private static void handleEiiE(Error ex, JFS jfs) {
        try {
            LOG.log(Level.SEVERE, "Classpath: " + jfs.currentClasspath(), ex);
        } catch (Throwable t) {
            ex.addSuppressed(t);
            LOG.log(Level.SEVERE, t.toString(), ex);
            LOG.log(Level.SEVERE, null, t);
            throw ex;
        }
    }
}
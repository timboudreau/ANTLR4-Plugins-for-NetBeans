package org.nemesis.antlr.live.language.coloring;

import org.nemesis.antlr.live.language.coloring.AttrTypes;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import static org.nemesis.adhoc.mime.types.AdhocMimeTypes.loggableMimeType;
import com.mastfrog.graph.StringGraph;
import com.mastfrog.graph.algorithm.Score;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.File;
import java.util.Collections;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.language.ColorUtils;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.ParseResultHook;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = AdhocColoringsRegistry.class)
public final class AdhocColoringsRegistry {

    private final Map<String, AdhocColorings> coloringsForMimeType = new ConcurrentHashMap<>();
    private final Map<String, Long> lastModifiedTimes = new ConcurrentHashMap<>();
    private final Lis saver = new Lis();
    private static final Logger LOG = Logger.getLogger(AdhocColoringsRegistry.class.getName());
    private final Hook hook = new Hook();

    public static AdhocColoringsRegistry getDefault() {
        return Lookup.getDefault().lookup(AdhocColoringsRegistry.class);
    }

    public Set<String> mimeTypes() {
        return coloringsForMimeType.keySet();
    }

    public AdhocColoringsRegistry() {
        // do nothing
    }

    public boolean isRegistered(String mimeType) {
        return coloringsForMimeType.containsKey(mimeType);
    }

    public void clear() {
        for (String key : new ArrayList<>(coloringsForMimeType.keySet())) {
            remove(key);
        }
    }

    class Hook extends ParseResultHook<ParserRuleContext> {

        public Hook() {
            super(ParserRuleContext.class);
        }

        @Override
        protected void onReparse(ParserRuleContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) throws Exception {
            update(mimeType, extraction);
        }
    }

    private boolean isModified(String key, Path path) {
        try {
            FileTime ft = Files.getLastModifiedTime(path);
            return isModified(key, ft.toMillis());
        } catch (IOException ioe) {
            return false;
        }
    }

    private boolean isModified(String key, long grammarFileLastModified) {
        Long val = lastModifiedTimes.get(key);
        if (val == null) {
            return true;
        }
        return (val < grammarFileLastModified);
    }

    public AdhocColorings remove(String mimeType) {
        AdhocColorings colorings = coloringsForMimeType.remove(mimeType);
        if (colorings != null) {
            store(colorings, mimeType);
            ParseResultHook.deregister(mimeType, hook);
        }
        return colorings;
    }

    public AdhocColorings reinitialize(String mimeType) {
        AdhocColorings colorings = coloringsForMimeType.get(mimeType);
        if (colorings != null) {
            Path path = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
            if (path != null) {
                GrammarSource<?> src = GrammarSource.find(path, ANTLR_MIME_TYPE);
                if (src != null) {
                    Optional<Source> source = src.lookup(Source.class);
                    if (source.isPresent()) {
                        Extraction[] ext = new Extraction[1];
                        try {
                            ParserManager.parse(Collections.singleton(source.get()), new UserTask() {
                                @Override
                                public void run(ResultIterator resultIterator) throws Exception {
                                    Parser.Result res = resultIterator.getParserResult();
                                    if (res instanceof ExtractionParserResult) {
                                        ext[0] = ((ExtractionParserResult) res).extraction();
                                    }
                                }
                            });
                            // do this outside the parser manager's lock
                            if (ext[0] != null) {
                                colorings.clear();
                                doUpdate(mimeType, ext[0]);
                            }
                        } catch (ParseException | IOException ex) {
                            LOG.log(Level.SEVERE, src + "", ex);
                        }
                    }
                }
            }
        }
        return colorings;
    }

    final class Lis implements ChangeListener, Runnable, PropertyChangeListener {

        private static final int DELAY = 10000;
        private final RequestProcessor.Task task = RequestProcessor.getDefault().create(this);
        private final ConcurrentSkipListSet<String> storeQueue = new ConcurrentSkipListSet<>();

        @Override
        public void stateChanged(ChangeEvent e) {
            // Notified when user edits the colorings
            for (Map.Entry<String, AdhocColorings> en : coloringsForMimeType.entrySet()) {
                if (en.getValue() == e.getSource()) {
                    storeQueue.add(en.getKey());
                    LOG.log(Level.FINEST, "Schedule store for {0}", loggableMimeType(en.getKey()));
                    break;
                }
            }
            // schedule the save task for 30 seconds in the
            // future - generally one edits more than one thing
            task.schedule(DELAY);
        }

        @Override
        public void run() {
            String toStore;
            Set<String> stored = new HashSet<>();
            while ((toStore = storeQueue.pollFirst()) != null) {
                stored.add(toStore);
            }
            for (String mimeType : stored) {
                AdhocColorings colorings = coloringsForMimeType.get(mimeType);
                if (mimeType != null) {
                    store(colorings, mimeType);
                }
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            stateChanged(new ChangeEvent(evt.getSource()));
        }
    }

    private void store(AdhocColorings colorings, String mimeType) {
        FileObject fo = FileUtil.getConfigFile(sfsPathFor(mimeType));
        try {
            if (fo == null) {
                fo = FileUtil.createData(FileUtil.getConfigRoot(), sfsPathFor(mimeType));
            }
            LOG.log(Level.FINE, "Store {0} colorings in {1} for {2}",
                    new Object[]{colorings.size(), fo.getPath(), loggableMimeType(mimeType)});
            try (OutputStream out = fo.getOutputStream()) {
                colorings.store(out);
                saver.storeQueue.remove(mimeType);
            } catch (IOException ioe) {
                Exceptions.printStackTrace(ioe);
            }
        } catch (IOException ioe2) {
            Exceptions.printStackTrace(ioe2);
        }
    }

    public void update(String mimeType, Extraction extraction) throws ParseException, IOException, BadLocationException {
        if (!this.coloringsForMimeType.containsKey(mimeType)) {
            return;
        }
        Path path = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
        if (path == null) {
            LOG.log(Level.WARNING, "Attempt to update colorings for a mime type "
                    + "that is not in AdhocMimeTypes: {0} for {1}",
                    new Object[]{mimeType, extraction.source()});
        }
        boolean exists = path != null && Files.exists(path);
        if (exists && isModified(mimeType, path)) {
            doUpdate(mimeType, extraction);
        }
    }

    private void doUpdate(String mimeType, Extraction ext) throws IOException {
        doUpdate(mimeType, ext, new HashSet<>());
    }

    private void doUpdate(String mimeType, Extraction ext, Set<GrammarSource<?>> seen) throws IOException {
        AdhocColorings existing = loadOrCreate(mimeType);
        doUpdate(existing, mimeType, ext, seen);
    }

    private void doUpdate(AdhocColorings colorings, String mimeType, Extraction ext, Set<GrammarSource<?>> seen) throws IOException {
        ColorUtils colors = new ColorUtils();
        Supplier<Color> backgrounds = colors.backgroundColorSupplier();

        StringGraph tree = ext.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES);
        Supplier<Color> foregrounds = colors.foregroundColorSupplier();

        List<Score<String>> mostConnectedThrough = tree.eigenvectorCentrality();
        mostConnectedThrough = mostConnectedThrough.isEmpty()
                ? Collections.emptyList()
                : mostConnectedThrough.subList(0, Math.min(mostConnectedThrough.size(), 5));

        for (Score<String> score : mostConnectedThrough) {
            if (!colorings.contains(score.node())) {
                Set<AttrTypes> attrs = EnumSet.of(AttrTypes.BACKGROUND, AttrTypes.ACTIVE);
                colorings.addIfAbsent(score.node(), backgrounds.get(), attrs);
            }
        }
        for (String lowest : tree.bottomLevelNodes()) {
            if (!colorings.contains(lowest)) {
                Set<AttrTypes> attrs = EnumSet.of(AttrTypes.FOREGROUND, AttrTypes.ACTIVE);
                switch (ThreadLocalRandom.current().nextInt(4)) {
                    case 1:
                        attrs.add(AttrTypes.BOLD);
                        break;
                    case 2:
                        attrs.add(AttrTypes.ITALIC);
                        break;
                    case 3:
                        attrs.add(AttrTypes.BOLD);
                        attrs.add(AttrTypes.ITALIC);
                        break;
                }
            }
        }
        Set<String> importantRules = new HashSet<>(tree.disjunctionOfClosureOfHighestRankedNodes());
        NamedSemanticRegions<RuleTypes> nameds = ext.namedRegions(AntlrKeys.RULE_NAMES);
        for (String id : nameds.nameArray()) {
            if (colorings.contains(id)) {
                continue;
            }
            Set<AttrTypes> attrs = EnumSet.of(AttrTypes.FOREGROUND);
            if (importantRules.contains(id)) {
                attrs.add(AttrTypes.ACTIVE);
            } else if (ThreadLocalRandom.current().nextInt(20) == 17) {
                attrs.add(AttrTypes.ACTIVE);
            }
            colorings.addIfAbsent(id, backgrounds.get(), attrs);
        }
        /*
        colorings.addIfAbsent("keywords", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND, AttrTypes.BOLD));
        colorings.addIfAbsent("symbols", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        colorings.addIfAbsent("numbers", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        colorings.addIfAbsent("identifier", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND, AttrTypes.ITALIC));
        colorings.addIfAbsent("delimiters", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        colorings.addIfAbsent("operators", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        colorings.addIfAbsent("string", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        colorings.addIfAbsent("literals", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        colorings.addIfAbsent("field", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        colorings.addIfAbsent("comment", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        colorings.addIfAbsent("whitespace", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
         */

        importImportedTokensAndRules(ANTLR_MIME_TYPE, ext, seen, colorings);
    }

    private void importImportedTokensAndRules(String mimeType, Extraction ext,
            Set<GrammarSource<?>> seen, AdhocColorings intoColorings) {
        ImportFinder finder = ImportFinder.forMimeType(mimeType);
        System.out.println("\n\nIMPORT TOKENS " + ext.source() + " finder: " + finder);
        seen.add(ext.source());
        Set<GrammarSource<?>> imports = finder.allImports(ext, CollectionUtils.blackHoleSet());
        System.out.println("   IMPORTS: " + imports);
        for (GrammarSource<?> src : imports) {
            System.out.println("   check source " + src);
            if (seen.contains(src)) {
                System.out.println("  seen it.");
                continue;
            }
            src.lookup(Source.class, source -> {
                try {
                    ParserManager.parse(Collections.singleton(source), new UserTask() {
                        @Override
                        public void run(ResultIterator resultIterator) throws Exception {
                            Parser.Result result = resultIterator.getParserResult();
                            if (result instanceof ExtractionParserResult) {
                                Extraction ext = ((ExtractionParserResult) result).extraction();
                                if (!seen.contains(ext.source())) { // could be indirect import, so check here too
                                    System.out.println("    recurse for " + ext.source());
                                    doUpdate(intoColorings, mimeType, ext, seen);
                                }
                            } else {
                                System.out.println("Not an extraction result: " + result);
                            }
                        }
                    });
                } catch (ParseException ex) {
                    LOG.log(Level.SEVERE, src + "", ex);
                }
            });
        }
    }

    public AdhocColorings get(String mimeType) {
        try {
            AdhocColorings colorings = loadOrCreate(mimeType);
            if (colorings.isEmpty()) {
                Path path = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
                File f = path.toFile();
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(f));
                Extraction extraction = ParsingUtils.parse(fo, res -> {
                    return res instanceof ExtractionParserResult
                            ? ((ExtractionParserResult) res).extraction() : null;
                });
                if (extraction != null) {
                    update(mimeType, extraction);
                }
            }
            return colorings;
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Getting colorings for " + AdhocMimeTypes.loggableMimeType(mimeType), ex);
            return new AdhocColorings();
        }
    }

    private AdhocColorings loadOrCreate(String mimeType) throws FileNotFoundException, IOException {
        if (coloringsForMimeType.containsKey(mimeType)) {
            return coloringsForMimeType.get(mimeType);
        }
        FileObject fo = FileUtil.getConfigFile(sfsPathFor(mimeType));
        AdhocColorings result;
        if (fo != null) {
            try (InputStream in = fo.getInputStream()) {
                result = AdhocColorings.load(in);
            }
            LOG.log(Level.FINE, "Loaded {0} colorings for {1} from {2}",
                    new Object[]{result.size(), fo.getPath(), loggableMimeType(mimeType)});
        } else {
            result = new AdhocColorings();
        }
        ParseResultHook.register(mimeType, hook);
        result.addChangeListener(WeakListeners.change(saver, result));
        result.addPropertyChangeListener(WeakListeners.propertyChange(saver, result));
        coloringsForMimeType.put(mimeType, result);
        return result;
    }

    private static final String SFS_FOLDER = "antlr/colorings/";

    private static String sfsPathFor(String mimeType) {
        return SFS_FOLDER + mimeType + "/colorings.antlrcolorings";
    }
}

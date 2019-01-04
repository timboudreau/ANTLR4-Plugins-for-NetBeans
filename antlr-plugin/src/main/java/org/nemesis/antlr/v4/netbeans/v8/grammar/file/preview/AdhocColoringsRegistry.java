package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

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
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.ANTLRv4GrammarChecker;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegions;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.loggableMimeType;
import org.netbeans.modules.parsing.spi.ParseException;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.ServiceProvider;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.graph.StringGraph;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named.NamedSemanticRegion;

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

    public static AdhocColoringsRegistry getDefault() {
        return Lookup.getDefault().lookup(AdhocColoringsRegistry.class);
    }

    public void clear() {
        for (String key : new ArrayList<>(coloringsForMimeType.keySet())) {
            remove(key);
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

    public void update(String mimeType) throws ParseException, IOException, BadLocationException {
        if (!this.coloringsForMimeType.containsKey(mimeType)) {
            return;
        }
        Path path = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
        boolean exists = Files.exists(path);
        if (exists && isModified(mimeType, path)) {
            // XXX doing it this way for tests that don't have a parser
            // manager, but it is much more expensive - only done once
            // on initialization, but still - should have a way to get a
            // cached result
            ANTLRv4GrammarChecker checker = NBANTLRv4Parser.parse(path);
            update(checker.getSemanticParser(), mimeType);
//            ParserManager.parse(Collections.singleton(Source.create(FileUtil.toFileObject(path.toFile()))), new UserTask() {
//                @Override
//                public void run(ResultIterator ri) throws Exception {
//                    Parser.Result res = ri.getParserResult();
//                    if (res instanceof NBANTLRv4Parser.ANTLRv4ParserResult) {
//                        update((ANTLRv4ParserResult) res, mimeType);
//                    }
//                }
//            });
        }
    }

    public void update(ANTLRv4ParserResult res, String mimeType) throws IOException {
        update(res.semanticParser(), mimeType);
    }

    public void update(ANTLRv4SemanticParser sem, String mimeType) throws IOException {
        AdhocColorings existing = getExisting(mimeType);
        StringGraph tree = sem.ruleTree();
        List<String> rules = new ArrayList<>();
        ColorUtils colors = new ColorUtils();
        Supplier<Color> backgrounds = colors.backgroundColorSupplier();
        Set<String> importantRules = new HashSet<>(tree.disjunctionOfClosureOfHighestRankedNodes());
        importantRules.addAll(tree.bottomLevelRules());

        Extraction ext = sem.extraction();
        NamedSemanticRegions<RuleTypes> nameds = ext.namedRegions(AntlrExtractor.RULE_NAMES);
        for (NamedSemanticRegion<RuleTypes> decl : nameds) {
            String id = decl.name();
            if (existing.contains(id)) {
                continue;
            }
            if (decl.kind() == RuleTypes.PARSER) {
                rules.add(id);
            }
            Set<AttrTypes> attrs = EnumSet.of(AttrTypes.BACKGROUND);
            if (importantRules.contains(id)) {
                attrs.add(AttrTypes.ACTIVE);
            } else if (ThreadLocalRandom.current().nextInt(20) == 17) {
                attrs.add(AttrTypes.ACTIVE);
            }
            existing.addIfAbsent(id, backgrounds.get(), attrs);
        }
//        for (RuleDeclaration decl : sem.allDeclarations()) {
//            if (existing.contains(decl.getRuleID())) {
//                continue;
//            }
//            if (decl.kind() == PARSER_RULE_DECLARATION) {
//                rules.add(decl.getRuleID());
//            }
//            Set<AttrTypes> attrs = EnumSet.of(AttrTypes.BACKGROUND);
//            if (importantRules.contains(decl.getRuleID())) {
//                attrs.add(AttrTypes.ACTIVE);
//            } else if (ThreadLocalRandom.current().nextInt(20) == 17) {
//                attrs.add(AttrTypes.ACTIVE);
//            }
//            existing.addIfAbsent(decl.getRuleID(), backgrounds.get(), attrs);
//        }
        Supplier<Color> foregrounds = colors.foregroundColorSupplier();
        existing.addIfAbsent("keywords", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND, AttrTypes.BOLD));
        existing.addIfAbsent("symbols", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        existing.addIfAbsent("numbers", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        existing.addIfAbsent("identifier", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND, AttrTypes.ITALIC));
        existing.addIfAbsent("delimiters", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        existing.addIfAbsent("operators", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        existing.addIfAbsent("string", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        existing.addIfAbsent("literals", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        existing.addIfAbsent("field", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
        existing.addIfAbsent("comment", foregrounds.get(), EnumSet.of(AttrTypes.ACTIVE,
                AttrTypes.FOREGROUND));
    }

    public AdhocColorings get(String mimeType) {
        try {
            AdhocColorings colorings = getExisting(mimeType);
            if (colorings.isEmpty()) {
                update(mimeType);
            }
            return colorings;
        } catch (IOException | ParseException | BadLocationException ex) {
            Exceptions.printStackTrace(ex);
            return new AdhocColorings();
        }
    }

    private AdhocColorings getExisting(String mimeType) throws FileNotFoundException, IOException {
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

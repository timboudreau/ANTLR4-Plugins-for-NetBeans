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
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.ANTLRv4GrammarChecker;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTree;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind.PARSER_RULE_DECLARATION;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.loggableMimeType;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.parsing.spi.ParseException;
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
                    new Object[] {colorings.size(), fo.getPath(), loggableMimeType(mimeType)});
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

    public static Color editorBackground() {
        MimePath mimePath = MimePath.parse("text/x-java");
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet result = fcs.getFontColors(FontColorNames.DEFAULT_COLORING);
        Color color = (Color) result.getAttribute(StyleConstants.ColorConstants.Background);
        return color == null ? UIManager.getColor("text") : color;
    }

    public static Color editorForeground() {
        MimePath mimePath = MimePath.parse("text/x-java");
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet result = fcs.getFontColors(FontColorNames.DEFAULT_COLORING);
        Color color = (Color) result.getAttribute(StyleConstants.ColorConstants.Foreground);
        return color == null ? UIManager.getColor("textText") : color;
    }

    Supplier<Color> foregroundColorSupplier() {
        Color base = editorBackground();
        float[] hsb = new float[3];
        Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), hsb);
        if (isBright(base)) {
            // gray background, gravitate toward dark
            hsb[2] = 0.2f;
        } else if (isMidTone(base)) {
            // light background, gravitate toward dark
            hsb[2] = 0.3f;
        } else {
            // dark background, gravitate toward light
            hsb[2] = 0.875f;
        }
        hsb[1] = 0.75f;
        return () -> {
            hsb[0] = randomlyTweak(hsb[0], 0.25f);
            float sat = clamp(randomlyTweak(hsb[1], 0.5f) - 0.25f);
            float bri = clamp(randomlyTweak(hsb[2], 0.5f));
            return new Color(Color.HSBtoRGB(hsb[0], sat, bri));
        };
    }

    private boolean isMidTone(Color color) {
        int diffR = Math.abs(color.getRed() - 128);
        int diffG = Math.abs(color.getGreen() - 128);
        int diffB = Math.abs(color.getBlue() - 128);
        return diffR + diffG + diffB < 64;
    }

    private boolean isBright(Color color) {
        int val = color.getRed() + color.getGreen() + color.getBlue();
        return val > (256 * 3) / 2;
    }

    Supplier<Color> backgroundColorSupplier() {
        Color base = editorBackground();
        float[] hsb = new float[3];
        Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), hsb);
        if (!isBright(base)) {
            hsb[2] = 0.75f;
        } else {
            hsb[2] = 0.35f;
        }
        hsb[1] = 0.545f;
        return () -> {
            hsb[0] = randomlyTweak(hsb[0], 0.45f); // keep the hue moving
            float sat = randomlyTweak(hsb[1], 0.45f);
            float bri = randomlyTweak(hsb[2], 0.35f);
            return new Color(Color.HSBtoRGB(hsb[0], sat, bri));
        };
    }

    private float clamp(float f) {
        return Math.max(0.0f, Math.min(f, 1.0f));
    }

    private float randomlyTweak(float f, float by) {
        float offset = (ThreadLocalRandom.current().nextFloat() * by)
                - (by / 2f);
        f += offset;
        if (f > 1.0f) {
            f -= 1.0f;
        } else if (f < 0f) {
            f = 1.0f + f;
        }
        return clamp(f);
    }

    public void update(ANTLRv4ParserResult res, String mimeType) throws IOException {
        update(res.semanticParser(), mimeType);
    }

    public void update(ANTLRv4SemanticParser sem, String mimeType) throws IOException {
        AdhocColorings existing = getExisting(mimeType);
        RuleTree tree = sem.ruleTree();
        List<String> rules = new ArrayList<>();
        Supplier<Color> backgrounds = backgroundColorSupplier();
        Set<String> importantRules = tree.disjunctionOfClosureOfHighestRankedNodes();
        for (RuleDeclaration decl : sem.allDeclarations()) {
            if (existing.contains(decl.getRuleID())) {
                continue;
            }
            if (decl.kind() == PARSER_RULE_DECLARATION) {
                rules.add(decl.getRuleID());
            }
            Set<AttrTypes> attrs = EnumSet.of(AttrTypes.BACKGROUND);
            if (importantRules.contains(decl.getRuleID())) {
                attrs.add(AttrTypes.ACTIVE);
            } else if (ThreadLocalRandom.current().nextInt(20) == 17) {
                attrs.add(AttrTypes.ACTIVE);
            }
            existing.addIfAbsent(decl.getRuleID(), backgrounds.get(), attrs);
        }
        Supplier<Color> foregrounds = foregroundColorSupplier();
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
                    new Object[] { result.size(), fo.getPath(), loggableMimeType(mimeType)});
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

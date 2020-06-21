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
package org.nemesis.antlr.live.language.coloring;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.IOException;
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
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.language.ColorUtils;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.ParseResultHook;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.named.NamedSemanticRegion;
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
    static final Logger LOG = Logger.getLogger(AdhocColoringsRegistry.class.getName());
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
            long millis = ft.toMillis();
            boolean result = isModified(key, ft.toMillis());
            if (result) {
                lastModifiedTimes.put(key, millis);
            }
            return result;
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
        generatedHighlightings(existing, mimeType, ext, seen);
    }

    public boolean ensureAllPresent(ParseTreeProxy proxy) {
        AdhocColorings colorings = get(proxy.mimeType());
        if (colorings == null || proxy.isUnparsed()) {
            return false;
        }
        return colorings.withChangesSuspended(() -> {;
            ColorUtils colors = new ColorUtils();
            Supplier<Color> backgrounds = colors.backgroundColorSupplier();
            Supplier<Color> foregrounds = colors.foregroundColorSupplier();
            Set<String> added = new HashSet<>(8);
            Set<String> removed = new HashSet<>(colorings.keys());
            removed.removeAll(proxy.allRuleNames());
            boolean changed = !removed.isEmpty();
            for (AntlrProxies.ProxyTokenType tk : proxy.tokenTypes()) {
                String nm = tk.name();
                if (!colorings.contains(nm) && !"EOF".equals(nm) && !"0".equals(nm)) {
                    added.add(nm);
                    // addIfAbsent will use the recovered value if there is one
                    colorings.addIfAbsent(nm, foregrounds.get(), AttrTypes.FOREGROUND);
                    changed = true;
                }
            }
            for (String rule : proxy.parserRuleNames()) {
                if (!colorings.contains(rule)) {
                    added.add(rule);
                    // addIfAbsent will use the recovered value if there is one
                    colorings.addIfAbsent(rule, backgrounds.get(), AttrTypes.BACKGROUND);
                    changed = true;
                }
            }
            for (String rem : removed) {
                colorings.remove(rem);
            }
            if (changed) {
                LOG.log(Level.FINEST, "eap removed colors for {0} added for {1} for grammar {2]",
                        new Object[]{removed, added, proxy.grammarName()});
                saver.task.schedule(5000);
            }
            return changed;
        });
    }

    private void generatedHighlightings(AdhocColorings colorings, String mimeType, Extraction ext, Set<GrammarSource<?>> seen) throws IOException {
        NamedSemanticRegions<RuleTypes> nameds = ext.namedRegions(AntlrKeys.RULE_NAMES);
        boolean anyAbsent = false;
        for (NamedSemanticRegion<RuleTypes> item : nameds.ofKind(RuleTypes.LEXER)) {
            if (!colorings.contains(item.name())) {
                anyAbsent = true;
                break;
            }
        }
        if (!anyAbsent) {
            for (NamedSemanticRegion<RuleTypes> item : nameds.ofKind(RuleTypes.PARSER)) {
                if (!colorings.contains(item.name())) {
                    anyAbsent = true;
                    break;
                }
            }
        }
        if (!anyAbsent) {
            return;
        }
        colorings.withChangesSuspended(() -> {
            LOG.log(Level.FINER, "generateHighlightings {0}", ext.source());
            // Okay, to randomly compute some initial syntax highlighting, we use a
            // some heuristics.  Does it all work?  Well, kinda...
            ColorUtils colors = new ColorUtils();
            Supplier<Color> backgrounds = colors.backgroundColorSupplier();
            Supplier<Color> foregrounds = colors.foregroundColorSupplier();

            StringGraph tree = ext.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES);

            // The set of nodes that score highest on eigenvector centrality - this
            // is a measure of how frequently they intersect on all possible paths
            // through the graph - container rules that are widely used; we take
            // the top 4 and will activate rules for those
            List<Score<String>> mostConnectedThrough = tree.eigenvectorCentrality();
            mostConnectedThrough = mostConnectedThrough.isEmpty()
                    ? Collections.emptyList()
                    : mostConnectedThrough.subList(0, Math.min(mostConnectedThrough.size(), 4));
            // For tokens, we will enable highlighting for those that are most used by
            // many different rules - PageRank is good for that
            List<Score<String>> rank = tree.pageRank();
            Collections.sort(rank);
            Set<String> topTokens = new HashSet<>();
            if (!rank.isEmpty()) {
                for (Score<String> score : rank) {
                    String ruleid = score.node();
                    if (RuleTypes.LEXER == nameds.kind(ruleid)) {
                        topTokens.add(ruleid);
                        if (topTokens.size() == 5) {
                            break;
                        }
                    }
                }
            }
            LOG.log(Level.FINEST, "Tokens from rank: {0}", topTokens);
            // This is the trickiest to understand:  We take the highest ranked nodes according
            // to eigenvector centrality - the most connected through.  Then we intersect the
            // closure - leaf nodes that are descendants of those often-connected-through nodes
            // which ONLY OCCUR IN THE CLOSURE OF ONE OF THE TOP RANKED NODES
            // These are rules which are distinctive enough to merit highlighting. - i.e. they're
            // not something quite as mundane as ; or =.
            Set<String> importantRules = new HashSet<>(tree.disjunctionOfClosureOfHighestRankedNodes());
            if (importantRules.size() > rank.size() / 4) {
                List<String> best = rankedByScore(mostConnectedThrough, rank.size() / 4);
                importantRules.retainAll(best);
            }
            LOG.log(Level.FINEST, "Rules from disjunction: {0}", importantRules);
            for (Score<String> centralNode : mostConnectedThrough) {
                importantRules.add(centralNode.node());
            }
            importantRules.addAll(topTokens);
            rules:
            for (String ruleId : nameds.nameArray()) {
                if (colorings.contains(ruleId) || "EOF".equals(ruleId) || "0".equals(ruleId)) {
                    continue;
                }
                Set<AttrTypes> attrs = EnumSet.noneOf(AttrTypes.class);
                RuleTypes type = nameds.kind(ruleId);
                Color color;
                switch (type) {
                    case LEXER:
                        // use foreground colors for tokens by default
                        attrs.add(AttrTypes.FOREGROUND);
                        color = foregrounds.get();
                        break;
                    case PARSER:
                        // Use background colors for parser rules by default
                        attrs.add(AttrTypes.BACKGROUND);
                        color = backgrounds.get();
                        break;
                    default:
                        continue rules;
                }
                switch (ThreadLocalRandom.current().nextInt(7)) {
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
                if (importantRules.contains(ruleId)) {
                    attrs.add(AttrTypes.ACTIVE);
                } else if (ThreadLocalRandom.current().nextInt(10) == 7) {
                    attrs.add(AttrTypes.ACTIVE);
                }
                AdhocColoring added = colorings.addIfAbsent(ruleId, color, attrs);
                if (added != null) {
                    LOG.log(Level.FINEST, "Added for {0} key {1}: {2}", new Object[] {ext.source(), ruleId, added});
                }
            }
            importImportedTokensAndRules(ANTLR_MIME_TYPE, ext, seen, colorings);
            return true;
        });
    }

    private static List<String> rankedByScore(List<Score<String>> l, int maxSize) {
        if (l.size() > maxSize) {
            l = l.subList(0, maxSize);
        }
        List<String> result = new ArrayList<>();
        for (Score<String> s : l) {
            result.add(s.node());
        }
        return result;
    }

    private void importImportedTokensAndRules(String mimeType, Extraction ext,
            Set<GrammarSource<?>> seen, AdhocColorings intoColorings) {
        ImportFinder finder = ImportFinder.forMimeType(mimeType);
        seen.add(ext.source());
        Set<GrammarSource<?>> imports = finder.allImports(ext, CollectionUtils.blackHoleSet());
        for (GrammarSource<?> src : imports) {
            if (seen.contains(src)) {
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
                                    generatedHighlightings(intoColorings, mimeType, ext, seen);
                                }
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

package org.nemesis.antlr.live.language;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.ParseResultHook;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.lexer.Language;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = MimeDataProvider.class, position = Integer.MAX_VALUE - 1)
public class AdhocMimeDataProvider implements MimeDataProvider {

    static final Logger LOG = Logger.getLogger(AdhocMimeDataProvider.class.getName());
    private final LanguageIC lang = new LanguageIC();
    private final FontColorsIC fcic = new FontColorsIC();
    private final ReparseIC reparseIc = new ReparseIC();
    private final Map<String, MimeEntry> lookups = new ConcurrentHashMap<>();
    private final Hook hook = new Hook();

    void clear() { // tests
        lookups.clear();
    }

    void gooseLanguage(String mimeType) {
        Debug.run(this, "goose-language " + mimeType, () -> {
            MimeEntry me = lookups.get(mimeType);
            if (me != null) {
                LOG.log(Level.FINEST, "Goose language {0}", AdhocMimeTypes.loggableMimeType(mimeType));
                me.goose();
            } else {
                LOG.log(Level.FINEST, "No entry to goose for {0}", AdhocMimeTypes.loggableMimeType(mimeType));
            }
            AdhocLanguageFactory.get().kill(mimeType);
        });
    }

    class Hook extends ParseResultHook<ParserRuleContext> implements BiConsumer<Extraction, GrammarRunResult<?>> {

        private final Map<String, String> lastExtractionHashForMimeType
                = new ConcurrentHashMap<>();
        private final Map<String, Long> lastModificationDateForMimeType
                = new ConcurrentHashMap<>();

        public Hook() {
            super(ParserRuleContext.class);
        }

        private boolean isRealUpdate(String mimeType, Extraction ext) {
            if (ext.isPlaceholder()) {
                return false;
            }
            System.out.println("exthash " + ext.tokensHash());
            Long lastMod = lastModificationDateForMimeType.get(mimeType);
            boolean result = false;
            if (lastMod == null) {
                result = true;
            }
            if (!result) {
                try {
                    long currentLastModified = ext.source().lastModified();
                    System.out.println("MOD TIME DIFF " + (currentLastModified - lastMod));
                    if (currentLastModified > lastMod) {
                        String lastHash = lastExtractionHashForMimeType.get(mimeType);
                        if (lastHash == null) {
                            result = true;
                        } else {
                            result = !lastHash.equals(ext.tokensHash());
                        }
                        System.out.println("HASHES " + lastHash + " -> " + ext.tokensHash());
                    }
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, ext.source().toString(), ex);
                }
            }
            if (result) {
                lastExtractionHashForMimeType.put(mimeType, ext.tokensHash());
                try {
                    lastModificationDateForMimeType.put(mimeType, ext.source().lastModified());
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, ext.source().toString(), ex);
                }
            }
            return result;
        }

        @Override
        protected void onReparse(ParserRuleContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) throws Exception {
            LOG.log(Level.FINE, "AdhocMimeDataProvider.hook got reparse of {0}", extraction.source());
            Optional<Path> sourcePathOpt = extraction.source().lookup(Path.class);
            assert sourcePathOpt.isPresent() : "Could not translate " + extraction.source() + " into Path";
            if (sourcePathOpt.isPresent()) {
                Path sourcePath = sourcePathOpt.get();
                String realMime = AdhocMimeTypes.mimeTypeForPath(sourcePath);
                Debug.runThrowing(this, "AdhocMimeDataProvider-hook.onReparse " + mimeType + " " + extraction.source(),
                        () -> {
                            StringBuilder sb = new StringBuilder(mimeType);
                            sb.append("\nsource: ").append(extraction.source().lookup(Path.class).isPresent()
                                    ? extraction.source().lookup(Path.class) : "no-path").append('\n');
                            return sb.toString();
                        }, () -> {
                            if (extraction.isPlaceholder()) {
                                Debug.message("placeholder extraction");
                            }
                            System.out.println("Adhoc mime data provider on reparse");
                            System.out.println("MIME IS " + AdhocMimeTypes.loggableMimeType(realMime));
                            Debug.message("Mime type for path: " + realMime);
                            long lm = extraction.source().lastModified();
                            if (isRealUpdate(realMime, extraction)) {
                                System.out.println("REAL UPDATE ");
                                AdhocDataObject.invalidateSources(realMime);
                                updateMimeType(realMime);
//                                gooseLanguage(realMime);
//                                AdhocLanguageFactory.get().fire();
                            } else {
                                Debug.failure("not a real update", () -> {
                                    return realMime + " " + mimeType + "\n"
                                            + extraction.tokensHash()
                                            + " " + lm
                                            + "\n"
                                            + lastExtractionHashForMimeType.get(realMime)
                                            + " " + lastModificationDateForMimeType.get(realMime);
                                });
                            }
                        });
            }
        }

        @Override
        public void accept(Extraction extraction, GrammarRunResult<?> runResult) {
            Debug.run(this, "AMDP-Hook-" + extraction.tokensHash(), extraction::toString, () -> {
                Optional<Path> sourcePathOpt = extraction.source().lookup(Path.class);
                if (sourcePathOpt.isPresent()) {
                    Path sourcePath = sourcePathOpt.get();
                    String realMime = AdhocMimeTypes.mimeTypeForPath(sourcePath);
                    Debug.message("Updating mime " + AdhocMimeTypes.loggableMimeType(realMime), runResult::toString);
                    if (isRealUpdate(realMime, extraction)) {
                        LOG.log(Level.FINE, "Update of {0} tok hash {1} from {2}",
                                new Object[]{AdhocMimeTypes.loggableMimeType(realMime), extraction.tokensHash(), sourcePath});
                        System.out.println("REAL UPDATE ");
                        AdhocDataObject.invalidateSources(realMime);
                        updateMimeType(realMime);
                        Debug.success("Updated mime type", sourcePath::toString);
//                    gooseLanguage(realMime);
                    } else {
                        Debug.failure("Not a real update", extraction::toString);
                    }
                }
            });
        }
    }

    public static AdhocMimeDataProvider getDefault() {
        return Lookup.getDefault().lookup(AdhocMimeDataProvider.class);
    }

    boolean isRegistered(String mimeType) {
        return lookups.containsKey(mimeType);
    }

    @Override
    public Lookup getLookup(MimePath mp) {
        MimeEntry entry = lookups.get(mp.getPath());
        return entry == null ? Lookup.EMPTY : entry.getLookup();
    }

    void removeMimeType(String mt) {
        MimeEntry entry = lookups.remove(mt);
        entry.shutdown();
        AdhocLanguageFactory.get().discard(mt);
        AdhocColoringsRegistry.getDefault().remove(mt);
    }

    public Lookup getLookup(String mimeType) {
        MimeEntry entry = lookups.get(mimeType);
        return entry == null ? Lookup.EMPTY : entry.getLookup();
    }

    void addMimeType(String mimeType) {
        MimeEntry en = new MimeEntry(mimeType, lang, fcic, reparseIc);
        LOG.log(Level.FINER, "Add mime entries for {0}", AdhocMimeTypes.loggableMimeType(en.mimeType));
        lookups.put(en.mimeType, en);
//        Path path = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
//        ParseResultHook.register(FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile())), hook);
        AdhocLanguageHierarchy.onNewEnvironment(mimeType, hook);
    }

    void updateMimeType(String mime) {
        LOG.log(Level.FINER, "Update parsing environment for {0}", AdhocMimeTypes.loggableMimeType(mime));
        boolean isReentry = AdhocLanguageFactory.get().update(mime);
//        if (!isReentry) {
        MimeEntry me = lookups.get(mime);
        if (me != null) {
            Debug.run(this, "update-mime-type " + mime, () -> {
                AdhocParserFactory pf = me.pf;
                if (pf != null) {
                    pf.updated();
                } else {
                    Debug.failure("no parser factory", me::toString);
                }
                AdhocLanguageHierarchy hier = me.lookup.lookup(AdhocLanguageHierarchy.class);
                if (hier != null) {
                    if (!isReentry) {
                        hier.languageUpdated();
                        gooseLanguage(mime);
                    }
                } else {
                    Debug.failure("no hierarchy", me::toString);
                }
            });
        }
//        }
    }

    static final class DebugLookup extends Lookup {

        private final AbstractLookup lkp;
        private final String mimeType;

        DebugLookup(AbstractLookup lkp, String mimeType) {
            this.lkp = lkp;
            this.mimeType = AdhocMimeTypes.loggableMimeType(mimeType);
        }

        @Override
        public <T> T lookup(Class<T> type) {
            LOG.log(Level.FINEST, "Lookup {0} for {1}",
                    new Object[]{type.getName(), mimeType});
            return lkp.lookup(type);
        }

        @Override
        public <T> Result<T> lookup(Template<T> tmplt) {
            LOG.log(Level.FINEST, "Lookup template {0} for {1}",
                    new Object[]{tmplt.getType().getName(), mimeType});
            return lkp.lookup(tmplt);
        }
    }

    static final class MimeEntry {

        private final InstanceContent content = new InstanceContent();
        private final Lookup lookup;
        private final AdhocEditorKit kit;
        private final HighlightsIC layers;
        private LanguageIC cvt;
        private final String mimeType;
        private final AdhocParserFactory pf;
        private final FontColorsIC fcic;
//        private final TaskFactory errorHighlighter;
//        private final ReparseIC reparseIc;

        MimeEntry(String mimeType, LanguageIC cvt, FontColorsIC fcic, ReparseIC reparseIc) {
            this.cvt = cvt;
            this.fcic = fcic;
            this.mimeType = mimeType;
            content.add(kit = new AdhocEditorKit(mimeType));
            content.add(mimeType, fcic);
            content.add(mimeType, cvt);
            content.add(pf = new AdhocParserFactory(mimeType));
            content.add(mimeType, layers = new HighlightsIC());
//            content.add(errorHighlighter = AdhocErrorsHighlighter.create());
//            content.add(mimeType, this.reparseIc = reparseIc);
            content.add(new AdhocReparseListeners(mimeType));
//            this.lookup = new DebugLookup(new AbstractLookup(content), mimeType);
            this.lookup = new AbstractLookup(content);
        }

        void goose() {
            LOG.log(Level.FINE, "Replace language converter for {0}",
                    AdhocMimeTypes.loggableMimeType(mimeType));

            Debug.message("replace-hierarchy for " + mimeType);
            content.remove(mimeType, cvt);
            // We need a new LanguageIC instance or we get the old
            // result - lookup weakly caches it?
            content.add(mimeType, cvt = new LanguageIC());
        }

        public Lookup getLookup() {
            return lookup;
        }

        private void shutdown() {
            // Ensure listeners get notified of things
            // going away, rather than just going silent
            content.remove(kit);
            content.remove(mimeType, cvt);
            content.remove(mimeType, fcic);
            content.remove(layers);
            content.remove(pf);
//            content.remove(errorHighlighter);
        }
    }

    static final class HighlightsIC implements InstanceContent.Convertor<String, HighlightsLayerFactory> {

        @Override
        public HighlightsLayerFactory convert(String obj) {
            return new AdhocHighlightLayerFactory(obj);
        }

        @Override
        public Class<? extends HighlightsLayerFactory> type(String obj) {
            return AdhocHighlightLayerFactory.class;
        }

        @Override
        public String id(String obj) {
            return "highlights-" + obj;
        }

        @Override
        public String displayName(String obj) {
            return id(obj);
        }
    }

    static final class FontColorsIC implements InstanceContent.Convertor<String, AdhocColorings> {

        @Override
        public AdhocColorings convert(String t) {
            return AdhocColoringsRegistry.getDefault().get(t);
        }

        @Override
        public Class<? extends AdhocColorings> type(String t) {
            return AdhocColorings.class;
        }

        @Override
        public String id(String t) {
            return "adhoc-colorings-" + t;
        }

        @Override
        public String displayName(String t) {
            return id(t);
        }
    }

    static final class LanguageIC implements InstanceContent.Convertor<String, Language<?>> {

        @Override
        public Language<?> convert(String t) {
            LOG.log(Level.FINEST, "LanguageIC.convert {0}", t);
            return AdhocLanguageFactory.get().language(t);
        }

        @Override
        @SuppressWarnings(value = {"rawtypes", "unchecked"})
        public Class<? extends Language<?>> type(String t) {
            Class c = Language.class;
            return c;
        }

        @Override
        public String id(String t) {
            return t;
        }

        @Override
        public String displayName(String t) {
            return t;
        }
    }

    static final class ReparseIC implements InstanceContent.Convertor<String, AdhocReparseListeners> {

        @Override
        public AdhocReparseListeners convert(String obj) {
            return new AdhocReparseListeners(obj);
        }

        @Override
        public Class<? extends AdhocReparseListeners> type(String obj) {
            return AdhocReparseListeners.class;
        }

        @Override
        public String id(String obj) {
            return AdhocReparseListeners.class.getName() + "-" + obj;
        }

        @Override
        public String displayName(String obj) {
            return id(obj);
        }
    }
}

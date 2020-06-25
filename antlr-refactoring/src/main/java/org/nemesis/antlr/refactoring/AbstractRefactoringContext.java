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
package org.nemesis.antlr.refactoring;

import com.mastfrog.function.throwing.ThrowingBiFunction;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.range.RangeRelation;
import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.nemesis.antlr.refactoring.common.FileObjectHolder;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.IndexAddressable.IndexAddressableItem;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.spi.ProblemDetailsFactory;
import org.netbeans.modules.refactoring.spi.ProblemDetailsImplementation;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Cancellable;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * Base class for refactoring-related types which provides thread-local caching
 * for extractions, and various utility methods for tasks that are commonly
 * needed (like creating Problem instances whose text is readable on dark themes
 * and high DPI screens0.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractRefactoringContext {

    private static final Map<Class<?>, Logger> loggers = new ConcurrentHashMap<>();
    private static boolean cacheDisabled = Boolean.getBoolean("AbstractRefactoringContext.noCache");
    private static final ThreadLocal<CacheContext> CTX = ThreadLocal.withInitial(CacheContext::new);
    static boolean debugLog = Boolean.getBoolean("antlr.refactoring.debug");

    protected static String escapeHtml(String s) {
        return Strings.escape(s, AbstractRefactoringContext::escape);
    }

    static CharSequence escape(char c) {
        switch (c) {
            case '<':
                return "&lt";
            case '>':
                return "&gt;";
            case '"':
                return "&quot;";
        }
        return Strings.singleChar(c);
    }

    /**
     * Logging support - all subclasses get a per-type logger; this allows debug
     * logging to be turned on and off globally, and provides some code-cruft
     * convenience.
     *
     * @return A logger
     */
    protected Logger log() {
        Class<?> type = getClass();
        Logger result = loggers.get(type);
        if (result == null) {
            result = Logger.getLogger(type.getName());
            if (debugLog) {
                result.setLevel(Level.ALL);
            }
            loggers.put(type, result);
        }
        return result;
    }

    /**
     * Test if a level is loggable.
     *
     * @param level The level
     * @param run A runnable to run if the level is loggable
     */
    protected void ifLoggable(Level level, Runnable run) {
        if (isLoggable(level)) {
            run.run();
        }
    }

    /**
     * Determine if a level is loggable.
     *
     * @param level The level
     * @return Whether or not that level is loggable
     */
    protected boolean isLoggable(Level level) {
        if (debugLog) {
            return true;
        }
        return log().isLoggable(level);
    }

    protected void log(Throwable thrown) {
        log(Level.SEVERE, thrown);
    }

    protected void log(Level level, Throwable thrown) {
        log().log(level, null, thrown);
    }

    protected void log(Level level, String template, Object first) {
        if (first instanceof Object[]) {
            log().log(level, template, (Object[]) first);
            return;
        }
        log().log(level, template, first);
    }

    protected void log(Level level, String template, Object first, Object... messages) {
        messages = ArrayUtils.prepend(first, messages);
        log().log(level, template, messages);
    }

    protected void log(Level level, String msg) {
        log().log(level, msg);
    }

    protected void logFine(String msg) {
        log(Level.FINE, msg);
    }

    protected void logFine(String msg, Object first) {
        log(Level.FINE, msg, first);
    }

    protected void logFine(String msg, Object first, Object... messages) {
        log().log(Level.FINE, msg, ArrayUtils.prepend(first, messages));
    }

    protected void logWarn(String msg) {
        log(Level.WARNING, msg);
    }

    protected void logWarn(String msg, Object first) {
        log(Level.WARNING, msg, first);
    }

    protected void logWarn(String msg, Object first, Object... messages) {
        log().log(Level.WARNING, msg, ArrayUtils.prepend(first, messages));
    }

    protected void logFinest(String msg) {
        log(Level.FINEST, msg);
    }

    protected void logFinest(String msg, Object first) {
        log(Level.FINEST, msg, first);
    }

    protected void logFinest(String msg, Object first, Object... messages) {
        log().log(Level.FINEST, msg, ArrayUtils.prepend(first, messages));
    }

    /**
     * Fetch an extraction for a file, using the cache if we are in the closure
     * of a call to inParsingContext. If an exception is thrown, reports that
     * and returns null.
     *
     * @param fo A file object
     * @return An extraction
     */
    static Extraction extraction(FileObject fo) {
        try {
            // Try to use the document where possible - it
            // may be modified
            return parse(fo);
        } catch (Exception ex) {
            org.openide.util.Exceptions.printStackTrace(ex);
            return null;
        }
    }

    /**
     * Find a position bounds in a refactoring; this may be passed in various
     * ways depending on how it is invoked.
     *
     * @param refactoring A refactoring
     * @return The bounds
     */
    protected static PositionBounds findPositionBounds(AbstractRefactoring refactoring) {
        final Lookup lookup = refactoring.getRefactoringSource();
        PositionBounds result = lookup.lookup(PositionBounds.class);
        if (result == null) {
            JTextComponent comp = lookup.lookup(JTextComponent.class);
            CloneableEditorSupport supp = lookup.lookup(CloneableEditorSupport.class);
            if (comp == null) {
                if (supp != null) {
                    JEditorPane[] panes = supp.getOpenedPanes();
                    if (panes != null && panes.length > 0) {
                        comp = panes[0];
                    }
                }
            }
            if (comp != null && supp != null) {
                int a = comp.getSelectionStart();
                int b = comp.getSelectionEnd();
                if (a >= 0 && b >= 0) {
                    int start = Math.min(a, b);
                    int end = Math.max(a, b);
                    PositionRef startPos = supp.createPositionRef(start, Position.Bias.Forward);
                    PositionRef endPos = supp.createPositionRef(end, Position.Bias.Backward);
                    result = new PositionBounds(startPos, endPos);
                }
            }
        }
        return result;
    }

    /**
     * Find a file object from an AbstractRefactoring. To avoid the default
     * rename refactoring from hijacking renames of elements within file and
     * renaming the file to the new variable name, we wrap the FileObject in a
     * wrapper object in the lookup; this method unwraps it if needed, or finds
     * one in the passed lookup if not.
     *
     * @param refactoring A refactoring
     * @return A file object or null if none
     */
    static FileObject findFileObject(AbstractRefactoring refactoring) {
        final Lookup lookup = refactoring.getRefactoringSource();
        FileObject fo = null;
        FileObjectHolder holder = lookup.lookup(FileObjectHolder.class);
        if (holder != null) {
            fo = holder.get();
        }
        if (fo == null) {
            fo = lookup.lookup(FileObject.class);
            if (fo == null) {
                DataObject ob = lookup.lookup(DataObject.class);
                if (ob != null) {
                    fo = ob.getPrimaryFile();
                }
            }
        }
        return fo;
    }

    private static final class CacheContext {

        private int entryCount;
        private final Map<FileObject, Extraction> foMap = new HashMap<>();
        private final Map<Document, Extraction> docMap = new IdentityHashMap<>();

        public <T> T enter(BiFunction<Map<FileObject, Extraction>, Map<Document, Extraction>, T> c) {
            entryCount++;
            try {
                return c.apply(foMap, docMap);
            } finally {
                entryCount--;
            }
        }

        boolean isActive() {
            return entryCount > 0;
        }

        void clear() {
            foMap.clear();
            docMap.clear();
        }
    }

    private static CacheContext context() {
        if (cacheDisabled) {
            return new CacheContext();
        }
        return CTX.get();
    }

    /**
     * Reeentrantly enter a parsing context - extractions for a given file or
     * document are cached until the earliest enterer exits to avoid overhead.
     *
     * @param <T> The return type
     * @param run The thing to run
     * @return Whatever the supplier returns
     */
    protected static <T> T inParsingContext(Supplier<T> run) {
        CacheContext ctx = context();
        boolean isNew = !ctx.isActive();
        try {
            if (isNew) {
                return ctx.enter((a, b) -> {
                    return NbAntlrUtils.withPostProcessingDisabled(() -> {
                        return run.get();
                    });
                });
            } else {
                return ctx.enter((a, b) -> {
                    return run.get();
                });
            }
        } finally {
            if (isNew) {
                ctx.clear();
                if (!cacheDisabled) {
                    CTX.remove();
                }
            }
        }
    }

    /**
     * Reeentrantly enter a parsing context - extractions for a given file or
     * document are cached until the earliest enterer exits to avoid overhead.
     *
     * @param <T> The type
     * @param c A function which takes the cache maps
     * @return Whatever the function returns
     * @throws Exception
     */
    static <T> T inParsingContext(ThrowingBiFunction<Map<FileObject, Extraction>, Map<Document, Extraction>, T> c) throws Exception {
        CacheContext ctx = CTX.get();
        boolean wasNew = !ctx.isActive();
        try {
            if (wasNew) {
                return ctx.enter((a, b) -> {
                    return NbAntlrUtils.withPostProcessingDisabled(() -> {
                        try {
                            return c.apply(a, b);
                        } catch (Exception ex1) {
                            return Exceptions.chuck(ex1);
                        }
                    });
                });
            } else {
                return ctx.enter((a, b) -> {
                    try {
                        return c.apply(a, b);
                    } catch (Exception ex1) {
                        return Exceptions.chuck(ex1);
                    }
                });
            }
        } finally {
            if (wasNew) {
                ctx.clear();
                CTX.remove();
            }
        }
    }

    /**
     * Parse a file for its extraction. Use this method in preference to parsing
     * directly with ParserManager, as it caches results, and extractions may be
     * needed for a file or document more than once during analysis. This method
     * will prefer the open Document for a file if there is one, so
     * modifications are accounted for.
     *
     * @param fo The file
     * @return An extraction or null
     * @throws Exception If something goes wrong
     */
    protected static Extraction parse(FileObject fo) throws Exception {
        return inParsingContext((foMap, docMap) -> {
            Document doc = null;
            try {
                DataObject dob = DataObject.find(fo);
                EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                if (ck != null) {
                    doc = ck.getDocument();
                    if (docMap.containsKey(doc)) {
                        return docMap.get(doc);
                    }
                }
            } catch (DataObjectNotFoundException ex) {
                return Exceptions.chuck(ex);
            }
            if (doc != null) {
                return parse(doc);
            } else {
                Extraction result = NbAntlrUtils.parseImmediately(fo);
                if (result != null) {
                    foMap.put(fo, result);
                }
                return result;
            }
        });
    }

    /**
     * Determine if two ranges are the same span efficiently - it is up to the
     * caller to determine if they belong to the same document - this just
     * compares the bounds.
     *
     * @param reg The tested-for region
     * @param found The discovered region
     * @return true if they match
     */
    protected static boolean sameRange(IntRange<? extends IntRange<?>> reg, IntRange<? extends IntRange<?>> found) {
        return reg == found || reg.relationTo(found) == RangeRelation.EQUAL || reg.equals(found);
    }

    /**
     * Parse a document for its extraction. Use this method in preference to
     * parsing directly with ParserManager, as it caches results, and
     * extractions may be needed for a file or document more than once during
     * analysis. This method will prefer the open Document for a file if there
     * is one, so modifications are accounted for.
     *
     * @param fo The file
     * @return An extraction or null
     * @throws Exception If something goes wrong
     */
    protected static Extraction parse(Document doc) throws Exception {
        return inParsingContext((foMap, docMap) -> {
            Extraction result = docMap.get(doc);
            if (result == null) {
                result = NbAntlrUtils.parseImmediately(doc);
                if (result != null) {
                    docMap.put(doc, result);
                }
                FileObject fo = NbEditorUtilities.getFileObject(doc);
                if (fo != null) {
                    foMap.put(fo, result);
                }
            }
            return result;
        });
    }

    /**
     * Find a region which matches the passed bounds - if the passed key is a
     * NameReferenceSetKey then both references and the collection of originals
     * from the key's referencing() key are searched.
     *
     * @param <T> The type
     * @param key The key
     * @param extraction An extraction
     * @param bounds Bounds for a region - a result will be returned if the
     * bounds are the same as or within the resulting region
     * @return Any region which matches
     */
    protected <T extends Enum<T>> FindResult<T> find(FileObject file, NamedExtractionKey<T> key, Extraction extraction, PositionBounds bounds) {
        return withKeys(key, (NamedRegionKey<T> namedKey, NameReferenceSetKey<T> refKey) -> {
            NamedSemanticRegion<T> result = find(bounds, extraction.namedRegions(namedKey));
            NamedExtractionKey<T> resultKey = key;
            if (result == null && refKey != null) {
                result = find(bounds, extraction.references(refKey));
                resultKey = refKey;
            }
            if (result != null) {
                return new FindResult<>(resultKey, result, extraction, file);
            }
            return null;
        });
    }

    protected <T extends Enum<T>> FindResult<T> findWithAttribution(FileObject file, NamedExtractionKey<T> key, Extraction extraction, PositionBounds bounds) {
        FindResult<T> result = find(file, key, extraction, bounds);
        if (result == null) {
            result = withKeys(key, (NamedRegionKey<T> namedKey, NameReferenceSetKey<T> refKey) -> {
                FindResult<T> res = refKey == null ? null : findInUnknowns(refKey, extraction, bounds);
                logFinest("findWithAttribution gets {0}", res);
                return res;
            });
        }
        return result;
    }

    /**
     * Given an extraction key which may or may not be a reference key, call the
     * passed function with that and the NamedRegionKey it references, or null
     * and the passed key if it is simply a NamedRegionKey in the first place.
     *
     * @param <T> The key param type
     * @param <R> The return type
     * @param key The key
     * @param func A function to produce the method result
     * @return The result of the function
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Enum<T>, R> R withKeys(NamedExtractionKey<T> key, BiFunction<NamedRegionKey<T>, NameReferenceSetKey<T>, R> func) {
        NamedRegionKey<T> namedKey = null;
        NameReferenceSetKey<T> refKey = null;
        if (notNull("key", key) instanceof NameReferenceSetKey<?>) {
            refKey = (NameReferenceSetKey<T>) key;
            namedKey = refKey.referencing();
        } else if (key instanceof NamedRegionKey<?>) {
            namedKey = (NamedRegionKey<T>) key;
        }
        assert namedKey != null : "Unknown named key type: " + key;
        return func.apply(namedKey, refKey);
    }

    /**
     * Get the lookup for a file.
     *
     * @param file A file
     * @return Its lookup - actually it's data object's node delegate's lookup
     */
    protected static Lookup lookupOf(FileObject file) {
        try {
            return DataObject.find(file).getNodeDelegate().getLookup();
        } catch (DataObjectNotFoundException ex) {
            org.openide.util.Exceptions.printStackTrace(ex);
            return Lookup.EMPTY;
        }
    }

    /**
     * Convert any IntRange (such as NamedSemanticRegion) into a PositionBounds
     * which will be updated if the file's document is modified.
     *
     * @param file A file
     * @param range A character range
     * @return A position bounds
     */
    protected static PositionBounds createPosition(FileObject file, IntRange range) {
        CloneableEditorSupport supp = lookupOf(file).lookup(CloneableEditorSupport.class);
        if (supp != null) {
            PositionRef startRef = supp.createPositionRef(range.start(), Position.Bias.Forward);
            PositionRef endRef = supp.createPositionRef(range.end(), Position.Bias.Backward);
            return new PositionBounds(startRef, endRef);
        }
        return null;
    }

    /**
     * Create a single Usage element.
     *
     * @param file The file
     * @param range The range at the time of analysis
     * @param of The name (usually the key name) of the type of element this is
     * @param name The text of the element
     * @param lookupContents Any contents which should be included in its lookup
     * @return A refactoring element
     */
    protected static RefactoringElementImplementation createUsage(FileObject file,
            IntRange<? extends IntRange> range, ExtractionKey<?> key, String name, Object... lookupContents) {
        return new Usage(file, range, key, name, lookupContents);
    }

    /**
     * Return whichever problem is non-null; if both non null, chain them with
     * <code>Problem.setNext()</code> in order of fatal-first if one is non
     * fatal.
     *
     * @param a One problem, or null
     * @param b Another problem, or null
     * @return A problem or null if both were null
     */
    public static Problem chainProblems(Problem a, Problem b) {
        if (a == null && b == null) {
            return null;
        } else if (a != null && b == null) {
            return a;
        } else if (b != null && a == null) {
            return b;
        } else {
            if (b.isFatal() && !a.isFatal()) {
                return attachTo(b, a);
            } else {
                return attachTo(a, b);
            }
        }
    }

    private static Problem attachTo(Problem a, Problem b) {
        while (a.getNext() != null) {
            a = a.getNext();
        }
        a.setNext(b);
        return a;
    }

    /**
     * File under "you've got to be kidding me": The refactoring API uses Swing
     * HTML rendering, so the font will be Times New Roman and on dark themes,
     * the foreground color will be close to the background color unless we do
     * backflips to set up the colors. So, use this method to create Problem
     * instances rather than the constructor.
     *
     * @param fatal If the problem is fatal
     * @param text The raw text (may contain HTML markup) to use
     * @return A problem which sets up fonts and colors correctly for the UI
     * based on UIManager colors and fonts
     */
    public static Problem createProblem(boolean fatal, String text) {
        return new Problem(fatal, problemBoilerplateHead() + text + problemBoilerplateTail());
    }

    /**
     * Create a fatal problem instance with the fonts and colors set correctly
     * to be readable on high dpi screens and dark themes.
     *
     * @param text The problem text
     * @return A prooblem
     */
    public static Problem fatalProblem(String text) {
        return createProblem(true, text);
    }

    /**
     * Create a fatal problem instance with the fonts and colors set correctly
     * to be readable on high dpi screens and dark themes.
     *
     * @param text The problem text
     * @return A prooblem
     */
    public static Problem warningProblem(String text) {
        return createProblem(false, text);
    }

    private static String PROBLEM_BOILERPLATE_HEAD;
    private static String PROBLEM_BOILERPLATE_TAIL = "</font>";

    private static String problemBoilerplateHead() {
        if (PROBLEM_BOILERPLATE_HEAD != null) {
            return PROBLEM_BOILERPLATE_HEAD;
        }
        // Dawn of time markup, but it works.
        StringBuilder sb = new StringBuilder("<font face='");
        Font f = UIManager.getFont("controlFont");
        if (f == null) {
            f = UIManager.getFont("Label.font");
            if (f == null) {
                f = new JLabel().getFont();
            }
        }
        if (f != null) {
            sb.append(f.getFamily()).append("' size='");
            // XXX this is not working right - the font size
            // winds up being ADDED to some arbitrary base font
            // size Swing decides on
            String prop = System.getProperty("uiFontSize");
            if (prop != null) {
                sb.append(prop);
            } else {
                sb.append(f.getSize());
            }
            sb.append("' ");
        } else {
            sb.append("Sans Serif' size='14' ");
        }
        Color fg = UIManager.getColor("textText");
        if (fg == null) {
            fg = UIManager.getColor("controlText");
            if (fg == null) {
                fg = UIManager.getColor("Label.foreground");
                if (fg == null) {
                    fg = new JLabel().getForeground();
                    if (fg == null) {
                        fg = Color.BLACK;
                    }
                }
            }
        }
        sb.append("color='");
        colorToHex(fg, sb);
        sb.append("'>");

        PROBLEM_BOILERPLATE_HEAD = sb.toString();
        return PROBLEM_BOILERPLATE_HEAD;
    }

    private static void colorToHex(Color c, StringBuilder sb) {
        sb.append('#');
        twoDigitHex(c.getRed(), sb);
        twoDigitHex(c.getGreen(), sb);
        twoDigitHex(c.getBlue(), sb);
    }

    private static void twoDigitHex(int val, StringBuilder into) {
        String res = Integer.toHexString(val);
        if (res.length() == 1) {
            into.append('0');
        }
        into.append(res);
    }

    private static String problemBoilerplateTail() {
        return PROBLEM_BOILERPLATE_TAIL;
    }

    /**
     * Creates a lookup from the passed object, proxying any objects in the
     * array which are instances of Lookup, and recursively doing the same with
     * any elements which are themselves arrays.
     *
     * @param objects An array of objects, lookups or arrays of objects
     * @return A lookup
     */
    protected static Lookup lookupFrom(Object... objects) {
        return new LookupBuilder().createFrom(objects);
    }

    private static final class LookupBuilder implements Consumer<Object>, Supplier<Lookup> {

        private final Set<Lookup> lookups = new LinkedHashSet<>(5);
        private final Set<Object> contents = new LinkedHashSet<>(20);
        private final Set<Object> seen = new HashSet<>();

        Lookup createFrom(Object[] objects) {
            accept(objects);
            return get();
        }

        @Override
        public void accept(Object t) {
            if (t != null) {
                if (seen.contains(t)) {
                    return;
                }
                seen.add(t);
                if (t instanceof Lookup) {
                    lookups.add((Lookup) t);
                } else if (t.getClass().isArray()) {
                    List<?> arrayList = CollectionUtils.toList(t);
                    for (Object o : arrayList) {
                        accept(o);
                    }
                } else {
                    contents.add(t);
                }
            }
        }

        @Override
        public Lookup get() {
            if (lookups.isEmpty() && contents.isEmpty()) {
                return Lookup.EMPTY;
            } else if (!lookups.isEmpty() && contents.isEmpty()) {
                if (lookups.size() == 1) {
                    return lookups.iterator().next();
                } else {
                    Lookup[] allLookups = lookups.toArray(new Lookup[lookups.size()]);
                    return new ProxyLookup(allLookups);
                }
            } else if (lookups.isEmpty() && !contents.isEmpty()) {
                Object[] all = contents.toArray(new Object[contents.size()]);
                return Lookups.fixed(all);
            } else {
                Object[] all = contents.toArray(new Object[contents.size()]);
                lookups.add(Lookups.fixed(all));
                Lookup[] allLookups = lookups.toArray(new Lookup[lookups.size()]);
                return new ProxyLookup(allLookups);
            }
        }
    }

    /**
     * Convert a throwable to a problem.
     *
     * @param th The throwable
     * @return A problem
     */
    public static Problem toProblem(Throwable th) {
        return toProblem(th, true);
    }

    /**
     * Convert a throwable to a problem.
     *
     * @param th The throwable
     * @param fatal Whether the resulting problem should be set to fatal
     * @return A problem
     */
    public static Problem toProblem(Throwable th, boolean fatal) {
        String msg = th.getMessage();
        if (msg == null) {
            msg = th.getClass().getSimpleName();
        }
        return toProblem(th, msg, fatal);
    }

    /**
     * Convert a throwable to a problem.
     *
     * @param th The throwable
     * @param msg The string to use in the problem
     * @return A problem
     */
    public static Problem toProblem(Throwable th, String msg) {
        return toProblem(th, msg, true);
    }

    /**
     * Convert a throwable to a problem.
     *
     * @param th The throwable
     * @param msg The string to use in the problem
     * @param fatal Whether the resulting problem should be set to fatal
     * @return A problem
     */
    public static Problem toProblem(Throwable th, String msg, boolean fatal) {
        return new Problem(fatal, msg, ProblemDetailsFactory.createProblemDetails(new ThPdI(th)));
    }

    /**
     * Find an item in a collection from an extraction (NamedSemanticRegions,
     * NamedRegionReferenceSets, SingletonEncounters) whose bounds enclose or
     * match the passed bounds.
     *
     * @param <I> The collection item type
     * @param <C> The collection type
     * @param selection The position
     * @param items The collection
     * @return A matching item or null
     */
    protected static <I extends IndexAddressableItem, C extends IndexAddressable<I>> I find(PositionBounds selection, C items) {
        int a = selection.getBegin().getOffset();
        int b = selection.getEnd().getOffset();
        int start = Math.min(a, b);
        int end = Math.max(a, b);
        I found = items.at(start);
        if (found != null) {
            if (start != end) {
                IntRange<? extends IntRange> test = Range.of(start, end);
                RangeRelation rel = found.relationTo(test);
                switch (rel) {
                    // If the selection is inside the item, ok.
                    // If the selection straddles the item, it's not a match
                    case EQUAL:
                    case CONTAINS:
                        break;
                    default:
                        return null;
                }
            }
            return found;
        }
        return null;
    }

    /**
     * If we haven't found any usages of a token, it may be because it is a
     * reference to something in another file. So, attribute the source and see
     * if we can find it. Note this will transform a rename or find usages of
     * one file into one of another file.
     *
     * @param <T> The key type
     * @param key The key
     * @param extraction The extraction
     * @param bounds The caret location or selection in the document being
     * searched for
     * @return A find result or null
     */
    @SuppressWarnings("unchecked")
    protected final <T extends Enum<T>> FindResult<T> findInUnknowns(NameReferenceSetKey<T> key, Extraction extraction, PositionBounds bounds) {
        Attributions<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T> attributions = extraction.resolveAll(key);
        SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> atttributedAtCaret
                = find(bounds, attributions.attributed());
        if (atttributedAtCaret != null) {
            logFine("Found attributed at caret {0}", atttributedAtCaret);
            AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T> ref = atttributedAtCaret.key();
            NamedSemanticRegion<T> el = ref.element();
            if (el instanceof NamedSemanticRegionReference<?>) {
                el = (NamedSemanticRegion<T>) ((NamedSemanticRegionReference<?>) el).referencing();
            }
            if (el != null) {
                Optional<FileObject> otherFile = ref.attributedTo().source().lookup(FileObject.class);
                if (otherFile.isPresent()) {
                    FindResult<T> result = new FindResult<>(key, el, ref.attributedTo(), otherFile.get());
                    return result;
                }
            }
        } else {
            logFinest("No unknown attributed element at caret {0} for {1} in {2}",
                    bounds, key, extraction.source());
        }
        return null;
    }

    protected static final class FindResult<T extends Enum<T>> {

        private final NamedExtractionKey<T> key;
        private final NamedSemanticRegion<T> region;
        private final Extraction extraction;
        private final FileObject file;

        FindResult(NamedExtractionKey<T> key,
                NamedSemanticRegion<T> region, Extraction extraction,
                FileObject file) {
            this.key = notNull("key", key);
            this.region = notNull("element", region);
            this.extraction = notNull("extraction", extraction);
            this.file = notNull("actualFile", file);
        }

        public NamedExtractionKey<T> key() {
            return key;
        }

        public NamedSemanticRegion<T> region() {
            return region;
        }

        public FileObject file() {
            return file;
        }

        public Extraction extraction() {
            return extraction;
        }

        @Override
        public String toString() {
            return "FindResult<" + key.type().getSimpleName() + ">("
                    + key + " " + file.getPath() + " " + region
                    + " parse " + extraction.tokensHash() + ")";
        }
    }

    /**
     * Determine if an IntRange (which all extraction collection items are, such
     * as NamedSemanticRegion or SingletonEncounter) matches or contains a
     * NetBeans editor PositionBounds (similar to IntRange but updated when the
     * document is altered).
     *
     * @param found The range to test against
     * @param v The editor selection bounds or caret location
     * @return Whether or not they match as described
     */
    protected static boolean rangeMatch(IntRange<? extends IntRange> found, PositionBounds v) {
        int a = v.getBegin().getOffset();
        int b = v.getEnd().getOffset();
        int start = Math.min(a, b);
        int end = Math.max(a, b);
        if (found != null) {
            if (start != end) {
                IntRange<? extends IntRange> test = Range.of(start, end);
                RangeRelation rel = found.relationTo(test);
                switch (rel) {
                    // If the selection is inside the item, ok.
                    // If the selection straddles the item, it's not a match
                    case EQUAL:
                    case CONTAINS:
                        break;
                    default:
                        return false;
                }
            }
            return true;
        }
        return false;
    }

    static final class ThPdI implements ProblemDetailsImplementation {

        private final Throwable thrown;

        public ThPdI(Throwable thrown) {
            this.thrown = thrown;
        }

        private String message() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (PrintStream print = new PrintStream(out, true, "UTF-8")) {
                thrown.printStackTrace(print);
            } catch (UnsupportedEncodingException ex) {
                throw new AssertionError(ex);
            }
            return new String(out.toByteArray(), UTF_8);
        }

        @Override
        @Messages({"retry=&Retry",
            "cancel=&Cancel",
            "refactoringException=Exception Thrown in Refactoring",
            "# {0} - exception type",
            "exceptionDetail=A(n) {0} was thrown during refactoring."
        })
        public void showDetails(Action action, Cancellable cnclbl) {
            JPanel panel = new JPanel(new BorderLayout());
            int ins = Utilities.isMac() ? 5 : 12;
            panel.setBorder(BorderFactory.createEmptyBorder(ins, ins, ins, ins));
            JLabel label = new JLabel(Bundle.exceptionDetail(thrown.getClass().getSimpleName()));
            label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, ins));
            JTextArea area = new JTextArea(message());
            panel.add(area, BorderLayout.CENTER);
            panel.add(label, BorderLayout.NORTH);
            area.setEditable(false);
            String retry = Bundle.retry();
            String cancel = Bundle.cancel();
            Object[] options = Utilities.isMac() ? new Object[]{cancel, retry} : new Object[]{retry, cancel};
            DialogDescriptor dlg = new DialogDescriptor(
                    panel,
                    Bundle.refactoringException(), true, options, cancel,
                    DialogDescriptor.DEFAULT_ALIGN, HelpCtx.DEFAULT_HELP, (ae) -> {
                        if (retry.equals(ae.getActionCommand())) {
                            action.actionPerformed(ae);
                        } else {
                            cnclbl.cancel();
                        }
                    });
            DialogDisplayer.getDefault().notify(dlg);
        }

        @Override
        @Messages({
            "# {0} - the exception type",
            "exception={0} thrown"
        })
        public String getDetailsHint() {
            return Bundle.exception(thrown.getClass().getSimpleName());
        }
    }

    /**
     * Determine if two extractions are of the same document. Since
     * GrammarSource will adaptively look things up (potentially, say, creating
     * a Snapshot for a file), we try some cheap methods before potentially
     * expensive tests.
     *
     * @param origFile A file
     * @param origExtraction An extraction of that file
     * @param target Another extraction
     * @return Whether or not the represent the same document
     */
    protected static boolean isSameSource(FileObject origFile, Extraction origExtraction, Extraction target) {
        // Determine if two files are the same source, trying the cheapest tests first
        boolean isRightFile = target == origExtraction || target.source().equals(origExtraction.source());
        if (!isRightFile) {
            Optional<FileObject> optSourceFile = target.source().lookup(FileObject.class);
            isRightFile = optSourceFile.isPresent() && origFile.equals(optSourceFile.get());
        }
        return isRightFile;
    }
}

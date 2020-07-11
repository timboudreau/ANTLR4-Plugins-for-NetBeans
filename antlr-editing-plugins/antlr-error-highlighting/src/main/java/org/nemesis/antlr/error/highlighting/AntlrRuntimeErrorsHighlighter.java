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
package org.nemesis.antlr.error.highlighting;

import com.mastfrog.function.IntBiConsumer;
import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Int;
import com.mastfrog.graph.StringGraph;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import static com.mastfrog.util.strings.Strings.deSingleQuote;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Segment;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.AntlrConstants;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import static org.nemesis.antlr.error.highlighting.ChannelsAndSkipExtractors.SUPERFLUOUS_PARENTEHSES;
import org.nemesis.antlr.error.highlighting.ChannelsAndSkipExtractors.SingleTermCtx;
import org.nemesis.antlr.error.highlighting.ChannelsAndSkipExtractors.TermCtx;
import org.nemesis.antlr.file.AntlrKeys;
import static org.nemesis.antlr.file.AntlrKeys.BLOCKS;
import static org.nemesis.antlr.file.AntlrKeys.GRAMMAR_TYPE;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAMES;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAME_REFERENCES;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.tool.ext.EpsilonRuleInfo;
import org.nemesis.antlr.memory.tool.ext.ProblematicEbnfInfo;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.refactoring.usages.ImportersFinder;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.graph.hetero.BitSetHeteroObjectGraph;
import org.nemesis.data.named.ContentsChecksums;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.distance.LevenshteinDistance;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRuntimeErrorsHighlighter implements Subscriber {

    // XXX this class isTopLevelWithAlternatives grown gargantuan and should be split into pluggable
    // pieces that deal with specific things.
    protected final OffsetsBag bag;
    private final AttributeSet errorHighlight;
    private final AttributeSet warningHighlight;
    static final Logger LOG = Logger.getLogger(
            AntlrRuntimeErrorsHighlighter.class.getName());

    private final Context ctx;
    private final static RequestProcessor subscribe = new RequestProcessor("antlr-runtime-errors", 1, false);
    private final CompL compl = new CompL();
    private ComponentListener cl;

    @SuppressWarnings("LeakingThisInConstructor")
    AntlrRuntimeErrorsHighlighter(Context ctx) {
        this.ctx = ctx;
        Document doc = ctx.getDocument();
        bag = new OffsetsBag(doc, true);
        // XXX listen for changes, etc
        MimePath mimePath = MimePath.parse(ANTLR_MIME_TYPE);
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet set = fcs.getFontColors("error");
        if (set == null) {
            set = fcs.getFontColors("errors");
            if (set == null) {
                set = AttributesUtilities.createImmutable(
                        EditorStyleConstants.WaveUnderlineColor,
                        Color.RED.darker());
            }
        }
        errorHighlight = set;
        set = fcs.getFontColors("warning");
        if (set == null) {
            set = fcs.getFontColors("warnings");
            if (set == null) {
                set = AttributesUtilities.createImmutable(
                        EditorStyleConstants.WaveUnderlineColor,
                        Color.ORANGE);
            }
        }
        warningHighlight = set;
        if (ctx.getComponent().isShowing()) {
            compl.setActive(true);
        }
        JTextComponent c = ctx.getComponent();
        c.addComponentListener(cl = WeakListeners.create(
                ComponentListener.class, compl, c));
        c.addPropertyChangeListener("ancestor",
                WeakListeners.propertyChange(compl, c));
        // This can race - double check
        EventQueue.invokeLater(() -> {
            if (ctx.getComponent().isShowing()) {
                LOG.log(Level.FINER, "Component is showing, set active");
                compl.setActive(true);
            }
        });
        LOG.log(Level.FINE, "Create an AntlrRuntimeErrorsHighlighter for {0}", doc);
    }

    private IntRange<? extends IntRange<?>> offsets(ProblematicEbnfInfo info, Extraction ext) {
        return Range.ofCoordinates(info.start(), info.end());
    }

    @Messages({
        "# {0} - ebnf",
        "canMatchEmpty=Can match the empty string: ''{0}''",
        "# {0} - replacement",
        "replaceEbnfWith=Replace with ''{0}''?",
        "# {0} - ebnf",
        "# {1} - firstReplacement",
        "# {2} - secondReplacement",
        "replaceEbnfWithLong=''{0}'' can match the empty string. \nReplace it with\n"
        + "''{1}'' or \n''{2}''"
    })
    private boolean handleEpsilon(ParsedAntlrError err, Fixes fixes, Extraction ext, EpsilonRuleInfo eps, Set<String> usedErrIds) throws BadLocationException {
        if (eps.problem() != null) {
            ProblematicEbnfInfo prob = eps.problem();
            IntRange<? extends IntRange<?>> problemBlock
                    = offsets(prob, ext);
            String msg = Bundle.canMatchEmpty(prob.text());

            String pid = prob.text() + "-" + prob.start() + ":" + prob.end();
            LOG.log(Level.FINEST, "Handle epsilon {0}", eps);
            fixes.addError(pid, problemBlock, msg, () -> {
                String repl = computeReplacement(prob.text());
                String prepl = computePlusReplacement(prob.text());
                return Bundle.replaceEbnfWithLong(prob.text(), repl, prepl);
            }, fc -> {
                String repl = computeReplacement(prob.text());
                String rmsg = Bundle.replaceEbnfWith(repl);
                fc.addReplacement(rmsg, problemBlock.start(), problemBlock.stop(), repl);
                String prepl = computePlusReplacement(prob.text());
                String rpmsg = Bundle.replaceEbnfWith(prepl);
                fc.addReplacement(rpmsg, problemBlock.start(), problemBlock.stop(), prepl);
            });
            return true;
        } else {
            IntRange<? extends IntRange<?>> cr = Range.ofCoordinates(eps.culpritStart(), eps.culpritEnd());
            IntRange<? extends IntRange<?>> vr = Range.ofCoordinates(eps.victimStart(), eps.victimEnd());
            String victimErrId = vr + "-" + err.code();
            if (!usedErrIds.contains(victimErrId)) {
                fixes.addWarning(victimErrId, vr.start(), vr.end(), eps.victimErrorMessage());
                usedErrIds.add(victimErrId);
            }
            String culpritErrId = cr + "-" + err.code();
            if (!usedErrIds.contains(culpritErrId)) {
                fixes.addWarning(culpritErrId, cr, eps.culpritErrorMessage());
                usedErrIds.add(culpritErrId);
            }
        }
        return false;
    }

    private String computePlusReplacement(String ebnfString) {
        String orig = ebnfString;
        boolean hasStar = false;
        boolean hasQuestion = false;
        boolean hasPlus = false;
        String vn = null;
        Matcher m = NAME_PATTERN.matcher(ebnfString);
        if (m.find()) {
            vn = m.group(1);
            ebnfString = m.group(2);
        }
        loop:
        for (;;) {
            switch (ebnfString.charAt(ebnfString.length() - 1)) {
                case '*':
                    hasStar = true;
                    ebnfString = ebnfString.substring(0, ebnfString.length() - 1);
                    break;
                case '?':
                    hasQuestion = true;
                    ebnfString = ebnfString.substring(0, ebnfString.length() - 1);
                    break;
                case '+':
                    hasPlus = true;
                    ebnfString = ebnfString.substring(0, ebnfString.length() - 1);
                    break;
                default:
                    break loop;
            }
        }
        String result;
        if (hasStar) {
            result = ebnfString + "+" + (hasQuestion ? "?" : "");
            if (vn != null) {
                boolean anyWhitespace = ANY_WHITESPACE.matcher(ebnfString).find();
                if (anyWhitespace) {
                    result = vn + "=(" + result + ")";
                } else {
                    result = vn + '=' + result;
                }
            }
        } else {
            result = ebnfString;
            if (vn != null) {
                result = vn + "=" + result;
            }
        }
        return result;
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("^(.*?)=(.*?)$");
    private static final Pattern ANY_WHITESPACE = Pattern.compile("\\s");

    private String computeReplacement(String ebnfString) {
        String orig = ebnfString;
        boolean hasStar = false;
        boolean hasQuestion = false;
        boolean hasPlus = false;
        String vn = null;
        Matcher m = NAME_PATTERN.matcher(ebnfString);
        if (m.find()) {
            vn = m.group(1);
            ebnfString = m.group(2);
        }
        loop:
        for (;;) {
            switch (ebnfString.charAt(ebnfString.length() - 1)) {
                case '*':
                    hasStar = true;
                    ebnfString = ebnfString.substring(0, ebnfString.length() - 1);
                    break;
                case '?':
                    hasQuestion = true;
                    ebnfString = ebnfString.substring(0, ebnfString.length() - 1);
                    break;
                case '+':
                    hasPlus = true;
                    ebnfString = ebnfString.substring(0, ebnfString.length() - 1);
                    break;
                default:
                    break loop;
            }
        }
        String result;
        if (hasStar) {
            result = ebnfString + " (" + ebnfString + (hasQuestion ? "?" : "") + ")?";
            if (vn != null) {
                boolean anyWhitespace = ANY_WHITESPACE.matcher(ebnfString).find();
                if (anyWhitespace) {
                    result = vn + "=(" + result + ')';
                } else {
                    result = vn + '=' + result;
                }
            }
        } else {
            result = ebnfString;
            if (vn != null) {
                boolean anyWhitespace = ANY_WHITESPACE.matcher(ebnfString).find();
                if (anyWhitespace) {
                    result = vn + "=(" + result + ')';
                } else {
                    result = vn + '=' + result;
                }
            }
        }
        return result;
    }

    private final class CompL extends ComponentAdapter implements Runnable, PropertyChangeListener {

        // Gets subscribing, which can trigger parsing of poms of all
        // dependencies, out of the critical path of opening an editor,
        // and turns listening off when the component is not visible
        private Runnable unsubscriber;
        private boolean active;
        private final RequestProcessor.Task task = subscribe.create(this);

        @Override
        public void componentShown(ComponentEvent e) {
            LOG.log(Level.FINEST, "Component shown {0}", ctx.getDocument());
            setActive(true);
        }

        @Override
        public void componentHidden(ComponentEvent e) {
            LOG.log(Level.FINEST, "Component hidden {0}", ctx.getDocument());
            setActive(false);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("ancestor".equals(evt.getPropertyName())) {
                setActive(evt.getNewValue() != null);
            }
        }

        void setActive(boolean active) {
            if (active != this.active) {
                this.active = active;
                LOG.log(Level.FINE, "Set active to {0} for {1}", new Object[]{active, ctx.getDocument()});
                if (active) {
                    task.schedule(350);
                } else {
                    task.cancel();
                    unsubscribe();
                }
            }
        }

        void unsubscribe() {
            if (unsubscriber != null) {
                LOG.log(Level.FINE, "Unsubscribe from rebuilds of {0}", ctx.getDocument());
                unsubscriber.run();
                unsubscriber = null;
            }
        }

        @Override
        public void run() {
            if (active) {
                FileObject fo = NbEditorUtilities.getFileObject(ctx.getDocument());
                if (fo != null) {
                    LOG.log(Level.FINE, "Subscribing to rebuilds of {0}", fo);
                    unsubscriber = RebuildSubscriptions.subscribe(fo, AntlrRuntimeErrorsHighlighter.this);
                } else {
                    LOG.log(Level.WARNING, "No FileObject to subscribe to for {0}", ctx.getDocument());
                }
            } else {
                LOG.log(Level.FINE, "Not active, don't subscribe to rebuilds of {0}", ctx.getDocument());
            }
        }
    }

    HighlightsContainer bag() {
        return bag;
    }

    private int offsetsOf(ParsedAntlrError error, IntBiConsumer startEnd) {
        Document doc = ctx.getDocument();
        if (doc instanceof StyledDocument) { // always will be outside some tests
            StyledDocument sdoc = (StyledDocument) doc;
            int docLength = sdoc.getLength();
            Element el = NbDocument.findLineRootElement(sdoc);
            /*
            System.out.println("\nERROR:\t'" + error.message() + "'");
            System.out.println("type:\t" + (error.isError() ? "ERROR" : "WARNING"));
            System.out.println("err-code:\t" + error.code());
            System.out.println("err-line:\t" + error.lineNumber() + ":" + error.lineOffset());
            System.out.println("err-offsets:\t" + error.fileOffset() + ":" + (error.fileOffset() + error.length()));
            System.out.println("err-length:\t" + error.length());
            System.out.println("doc-length:\t" + docLength);
            System.out.println("total-lines:\t" + el.getElementCount());
            System.out.println("");
             */
            int lineNumber = error.lineNumber() - 1 >= el.getElementCount()
                    ? el.getElementCount() - 1 : error.lineNumber() - 1;
            if (lineNumber < 0) {
                lineNumber = error.lineNumber();
                if (lineNumber < 0) {
                    lineNumber = 0;
                }
            }
            int lineOffsetInDocument = NbDocument.findLineOffset(sdoc, lineNumber);
            int errorStartOffset = Math.max(0, lineOffsetInDocument + error.lineOffset());
            int errorEndOffset = Math.min(docLength - 1, errorStartOffset + error.length());
            if (errorStartOffset < errorEndOffset) {
                startEnd.accept(Math.min(docLength - 1, errorStartOffset), Math.min(docLength - 1, errorEndOffset));
            } else {
                LOG.log(Level.INFO, "Computed nonsensical error start offsets "
                        + "{0}:{1} for line {2} of {3} for error {4}",
                        new Object[]{
                            errorStartOffset, errorEndOffset,
                            lineNumber, el.getElementCount(), error
                        });
            }
            return docLength;
        }
        return 0;
    }

    @Messages({
        "# {0} - grammarName",
        "# {1} - firstRuleName",
        "eofUnhandled=End-of-file (EOF) is not handled in {0}."
        + " Append it to {1}?",
        "detailsNoEof=A grammar which does not handle EOF may not parse all of "
        + "the input it is given, leading to unexpected results.",
        "# {0} - insertionText",
        "# {1} - ruleName",
        "insertEof=Insert {0} at end of rule {1}"
    })
    private void checkClosureOfFirstRuleContainsEOF(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {
        NamedSemanticRegions<RuleTypes> regions = extraction.namedRegions(AntlrKeys.RULE_BOUNDS);
        SingletonEncounters<GrammarDeclaration> gt = extraction.singletons(GRAMMAR_TYPE);
        if (!regions.isEmpty() && !gt.isEmpty()) {
            SingletonEncounters.SingletonEncounter<GrammarDeclaration> grammarType = gt.first();
            GrammarDeclaration gg = grammarType.get();
            SemanticRegions<UnknownNameReference<RuleTypes>> unks = extraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES);

            switch (gg.type()) {
                case COMBINED:
                    NamedSemanticRegion<RuleTypes> firstRuleBounds = regions.index().first();
                    List<? extends SemanticRegion<UnknownNameReference<RuleTypes>>> eofMentions
                            = unks.collect((unk) -> "EOF".equals(unk.name()));
                    if (eofMentions.isEmpty()) {
                        extraction.source().lookup(Document.class, doc -> {
                            try {
                                int insertPoint = firstRuleBounds.stop();
                                String msg = Bundle.eofUnhandled(extraction.source().name(), firstRuleBounds.name());
                                fixes.addWarning("no-eof", firstRuleBounds, msg, Bundle::detailsNoEof, fixen -> {
                                    try {
                                        String toInsert = EOFInsertionStringGenerator.getEofInsertionString(doc);
                                        fixen.addInsertion(Bundle.insertEof(toInsert, firstRuleBounds.name()),
                                                insertPoint, insertPoint, toInsert);
                                    } catch (IOException ex) {
                                        Exceptions.printStackTrace(ex);
                                    }
                                });
                            } catch (Exception ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        });
                    }
                    break;
            }
        }
    }

    @Override
    public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {
        if (res == null) {
            return;
        }
        LOG.log(Level.FINE, "onRebuilt {0}", extraction.source());
        Optional<Document> doc = extraction.source().lookup(Document.class);
        if (!doc.isPresent()) {
            LOG.log(Level.FINE, "Doc not present from source {0}", extraction.source());
            return;
        }
        if (!doc.get().equals(ctx.getDocument())) {
            LOG.log(Level.INFO, "Called with wrong extraction: {0} expecting {1}", new Object[]{doc.get(), ctx.getDocument()});
            // Currently we can be notified about any document in the
            // project
            return;
        }
        try {
            long lm = extraction.source().lastModified();
            if (lm > res.grammarFileLastModified) {
                LOG.log(Level.INFO, "Discarding error highlight pass for {0} "
                        + " - source last modified date is {1}ms newer "
                        + "than at the time of parsing. It should be reparsed "
                        + "again presently.", new Object[]{
                            extraction.source(), (lm - res.grammarFileLastModified)});
                return;
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        Set<String> usedErrorIds = new HashSet<>();
        LOG.log(Level.FINE, "onRebuilt {0}", extraction.source());
        updateErrorHighlights(res, extraction, fixes, usedErrorIds);
        SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = extraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
        addHintsForUnresolvableReferences(unknowns, extraction, fixes, usedErrorIds);
        addHintsForMissingImports(extraction, fixes, usedErrorIds);
        checkGrammarNameMatchesFile(tree, mimeType, extraction, res, populate, fixes, usedErrorIds);
        flagOrphansIfNoImports(tree, mimeType, extraction, res, populate, fixes, usedErrorIds);
        flagDuplicateBlocks(tree, mimeType, extraction, res, populate, fixes, usedErrorIds);
        flagStringLiteralsInParserRules(tree, mimeType, extraction, res, populate, fixes, usedErrorIds);
        flagSkips(tree, mimeType, extraction, res, populate, fixes, usedErrorIds);
        checkClosureOfFirstRuleContainsEOF(tree, mimeType, extraction, res, populate, fixes);
        flagSuperfluousParentheses(tree, mimeType, extraction, res, populate, fixes, usedErrorIds);
    }

    private Set<String> findDeletableClosureOfOrphan(String orphan, StringGraph usageGraph) {
        Set<String> closure = usageGraph.closureOf(orphan);
        Set<String> toDelete = new LinkedHashSet<>();
        toDelete.add(orphan);
        for (String child : closure) {
            toDelete.addAll(usageGraph.closureOf(child));
        }
        Set<String> stillInUse = new HashSet<>();
        for (String del : toDelete) {
            Set<String> rev = usageGraph.reverseClosureOf(del);
            if (!toDelete.containsAll(rev)) {
                Set<String> usedBy = new HashSet<>(rev);
                usedBy.removeAll(toDelete);
                stillInUse.add(del);
            }
        }
        toDelete.removeAll(stillInUse);
        return toDelete;
    }

    private String elide(String s) {
        if (s.length() > 64) {
            return s.substring(0, 63) + "\u2026"; // ellipsis
        }
        return s;
    }

    @Messages({
        "skipBad=Using skip creates problems for editors which cannot ignore tokens. Replace with a channel directive.",
        "# {0} - n",
        "replaceWithChannel=Replace skip with channel({0})?"
    })
    private void flagSkips(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes, Set<String> usedErrorIds) {
        SemanticRegions<ChannelsAndSkipExtractors.ChannelOrSkipInfo> all = extraction.regions(ChannelsAndSkipExtractors.CHSKIP);
        int[] maxUsedChannel = new int[1];
        List<SemanticRegion<ChannelsAndSkipExtractors.ChannelOrSkipInfo>> skips = new ArrayList<>();
        all.forEach(maybeSkip -> {
            switch (maybeSkip.key().kind()) {
                case CHANNEL:
                    maxUsedChannel[0] = Math.max(maybeSkip.key().channelNumber(), maxUsedChannel[0]);
                    return;
                default:
                    String errId = "skip-" + maybeSkip.key() + "-" + maybeSkip.index();
                    if (!usedErrorIds.contains(errId)) {
                        skips.add(maybeSkip);
                    }
            }
        });
        if (!skips.isEmpty()) {
            maxUsedChannel[0]++;
            for (SemanticRegion<ChannelsAndSkipExtractors.ChannelOrSkipInfo> region : skips) {
                String errId = "skip-" + region.key() + "-" + region.index();
                usedErrorIds.add(errId);
                try {
                    fixes.addWarning(errId, region, Bundle.skipBad(), fixen -> {
                        String replacement = "channel (" + maxUsedChannel[0] + ")";
                        fixen.addReplacement(Bundle.replaceWithChannel(maxUsedChannel[0]), region.start(), region.end() + 1, replacement);
                    });
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    @Messages({
        "# {0} - count",
        "# {1} - literal",
        "literalInParserRule=String literal {1} encountered in parser rule {0} times. Replace with lexer rule?",
        "# {0} - literal",
        "replaceWithLexerRule=Replace all occurrences of {0} with a lexer rule?",
        "# {0} - literal",
        "# {1} - existingRule",
        "replaceWithExistingRule=Replace {0} with reference to identical rule {1}"
    })
    private void flagStringLiteralsInParserRules(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes, Set<String> usedErrorIds) {
        SemanticRegions<TermCtx> terms = extraction.regions(ChannelsAndSkipExtractors.LONELY_STRING_LITERALS);
        SemanticRegions<SingleTermCtx> matchingRules = extraction.regions(ChannelsAndSkipExtractors.SOLO_STRING_LITERALS);
        Map<String, String> index = new HashMap<>();
        for (SemanticRegion<SingleTermCtx> ctx : matchingRules) {
            index.put(ctx.key().text, ctx.key().ruleName);
        }
        terms.forEach(region -> {
            String id = region.key().text + "-pbr-" + region.index();
            if (!usedErrorIds.contains(id)) {
                PositionBoundsRange base = PositionBoundsRange.create(extraction.source(), region);
                if (base == null) {
                    return;
                }
                usedErrorIds.add(id);
                List<PositionBoundsRange> ranges = new ArrayList<>(terms.size());
                int[] count = new int[1];
                terms.forEach(region2 -> {
                    if (region.key().text.equals(region2.key().text)) {
                        PositionBoundsRange pbr = PositionBoundsRange.create(extraction.source(), region2);
                        if (pbr == null) {
                            return;
                        }
                        ranges.add(pbr);
                        count[0]++;
                    }
                });
                String literal = deSingleQuote(region.key().text);
                String targetRule = index.get(literal);
                try {
                    if (targetRule != null) {
                        String msg = Bundle.replaceWithExistingRule(region.key().text, targetRule);
//                        PositionBoundsRange rng = PositionBoundsRange.create(extraction.source(), region);
                        fixes.addHint(id, region, msg, fixen -> {
//                            fixen.addReplacement(msg, rng.start(), rng.end(), targetRule);
                            fixen.addReplacement(msg, region.start(), region.end(), targetRule);
                        });
                    } else {
                        String msg = Bundle.literalInParserRule(count[0], region.key().text);
                        RuleNamingConvention namingConvention = RuleNamingConvention.forExtraction(extraction);
                        fixes.addHint(id, region, msg, fixen -> {
                            String generatedName = namingConvention.adjustName(generateLexerRuleName(region.key().text), RuleTypes.LEXER);
                            NamedSemanticRegions<RuleTypes> existingNames = extraction.namedRegions(RULE_NAMES);
                            int ix = 0;
                            String uniqueName = generatedName;
                            while (existingNames.contains(uniqueName)) {
                                uniqueName = generatedName + "_" + ++ix;
                            }
                            ExtractRule extract = new ExtractRule(ranges, extraction, base, region.key().text, uniqueName);
                            fixen.add(Bundle.replaceWithLexerRule(region.key().text), extract);
                        });
                    }
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
    }

    private static final Pattern QUOTES = Pattern.compile("[\"'](.*)[\"']");

    static String generateLexerRuleName(String text) {
        Matcher m = QUOTES.matcher(text);
        if (m.find()) {
            text = m.group(1);
        }
        return capitalize(Escaper.JAVA_IDENTIFIER_CAMEL_CASE.escape(text));
    }

    @Messages({
        //        "# {0} - content",
        "superfluousParentheses=Superfluous parentheses. Remove them?"
    })
    private void flagSuperfluousParentheses(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Set<String> usedErrorIds) {
        SemanticRegions<Integer> regs = extraction.regions(SUPERFLUOUS_PARENTEHSES);
        for (SemanticRegion<Integer> reg : regs) {
            String errId = "sp-" + reg.key();
            if (!usedErrorIds.contains(errId)) {
                PositionBoundsRange pbr = PositionBoundsRange.create(extraction.source(), reg);
                if (pbr != null) {
                    try {
                        if (reg.start() < reg.stop()) {
                            usedErrorIds.add(errId);
                            fixes.addHint(errId, pbr, Bundle.superfluousParentheses(), fixen -> {
                                Document doc = extraction.source().lookup(Document.class).get();
                                Segment seg = new Segment();
                                Bool failed = Bool.create();
                                doc.render(() -> {
                                    try {
                                        int startOffset = reg.start() + 1;
                                        int endOffset = reg.stop();
                                        doc.getText(startOffset, endOffset - startOffset, seg);
                                    } catch (BadLocationException ex) {
                                        Exceptions.printStackTrace(ex);
                                        failed.set();
                                    }
                                });
                                failed.ifUntrue(() -> {
                                    fixen.addReplacement(Bundle.superfluousParentheses(),
                                            pbr.start(), pbr.end(),
                                            seg.toString().trim());
                                });
                            });
                        }
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
    }

    @Messages({
        "# {0} - occurrences",
        "# {1} - text",
        "dup_hints_detail=The block ''{0}'' occurs {1} times in the grammar. Extract into a new rule?",
        "# {0} - occurrences",
        "dup_block=The same parenthesized expression occurs at {0}.  Extract into a new rule?"
    })
    private void flagDuplicateBlocks(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Set<String> usedErrorIds) {
        NamedSemanticRegions<RuleTypes> allNames = extraction.namedRegions(RULE_NAMES);
        NamedRegionReferenceSets<RuleTypes> refs = extraction.nameReferences(RULE_NAME_REFERENCES);
        Set<RuleTypes> ruleReferenceTypesInDuplicateRegion = EnumSet.noneOf(RuleTypes.class);
        ContentsChecksums<SemanticRegion<Void>> checksums = extraction.checksums(BLOCKS);
        if (!checksums.isEmpty()) {
            int[] index = new int[1];
            // XXX we should detect the case that the checksum match is also the only
            // contents of a rule, and in that case, offer to USE that rule rather than
            // generate a new one - i.e. if you have
            // Thing : DIGIT '\n';
            // Newline : '\n';
            // then offer to replace the first '\n' with a reference to Newline - or
            // create a fragment rule for '\n' and use that from both
            checksums.visitRegionGroups(duplicateSet -> {
                index[0]++;
                List<PositionBoundsRange> ranges = new ArrayList<>(duplicateSet.size());
                StringBuilder occurrences = new StringBuilder(duplicateSet.size() * 4);
                String text = null;
                List<String> namesInBlock = new ArrayList<String>();
                for (SemanticRegion<Void> tokensWithSameChecksum : duplicateSet) {
                    PositionBoundsRange pbr = PositionBoundsRange.create(extraction.source(), tokensWithSameChecksum);
                    if (pbr == null) {
                        return;
                    }
                    ranges.add(pbr);
                    if (text == null) {
                        try {
                            text = Strings.dequote(pbr.bounds().getText().trim(), '(', ')');
                        } catch (BadLocationException | IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                    if (ruleReferenceTypesInDuplicateRegion.isEmpty()) {
                        loop:
                        for (NamedSemanticRegionReference<RuleTypes> ref : refs.asIterable()) {
                            switch (tokensWithSameChecksum.relationTo(ref)) {
                                case BEFORE:
//                                        break loop;
                                    break;
                                case CONTAINS:
                                case CONTAINED:
                                case EQUAL:
                                    namesInBlock.add(ref.name());
                                    ruleReferenceTypesInDuplicateRegion.add(ref.kind());
                            }
                        }
                    }
                    if (occurrences.length() > 0) {
                        occurrences.append(',');
                    }
                    occurrences.append(tokensWithSameChecksum.start()).append(':').append(tokensWithSameChecksum.stop());
                }
                String details = Bundle.dup_hints_detail(text, duplicateSet.size());
                Supplier<String> det = () -> details;
                String msg = Bundle.dup_block(duplicateSet.size());
                RuleNamingConvention convention = RuleNamingConvention.forExtraction(extraction);
                String rn = createRuleName(namesInBlock, ruleReferenceTypesInDuplicateRegion, convention, allNames);
                for (PositionBoundsRange r : ranges) {
                    ExtractRule extract = new ExtractRule(ranges, extraction, r, text, rn);
                    String id = "bx-" + index[0] + "-" + r.original().start() + "-" + r.original().end();
                    if (!usedErrorIds.contains(id)) {
                        try {
                            fixes.addHint(id, r, msg, det, fixen -> {
                                fixen.add(details, extract);
                            });
                        } catch (BadLocationException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            });
        }
    }

    private static String createRuleName(List<String> rulesReferenced, Set<RuleTypes> ruleTypes, RuleNamingConvention namingConvention, NamedSemanticRegions<RuleTypes> allNames) {
        boolean containsParserRule = ruleTypes.isEmpty() || ruleTypes.contains(RuleTypes.PARSER);
        if (rulesReferenced.isEmpty()) {
            return containsParserRule ? "new_rule" : "NewRule";
        }
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = rulesReferenced.iterator(); it.hasNext();) {
            String name = it.next();
            if (containsParserRule) {
                name = name.toLowerCase();
            } else {
                switch (namingConvention) {
                    case LEXER_RULES_UPPER_CASE:
                        name = name.toUpperCase();
                }
            }
            sb.append(name);
            if (sb.length() > 32 && !allNames.contains(sb.toString())) {
                break;
            }
            if (it.hasNext() && (containsParserRule || namingConvention == RuleNamingConvention.LEXER_RULES_UPPER_CASE)) {
                sb.append('_');
            }
        }
        String result = sb.toString();
        if (allNames.contains(result)) {
            for (int i = 1;; i++) {
                String test = result + "_" + i;
                if (!allNames.contains(test)) {
                    result = test;
                    break;
                }
            }
        }
        return result;
    }

    @Messages({
        "# {0} - Rule name",
        "unusedRule=Unused rule ''{0}''",
        "# {0} - Rule name",
        "deleteRule=Delete rule ''{0}''",
        "# {0} - Rule name",
        "# {1} - Top rule",
        "# {2} - Path",
        "notReachableRule=Rule ''{0}'' is used but not reachable from {1} but is used by {2}",
        "# {0} - Rule name",
        "# {1} - Path",
        "deleteRuleAndClosure=Delete ''{0}'' and its closure {1}",})
    private void flagOrphansIfNoImports(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType,
            Extraction extraction,
            AntlrGenerationResult res,
            ParseResultContents populate,
            Fixes fixes,
            Set<String> usedErrorIds
    ) {
        if (res == null || res.mainGrammar == null || res.mainGrammar.isLexer()) {
            // XXX should use usages finder for lexer rules
            return;
        }
        NamedSemanticRegions<RuleTypes> rules = extraction.namedRegions(AntlrKeys.RULE_NAMES);
        if (!rules.isEmpty()) {
            StringGraph graph = extraction.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES);
            if (graph == null) {
                // In an undo operation, the extraction nmay have been disposed
                // Should fix this never to return null
                return;
            }
            NamedSemanticRegions<RuleTypes> ruleBounds = extraction.namedRegions(AntlrKeys.RULE_BOUNDS);
            NamedSemanticRegion<RuleTypes> firstRule = rules.index().first();
            String firstRuleName = firstRule.name();
            Set<String> seen = new HashSet<>();
            Optional<Document> doc = extraction.source().lookup(Document.class);
            if (!doc.isPresent()) {
                return;
            }
            // Flag orphan nodes for deletion hints
            Set<String> orphans = new LinkedHashSet<>(graph.topLevelOrOrphanNodes());

            // Find any nodes that have skip or channel directives
            // and omit them from orphans - they are used to route
            // comments and whitespace out of the parse tree, but
            // will look like they are unused
            SemanticRegions<ChannelsAndSkipExtractors.ChannelOrSkipInfo> skippedAndSimilar
                    = extraction.regions(ChannelsAndSkipExtractors.CHSKIP);
            // Build a graph that cross-references the rule that contains a
            // skip or channel directive with the named rule that contains
            // it - rules that contain a skip or channel directive will have
            // an edge to that directive, so we just need to iterate our
            // rules and exclude the parents
            BitSetHeteroObjectGraph<NamedSemanticRegion<RuleTypes>, SemanticRegion<ChannelsAndSkipExtractors.ChannelOrSkipInfo>, ?, SemanticRegions<ChannelsAndSkipExtractors.ChannelOrSkipInfo>> hetero
                    = ruleBounds.crossReference(skippedAndSimilar);

            boolean[] hasImporters = new boolean[1];
            for (SemanticRegion<ChannelsAndSkipExtractors.ChannelOrSkipInfo> s : skippedAndSimilar) {
                // Find the owners and remove them
                for (NamedSemanticRegion<RuleTypes> parent : hetero.rightSlice().parents(s)) {
                    orphans.remove(parent.name());
                }
            }
            Optional<FileObject> of = extraction.source().lookup(FileObject.class);
            if (of.isPresent()) {
                ImportersFinder imf = ImportersFinder.forFile(of.get());
                Problem p = imf.usagesOf(() -> false, of.get(), AntlrKeys.IMPORTS,
                        (IntRange<? extends IntRange<?>> a, String grammarName, FileObject importer, ExtractionKey<?> importerKey, Extraction importerExtraction) -> {
                            if (importer.equals(of.get())) {
                                // self import?
                                return null;
                            }
                            SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = importerExtraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
                            // XXX could attribute and be sure we're talking about the right references
                            for (SemanticRegion<UnknownNameReference<RuleTypes>> r : unknowns) {
                                String name = r.key().name();
                                orphans.remove(name);
                            }
                            hasImporters[0] = true;
                            return null;
                        });
                if (p != null && p.isFatal()) {
                    throw new IllegalStateException("Failed searching for importers: " + p.getMessage());
                } else if (p != null) {
                    LOG.log(Level.WARNING, p.getMessage());
                }
            }
            if (!hasImporters[0]) {
                orphans.remove(firstRuleName);
            }
            for (String name : orphans) {
                String errId = "orphan-" + name;
                if (usedErrorIds.contains(errId)) {
                    continue;
                }
                if (seen.contains(name)) {
                    continue;
                }
                usedErrorIds.add(errId);
                try {
                    String msg = Bundle.unusedRule(name);
                    fixes.addWarning(errId, rules.regionFor(name), msg, fixen -> {
                        NamedSemanticRegion<RuleTypes> bounds = ruleBounds.regionFor(name);
                        // Offer to delete just that rule:
                        if (graph.inboundReferenceCount(name) == 0) {
                            fixen.addDeletion(Bundle.deleteRule(name), bounds.start(), bounds.end());
                            seen.add(name);
                        }

                        Set<String> deletableClosure = findDeletableClosureOfOrphan(name, graph);
                        if (deletableClosure.size() > 1) {
                            // We can also delete the closure of this rule wherever no other
                            // rules reference it
                            Set<IntRange<? extends IntRange<?>>> closureRanges = new HashSet<>();
                            // We will need to prune some rules out of the closure if they
                            // would still be referenced by others, as those would become
                            // syntax errors
                            for (String cl : deletableClosure) {
                                closureRanges.add(ruleBounds.regionFor(cl));
                            }
                            if (closureRanges.size() > 0) {
                                // Don't put the name of the rule in its closure
                                deletableClosure.remove(name);
                                String closureString
                                        = "<i>"
                                        + Strings.join(", ",
                                                CollectionUtils.reversed(
                                                        new ArrayList<>(deletableClosure)));
                                fixen.addDeletions(
                                        Bundle.deleteRuleAndClosure(name,
                                                elide(closureString)),
                                        closureRanges);
                                seen.add(name);
                            }
                        }
                    });

                    Set<String> closure = graph.closureOf(name);
                    for (String node : closure) {
                        if (seen.contains(node)) {
                            continue;
                        }
                        seen.add(node);
                        // XXX at some point, note if there is a channels() or skip
                        // directive and don't offer to delete those
                        Set<String> revClosure = graph.reverseClosureOf(node);
                        if (!revClosure.contains(firstRuleName)) {
                            String rc = Strings.join("<-", revClosure);
                            NamedSemanticRegion<RuleTypes> subBounds
                                    = ruleBounds.regionFor(node);
                            fixes.addWarning("nr-" + node, subBounds,
                                    Bundle.notReachableRule(node, firstRuleName,
                                            rc));
                        }
                    }
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    @Messages({
        "# {0} - the file name",
        "# {1} - the grammar name as found in the grammar file",
        "fileAndGrammarMismatch=File name {0} does not match grammar name {1}",
        "# {0} - the file name",
        "# {1} - the grammar name as found in the grammar file",
        "replaceGrammarName=Replace {0} with {1}"
    })
    private void checkGrammarNameMatchesFile(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType,
            Extraction extraction,
            AntlrGenerationResult res,
            ParseResultContents populate,
            Fixes fixes,
            Set<String> usedErrorIds
    ) {
        SingletonEncounters<GrammarDeclaration> grammarDecls = extraction.singletons(AntlrKeys.GRAMMAR_TYPE);
        if (grammarDecls.hasEncounter()) {
            SingletonEncounters.SingletonEncounter<GrammarDeclaration> decl = grammarDecls.first();
            assert decl != null;
            Optional<FileObject> file = extraction.source().lookup(FileObject.class);
            if (file != null) {
                String name = file.get().getName();
                if (!Objects.equals(name, decl.get().name())) {
                    try {
                        String msg = Bundle.fileAndGrammarMismatch(name, decl.get().name());
                        String errId = "bad-name-" + name;
                        if (!usedErrorIds.contains(errId)) {
                            usedErrorIds.add(errId);
                            fixes.addError(errId, decl.start(), decl.end(),
                                    msg, fixen -> {
                                        fixen.addReplacement(Bundle.replaceGrammarName(
                                                decl.get().name(), extraction.source().name()),
                                                decl.start(), decl.end(), name);
                                    });
                        }
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
    }

    private List<EpsilonRuleInfo> updateErrorHighlights(AntlrGenerationResult res,
            Extraction extraction, Fixes fixes, Set<String> usedErrIds) {
        if (res == null) {
            return Collections.emptyList();
        }
        OffsetsBag brandNewBag = new OffsetsBag(ctx.getDocument(), true);
        boolean[] anyHighlights = new boolean[1];
        List<ParsedAntlrError> errors = res.errors();
        Optional<Path> path = extraction.source().lookup(Path.class);
        List<EpsilonRuleInfo> epsilons = new ArrayList<>(errors.size());
        for (ParsedAntlrError err : errors) {
            LOG.log(Level.FINEST, "Handle err {0}", err);
            boolean shouldAdd = true;
            if (path.isPresent()) {
                // Convert to UnixPath to ensure endsWith test works
                UnixPath p = UnixPath.get(path.get());
                // We can have errors in included files, so only
                // process errors in the one we're really supposed
                // to show errors for
                shouldAdd = p.endsWith(err.path());
            }
            if (shouldAdd) {
                // Special handling for epsilons - these are wildcard
                // blocks that can match the empty string - we have
                // hints that will offer to replace
                EpsilonRuleInfo eps = err.info(EpsilonRuleInfo.class);
                if (eps != null) {
                    epsilons.add(eps);
                    try {
                        handleEpsilon(err, fixes, extraction, eps, usedErrIds);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    continue;
                }
                offsetsOf(err, (startOffset, endOffset) -> {
                    if (startOffset == endOffset) {
                        LOG.log(Level.INFO, "Got silly start and end offsets "
                                + "{0}:{1} - probably we are compuing fixes for "
                                + " an old revision of {2}.",
                                new Object[]{startOffset, endOffset,
                                    res.grammarName});
                        return;
                    }
                    try {
                        if (err.length() > 0) {
                            anyHighlights[0] = true;
                            brandNewBag.addHighlight(startOffset, endOffset,
                                    err.isError() ? errorHighlight : warningHighlight);
                        } else {
                            LOG.log(Level.WARNING, "Got {0} length error {1}", new Object[]{err.length(), err});
                            return;
                        }
                        if (!handleFix(err, fixes, extraction, usedErrIds)) {
                            String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                            if (!usedErrIds.contains(errId)) {
                                usedErrIds.add(errId);
                                if (err.isError()) {
                                    LOG.log(Level.FINEST, "Add error for {0} offsets {1}:{2}",
                                            new Object[]{err, startOffset, endOffset});
                                    fixes.addError(errId, startOffset, endOffset,
                                            err.message());
                                } else {
                                    LOG.log(Level.FINEST, "Add warning for {0} offsets {1}:{2}",
                                            new Object[]{err, startOffset, endOffset});
                                    fixes.addWarning(errId, startOffset, endOffset,
                                            err.message());
                                }
                            } else {
                                LOG.log(Level.FINE, "ErrId {0} already handled", errId);
                            }
                        } else {
                            LOG.log(Level.FINEST, "Handled with fix: {0}", err);
                        }
                    } catch (IllegalStateException ex) {
                        LOG.log(Level.FINE, "No line offsets in {0}", err);
                    } catch (BadLocationException | IndexOutOfBoundsException ex) {
                        Document doc = ctx.getDocument();
                        LOG.log(Level.WARNING, "Error line " + err.lineNumber()
                                + " position in line " + err.lineOffset()
                                + " file offset " + err.fileOffset()
                                + " err length " + err.length()
                                + " computed start:end: " + startOffset + ":" + endOffset
                                + " document length " + doc.getLength()
                                + " extraction source " + extraction.source()
                                + " as file " + extraction.source().lookup(FileObject.class)
                                + " my context file " + NbEditorUtilities.getFileObject(ctx.getDocument())
                                + " err was " + err, ex);
                    }
                });
            }
        }
        Mutex.EVENT.readAccess(() -> {
            if (anyHighlights[0]) {
                bag.setHighlights(brandNewBag);
            } else {
                bag.clear();
            }
            brandNewBag.discard();
        });
        return epsilons;
    }

    private void addHintsForMissingImports(Extraction extraction, Fixes fixes, Set<String> usedErrorIds) {
        ImportFinder imf = ImportFinder.forMimeType(AntlrConstants.ANTLR_MIME_TYPE);
        Set<NamedSemanticRegion<?>> unresolved = new HashSet<>();
        imf.allImports(extraction, unresolved);
        if (!unresolved.isEmpty()) {
            LOG.log(Level.FINER, "Found unresolved imports {0} in {1}",
                    new Object[]{unresolved, extraction.source()});
            for (NamedSemanticRegion<?> n : unresolved) {
                try {
                    addFixImportError(extraction, fixes, n, usedErrorIds);
                } catch (BadLocationException ble) {
                    LOG.log(Level.WARNING, "Exception adding hint for unresolved: " + n, ble);
                }
            }
        }
    }

    private void addHintsForUnresolvableReferences(SemanticRegions<UnknownNameReference<RuleTypes>> unknowns, Extraction extraction, Fixes fixes, Set<String> usedErrIds) throws MissingResourceException {
        if (!unknowns.isEmpty()) {
            Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved
                    = extraction.resolveAll(AntlrKeys.RULE_NAME_REFERENCES);

            SemanticRegions<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes>> attributed
                    = resolved == null ? SemanticRegions.empty() : resolved.attributed();

            Iterator<SemanticRegion<UnknownNameReference<RuleTypes>>> it = unknowns.iterator();
            while (it.hasNext()) {
                SemanticRegion<UnknownNameReference<RuleTypes>> unk = it.next();
                String text = unk.key().name();
                if ("EOF".equals(text)) {
                    continue;
                }
                if (attributed.at(unk.start()) != null) {
                    continue;
                }
                try {
                    String hint = NbBundle.getMessage(AntlrRuntimeErrorsHighlighter.class, "unknown_rule_referenced", text);
                    String errId = "unk-" + text + "-" + unk.start() + "-" + unk.end();
                    if (!usedErrIds.contains(errId)) {
                        fixes.addError(errId, unk, hint, fixConsumer -> {
                            NamedSemanticRegions<RuleTypes> nameRegions = extraction.namedRegions(AntlrKeys.RULE_NAMES);
                            for (String sim : nameRegions.topSimilarNames(text, 5)) {
                                String msg = NbBundle.getMessage(AntlrRuntimeErrorsHighlighter.class,
                                        "replace", text, sim);
                                fixConsumer.addReplacement(msg, unk.start(), unk.end(), sim);
                            }
                        });
                        usedErrIds.add(errId);
                    }
                } catch (BadLocationException ex) {
                    LOG.log(Level.WARNING, "Exception adding hint for unknown rule: " + unk, ex);
                }
            }
        }
    }

    @Messages({
        "# {0} - replacement grammar name",
        "replaceWith=Replace with {0}",
        "# {0} - replacement grammar name",
        "deleteImport=Delete import of {0}"
    })
    private <T extends Enum<T>> void addFixImportError(Extraction extraction, Fixes fixes, NamedSemanticRegion<T> reg, Set<String> usedErrorIds) throws BadLocationException {
        String target = reg.name();
        LOG.log(Level.FINEST, "Try to add import fix of {0} in {1} for {2}",
                new Object[]{target, extraction.source(), reg});
        String errId = "unresolv:" + target;
        if (usedErrorIds.contains(errId)) {
            return;
        }
        fixes.addError(errId, reg, Bundle.unresolved(reg.name()), fixer -> {
            Optional<FileObject> ofo = extraction.source().lookup(FileObject.class);
            if (ofo.isPresent()) {
                FileObject fo = ofo.get();
                Map<String, FileObject> nameForGrammar = new HashMap<>();
                Iterable<FileObject> allAntlrFilesInProject = CollectionUtils.concatenate(
                        Folders.ANTLR_GRAMMAR_SOURCES.allFiles(fo),
                        Folders.ANTLR_IMPORTS.allFiles(fo)
                );

                int ct = 0;
                for (FileObject possibleImport : allAntlrFilesInProject) {
                    ct++;
                    if (possibleImport.equals(fo)) {
                        LOG.log(Level.FINEST, "Skip possible import of self {0}",
                                possibleImport);
                        continue;
                    }
                    LOG.log(Level.FINEST, "Add possible import: {0}",
                            possibleImport.getNameExt());
                    nameForGrammar.put(possibleImport.getName(), fo);
                }
                LOG.log(Level.FINEST, "Found {0} possible imports for {1}: {2}",
                        new Object[]{ct, extraction.source(), nameForGrammar});

                List<String> matches = LevenshteinDistance.topMatches(5,
                        target, new ArrayList<>(nameForGrammar.keySet()));
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, "Possible import matches for '{0}' are {1} from {2}",
                            new Object[]{target, matches, nameForGrammar.keySet()});
                }
                for (String match : matches) {
                    if (match.equals(target)) {
                        continue;
                    }
                    fixer.addReplacement(Bundle.replaceWith(match), reg.start(), reg.end(), match);
                }
                fixer.addDeletion(Bundle.deleteImport(target), reg.start(), reg.end());
            } else {
                LOG.log(Level.WARNING, "No fileobject could be looked up "
                        + "from {0}", extraction.source());
            }
        });
    }

    @Messages({
        "# {0} - unresolvable imported grammar name",
        "unresolved=Unresolvable import: {0}",
        "capitalize=Capitalize name to make this a lexer rule",
        "illegalLabel=Cannot use a label here.  Remove?"
    })
    boolean handleFix(ParsedAntlrError err, Fixes fixes, Extraction ext, Set<String> usedErrIds) throws BadLocationException {
        EpsilonRuleInfo eps = err.info(EpsilonRuleInfo.class);
        if (eps != null) {
            return handleEpsilon(err, fixes, ext, eps, usedErrIds);
        }
        switch (err.code()) {
            case 51: // rule redefinition
            case 52: // lexer rule in parser grammar
            case 53: // parser rule in lexer grammar
            case 184: // rule overlapped by other rule and will never be used
                String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                if (usedErrIds.contains(errId)) {
                    return false;
                }
                NamedSemanticRegion<RuleTypes> region = ext.namedRegions(AntlrKeys.RULE_BOUNDS).at(err.fileOffset());
                if (region == null) {
                    return false;
                }
                fixes.addError(errId, region.start(), region.end(), err.message(), fixConsumer -> {
                    if (err.code() == 53) {
                        String name = findRuleNameInErrorMessage(err.message());
                        if (name != null) {
                            offsetsOf(err, (start, end) -> {
                                fixConsumer.addReplacement(Bundle.capitalize(), start, end, capitalize(name));
                            });
                        }
                    }

                    fixConsumer.addDeletion(NbBundle.getMessage(
                            AntlrRuntimeErrorsHighlighter.class, "delete_rule"),
                            region.start(), region.end());
                });
                return true;
            case 119:
                // e.g., "The following sets of rules are mutually left-recursive [foo, baz, koog]"
                int bstart = err.message().lastIndexOf('[');
                int bend = err.message().lastIndexOf(']');
                if (bend > bstart + 1 && bstart > 0) {
                    String sub = err.message().substring(bstart, bend - 1);
                    NamedSemanticRegions<RuleTypes> regions = ext.namedRegions(AntlrKeys.RULE_NAMES);
                    for (String item : sub.split(",")) {
                        item = item.trim();
                        NamedSemanticRegion<RuleTypes> reg = regions.regionFor(item);
                        if (reg != null) {
                            fixes.addError(reg, err.message());
                        }
                    }
                }
                // error 130 : label str assigned to a block which is not a set
                return true;
            case 130: // Parser rule Label assigned to a block which is not a set
            case 201: // label in a lexer rule

                String errId2 = err.code() + "-" + err.lineNumber() + "-" + err.lineOffset();
                if (!usedErrIds.contains(errId2)) {
                    Bool added = Bool.create();
                    offsetsOf(err, (start, end) -> {
                        if (end > start) {
                            PositionBoundsRange pbr = PositionBoundsRange.create(ext.source(), start, end);
                            try {
                                fixes.addError(pbr, err.message(), Bundle::illegalLabel, fixen -> {
                                    Int realEnd = Int.of(-1);
                                    ext.source().lookup(Document.class, d -> {
                                        d.render(() -> {
                                            Segment seg = new Segment();
                                            int len = d.getLength();
                                            if (len > pbr.end()) {
                                                try {
                                                    d.getText(pbr.end(), len - pbr.end(), seg);
                                                    for (int i = 0; i < seg.length(); i++) {
                                                        if ('=' == seg.charAt(i)) {
                                                            realEnd.set(i + 1);
                                                            return;
                                                        }
                                                    }
                                                } catch (BadLocationException ex) {
                                                    Exceptions.printStackTrace(ex);
                                                }
                                            }
                                        });
                                    });
                                    if (realEnd.getAsInt() > pbr.start()) {
                                        fixen.addDeletion(Bundle.illegalLabel(), pbr.start(), realEnd.getAsInt());
                                        added.set();
                                    }
                                });
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    });
                    return added.get();
                }
            default:
                return false;
        }
    }

    private static String findRuleNameInErrorMessage(String msg) {
        String msgStart = "parser rule ";
        // parser rule
        if (msg.startsWith(msgStart) && msg.length() > msgStart.length()) {
            StringBuilder sb = new StringBuilder();
            for (int i = msgStart.length(); i < msg.length(); i++) {
                char c = msg.charAt(i);
                if (Character.isLetter(c) || Character.isDigit(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }

    private static String capitalize(String name) {
        char[] c = name.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        return new String(c);
    }
}

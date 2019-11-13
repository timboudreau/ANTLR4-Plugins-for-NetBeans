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
import com.mastfrog.graph.StringGraph;
import com.mastfrog.range.IntRange;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.strings.Strings;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
import javax.swing.Action;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.AntlrConstants;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import static org.nemesis.antlr.error.highlighting.EbnfHintsExtractor.SOLO_EBNFS;
import org.nemesis.antlr.file.AntlrKeys;
import static org.nemesis.antlr.file.AntlrKeys.RULE_BOUNDS;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.DocumentEditBag;
import org.nemesis.antlr.spi.language.fix.FixImplementation;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.ContentsChecksums;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.distance.LevenshteinDistance;
import org.nemesis.editor.utils.DocumentOperator;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.text.NbDocument;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrRuntimeErrorsHighlighter implements Subscriber {

    protected final OffsetsBag bag;
    private final AttributeSet underlining;
    private static final Logger LOG = Logger.getLogger(
            AntlrRuntimeErrorsHighlighter.class.getName());
    private final Context ctx;

    @SuppressWarnings("LeakingThisInConstructor")
    AntlrRuntimeErrorsHighlighter(Context ctx) {
        this.ctx = ctx;
        bag = new OffsetsBag(ctx.getDocument(), true);
        // XXX listen for changes, etc
        MimePath mimePath = MimePath.parse(ANTLR_MIME_TYPE);
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet set = fcs.getFontColors("errors");
        if (set == null) {
            set = fcs.getFontColors("errors");
            if (set == null) {
                set = AttributesUtilities.createImmutable(EditorStyleConstants.WaveUnderlineColor, Color.RED);
            }
        }
        underlining = set;
        FileObject fo = NbEditorUtilities.getFileObject(ctx.getDocument());
        // Will be weakly referenced, so no leak:
        RebuildSubscriptions.subscribe(fo, this);
    }

    HighlightsContainer bag() {
        return bag;
    }

    private void offsetsOf(ParsedAntlrError error, IntBiConsumer startEnd) {
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
            int lineOffsetInDocument = NbDocument.findLineOffset((StyledDocument) doc, lineNumber);
            int errorStartOffset = Math.max(0, lineOffsetInDocument + error.lineOffset());
            int errorEndOffset = Math.min(docLength - 1, errorStartOffset + error.length());
            if (errorStartOffset < errorEndOffset) {
                startEnd.accept(errorStartOffset, errorEndOffset);
            } else {
                LOG.log(Level.INFO, "Computed nonsensical error start offsets "
                        + "{0}:{1} for line {2} of {3} for error {4}",
                        new Object[]{
                            errorStartOffset, errorEndOffset,
                            lineNumber, el.getElementCount(), error
                        });
            }
        }
    }

    @Override
    public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {
        updateErrorHighlights(res, extraction, fixes);
        SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = extraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
        addHintsForUnresolvableReferences(unknowns, extraction, fixes);
        addHintsForMissingImports(extraction, fixes);
        NamedSemanticRegions<EbnfItem> ebnfsThatCanMatchTheEmptyString
                = extraction.namedRegions(SOLO_EBNFS);
        if (ebnfsThatCanMatchTheEmptyString != null && !ebnfsThatCanMatchTheEmptyString.isEmpty()) {
            addEbnfHints(fixes, ebnfsThatCanMatchTheEmptyString);
        }
        checkGrammarNameMatchesFile(tree, mimeType, extraction, res, populate, fixes);
        flagOrphansIfNoImports(tree, mimeType, extraction, res, populate, fixes);
        flagDuplicateBlocks(tree, mimeType, extraction, res, populate, fixes);
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
        "# {0} - occurrences",
        "# {1} - text",
        "dup_hints_detail=The block ''{0}'' occurs {1} times in the grammar. Extract into a new rule?",
        "# {0} - occurrences",
        "dup_block=The same parenthesized expression occurs at {0}.  Extract into a new rule?"
    })
    private void flagDuplicateBlocks(ANTLRv4Parser.GrammarFileContext tree,
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {
        ContentsChecksums<SemanticRegion<Void>> checksums = extraction.checksums(AntlrKeys.BLOCKS);
        if (!checksums.isEmpty()) {
            int[] index = new int[1];
            checksums.visitRegionGroups(group -> {
                System.out.println("\nDuplicate region groups: " + group);
                index[0]++;
                List<PositionBoundsRange> ranges = new ArrayList<>(group.size());
                StringBuilder occurrences = new StringBuilder(group.size() * 4);
                String text = null;
                for (SemanticRegion<Void> region : group) {
                    PositionBoundsRange pbr = PositionBoundsRange.create(extraction.source(), region);
                    if (pbr == null) {
                        System.out.println("Could not create pbr for " + region);
                        return;
                    }
                    ranges.add(pbr);
                    if (text == null) {
                        try {
                            text = pbr.bounds().getText();
                        } catch (BadLocationException | IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                    if (occurrences.length() > 0) {
                        occurrences.append(',');
                    }
                    occurrences.append(region.start()).append(':').append(region.stop());
                }
                String details = Bundle.dup_hints_detail(text, group.size());
                Supplier<String> det = () -> details;
                String msg = Bundle.dup_block(group.size());
                for (PositionBoundsRange r : ranges) {
                    ExtractRule extract = new ExtractRule(ranges, extraction, r, text);
                    String id = "bx-" + index[0] + "-" + r.original().index();
                    try {
                        fixes.addHint(id, r, msg, det, fixen -> {
                            fixen.add(details, extract);
                        });
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
        }
    }

    static final class ExtractRule implements FixImplementation, Runnable, DocumentListener, ActionListener {

        private final List<PositionBoundsRange> ranges;
        private final Extraction ext;
        private final PositionBoundsRange preferred;
        private final String text;
        private final Timer timer;
        private PositionBounds toSelect;
        private BaseDocument doc;

        ExtractRule(List<PositionBoundsRange> ranges, Extraction ext, PositionBoundsRange preferred, String text) {
            this.ranges = ranges;
            this.ext = ext;
            this.preferred = preferred;
            this.text = text;
            timer = new Timer(75, this);
            timer.setRepeats(false);
        }

        private String newRuleText() {
            return Strings.escape(text, ch -> {
                switch (ch) {
                    case '\n':
                    case '\t':
                    case 0:
                        return "";
                    case ' ':
                        return "";
                }
                return Strings.singleChar(ch);
            }).replaceAll("\\s+", " ");
        }

        private String newRuleName() {
            // XXX scan the references inside the bounds, and figure out if any
            // are parser rules, if so, lower case, if not upper
            return "new_rule";
        }

        private int newRuleInsertPosition() {
            // XXX get the current caret position and find the rule nearest
            NamedSemanticRegions<RuleTypes> rules = ext.namedRegions(RULE_BOUNDS);
            NamedSemanticRegion<RuleTypes> region = rules.at(preferred.original().start());
            if (region != null) {
                return region.end() + 1;
            }
            for (PositionBoundsRange pb : ranges) {
                region = rules.at(pb.start());
                if (region != null) {
                    return region.end() + 1;
                }
            }
            return ext.source().lookup(Document.class).get().getLength() - 1;
        }

        @Override
        public void implement(BaseDocument document, Optional<FileObject> file, Extraction extraction, DocumentEditBag edits) throws Exception {
            doc = document;
            String name = newRuleName();
            int pos = newRuleInsertPosition();
            String newText = newRuleText();
            toSelect = PositionBoundsRange.createBounds(extraction.source(), pos, pos + name.length());
            DocumentOperator.builder().disableTokenHierarchyUpdates().readLock().writeLock().singleUndoTransaction()
                    .blockIntermediateRepaints().acquireAWTTreeLock().build().operateOn((StyledDocument) document)
                    .operate(() -> {
                        for (PositionBoundsRange bds : ranges) {
                            edits.replace(document, bds.start(), bds.end(), name);
                        }
                        int newTextStart = toSelect.getBegin().getOffset();
                        document.addPostModificationDocumentListener(this);
                        edits.insert(document, newTextStart, '\n' + name + " : " + newText + ";\n");
                        return null;
                    });
        }

        @Override
        public void run() {
            if (!timer.isRunning()) {
                timer.start();
            }
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            doc.removePostModificationDocumentListener(this);
            EventQueue.invokeLater(this);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            doc.removePostModificationDocumentListener(this);
            EventQueue.invokeLater(this);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            doc.removePostModificationDocumentListener(this);
            EventQueue.invokeLater(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            System.out.println("Try to position cursor");
            JTextComponent comp = EditorRegistry.findComponent(doc);
            if (comp != null && toSelect != null) {
                int caretDest = toSelect.getBegin().getOffset() + 1;
                comp.getCaret().setDot(caretDest);
                System.out.println("  send caret to " + caretDest);
                String mimeType = NbEditorUtilities.getMimeType(doc);
                if (mimeType != null) {
                    System.out.println("  mime" + mimeType);
                    Action action = FileUtil.getConfigObject("Editors/" + mimeType + "/Actions/in-place-refactoring.instance", Action.class);
                    System.out.println("    action " + action);
                    if (action != null) {
                        ActionEvent ae = new ActionEvent(comp, ActionEvent.ACTION_PERFORMED, "in-place-refactoring");
                        System.out.println("   sending action event to " + action);
                        action.actionPerformed(ae);
                    }
                }
            }
        }
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
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {
        if (extraction.namedRegions(AntlrKeys.IMPORTS).isEmpty()) {
            NamedSemanticRegions<RuleTypes> rules = extraction.namedRegions(AntlrKeys.RULE_NAMES);
            if (!rules.isEmpty()) {
                StringGraph graph = extraction.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES);
                NamedSemanticRegions<RuleTypes> ruleBounds = extraction.namedRegions(AntlrKeys.RULE_BOUNDS);
                NamedSemanticRegion<RuleTypes> firstRule = rules.index().first();
                String firstRuleName = firstRule.name();
                Set<String> seen = new HashSet<>();
                Optional<Document> doc = extraction.source().lookup(Document.class);
                if (!doc.isPresent()) {
                    return;
                }
                Set<String> orphans = new LinkedHashSet<>(graph.topLevelOrOrphanNodes());
                orphans.remove(firstRuleName);
                for (String name : orphans) {
                    if (seen.contains(name)) {
                        continue;
                    }
                    try {
                        String msg = Bundle.unusedRule(name);
                        fixes.addWarning("orphan-" + name, rules.regionFor(name), msg, fixen -> {
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
            String mimeType, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes) {
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
                        fixes.addError("bad-name-" + name, decl.start(), decl.end(),
                                msg, fixen -> {
                                    fixen.addReplacement(Bundle.replaceGrammarName(
                                            decl.get().name(), extraction.source().name()),
                                            decl.start(), decl.end(), name);
                                });
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
    }

    private void updateErrorHighlights(AntlrGenerationResult res, Extraction extraction, Fixes fixes) {
        OffsetsBag brandNewBag = new OffsetsBag(ctx.getDocument(), true);
        boolean[] anyHighlights = new boolean[1];
        List<ParsedAntlrError> errors = res.errors();
        Optional<Path> path = extraction.source().lookup(Path.class);
        for (ParsedAntlrError err : errors) {
            boolean shouldAdd = true;
            if (path.isPresent()) {
                Path p = path.get();
                shouldAdd = p.endsWith(err.path());
            }
            if (shouldAdd) {
                offsetsOf(err, (startOffset, endOffset) -> {
                    try {
                        if (err.isError() && err.length() > 0) {
                            anyHighlights[0] = true;
                            brandNewBag.addHighlight(startOffset, endOffset, underlining);
                        } else {
                            LOG.log(Level.WARNING, "Got {0} length error {1}", new Object[]{err.length(), err});
                            return;
                        }
                        if (!handleFix(err, fixes, extraction)) {
                            String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                            if (err.isError()) {
                                fixes.addError(errId, startOffset, endOffset,
                                        err.message());
                            } else {
                                fixes.addWarning(errId, startOffset, endOffset,
                                        err.message());
                            }
                        }
                    } catch (IllegalStateException ex) {
                        LOG.log(Level.FINE, "No line offsets in {0}", err);
                    } catch (BadLocationException | IndexOutOfBoundsException ex) {
                        LOG.log(Level.WARNING, "Error line " + err.lineNumber()
                                + " position in line " + err.lineOffset()
                                + " file offset " + err.fileOffset()
                                + " err length " + err.length()
                                + " computed start:end: " + startOffset + ":" + endOffset, ex);
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
    }

    private void addHintsForMissingImports(Extraction extraction, Fixes fixes) {
        ImportFinder imf = ImportFinder.forMimeType(AntlrConstants.ANTLR_MIME_TYPE);
        Set<NamedSemanticRegion<?>> unresolved = new HashSet<>();
        imf.allImports(extraction, unresolved);
        if (!unresolved.isEmpty()) {
            LOG.log(Level.FINER, "Found unresolved imports {0} in {1}",
                    new Object[]{unresolved, extraction.source()});
            for (NamedSemanticRegion<?> n : unresolved) {
                try {
                    addFixImportError(extraction, fixes, n);
                } catch (BadLocationException ble) {
                    LOG.log(Level.WARNING, "Exception adding hint for unresolved: " + n, ble);
                }
            }
        }
    }

    private void addHintsForUnresolvableReferences(SemanticRegions<UnknownNameReference<RuleTypes>> unknowns, Extraction extraction, Fixes fixes) throws MissingResourceException {
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
                    fixes.addHint(hint, unk, text, fixConsumer -> {
                        NamedSemanticRegions<RuleTypes> nameRegions = extraction.namedRegions(AntlrKeys.RULE_NAMES);
                        for (String sim : nameRegions.topSimilarNames(text, 5)) {
                            String msg = NbBundle.getMessage(AntlrRuntimeErrorsHighlighter.class,
                                    "replace", text, sim);
                            fixConsumer.addReplacement(msg, unk.start(), unk.end(), sim);
                        }
                    });
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
    private <T extends Enum<T>> void addFixImportError(Extraction extraction, Fixes fixes, NamedSemanticRegion<T> reg) throws BadLocationException {
        String target = reg.name();
        LOG.log(Level.FINEST, "Try to add import fix of {0} in {1} for {2}",
                new Object[]{target, extraction.source(), reg});

        fixes.addError("unresolvable:" + target, reg, Bundle.unresolved(reg.name()), fixer -> {
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
        "unresolved=Unresolvable import: {0}"
    })
    boolean handleFix(ParsedAntlrError err, Fixes fixes, Extraction ext) throws BadLocationException {
        switch (err.code()) {
            case 51: // rule redefinition
            case 52: // lexer rule in parser grammar
            case 53: // parser rule in lexer grammar
                String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                NamedSemanticRegion<RuleTypes> region = ext.namedRegions(AntlrKeys.RULE_BOUNDS).at(err.fileOffset());
                if (region == null) {
                    return false;
                }
                fixes.addError(errId, region.start(), region.end(), err.message(), fixConsumer -> {
                    fixConsumer.addDeletion(NbBundle.getMessage(
                            AntlrRuntimeErrorsHighlighter.class, "delete_rule"),
                            region.start(), region.end());
                });
                return true;
            case 148: // epsilon closure
            case 153: // epsilon lr follow
            case 154: // epsilon optional
            default:
                return false;
        }
    }

    @Messages({
        "# {0} - the original ebnf expression ending in * or ?",
        "# {1} - the proposed replacement",
        "emptyStringMatch={0} can match the empty string. Replace with {1}?"
    })
    private void addEbnfHints(Fixes fixes, NamedSemanticRegions<EbnfItem> ebnfs) {
        for (NamedSemanticRegion<EbnfItem> reg : ebnfs) {
            String tokenAndEbnf = reg.name();
            String wildcarded = tokenAndEbnf;
            for (char c = wildcarded.charAt(wildcarded.length() - 1); c == '?'
                    || c == '*'; c = wildcarded.charAt(wildcarded.length() - 1)) {
                wildcarded = wildcarded.substring(0, wildcarded.length() - 1);
            }
            EbnfItem replacementEbnf = reg.kind().nonEmptyMatchingReplacement();
            String replacement;
            if (replacementEbnf.matchesMultiple()) {
                replacement = wildcarded + " (" + wildcarded + replacementEbnf + ")?";
            } else {
                replacement = wildcarded + replacementEbnf;
            }
            String msg = Bundle.emptyStringMatch(tokenAndEbnf, replacement);
            try {
                fixes.addHint(reg.toString(), reg, msg, fc -> {
                    fc.addReplacement(msg, reg.start(), reg.end(), replacement);
                });
            } catch (BadLocationException ex) {
                LOG.log(Level.SEVERE, "Exception adding ebnf hint", ex);
            }
        }
    }
}

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
import com.mastfrog.util.collections.CollectionUtils;
import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.AntlrConstants;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import static org.nemesis.antlr.error.highlighting.EbnfHintsExtractor.SOLO_EBNFS;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.distance.LevenshteinDistance;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
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

    static {
        LOG.setLevel(Level.ALL);
    }

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
                        System.err.println("No line offsets in " + err);
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

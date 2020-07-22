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
package org.nemesis.antlr.error.highlighting.hints;

import org.nemesis.antlr.error.highlighting.spi.AntlrHintGenerator;
import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Int;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.strings.Strings.capitalize;
import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.tool.ext.EpsilonRuleInfo;
import org.nemesis.antlr.memory.tool.ext.ProblematicEbnfInfo;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - unresolvable imported grammar name",
    "unresolved=Unresolvable import: {0}",
    "capitalize=Capitalize name to make this a lexer rule",
    "illegalLabel=Cannot use a label here.  Remove?",})
@ServiceProvider(service=AntlrHintGenerator.class)
public final class AntlrErrorHighlightsAndHints extends AntlrHintGenerator implements LookupListener {

    private Lookup.Result<FontColorSettings> settingsResult;
    private FontColorSettings settings;
    private AttributeSet errors;
    private AttributeSet warnings;

    private FontColorSettings settings() {
        if (settings != null) {
            return settings;
        }
        if (settingsResult != null) {
            Collection<? extends FontColorSettings> all = settingsResult.allInstances();
            if (!all.isEmpty()) {
                return settings = all.iterator().next();
            } else {
                return null;
            }
        }
        Lookup lookup = MimeLookup.getLookup(MimePath.parse(ANTLR_MIME_TYPE));
        FontColorSettings result = null;
        settingsResult = lookup.lookupResult(FontColorSettings.class);
        Collection<? extends FontColorSettings> all = settingsResult.allInstances();
        if (!all.isEmpty()) {
            result = all.iterator().next();
        }
        settingsResult.addLookupListener(this);;
        return settings = result;
    }

    @Override
    public void resultChanged(LookupEvent le) {
        settings = null;
        errors = null;
        warnings = null;
    }

    private AttributeSet defaultErrors() {
        return AttributesUtilities.createImmutable(
                EditorStyleConstants.WaveUnderlineColor,
                Color.RED.darker());
    }

    private AttributeSet defaultWarnings() {
        return AttributesUtilities.createImmutable(
                EditorStyleConstants.WaveUnderlineColor,
                Color.ORANGE);
    }

    private AttributeSet find(String... names) {
        FontColorSettings colorings = settings();
        if (colorings == null) {
            return null;
        }
        for (String nm : names) {
            AttributeSet attrs = colorings.getFontColors(nm);
            if (attrs == null) {
                attrs = colorings.getTokenFontColors(nm);
            }
            if (attrs != null) {
                return attrs;
            }
        }
        return null;
    }

    private AttributeSet errors() {
        if (errors != null) {
            return errors;
        }
        AttributeSet result = find("error", "errors");
        if (result == null) {
            result = defaultErrors();
        }
        return errors = result;
    }

    private AttributeSet warnings() {
        if (warnings != null) {
            return warnings;
        }
        AttributeSet result = find("error", "errors");
        if (result == null) {
            result = defaultWarnings();
        }
        return warnings = result;
    }

    @Override
    protected boolean generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes, Document doc, PositionFactory positions, OffsetsBag highlights) throws BadLocationException {
        Bool any = Bool.create();
        updateErrorHighlights(tree, extraction, res, populate, fixes, doc, positions, highlights, any);
        return any.getAsBoolean();
    }

    private void updateErrorHighlights(ANTLRv4Parser.GrammarFileContext tree,
            Extraction extraction, AntlrGenerationResult res, ParseResultContents populate,
            Fixes fixes, Document doc, PositionFactory positions, OffsetsBag brandNewBag, Bool anyHighlights) throws BadLocationException {
        if (res == null) {
            return;
        }

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
                        handleEpsilon(err, fixes, extraction, eps);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    continue;
                }
                offsetsOf(doc, err, (startOffset, endOffset) -> {
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
                            anyHighlights.set();
                            brandNewBag.addHighlight(startOffset, endOffset,
                                    err.isError() ? errors() : warnings());
                        } else {
                            LOG.log(Level.WARNING, "Got {0} length error {1}", new Object[]{err.length(), err});
                            return;
                        }
                        if (!handleFix(err, fixes, extraction, doc, positions)) {
                            String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                            if (!fixes.isUsedErrorId(errId)) {
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
                        LOG.log(Level.WARNING, "Error line " + err.lineNumber()
                                + " position in line " + err.lineOffset()
                                + " file offset " + err.fileOffset()
                                + " err length " + err.length()
                                + " computed start:end: " + startOffset + ":" + endOffset
                                + " document length " + doc.getLength()
                                + " extraction source " + extraction.source()
                                + " as file " + extraction.source().lookup(FileObject.class)
                                + " my context file " + NbEditorUtilities.getFileObject(doc)
                                + " err was " + err, ex);
                    }
                });
            }
        }
    }

    /**
     * Recognize the error code, and if it's one we can provide a fix for, do that,
     * rather than using the stock highlighting.
     *
     * @param err The error
     * @param fixes The fixes
     * @param ext The extraction
     * @param doc The document
     * @param positions The position factory
     * @return True if the error was handled
     * @throws BadLocationException
     */
    @Messages({
        "# {0} - reason",
        "deleteRuleForReason=Delete rule? {0}"
    })
    boolean handleFix(ParsedAntlrError err, Fixes fixes, Extraction ext,
            Document doc, PositionFactory positions) throws BadLocationException {
        EpsilonRuleInfo eps = err.info(EpsilonRuleInfo.class);
        if (eps != null) {
            return handleEpsilon(err, fixes, ext, eps);
        }
        switch (err.code()) {
            case 51: // rule redefinition
            case 52: // lexer rule in parser grammar
            case 53: // parser rule in lexer grammar
            case 184: // rule overlapped by other rule and will never be used
                String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                if (fixes.isUsedErrorId(errId)) {
                    return false;
                }
                Bool bl = Bool.create();
                offsetsOf(doc, err, (start, end) -> {
                    NamedSemanticRegion<RuleTypes> region = ext.namedRegions(AntlrKeys.RULE_BOUNDS).at(start);
                    if (region == null) {
                        return;
                    }
                    PositionRange pr = positions.range(region);
                    if (region == null) {
                        bl.set(false);
                    }
                    fixes.addError(errId, region.start(), region.end(), err.message(), fixConsumer -> {
                        if (err.code() == 53) {
                            String name = findRuleNameInErrorMessage(err.message());
                            if (name != null) {
                                try {
                                    PositionRange rng = positions.range(start, end);
                                    fixConsumer.addFix(Bundle.capitalize(), bag -> {
                                        bag.replace(rng, capitalize(name));
                                    });
                                } catch (BadLocationException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            } else {
                                System.out.println("NO RULE IN ERROR MSG '" + err.message() + "'");
                            }
                        }
                        fixConsumer.addFix(Bundle.deleteRuleForReason(err.message()),
                                bag -> bag.delete(pr));
                        bl.set(true);
                    });

                });
                return bl.get();
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
                return true;
            // error 130 : label str assigned to a block which is not a set
            case 130: // Parser rule Label assigned to a block which is not a set
            case 201: // label in a lexer rule

                String errId2 = err.code() + "-" + err.lineNumber() + "-" + err.lineOffset();
                if (!fixes.isUsedErrorId(errId2)) {
                    Bool added = Bool.create();
                    offsetsOf(doc, err, (start, end) -> {
                        if (end > start) {
                            PositionRange pbr = positions.range(start, end);
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
                                    fixen.addFix(Bundle.illegalLabel(), bag -> {
                                        bag.delete(pbr);
                                    });
                                    added.set();
                                }
                            });
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

    interface BadLocationIntBiConsumer {

        void accept(int start, int end) throws BadLocationException;
    }

    private int offsetsOf(Document doc, ParsedAntlrError error, BadLocationIntBiConsumer startEnd) throws BadLocationException {
        LineDocument lines = LineDocumentUtils.as(doc, LineDocument.class);
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
        if (lines != null) {
            int docLength = lines.getLength();
//            error.hasFileOffset();
            int lc = LineDocumentUtils.getLineCount(lines);
            int lineNumber = Math.max(0, error.lineNumber() - 1 >= lc
                    ? lc - 1 : error.lineNumber() - 1);
            int lineOffsetInDocument = NbDocument.findLineOffset((StyledDocument) doc, lineNumber);
            int errorStartOffset = Math.max(0, lineOffsetInDocument + error.lineOffset());
            int errorEndOffset = Math.min(lines.getLength() - 1, errorStartOffset + error.length());
            if (errorStartOffset < errorEndOffset) {
                startEnd.accept(Math.min(docLength - 1, errorStartOffset), Math.min(docLength - 1, errorEndOffset));
            } else {
                LOG.log(Level.INFO, "Computed nonsensical error start offsets "
                        + "{0}:{1} for line {2} of {3} for error {4}",
                        new Object[]{
                            errorStartOffset, errorEndOffset,
                            lineNumber, lc, error
                        });
            }
            return docLength;
        }
        return 0;
    }

    private IntRange<? extends IntRange<?>> offsets(ProblematicEbnfInfo info, Extraction ext) {
        return Range.ofCoordinates(info.start(), info.end());
    }

    @NbBundle.Messages({
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
    private boolean handleEpsilon(ParsedAntlrError err, Fixes fixes, Extraction ext, EpsilonRuleInfo eps) throws BadLocationException {
        if (eps.problem() != null) {
            ProblematicEbnfInfo prob = eps.problem();
            IntRange<? extends IntRange<?>> problemBlock
                    = offsets(prob, ext);

            PositionRange rng = PositionFactory.forDocument(
                    ext.source().lookup(Document.class).get()).range(problemBlock);

            String msg = Bundle.canMatchEmpty(prob.text());

            String pid = prob.text() + "-" + prob.start() + ":" + prob.end();
            LOG.log(Level.FINEST, "Handle epsilon {0}", eps);
            fixes.addError(pid, problemBlock, msg, () -> {
                String repl = computeReplacement(prob.text());
                String prepl = computePlusReplacement(prob.text());
                return Bundle.replaceEbnfWithLong(prob.text(), repl, prepl);
            }, fc -> {
                String repl = computeReplacement(prob.text());
                fc.addFix(Bundle.replaceEbnfWith(repl), bag -> {
                    bag.replace(rng, repl);
                });
                String prepl = computePlusReplacement(prob.text());
                String rpmsg = Bundle.replaceEbnfWith(prepl);
                fc.addFix(rpmsg, bag -> {
                    bag.replace(rng, prepl);
                });
            });
            return true;
        } else {
            IntRange<? extends IntRange<?>> cr = Range.ofCoordinates(eps.culpritStart(), eps.culpritEnd());
            IntRange<? extends IntRange<?>> vr = Range.ofCoordinates(eps.victimStart(), eps.victimEnd());
            String victimErrId = vr + "-" + err.code();
            if (!fixes.isUsedErrorId(victimErrId)) {
                fixes.addWarning(victimErrId, vr.start(), vr.end(), eps.victimErrorMessage());
            }
            String culpritErrId = cr + "-" + err.code();
            if (!fixes.isUsedErrorId(culpritErrId)) {
                fixes.addWarning(culpritErrId, cr, eps.culpritErrorMessage());
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
}

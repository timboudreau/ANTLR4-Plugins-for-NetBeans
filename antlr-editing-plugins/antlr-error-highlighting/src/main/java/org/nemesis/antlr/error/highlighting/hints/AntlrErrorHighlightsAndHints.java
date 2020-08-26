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

import org.nemesis.antlr.error.highlighting.spi.BadLocationIntBiConsumer;
import org.nemesis.antlr.error.highlighting.spi.AntlrHintGenerator;
import com.mastfrog.function.state.Bool;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.util.path.UnixPath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.error.highlighting.spi.ErrorHintGenerator;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.tool.ext.EpsilonRuleInfo;
import org.nemesis.antlr.memory.tool.ext.ProblematicEbnfInfo;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - unresolvable imported grammar name",
    "unresolved=Unresolvable import: {0}",
})
@ServiceProvider(service = AntlrHintGenerator.class)
public final class AntlrErrorHighlightsAndHints extends AntlrHintGenerator {

    private AttributeSet errors() {
        return super.colorings().errors();
    }

    private AttributeSet warnings() {
        return super.colorings().warnings();
    }

    @Override
    protected boolean generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes, Document doc, PositionFactory positions, OffsetsBag highlights) throws BadLocationException {
        Bool any = Bool.create();
        updateErrorHighlights(tree, extraction, res, populate, fixes, doc, positions, highlights, any);
        return any.getAsBoolean();
    }

    private void updateErrorHighlights(ANTLRv4Parser.GrammarFileContext tree,
            Extraction extraction, AntlrGenerationResult res,
            ParseResultContents populate, Fixes fixes, Document doc,
            PositionFactory positions, OffsetsBag brandNewBag,
            Bool anyHighlights) throws BadLocationException {
        if (res == null || extraction == null) {
            LOG.log(Level.FINE, "Null generation result; abort error processing {0}",
                    extraction.source().name());
            return;
        }
        List<ParsedAntlrError> errors = res.errors();
        Optional<Path> path = extraction.source().lookup(Path.class);
        List<EpsilonRuleInfo> epsilons = new ArrayList<>(errors.size());
        LOG.log(Level.FINER, "updateErrorHighlights for {0} with {1} errors",
                new Object[]{extraction.source().name(), errors.size()});
        for (ParsedAntlrError err : errors) {
            LOG.log(Level.FINEST, "{0} Handle err {1}", new Object[]{
                extraction.source().name(), err});
            boolean shouldAdd = true;
            if (path.isPresent()) {
                // Convert to UnixPath to ensure endsWith test works
                UnixPath p = UnixPath.get(path.get());
                // We can have errors in included files, so only
                // process errors in the one we're really supposed
                // to show errors for
                shouldAdd = p.endsWith(err.path());
                if (!shouldAdd) {
                    LOG.log(Level.INFO, "Antlr error file does not match "
                            + "highlighted file: {0} does not end with {1}",
                            new Object[]{p, err.path()});
                }
            }
            if (shouldAdd) {
                // Special handling for epsilons - these are wildcard
                // blocks that can match the empty string - we have
                // hints that will offer to replace
                EpsilonRuleInfo eps = err.info(EpsilonRuleInfo.class);
                if (eps != null) {
                    epsilons.add(eps);
                    try {
                        if (handleEpsilon(err, fixes, extraction, eps, brandNewBag, anyHighlights)) {
                            continue;
                        }
                    } catch (Exception | Error ex) {
                        LOG.log(Level.SEVERE, "Handling epsilon in " + extraction.source().name(), ex);
                    }
                }
                offsetsOf(doc, err, (startOffset, endOffset) -> {
                    if (startOffset == endOffset) {
                        if (err.length() > 0) {
                            endOffset = startOffset + err.length();
                        } else {
                            LOG.log(Level.INFO, "Got silly start and end offsets "
                                    + "{0}:{1} - probably we are compuing fixes for "
                                    + " an old revision of {2}.",
                                    new Object[]{startOffset, endOffset,
                                        res.grammarName});
                            return;
                        }
                    }
                    try {
                        if (startOffset == endOffset) {
                            LOG.log(Level.WARNING, "Got {0} length error {1}"
                                    + " from {2} to {3}", new Object[]{err.length(), err, startOffset, endOffset});
                            return;
                        }
                        boolean handled = ErrorHintGenerator.handleError(tree, err, fixes, extraction, doc, positions, brandNewBag, anyHighlights, this::colorings);
                        if (!handled) {
//                            String errId = err.lineNumber() + ";" + err.code() + ";" + err.lineOffset();
                            String errId = err.id();
                            anyHighlights.set();
                            brandNewBag.addHighlight(startOffset, Math.max(startOffset + err.length(), endOffset),
                                    err.isError() ? errors() : warnings());
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
                        LOG.log(Level.FINE, "No line offsets in " + err, ex);
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
                    } catch (RuntimeException | Error ex) {
                        LOG.log(Level.SEVERE, "Error processing errors for " + extraction.source().name(), ex);
                    }
                });
            } else {
                LOG.log(Level.FINE, "Error is in a different file: {0} vs {1}",
                        new Object[]{err.path(), path.isPresent() ? path.get() : "<no-path>"});
            }
        }
    }

    private static final boolean WIN = Utilities.isWindows();

    private int offsetsOf(Document doc, ParsedAntlrError error, BadLocationIntBiConsumer startEnd) throws BadLocationException {
        if (!WIN && error.hasFileOffset()) {
            // The updated and optimized line offset finding in MemoryTool seems
            // to be working well enough to use it rather than not trusting it.
            // Leaving it off on Windows for now, since \r's will probably
            // screw up the line lengths

            // Pending - left recursion items wind up spanning to the end of the file
//            startEnd.accept(error.fileOffset(), error.fileOffset() + error.length());
        }
        LineDocument lines = LineDocumentUtils.as(doc, LineDocument.class);
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
                if (errorStartOffset == 0 && errorEndOffset == -1) {
                    // Antlr does this for a few errors such as 99: Grammar contains no rules
                    startEnd.accept(0, 0);
                } else {
                    LOG.log(Level.INFO, "Computed nonsensical error start offsets "
                            + "{0}:{1} for line {2} of {3} for error {4}",
                            new Object[]{
                                errorStartOffset, errorEndOffset,
                                lineNumber, lc, error
                            });
                }
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
    private boolean handleEpsilon(ParsedAntlrError err, Fixes fixes, Extraction ext, EpsilonRuleInfo eps,
            OffsetsBag brandNewBag, Bool anyHighlights) throws BadLocationException {
        if (eps.problem() != null) {
            ProblematicEbnfInfo prob = eps.problem();
            IntRange<? extends IntRange<?>> problemBlock
                    = offsets(prob, ext);

            PositionRange rng = PositionFactory.forDocument(
                    ext.source().lookup(Document.class).get()).range(problemBlock);

            String msg = Bundle.canMatchEmpty(prob.text());

            String pid = prob.text() + "-" + prob.start() + ":" + prob.end();
            LOG.log(Level.FINEST, "Handle epsilon {0}", eps);
            brandNewBag.addHighlight(rng.start(), rng.end(), warnings());
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
                brandNewBag.addHighlight(vr.start(), vr.end(), warnings());
                anyHighlights.set();
                fixes.addWarning(victimErrId, vr.start(), vr.end(), eps.victimErrorMessage());
                return true;
            }
            String culpritErrId = cr + "-" + err.code();
            if (!fixes.isUsedErrorId(culpritErrId)) {
                brandNewBag.addHighlight(cr.start(), cr.end(), errors());
                anyHighlights.set();
                fixes.addWarning(culpritErrId, cr, eps.culpritErrorMessage());
                return true;
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

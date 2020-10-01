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
package org.nemesis.antlr.live.language;

import com.mastfrog.function.TriConsumer;
import com.mastfrog.function.state.Bool;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.subscription.SubscribableBuilder;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.SetFactories;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.NAME;
import static javax.swing.Action.SHORT_DESCRIPTION;
import static javax.swing.Action.SMALL_ICON;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.language.AdhocHighlighterManager.HighlightingInfo;
import org.nemesis.antlr.live.language.AlternativesExtractors.AlternativeKey;
import static org.nemesis.antlr.live.language.AlternativesExtractors.OUTER_ALTERNATIVES_WITH_SIBLINGS;
import org.nemesis.antlr.live.parsing.EmbeddedParserFeatures;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.Ambiguity;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ErrorNodeTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElementKind;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyDetailedSyntaxError;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.TokenAssociated;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocErrorHighlighter extends AbstractAntlrHighlighter implements Runnable {

    static final String PREFS_KEY_HIGHLIGHT_AMBIGUITIES = "highlight-ambiguities";
    static final String PREFS_KEY_HIGHLIGHT_LEXER_ERRORS = "highlight-lexer-errors";
    static final String PREFS_KEY_HIGHLIGHT_PARSER_ERRORS = "highlight-parser-errors";

    private static final int REFRESH_ERRORS_DELAY = 750;
    protected final OffsetsBag bag;
    private static AttributeSet errorColoring;
    private final RequestProcessor.Task refreshErrorsTask;
    private static final Set<AdhocErrorHighlighter> INSTANCES = new WeakSet<>();

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocErrorHighlighter(AdhocHighlighterManager mgr) {
        super(mgr, ZOrder.SHOW_OFF_RACK.forPosition(2001));
        bag = new OffsetsBag(mgr.document());
        refreshErrorsTask = mgr.threadPool().create(this);
        INSTANCES.add(this);
    }

    @Override
    public HighlightsContainer getHighlightsBag() {
        return bag;
    }

    private static Color errorColor() {
        Color c = UIManager.getColor("nb.errorForeground");
        if (c == null) {
            c = new Color(255, 190, 190);
        }
        return c;
    }

    public static AttributeSet errors() {
        // Do not cache - user can edit these
//        MimePath mimePath = MimePath.parse(ANTLR_MIME_TYPE);
//        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
//        AttributeSet result = fcs.getFontColors("nested_blocks");
//        assert result != null : "nested_block missing from colors";
//        return result;
        // XXX fixme
        if (errorColoring != null) {
            return errorColoring;
        }
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setUnderline(set, true);
        Color c = errorColor();
        StyleConstants.setForeground(set, c);
        StyleConstants.setBackground(set, new Color(60, 10, 10));
        set.addAttribute(EditorStyleConstants.WaveUnderlineColor, c.darker());
        return errorColoring = AttributesUtilities.createImmutable(set);
    }

    private static AttributeSet pErrors;

    public static AttributeSet parserErrors() {
        if (pErrors != null) {
            return pErrors;
        }
        SimpleAttributeSet s = new SimpleAttributeSet();
//        Color c = Color.RED;
        StyleConstants.setForeground(s, Color.RED);
        s.addAttribute(StyleConstants.Underline, Color.ORANGE);
        s.addAttribute(StyleConstants.Bold, true);
//        StyleConstants.setBackground(s, Color.BLUE);
//        s.addAttribute(EditorStyleConstants.BottomBorderLineColor, c);
//        s.addAttribute(EditorStyleConstants.LeftBorderLineColor, c);
//        s.addAttribute(EditorStyleConstants.RightBorderLineColor, c);
//        s.addAttribute(EditorStyleConstants.TopBorderLineColor, c);
        return pErrors = AttributesUtilities.createImmutable(s);
    }

    private static AttributeSet warning;

    public static AttributeSet warning() {
        if (warning != null) {
            return warning;
        }
        FontColorSettings fcs = MimeLookup.getLookup(MimePath.parse(ANTLR_MIME_TYPE)).lookup(FontColorSettings.class);
        warning = fcs.getFontColors("warning");
        if (warning == null) {
            SimpleAttributeSet sas = new SimpleAttributeSet();
            sas.addAttribute(EditorStyleConstants.WaveUnderlineColor, Color.YELLOW.darker());
            warning = AttributesUtilities.createImmutable(sas);
        }
        return warning;
    }

    @Override
    @Messages({"buildFailed=Build Failed",
        "# {0} - ruleName",
        "# {1} - text",
        "# {2} - alternatives",
        "ambiguityMsg=Ambiguity in ''{0}'' for ''{1}''. Alternatives: {2}",
        "# {0} - matches",
        "ambiguityMatches=Ambiguity between: {0}",
        "parseError=Parse Tree Error Node"
    })
    public void refresh(HighlightingInfo info) {
        // We may get called multiple times with a flurry of
        // reparses - avoid doing extra work by delaying and
        // coalescing
        this.refreshErrorsTask.schedule(REFRESH_ERRORS_DELAY);
    }

    private void doRefresh(HighlightingInfo info) {
        if (info == null || info.semantics == null) {
            return;
        }
        AntlrProxies.ParseTreeProxy semantics = info.semantics;
        if (!semantics.isUnparsed() && semantics.text() == null || semantics.text().length() < 2) {
            // Just a no-text parse the lexer used to extract a list of tokens; ignore it.
            return;
        }
        Document doc = info.doc;
        // Ensure offsets within the document don't change out from under us:
        AdhocEditorKit.renderWhenPossible(doc, () -> {
            if (doc.getLength() == 0) {
                return;
            }
            renderRefresh(info, doc, semantics);
        });
    }

    private void renderRefresh(HighlightingInfo info, Document doc, ParseTreeProxy semantics) {
        OffsetsBag bag = new OffsetsBag(doc, true);
        List<ErrorDescription> set = new ArrayList<>();
        AttributeSet attrs = errors();
        LineDocument ld = LineDocumentUtils.as(doc, LineDocument.class);
        if (semantics.isUnparsed()) {
            set.add(ErrorDescriptionFactory
                    .createErrorDescription(org.netbeans.spi.editor.hints.Severity.ERROR,
                            Bundle.buildFailed(), doc, 0));
        } else {
            if (highlightAmbiguities() && !semantics.ambiguities().isEmpty()) {
                highlightAmbiguities(semantics, ld, doc, bag, set);
            }
            if (highlightLexerErrors() && !semantics.syntaxErrors().isEmpty()) {
                highlightSyntaxErrors(semantics, ld, attrs, doc, bag, set);
            }
            if (highlightParserErrors()) {
                highlightParserErrors(semantics, doc, bag, set);
            }
        }
        HintsController.setErrors(doc, "1", set);
        Mutex.EVENT.readAccess(() -> {
            try {
                this.bag.setHighlights(bag);
            } finally {
                bag.discard();
            }
        });
    }

    private void highlightParserErrors(ParseTreeProxy semantics, Document doc, OffsetsBag bag1, List<ErrorDescription> set) {
        for (AntlrProxies.ParseTreeElement el : coalescedErrorElements(semantics)) {
            if (el.kind() == ParseTreeElementKind.ERROR) {
                SimpleAttributeSet tip = new SimpleAttributeSet();
                tip.addAttribute(EditorStyleConstants.Tooltip, el.stringify(semantics));
                tip.addAttribute(EditorStyleConstants.DisplayName, el.stringify(semantics));
                try {
                    if (el.isSynthetic() && el instanceof ErrorNodeTreeElement) {
                        int start = ((ErrorNodeTreeElement) el).tokenStart();
                        int end = ((ErrorNodeTreeElement) el).tokenEnd();
                        if (end == start) {
                            end = Math.min(doc.getLength() - 1, start + 1);
                        }
                        if (start >= 0 && end > start) {
                            bag1.addHighlight(start, end, AttributesUtilities.createComposite(parserErrors(), tip));
                            Position startPos = doc.createPosition(start);
                            Position endPos = doc.createPosition(end);
                            set.add(ErrorDescriptionFactory
                                    .createErrorDescription(Severity.ERROR, el.stringify(semantics),
                                            doc, startPos, endPos));
                        }
                    } else {
                        if (el instanceof TokenAssociated) {
                            TokenAssociated ta = (TokenAssociated) el;
                            if (ta.startTokenIndex() >= 0 && ta.stopTokenIndex() >= 0) {
                                List<ProxyToken> toks = semantics.tokensForElement(el);
                                if (!toks.isEmpty()) {
                                    ProxyToken first = toks.get(0);
                                    ProxyToken last = toks.get(toks.size() - 1);
                                    bag1.addHighlight(first.getStartIndex(), last.getEndIndex(), AttributesUtilities.createComposite(parserErrors(), tip));
                                    Position startPos = doc.createPosition(first.getStartIndex());
                                    Position endPos = doc.createPosition(last.getStopIndex());
                                    set.add(ErrorDescriptionFactory
                                            .createErrorDescription(Severity.ERROR, el.stringify(semantics),
                                                    doc, startPos, endPos));
                                }
                            }
                        }
                    }
                } catch (BadLocationException ble) {
                    Logger.getLogger(AdhocErrorHighlighter.class.getName()).log(Level.INFO,
                            "BLE finding offsets of " + el, ble);
                }
            }
        }
    }

    private void highlightSyntaxErrors(ParseTreeProxy semantics, LineDocument ld, AttributeSet attrs, Document doc, OffsetsBag bag1, List<ErrorDescription> set) {
        LOG.log(Level.FINER, "Refresh errors with {0} errors", semantics.syntaxErrors().size());
        Set<IntRange<? extends IntRange<?>>> used = new HashSet<>();
        if (ld != null && !semantics.syntaxErrors().isEmpty()) {
            for (AntlrProxies.ProxySyntaxError e : semantics.syntaxErrors()) {
                SimpleAttributeSet ttip = new SimpleAttributeSet();
                ttip.addAttribute(EditorStyleConstants.WaveUnderlineColor, Color.RED);
                ttip.addAttribute(EditorStyleConstants.Tooltip, e.message());
                AttributeSet finalAttrs = AttributesUtilities.createComposite(attrs, ttip);
                try {
                    IntRange<? extends IntRange<?>> range;
                    if (e instanceof ProxyDetailedSyntaxError && isSane((ProxyDetailedSyntaxError) e)) {
                        ProxyDetailedSyntaxError d = (ProxyDetailedSyntaxError) e;
                        int start = Math.max(0, d.startIndex());
                        int end = Math.min(doc.getLength() - 1, d.endIndex());
                        if (start < 0 || end < 0 || end < start) {
                            continue;
                        }
                        range = Range.ofCoordinates(Math.min(start, end), Math.max(start, end));
                        bag1.addHighlight(range.start(), range.end(), finalAttrs);
                        Position startPos = ld.createPosition(range.start(), Position.Bias.Backward);
                        Position endPos = ld.createPosition(range.end(), Position.Bias.Forward);
                        set.add(ErrorDescriptionFactory
                                .createErrorDescription(Severity.ERROR, e.message(), doc, startPos, endPos));
                    } else {
                        int start = LineDocumentUtils.getLineStart(ld, e.line())
                                + e.charPositionInLine();
                        int end = LineDocumentUtils.getLineEnd(ld, start);
                        range = Range.ofCoordinates(Math.min(start, end), Math.max(start, end));
                        bag1.addHighlight(range.start(), range.end(), finalAttrs);
                        LOG.log(Level.FINEST, "Add highlight for {0}", e);
                        Position startPos = ld.createPosition(start);
                        Position endPos = ld.createPosition(end);
                        set.add(ErrorDescriptionFactory
                                .createErrorDescription(Severity.ERROR, e.message(), doc, startPos, endPos));
                    }
                    used.add(range);
                } catch (BadLocationException ble) {
                    Logger.getLogger(AdhocErrorHighlighter.class.getName()).log(
                            Level.INFO, "BLE parsing " + e, ble);
                }
            }
            for (AntlrProxies.ProxyToken tok : semantics.tokens()) {
                if (semantics.isErroneousToken(tok)) {
                    IntRange<? extends IntRange<?>> range
                            = Range.ofCoordinates(Math.max(0, tok.getStartIndex()),
                                    Math.min(doc.getLength() - 1, tok.getEndIndex()));
                    if (!used.contains(range)) {
                        bag1.addHighlight(range.start(), range.end(), attrs);
                        LOG.log(Level.FINEST, "Add bad token highlight {0}", tok);
                        used.add(range);
                    }
                }
            }
        }
    }

    @Messages({
        "# {0} - subruleName",
        "# {1} - ruleName",
        "# {2} - grammarName",
        "navToAmbiguityCulprit=<html>Go to alt <pre>{0}</pre> of <b><pre>{1}</pre></b> in <i>{2}</i>",
        "# {0} - ruleName",
        "# {1} - theText",
        "# {2} - alternativeTexts",
        "ambigDetails=More than one alternative in ``{0}`` can match {1}:\n{2}",
        "# {0} - ruleName",
        "# {1} - theText",
        "# {2} - alternativeTexts",
        "ambigHtmlTip=<html>More than one alternative in <code>{0}</code> can match ''{1}.<p>"
    })
    private void highlightAmbiguities(ParseTreeProxy semantics, LineDocument ld, Document doc, OffsetsBag bag1, List<ErrorDescription> set) {
        List<? extends AntlrProxies.Ambiguity> ambiguities = semantics.ambiguities();
        PositionFactory positions = PositionFactory.forDocument(doc);
        boolean handled = withAlternativeRegions(semantics, ambiguities, (DataObject dob, Map<String, IntMap<LabelAndRange>> ranges, StyledDocument d) -> {
            highlightAmbiguitiesCrossReferencingWithGrammar(ambiguities, semantics, ranges, positions, bag1, set, doc);
        });
        if (!handled) {
            highlightAmbiguitiesCrossReferencingWithGrammar(ambiguities, semantics, Collections.emptyMap(), positions, bag1, set, doc);
        }
    }

    @Messages({
        "# {0} - theText",
        "# {1} - theRule",
        "ambiguitiesInText=<html>Ambiguity in <b>{1}</b> when parsing ''<i>{0}</i>''"
    })
    private void highlightAmbiguitiesCrossReferencingWithGrammar(List<? extends AntlrProxies.Ambiguity> ambiguities,
            ParseTreeProxy semantics, Map<String, IntMap<LabelAndRange>> ranges, PositionFactory positions,
            OffsetsBag bag, List<ErrorDescription> set, Document doc) {

        RotatingColors colors = new RotatingColors();
        LineDocument lineDoc = LineDocumentUtils.asRequired(doc, LineDocument.class);
        // Okay, this is pretty complex, but wnat is going on:
        //
        // 1.  Get the ambiguities found in this sample file, which each point to some group of
        //     | this | that top-level or clauses in some rule, where more than one could have
        //     matched the text
        // 2.  Get the set of alternatives with siblings for the grammar file (which we find from
        //     the mime type of this file, which encodes or is mapped to it by AdhocMimetypes)
        // 3.  Search the range of alternatives inside the target rule, and build a map of
        //     alternative-index to alternative name + bounds, converting to using PositionRange
        //     (backed by PositionBounds), so our positions will survive edits
        // 4   Pack all of this stuff in a Map of rule-name:alternative:nameAndOffsets so we
        //     only do all this looking up and resolving of stuff once per parse
        // 5.  Rip through all the ambiguities we have, and for anything we were able to resolve
        //     back to the original file, generate a hint that says what's going on
        for (AntlrProxies.Ambiguity amb : ambiguities) {
            int startChar = semantics.tokens().get(amb.start()).getStartIndex();
            int endChar = semantics.tokens().get(amb.stop()).getStopIndex();
            if ((startChar < 0 || endChar < 0) | endChar < startChar) {
                // synthetic token or eof
                continue;
            }
            SimpleAttributeSet sas = new SimpleAttributeSet();
            sas.addAttribute(StyleConstants.Background, colors.get());
            StringBuilder alts = new StringBuilder();
            String ruleName = semantics.ruleNameFor(amb);
            IntMap<LabelAndRange> rangesForRule = ranges.get(ruleName);
            StringBuilder alternativeTexts = new StringBuilder();
            StringBuilder tipTexts = new StringBuilder();
            CharSequence ambText = Strings.elide(Escaper.BASIC_HTML.and(Escaper.CONTROL_CHARACTERS).escape(semantics.textOf(amb)), 40);
            OuterFix fixen = new OuterFix(Bundle.ambiguitiesInText(ambText, ruleName));

            BitSet conflictingAlternatives = amb.conflictingAlternatives();
            for (int bit = conflictingAlternatives.nextSetBit(0);
                    bit >= 0;
                    bit = conflictingAlternatives.nextSetBit(bit + 1)) {
                if (alts.length() > 0) {
                    alts.append(", ");
                }
                if (rangesForRule != null) {
                    LabelAndRange range = rangesForRule.get(bit);
                    if (range != null) {
                        if (alternativeTexts.length() != 0) {
                            alternativeTexts.append("\n");
                        }
                        tipTexts.append("<b>").append(range.alternativeName).append("</b>: ")
                                .append("<code>").append(range.alternativeText).append("</code></p><p>");
                        alternativeTexts.append(range.alternativeName).append(": '").append(range.alternativeText).append('\'');
                        alts.append(range.alternativeName);
                        int targetAlternative = bit;
                        String navMessage = Bundle.navToAmbiguityCulprit(range.alternativeName, ruleName, semantics.grammarName());
                        fixen.add(new NavigationFix(navMessage, range.range, () -> {
                            goToAlternativeInGrammar(ruleName, conflictingAlternatives, targetAlternative, ranges);
                        }));
                        continue;
                    }
                }
                alts.append(bit);
            }
            try {
                if (startChar < 0 || startChar >= endChar) {
                    // eof or similar
                    continue;
                }
                CharSequence ambText2 = semantics.textOf(amb);
                int lastNonWhitespaceOnLine = LineDocumentUtils.getLineLastNonWhitespace(lineDoc, startChar);

                // Set the error to only be on the line where the ambiguity starts,
                // or the results are confusing
                PositionRange range = positions.range(startChar, Position.Bias.Forward,
                        Math.min(lastNonWhitespaceOnLine, endChar), Position.Bias.Backward);

                AttributeSet warn = AttributesUtilities.createComposite(warning(), sas);
                CharSequence textMunged = Strings.elide(escapeControlCharactersAndMultipleWhitespace(ambText2), 40 / 2);
                String ambMessage = Bundle.ambiguityMsg(ruleName, textMunged, alts);
                String detailMessage = alternativeTexts.length() == 0 ? null
                        : Bundle.ambigDetails(ruleName, textMunged, alternativeTexts);
                String tipMessage = Bundle.ambigHtmlTip(ruleName, escapeForHtml(Strings.elide(ambText2)), tipTexts.append("</p>"));
                sas.addAttribute(EditorStyleConstants.Tooltip, tipMessage);

                addHighlightsAvoidingCrossingNewlines(amb, warn, semantics, bag, lineDoc);

                Fix fix = fixen.commit();

                if (fix != null) {
                    set.add(ErrorDescriptionFactory.createErrorDescription(
                            amb.identifier(),
                            Severity.WARNING, ambMessage, detailMessage,
                            ErrorDescriptionFactory.lazyListForFixes(Collections.singletonList(fix)), doc, range.startPosition(), range.endPosition()));
                } else {
                    set.add(ErrorDescriptionFactory.createErrorDescription(
                            amb.identifier(), Severity.WARNING,
                            ambMessage, detailMessage, ErrorDescriptionFactory.lazyListForFixes(Collections.emptyList()),
                            doc, range.startPosition(), range.endPosition()));
                }
            } catch (BadLocationException ble) {
                Logger.getLogger(AdhocErrorHighlighter.class.getName()).log(
                        Level.INFO, "BLE on ambiguity " + amb, ble);
            }
        }
    }

    private void addHighlightsAvoidingCrossingNewlines(Ambiguity amb, AttributeSet attrs, ParseTreeProxy semantics, OffsetsBag bag, LineDocument lineDoc) throws BadLocationException {
        addHighlightsAvoidingCrossingNewlines(amb.start(), amb.stop(), attrs, semantics, bag, lineDoc);
    }

    private void addHighlightsAvoidingCrossingNewlines(int firstToken, int lastToken, AttributeSet attrs, ParseTreeProxy semantics, OffsetsBag bag, LineDocument doc) throws BadLocationException {
        ProxyToken first = semantics.tokens().get(firstToken);
        ProxyToken last = semantics.tokens().get(lastToken);
        int start = first.getStartIndex();
        int end = last.getEndIndex() - last.trim();

        // We will sometimes encounter ambiguities which span multiple lines;
        // this is the only way to avoid leaving a long trailing underline out
        // to the margin
        if (first.getLine() == last.getLine()) {
            int endHighlightOffset = LineDocumentUtils.getLineLastNonWhitespace(doc, first.getStartIndex());
            bag.addHighlight(start, Math.max(start + 1, Math.min(endHighlightOffset, end)), attrs);
        } else {
            int firstLine = LineDocumentUtils.getLineIndex(doc, start);
            int lastLine = LineDocumentUtils.getLineIndex(doc, end);
            int offset = first.getStartIndex();
            for (int i = firstLine; i <= lastLine; i++) {
                int lineOffset;
                int lineEnd = LineDocumentUtils.getLineEnd(doc, offset);
                if (offset == lineEnd || LineDocumentUtils.isLineWhitespace(doc, offset) || LineDocumentUtils.isLineEmpty(doc, offset)) {
                    offset++;
                    continue;
                }
                if (i == firstLine) {
                    int lineStart = LineDocumentUtils.getLineStart(doc, offset);
                    lineOffset = lineStart + first.getCharPositionInLine();
                } else {
                    lineOffset = LineDocumentUtils.getLineFirstNonWhitespace(doc, offset);
                }
                int endHighlightOffset = LineDocumentUtils.getLineLastNonWhitespace(doc, offset);
                if (i == lastLine) {
                    endHighlightOffset = Math.min(endHighlightOffset, last.getStartIndex() + last.trimmedLength());
                }
                if (endHighlightOffset > lineOffset) {
                    bag.addHighlight(lineOffset, endHighlightOffset, attrs);
                }
                offset = lineEnd + 1;
            }
        }
    }

    static class OuterFix implements Fix {

        private final String text;
        private List<Fix> fixen;

        public OuterFix(String text) {
            this.text = text;
        }

        Fix commit() {
            if (fixen == null) {
                return null;
            }
            return ErrorDescriptionFactory.attachSubfixes(this, fixen);
        }

        void add(Fix fix) {
            if (fixen == null) {
                fixen = new ArrayList<>();
            }
            fixen.add(fix);
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public ChangeInfo implement() throws Exception {
            if (fixen == null) {
                return null;
            }
            return fixen.get(0).implement();
        }

    }

    private static CharSequence escapeControlCharactersAndMultipleWhitespace(CharSequence seq) {
        return new WhitespaceDedup().and(Escaper.CONTROL_CHARACTERS).escape(seq);
    }

    private static CharSequence escapeForHtml(CharSequence seq) {
        return Escaper.BASIC_HTML.escape(seq);
    }

    static class WhitespaceDedup implements Escaper {

        private char lastChar = ' ';

        @Override
        public CharSequence escape(char c) {
            try {
                if (c == '\n') {
                    return " ";
                }
                if (Character.isWhitespace(c)) {
                    if (Character.isWhitespace(lastChar)) {
                        return "";
                    }
                }
                return null;
            } finally {
                lastChar = c;
            }
        }

    }

    private boolean isSane(ProxyDetailedSyntaxError err) {
        return true;
    }

    static List<ErrorNodeTreeElement> coalescedErrorElements(ParseTreeProxy proxy) {
        List<ErrorNodeTreeElement> origs = new ArrayList<>(proxy.allErrorElements());

        Collections.sort(origs, (a, b) -> {
            int aStart, bStart, aEnd, bEnd;
            if (a.isSynthetic()) {
                aStart = a.tokenStart();
                aEnd = a.tokenEnd();
            } else {
                List<ProxyToken> toks = proxy.tokensForElement(a);
                ProxyToken first = toks.get(0);
                ProxyToken last = toks.get(toks.size() - 1);
                aStart = first.getStartIndex();
                aEnd = last.getEndIndex();
            }
            if (b.isSynthetic()) {
                bStart = b.tokenStart();
                bEnd = b.tokenEnd();
            } else {
                List<ProxyToken> toks = proxy.tokensForElement(b);
                ProxyToken first = toks.get(0);
                ProxyToken last = toks.get(toks.size() - 1);
                bStart = first.getStartIndex();
                bEnd = last.getEndIndex();
            }

            int result = Integer.compare(aStart, bStart);
            if (result == 0) {
                result = Integer.compare(aEnd, bEnd);
            }
            return result;
        });
        List<ErrorNodeTreeElement> result = new ArrayList<>(origs.size());
        int currStart = Integer.MIN_VALUE;
        int currEnd = Integer.MIN_VALUE;
        int currTokenIndexStart = Integer.MIN_VALUE;
        int currTokenIndexStop = Integer.MIN_VALUE;
        int maxDepth = Integer.MIN_VALUE;
        List<String> symbols = new ArrayList<>();
        for (ErrorNodeTreeElement orig : origs) {
            if (currStart == Integer.MIN_VALUE) {
                currStart = orig.tokenStart();
                currEnd = orig.tokenEnd();
                maxDepth = Math.max(maxDepth, orig.depth());
                if (orig.isSynthetic()) {
                    currTokenIndexStart = -1;
                    currTokenIndexStop = -1;
                } else {
                    currTokenIndexStart = orig.startTokenIndex();
                    currTokenIndexStop = orig.stopTokenIndex();
                }
                symbols.add(orig.tokenText());
            } else {
                if (orig.tokenStart() <= currEnd + 1) {
                    symbols.add(orig.tokenText());
                    currEnd = orig.tokenEnd();
                    if (orig.isSynthetic()) {
                    } else {
                        currTokenIndexStop = orig.stopTokenIndex();
                    }
                } else {
                    if (currStart != Integer.MAX_VALUE) {
                        String text = Strings.join(", ", symbols);
                        symbols.clear();
                        result.add(new ErrorNodeTreeElement(currTokenIndexStart,
                                currTokenIndexStop, maxDepth,
                                currStart, currEnd, text, -1));
                        currTokenIndexStart = -1;
                        currTokenIndexStop = -1;
                    }
                    currStart = orig.tokenStart();
                    currEnd = orig.tokenEnd();
                    symbols.add(orig.tokenText());
                    if (!orig.isSynthetic()) {
                        currTokenIndexStart = orig.startTokenIndex();
                        currTokenIndexStop = orig.stopTokenIndex();
                    }
                }
            }
        }
        if (currStart != Integer.MAX_VALUE) {
            String text = Strings.join(", ", symbols);
            symbols.clear();
            result.add(new ErrorNodeTreeElement(currTokenIndexStart,
                    currTokenIndexStop, maxDepth,
                    currStart, currEnd, text, -1));
        }
        return result;
    }

    private void goToAlternativeInGrammar(String ruleName, BitSet rec, int alternative,
            Map<String, IntMap<LabelAndRange>> map) {
        String mime = mgr.mimeType();
        Path grammarFile = AdhocMimeTypes.grammarFilePathForMimeType(mime);
        if (grammarFile != null) {
            File file = FileUtil.normalizeFile(grammarFile.toFile());
            if (file != null) {
                FileObject fo = FileUtil.toFileObject(file);
                if (fo != null) {
                    try {
                        DataObject dob = DataObject.find(fo);
                        goToAlternativeInGrammar(ruleName, rec, alternative, dob, map);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
    }

    private boolean withAlternativeRegions(ParseTreeProxy proxy, Collection<? extends AntlrProxies.Ambiguity> records,
            TriConsumer<DataObject, Map<String, IntMap<LabelAndRange>>, StyledDocument> c) {
        String mime = mgr.mimeType();
        Path grammarFile = AdhocMimeTypes.grammarFilePathForMimeType(mime);
        if (grammarFile != null) {
            File file = FileUtil.normalizeFile(grammarFile.toFile());
            if (file != null) {
                FileObject fo = FileUtil.toFileObject(file);
                if (fo != null) {
                    try {
                        DataObject dob = DataObject.find(fo);
                        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                        if (ck == null) {
                            return false;
                        }
                        StyledDocument doc = ck.openDocument();
                        return withAlternativeRegions(proxy, records, dob, doc, c);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
        return false;
    }

    private boolean withAlternativeRegions(ParseTreeProxy proxy, Collection<? extends AntlrProxies.Ambiguity> records,
            DataObject grammarDO, StyledDocument doc,
            TriConsumer<DataObject, Map<String, IntMap<LabelAndRange>>, StyledDocument> c) {
        Extraction ext = NbAntlrUtils.extractionFor(doc);
        if (ext.isPlaceholder()) {
            return false;
        }
        return withAlternativeRegions(proxy, records, grammarDO, doc, ext, c);
    }

    private boolean withAlternativeRegions(ParseTreeProxy proxy, Collection<? extends AntlrProxies.Ambiguity> records,
            DataObject grammarDO, StyledDocument grammarDoc,
            Extraction ext,
            TriConsumer<DataObject, Map<String, IntMap<LabelAndRange>>, StyledDocument> c) {
        NamedSemanticRegions<RuleTypes> ruleBounds = ext.namedRegions(AntlrKeys.RULE_BOUNDS);
        if (ruleBounds.isEmpty()) {
            return false;
        }
        SemanticRegions<AlternativeKey> alts = ext.regions(OUTER_ALTERNATIVES_WITH_SIBLINGS);
        if (alts.isEmpty()) {
            return false;
        }
        Map<String, Set<AntlrProxies.Ambiguity>> recordsForName = CollectionUtils.supplierMap(() -> new HashSet<>(5));
        Map<String, BitSet> altsForName = CollectionUtils.supplierMap(() -> new BitSet(7));
        records.forEach(rec -> {
            String ruleName = proxy.ruleNameFor(rec);
            recordsForName.get(ruleName).add(rec);
            altsForName.get(ruleName).or(rec.conflictingAlternatives());
        });
        Map<String, IntMap<LabelAndRange>> alternativeSpansForRule = new HashMap<>();

        PositionFactory positions = PositionFactory.forDocument(grammarDoc);
        Bool anyFound = Bool.create();
        grammarDoc.render(() -> {
            for (Map.Entry<String, BitSet> e : altsForName.entrySet()) {
                NamedSemanticRegion<RuleTypes> rule = ruleBounds.regionFor(e.getKey());
                if (rule != null) {
                    List<? extends SemanticRegion<AlternativeKey>> targets
                            = alts.collectBetween(rule.start(), rule.end(), ak -> {
                                return e.getValue().get(ak.alternativeIndex());
                            });
                    IntMap<LabelAndRange> oneMap = IntMap.create(e.getValue().cardinality());
                    targets.forEach(reg -> {
                        try {
                            String altName = reg.key().label();
                            PositionRange currRange = positions.range(reg);
                            String alternativeText = PositionFactory.toPositionBounds(currRange).getText();
                            oneMap.put(reg.key().alternativeIndex(), new LabelAndRange(currRange, altName, alternativeText));
                            anyFound.set();
                        } catch (BadLocationException ex) {
                            Exceptions.printStackTrace(ex);
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    });
                    alternativeSpansForRule.put(e.getKey(), oneMap);
                }
            }
        });
        if (anyFound.getAsBoolean()) {
            c.accept(grammarDO, alternativeSpansForRule, grammarDoc);
            return true;
        }
        return false;
    }

    private static final class LabelAndRange {

        final PositionRange range;
        final String alternativeName;
        final String alternativeText;

        public LabelAndRange(PositionRange range, String alternativeName, String alternativeText) {
            this.range = range;
            this.alternativeName = alternativeName;
            this.alternativeText = alternativeText;
        }

    }

    @Messages({
        "# {0} - alternativeName",
        "# {1} - ruleName",
        "# {2} - fileName",
        "openingAlt=Open alt {0} of {1} in {2}"
    })
    private boolean goToAlternativeInGrammar(String ruleName, BitSet rec, int alternative,
            DataObject grammarDO,
            Map<String, IntMap<LabelAndRange>> map) {

        IntMap<LabelAndRange> targets = map.get(ruleName);
        if (targets == null || targets.isEmpty()) {
            return false;
        }
        if (alternative < 0) {
            alternative = rec.nextSetBit(0);
            if (alternative < 0) {
                return false;
            }
        }
        LabelAndRange range = targets.get(alternative);
        if (range == null) {
            // should not happen, but find one that does exist
            for (int bit = rec.nextSetBit(0); bit >= 0; bit = rec.nextSetBit(bit + 1)) {
                range = targets.get(bit);
                if (range != null) {
                    break;
                }
            }
        }
        if (range == null) {
            return false;
        }
        StatusDisplayer.getDefault().setStatusText(Bundle.openingAlt(range.alternativeName, ruleName, grammarDO.getName()));
        if (!Range.of(0, range.range.document().getLength()).contains(range.range)) {
            return false;
        }

        // Use EditorRegistry, as it includes the preview editor and
        // we don't want to open a separate document if we don't have to
        JTextComponent editor = EditorRegistry.findComponent(range.range.document());
        TopComponent tc = null;
        if (editor != null) {
            tc = NbEditorUtilities.getOuterTopComponent(editor);
        }
        if (editor == null || tc == null) {
            openUsingNewEditor(range.range.document(), range.range);
        } else {
            openUsingComponent(editor, tc, range.range);
        }
        return true;
    }

    private void openUsingComponent(JTextComponent comp, TopComponent outermost, PositionRange range) {
        comp.setSelectionStart(range.start());
        comp.setSelectionEnd(range.end());
        if (TopComponent.getRegistry().getActivated() != outermost) {
            outermost.requestActive();
        }
        try {
            // Center the rectangle, or it can wind up half way
            // off the bottom of the screen.
            Rectangle visibleBounds = comp.getVisibleRect();
            int cy = visibleBounds.height / 2;
            Rectangle characterBounds = comp.getUI().modelToView(comp, range.start());
            characterBounds.add(comp.getUI().modelToView(comp, range.end()));
            characterBounds.x = 0;
            cy -= characterBounds.height;
            characterBounds.y -= cy;
            characterBounds.height += cy;
            // Swing components *will* do negative scroll, and the
            // effect is not a good one
            characterBounds.y = Math.max(0, characterBounds.y);

            comp.scrollRectToVisible(characterBounds);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        comp.requestFocus();
    }

    private void openUsingNewEditor(Document doc, PositionRange select) {
        LineDocument ld = LineDocumentUtils.asRequired(doc, LineDocument.class);
        int lineStart = LineDocumentUtils.getLineStart(ld, select.start());
        int column = select.start() - lineStart;
        Line ln = NbEditorUtilities.getLine(doc, select.start(), false);
        ln.show(Line.ShowOpenType.REUSE_NEW, Line.ShowVisibilityType.FOCUS, column);
    }

    private boolean invalidateGrammarSource(boolean reparse) {
        Document doc = mgr.document();
        String mime = mgr.mimeType();
        Path grammarFile = AdhocMimeTypes.grammarFilePathForMimeType(mime);
        if (grammarFile != null) {
            File file = FileUtil.normalizeFile(grammarFile.toFile());
            if (file != null) {
                FileObject fo = FileUtil.toFileObject(file);
                if (fo != null) {
                    NbAntlrUtils.invalidateSource(fo);
                    if (reparse) {
                        mgr.forceGrammarReparse(fo);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    void onHighlightItemsChanged(boolean isAddAmbiguities) {
        if (isAddAmbiguities) {
            // In this case, we need to force a new ParseTreeProxy to be
            // generated
            // XXX, we just need a new ParseTreeProxy - reparsing the
            // grammar is overkill
//            invalidateGrammarSource(mgr.isActive());
            mgr.threadPool().submit(() -> {
                Document doc = mgr.document();
                FileObject fo = NbEditorUtilities.getFileObject(doc);
                if (fo != null) {
                    NbAntlrUtils.invalidateSource(fo);
                }
                mgr.documentReparse();
            });
        } else {
            HighlightingInfo info = mgr.lastInfo();
            if (info != null) {
                refresh(info);
            }
        }
    }

    @Override
    public void onColoringsChanged() {
        onHighlightItemsChanged(false);
    }

    static void onChange(boolean isAddAmbiguities) {
        for (AdhocErrorHighlighter hl : INSTANCES) {
            hl.onHighlightItemsChanged(isAddAmbiguities);
        }
    }

    static boolean highlightAmbiguities() {
        return prefs().getBoolean(PREFS_KEY_HIGHLIGHT_AMBIGUITIES, false);
    }

    static boolean highlightAmbiguities(boolean val) {
        boolean old = highlightAmbiguities();
        if (val != old) {
            prefs().putBoolean(PREFS_KEY_HIGHLIGHT_AMBIGUITIES, val);
            // We need to preserve the previous prediction mode and restore
            // it when ambiguity detection is turned off
            if (val) {
                PredictionMode oldMode = EmbeddedParserFeatures.getInstance(null).currentPredictionMode();
                NbPreferences.forModule(AdhocErrorHighlighter.class).putInt("nonAmbigMode", oldMode.ordinal());
                EmbeddedParserFeatures.getInstance(null).setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
            } else {
                int ord = NbPreferences.forModule(AdhocErrorHighlighter.class).getInt("nonAmbigMode", PredictionMode.LL.ordinal());
                PredictionMode md = PredictionMode.values()[ord];
                EmbeddedParserFeatures.getInstance(null).setPredictionMode(md);
            }
            onChange(val);
            subs.eventInput.onEvent(PREFS_KEY_HIGHLIGHT_AMBIGUITIES,
                    new PropertyChangeEvent(AdhocErrorHighlighter.class,
                            PREFS_KEY_HIGHLIGHT_AMBIGUITIES, !val, val));
            return true;
        }
        return false;
    }

    static boolean highlightParserErrors() {
        return prefs().getBoolean(PREFS_KEY_HIGHLIGHT_PARSER_ERRORS, false);
    }

    static boolean highlightParserErrors(boolean val) {
        boolean old = highlightParserErrors();
        if (val != old) {
            prefs().putBoolean(PREFS_KEY_HIGHLIGHT_PARSER_ERRORS, val);
            onChange(false);
            subs.eventInput.onEvent(PREFS_KEY_HIGHLIGHT_PARSER_ERRORS,
                    new PropertyChangeEvent(AdhocErrorHighlighter.class,
                            PREFS_KEY_HIGHLIGHT_PARSER_ERRORS, !val, val));
            return true;
        }
        return false;
    }

    static boolean highlightLexerErrors() {
        return prefs().getBoolean(PREFS_KEY_HIGHLIGHT_LEXER_ERRORS, true);
    }

    static boolean highlightLexerErrors(boolean val) {
        boolean old = highlightLexerErrors();
        if (val != old) {
            prefs().putBoolean(PREFS_KEY_HIGHLIGHT_LEXER_ERRORS, val);
            onChange(false);
            subs.eventInput.onEvent(PREFS_KEY_HIGHLIGHT_LEXER_ERRORS,
                    new PropertyChangeEvent(AdhocErrorHighlighter.class,
                            PREFS_KEY_HIGHLIGHT_LEXER_ERRORS, !val, val));
            return true;
        }
        return false;
    }

    @Messages({"highlightAmbiguities=Highlight Ambiguities",
        "highlightAmbiguitiesDesc=Enables highlighting of cases of ambiguity in the lexer; "
        + " disabled by default because it can create a lot of visual noise."})
    public static Action toggleHighlightAmbiguitiesAction() {
        return toggleHighlightAmbiguitiesAction(true);
    }

    static ToggleHighlightAmbiguitiesAction toggleHighlightAmbiguitiesAction(boolean icon) {
        // Actions.checkbox() returns an action that never has an icon, even using
        // putValue(), so IOWindow will throw an exception (and there will be no
        // indication in the toolbar button whether it is selected or not). So,
        // we do it the old-fashioned way.
        return new ToggleHighlightAmbiguitiesAction(icon);
    }

    public static Action toggleHighlightParserErrorsAction() {
        return toggleHighlightParserErrorsAction(true);
    }

    static ToggleHighlightParserErrorsAction toggleHighlightParserErrorsAction(boolean icon) {
        // Actions.checkbox() returns an action that never has an icon, even using
        // putValue(), so IOWindow will throw an exception (and there will be no
        // indication in the toolbar button whether it is selected or not). So,
        // we do it the old-fashioned way.
        return new ToggleHighlightParserErrorsAction(icon);
    }

    public static Action toggleHighlightLexerErrorsAction() {
        return toggleHighlightLexerErrorsAction(true);
    }

    public static ToggleHighlightLexerErrorsAction toggleHighlightLexerErrorsAction(boolean icon) {
        // Actions.checkbox() returns an action that never has an icon, even using
        // putValue(), so IOWindow will throw an exception (and there will be no
        // indication in the toolbar button whether it is selected or not). So,
        // we do it the old-fashioned way.
        return new ToggleHighlightLexerErrorsAction(icon);
    }

//    subs = SubscribableBuilder.withKeys(String.class)
//            .<PropertyChangeEvent, PropertyChangeListener>withEventApplier((String changed, PropertyChangeEvent ce, Collection<? extends PropertyChangeListener> listeners) -> {
//                for (PropertyChangeListener l : listeners) {
//                    l.propertyChange(ce);
//                }
//            }).storingSubscribersIn(SetFactories.CONCURRENT_EQUALITY)
//            .withSets(SetFactories.WEAK_HASH).threadSafe()
//            .withSynchronousEventDelivery().build();
    private static final SubscribableBuilder.SubscribableContents<String, String, PropertyChangeListener, PropertyChangeEvent> subs = SubscribableBuilder.withKeys(String.class)
            .<PropertyChangeEvent, PropertyChangeListener>withEventApplier((String changed, PropertyChangeEvent ce, Collection<? extends PropertyChangeListener> listeners) -> {
                for (PropertyChangeListener l : listeners) {
                    l.propertyChange(ce);
                }
            }).storingSubscribersIn(SetFactories.CONCURRENT_EQUALITY)
            .withSets(SetFactories.WEAK_HASH).threadSafe()
            .withSynchronousEventDelivery().build();

    static abstract class AbstractPrefsKeyToggleAction extends AbstractAction implements Icon, Presenter.Popup, PropertyChangeListener, Runnable {

        private final String key;
        private JCheckBoxMenuItem presenter;

        @SuppressWarnings({"OverridableMethodCallInConstructor", "LeakingThisInConstructor"})
        AbstractPrefsKeyToggleAction(boolean icon, String key, String displayName, String description) {
            this.key = key;
            putValue(NAME, displayName);
            if (icon) {
                putValue(SMALL_ICON, this);
            }
            putValue(SELECTED_KEY, key);
            if (description != null) {
                putValue(SHORT_DESCRIPTION, description);
            }
            subs.subscribable.subscribe(key, this);
        }

        protected abstract boolean currentValue();

        protected abstract boolean updateValue(boolean val);

        private void setValue(boolean val) {
            if (updateValue(val)) {
                firePropertyChange(key, !val, val);
            }
        }

        protected void toggleValue() {
            setValue(!currentValue());
        }

        @Override
        public Object getValue(String key) {
            if (this.key.equals(key)) {
                return currentValue();
            }
            return super.getValue(key);
        }

        @Override
        public JMenuItem getPopupPresenter() {
            if (presenter == null) {
                presenter = new JCheckBoxMenuItem((Action) this);
                presenter.setSelected(currentValue());
                String desc = (String) getValue(SHORT_DESCRIPTION);
                if (desc != null) {
                    presenter.setToolTipText(desc);
                }
                PropertyChangeListener pcl = evt -> {
                    presenter.repaint();
                };
                subs.subscribable.subscribe(key, pcl);
                // hold a reference
                presenter.putClientProperty("_pcl", pcl);
            }
            return presenter;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            toggleValue();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            // triger a repaint in presenters
            EventQueue.invokeLater(this);
        }

        public void run() {
            setEnabled(false);
            setEnabled(true);
        }
    }

    static final class ToggleHighlightAmbiguitiesAction extends AbstractPrefsKeyToggleAction {

        ToggleHighlightAmbiguitiesAction(boolean icon) {
            super(icon, PREFS_KEY_HIGHLIGHT_AMBIGUITIES, Bundle.highlightAmbiguities(), Bundle.highlightAmbiguitiesDesc());
        }

        @Override
        protected boolean currentValue() {
            return highlightAmbiguities();
        }

        @Override
        protected boolean updateValue(boolean val) {
            return highlightAmbiguities(val);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            String txt = "a|?";
            Graphics2D gg = (Graphics2D) g;
            Font f = c.getFont();
            FontMetrics fm = gg.getFontMetrics(f);
            float ht = fm.getAscent();
            float w = fm.stringWidth(txt);
            Insets ins = ((JComponent) c).getInsets();
            float availH = Math.max(4, (c.getHeight() - y) - ins.bottom);
            float availW = Math.max(4, ((c.getWidth() - x)) - ins.right);
            float scaleX = 1;
            float scaleY = 1;
            if (availW < w) {
                scaleX = w / availW;
            }
            if (availH < ht) {
                scaleY = ht / availH;
            }
            AffineTransform xform = AffineTransform.getScaleInstance(scaleX, scaleY);
            f = f.deriveFont(xform);
            gg.setFont(f);
            fm = gg.getFontMetrics();
            float left = x;
            float top = y;
            ht = fm.getAscent();
            w = fm.stringWidth(txt);
            if (ht < availH) {
                top += (availH / 2) - (ht / 2);
            }
            if (w < availW) {
                left += (availW / 2) - (w / 2);
            }
            if (highlightAmbiguities()) {
                g.setColor(c.getForeground());
            } else {
                g.setColor(UIManager.getColor("ScrollBar.thumbShadow"));
            }
            gg.drawString(txt, left, top + fm.getAscent());
        }

        @Override
        public int getIconWidth() {
            return 24;
        }

        @Override
        public int getIconHeight() {
            return 24;
        }
    }

    @Messages({
        "highlightLexerErrors=Highlight Lexer Syntax Errors",
        "highlightLexerErrorsDesc=Highlight syntax errors from the lexer"
    })
    static final class ToggleHighlightLexerErrorsAction extends AbstractPrefsKeyToggleAction {

        ToggleHighlightLexerErrorsAction(boolean icon) {
            super(icon, PREFS_KEY_HIGHLIGHT_LEXER_ERRORS, Bundle.highlightLexerErrors(), Bundle.highlightLexerErrorsDesc());
        }

        @Override
        protected boolean currentValue() {
            return highlightLexerErrors();
        }

        @Override
        protected boolean updateValue(boolean val) {
            return highlightLexerErrors(val);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            String txt = "<?>";
            Graphics2D gg = (Graphics2D) g;
            Font f = c.getFont();
            FontMetrics fm = gg.getFontMetrics(f);
            float ht = fm.getAscent();
            float w = fm.stringWidth(txt);
            Insets ins = ((JComponent) c).getInsets();
            float availH = Math.max(4, (c.getHeight() - y) - ins.bottom);
            float availW = Math.max(4, ((c.getWidth() - x)) - ins.right);
            float scaleX = 1;
            float scaleY = 1;
            if (availW < w) {
                scaleX = w / availW;
            }
            if (availH < ht) {
                scaleY = ht / availH;
            }
            AffineTransform xform = AffineTransform.getScaleInstance(scaleX, scaleY);
            f = f.deriveFont(xform);
            gg.setFont(f);
            fm = gg.getFontMetrics();
            float left = x;
            float top = y;
            ht = fm.getAscent();
            w = fm.stringWidth(txt);
            top += (availH / 2) - (ht / 2);
            left += (availW / 2) - (w / 2);
            if (highlightLexerErrors()) {
                g.setColor(c.getForeground());
            } else {
                g.setColor(UIManager.getColor("ScrollBar.thumbHighlight"));
            }
            gg.drawString(txt, left, top + fm.getAscent());
        }

        @Override
        public int getIconWidth() {
            return 24;
        }

        @Override
        public int getIconHeight() {
            return 24;
        }
    }

    @Messages({
        "highlightParserErrors=Highlight Parse Errors",
        "highlightParserErrorsDesc=Highlight error nodes generated when parsing the document; "
        + "these are errors where the lexer recognized the tokens, but they did not "
        + "come in a sequence that made sense to the parser."
    })
    static final class ToggleHighlightParserErrorsAction extends AbstractPrefsKeyToggleAction {

        private final Line2D.Float line = new Line2D.Float();

        ToggleHighlightParserErrorsAction(boolean icon) {
            super(icon, PREFS_KEY_HIGHLIGHT_PARSER_ERRORS, Bundle.highlightParserErrors(), Bundle.highlightParserErrorsDesc());
        }

        @Override
        protected boolean currentValue() {
            return highlightParserErrors();
        }

        @Override
        protected boolean updateValue(boolean val) {
            return highlightParserErrors(val);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D gg = (Graphics2D) g;
            Font f = c.getFont();
            FontMetrics fm = gg.getFontMetrics(f);
            float ht = fm.getAscent();
            Insets ins = ((JComponent) c).getInsets();
            float availH = Math.max(4, (c.getHeight() - y) - ins.bottom);
            float availW = Math.max(4, ((c.getWidth() - x)) - ins.right);
            float w = (availW - 2) / 4F;

            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float top = availH / 4F;

            float sw = fm.stringWidth("x");
            float sh = fm.getAscent();
            float scale = top / sh;
            f = f.deriveFont(AffineTransform.getScaleInstance(scale, scale));
            gg.setFont(f);

            if (highlightParserErrors()) {
                g.setColor(c.getForeground());
            } else {
                g.setColor(UIManager.getColor("ScrollBar.thumbHighlight"));
            }
            fm = gg.getFontMetrics();
            float sl = (x + (availW / 2F)) - sw / 2F;
            gg.drawString("x", sl, y + top + fm.getAscent());
            gg.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1));
            float xoff = x + (availW / 2) - (w / 2);
            line.setLine(xoff, y + top, xoff + w, y + top);
            gg.draw(line);
            line.setLine(x + 1, y + top + top, x + w + 1, y + top + top);
            gg.draw(line);
            line.setLine((x + availW - 1) - w, y + top + top, (x + availW - (w + 1)), y + top + top);
            gg.draw(line);
        }

        @Override
        public int getIconWidth() {
            return 24;
        }

        @Override
        public int getIconHeight() {
            return 24;
        }
    }

    private static Preferences prefs() {
        return NbPreferences.forModule(AdhocErrorHighlighter.class);
    }

    public void run() {
        HighlightingInfo info = mgr.lastInfo();
        if (info != null) {
            // createErrorDescription will lock the document;
            // so will the call to set the errors - so rather than
            // risk deadlock, we do this in a background thread and
            // pre-lock the document so we don't lock/unlock once per
            // error
            //
            // This little hack gives us some further deadlock-proofing
            // by ensuring we don't run when something else has the
            // document locked
            AdhocEditorKit.renderWhenPossible(info.doc, () -> {
                doRefresh(this.mgr.lastInfo());
            });
        }
    }

}

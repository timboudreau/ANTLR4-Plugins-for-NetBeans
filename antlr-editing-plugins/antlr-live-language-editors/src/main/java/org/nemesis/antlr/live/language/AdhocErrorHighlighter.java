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

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.subscription.SubscribableBuilder;
import com.mastfrog.util.collections.SetFactories;
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
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.live.language.AdhocHighlighterManager.HighlightingInfo;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ErrorNodeTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElementKind;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyDetailedSyntaxError;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.TokenAssociated;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;
import org.openide.util.actions.Presenter;

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
        super(mgr, ZOrder.TOP_RACK.forPosition(2001));
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
        "# {1} - alternatives",
        "ambiguityMsg=Ambiguity in {0}. Alternatives: {1}",
        "# {0} - matches",
        "ambiguityMatches=Ambiguity between: {0}",
        "parseError=Parse Tree Error Node"
    })
    protected void refresh(HighlightingInfo info) {
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
            if (highlightAmbiguities()) { // XXX useful but very noisy on unfinished grammars
                for (AntlrProxies.Ambiguity amb : semantics.ambiguities()) {
                    ProxyToken a = semantics.tokens().get(amb.startOffset);
                    ProxyToken b = semantics.tokens().get(amb.stopOffset);
                    SimpleAttributeSet sas = new SimpleAttributeSet();
                    StringBuilder alts = new StringBuilder();
                    for (int bit = amb.conflictingAlternatives.nextSetBit(0);
                            bit >= 0;
                            bit = amb.conflictingAlternatives.nextSetBit(bit + 1)) {
                        if (bit >= 0 && bit < semantics.tokenTypeCount()) {
                            if (alts.length() > 0) {
                                alts.append(", ");
                            }
                            String type = semantics.parserRuleNames().get(bit);
                            alts.append(type);
                        }
                    }
                    Position startPos, endPos;
                    try {
                        if (ld != null) {
                            startPos = ld.createPosition(amb.startOffset, Position.Bias.Backward);
                            endPos = ld.createPosition(amb.end(), Position.Bias.Backward);
                        } else {
                            startPos = doc.createPosition(amb.startOffset);
                            endPos = doc.createPosition(amb.end());
                        }
                        sas.addAttribute(EditorStyleConstants.Tooltip,
                                Bundle.ambiguityMatches(alts.toString()));
                        bag.addHighlight(a.getStartIndex(), b.getEndIndex(),
                                AttributesUtilities.createComposite(warning(), sas));
                        set.add(ErrorDescriptionFactory.createErrorDescription(Severity.WARNING,
                                Bundle.ambiguityMsg(amb.ruleName, alts),
                                doc, startPos, endPos));
                    } catch (BadLocationException ble) {
                        Logger.getLogger(AdhocErrorHighlighter.class.getName()).log(
                                Level.INFO, "BLE on ambiguity " + amb, ble);
                    }
                }
            }
            if (highlightLexerErrors()) {
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
                                bag.addHighlight(range.start(), range.end(),
                                        finalAttrs);
                                Position startPos = ld.createPosition(range.start(), Position.Bias.Backward);
                                Position endPos = ld.createPosition(range.end(), Position.Bias.Forward);
                                set.add(ErrorDescriptionFactory
                                        .createErrorDescription(Severity.ERROR, e.message(), doc, startPos, endPos));
                            } else {
                                int start = LineDocumentUtils.getLineStart(ld, e.line())
                                        + e.charPositionInLine();
                                int end = LineDocumentUtils.getLineEnd(ld, start);
                                range = Range.ofCoordinates(Math.min(start, end), Math.max(start, end));
                                bag.addHighlight(range.start(), range.end(), finalAttrs);
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
                                bag.addHighlight(range.start(), range.end(), attrs);
                                LOG.log(Level.FINEST, "Add bad token highlight {0}", tok);
                                used.add(range);
                            }
                        }
                    }
                }
            }
            if (highlightParserErrors()) {
                for (AntlrProxies.ParseTreeElement el : coalescedErrorElements(semantics)) {
                    if (el.kind() == ParseTreeElementKind.ERROR) {
                        SimpleAttributeSet tip = new SimpleAttributeSet();
                        tip.addAttribute(EditorStyleConstants.Tooltip, el.stringify());
                        tip.addAttribute(EditorStyleConstants.DisplayName, el.stringify());
                        try {
                            if (el.isSynthetic() && el instanceof ErrorNodeTreeElement) {
                                int start = ((ErrorNodeTreeElement) el).tokenStart();
                                int end = ((ErrorNodeTreeElement) el).tokenEnd();
                                if (end == start) {
                                    end = Math.min(doc.getLength() - 1, start + 1);
                                }
                                if (start >= 0 && end > start) {
                                    bag.addHighlight(start, end,
                                            AttributesUtilities.createComposite(parserErrors(), tip));
                                    Position startPos = doc.createPosition(start);
                                    Position endPos = doc.createPosition(end);
                                    set.add(ErrorDescriptionFactory
                                            .createErrorDescription(Severity.ERROR, el.stringify(),
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
                                            bag.addHighlight(first.getStartIndex(), last.getEndIndex(),
                                                    AttributesUtilities.createComposite(parserErrors(), tip));
                                            Position startPos = doc.createPosition(first.getStartIndex());
                                            Position endPos = doc.createPosition(last.getStopIndex());
                                            set.add(ErrorDescriptionFactory
                                                    .createErrorDescription(Severity.ERROR, el.stringify(),
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

    void onHighlightItemsChanged() {
        HighlightingInfo info = mgr.lastInfo();
        if (info != null) {
            refresh(info);
        }
    }

    @Override
    protected void onColoringsChanged() {
        onHighlightItemsChanged();
    }

    static void onChange() {
        for (AdhocErrorHighlighter hl : INSTANCES) {
            hl.onHighlightItemsChanged();
        }
    }

    static boolean highlightAmbiguities() {
        return prefs().getBoolean(PREFS_KEY_HIGHLIGHT_AMBIGUITIES, false);
    }

    static boolean highlightAmbiguities(boolean val) {
        boolean old = highlightAmbiguities();
        prefs().putBoolean(PREFS_KEY_HIGHLIGHT_AMBIGUITIES, val);
        if (val != old) {
            onChange();
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
        prefs().putBoolean(PREFS_KEY_HIGHLIGHT_PARSER_ERRORS, val);
        if (val != old) {
            onChange();
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
        prefs().putBoolean(PREFS_KEY_HIGHLIGHT_LEXER_ERRORS, val);
        if (val != old) {
            onChange();
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

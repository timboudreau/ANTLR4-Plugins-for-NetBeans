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
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
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
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
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
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocErrorHighlighter extends AbstractAntlrHighlighter implements Runnable {

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

    public static AttributeSet coloring() {
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
        Color c = UIManager.getColor("nb.errorForeground");
        if (c == null) {
            c = new Color(255, 190, 190);
        }
        StyleConstants.setForeground(set, c);
        StyleConstants.setBackground(set, new Color(120, 20, 20));
        set.addAttribute(EditorStyleConstants.WaveUnderlineColor, c.darker());
        return errorColoring = AttributesUtilities.createImmutable(set);
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
    @Messages({"buildFailed=Build Failed"})
    protected void refresh(HighlightingInfo info) {
        Document doc = info.doc;
        AntlrProxies.ParseTreeProxy semantics = info.semantics;
        OffsetsBag bag = new OffsetsBag(doc, true);
        List<ErrorDescription> set = new ArrayList<>();
        AttributeSet attrs = coloring();
        if (semantics.text() == null || semantics.text().length() < 2) {
            // do nothing
        } else if (semantics.isUnparsed()) {
            set.add(ErrorDescriptionFactory
                    .createErrorDescription(org.netbeans.spi.editor.hints.Severity.ERROR,
                            Bundle.buildFailed(), doc, 0));
        } else {
            LOG.log(Level.FINER, "Refresh errors with {0} errors", semantics.syntaxErrors().size());
            Set<IntRange<? extends IntRange<?>>> used = new HashSet<>();
            LineDocument ld = LineDocumentUtils.as(doc, LineDocument.class);
            if (!semantics.syntaxErrors().isEmpty()) {
                for (AntlrProxies.ProxySyntaxError e : semantics.syntaxErrors()) {
                    SimpleAttributeSet ttip = new SimpleAttributeSet();
                    ttip.addAttribute(EditorStyleConstants.WaveUnderlineColor, Color.RED);
                    ttip.addAttribute(EditorStyleConstants.Tooltip, e.message());
                    AttributeSet finalAttrs = AttributesUtilities.createComposite(attrs, ttip);
                    try {
                        IntRange<? extends IntRange<?>> range;
                        if (e instanceof AntlrProxies.ProxyDetailedSyntaxError) {
                            AntlrProxies.ProxyDetailedSyntaxError d = (AntlrProxies.ProxyDetailedSyntaxError) e;
                            int start = Math.max(0, d.startIndex());
                            int end = Math.min(doc.getLength() - 1, d.endIndex());
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
                        Logger.getLogger(AdhocErrorHighlighter.class.getName()).log(Level.INFO, "BLE parsing " + e, ble);
                    }
                }
                for (AntlrProxies.ProxyToken tok : semantics.tokens()) {
                    if (semantics.isErroneousToken(tok)) {
                        int start = Math.max(0, tok.getStartIndex());
                        int end = Math.min(doc.getLength() - 1, tok.getEndIndex());
                        if (!used.contains(Range.ofCoordinates(Math.min(start, end), Math.max(start, end)))) {
                            bag.addHighlight(Math.min(start, end), Math.max(start, end), attrs);
                            LOG.log(Level.FINEST, "Add bad token highlight {0}", tok);
                        }
                    }
                }
            }
        }
        // XXX useful but very noisy
        if (highlightAmbiguities()) {
            for (AntlrProxies.Ambiguity amb : semantics.ambiguities()) {
                ProxyToken a = semantics.tokens().get(amb.startOffset);
                ProxyToken b = semantics.tokens().get(amb.stopOffset);
                bag.addHighlight(a.getStartIndex(), b.getEndIndex(), warning());
                SimpleAttributeSet sas = new SimpleAttributeSet();
                sas.addAttribute(EditorStyleConstants.Tooltip, "Matches " + amb.conflictingAlternatives);
                set.add(ErrorDescriptionFactory.createErrorDescription(Severity.WARNING, "Ambiguity in rule '" + amb.ruleName + "'", NbEditorUtilities.getFileObject(doc), amb.startOffset, amb.end()));
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
        this.refreshErrorsTask.schedule(REFRESH_ERRORS_DELAY);
    }

    void onHighlightAmbiguitiesChanged() {
        HighlightingInfo info = mgr.lastInfo();
        if (info != null) {
            refresh(info);
        }
    }

    @Override
    protected void onColoringsChanged() {
        onHighlightAmbiguitiesChanged();
    }

    static boolean highlightAmbiguities() {
        return prefs().getBoolean("highlight-ambiguities", false);
    }

    static boolean highlightAmbiguities(boolean val) {
        boolean old = highlightAmbiguities();
        prefs().putBoolean("highlight-ambiguities", val);
        if (val != old) {
            for (AdhocErrorHighlighter hl : INSTANCES) {
                hl.onHighlightAmbiguitiesChanged();
            }
            return true;
        }
        return false;
    }

    @Messages({"highlightAmbiguities=Highlight Ambiguities",
        "highlightAmbiguitiesDesc=Enables highlighting of cases of ambiguity in the lexer; "
        + " disabled by default because it can create a lot of visual noise."})
    public static Action toggleHighlightAmbiguitiesAction() {
        // Actions.checkbox() returns an action that never has an icon, even using
        // putValue(), so IOWindow will throw an exception (and there will be no
        // indication in the toolbar button whether it is selected or not). So,
        // we do it the old-fashioned way.
        return new ToggleHighlightAmbiguitiesAction();
    }

    static final class ToggleHighlightAmbiguitiesAction extends AbstractAction implements Icon {

        ToggleHighlightAmbiguitiesAction() {
            putValue("hideActionText", true);
            putValue(SMALL_ICON, this);
            putValue(NAME, Bundle.highlightAmbiguities());
            putValue(SHORT_DESCRIPTION, Bundle.highlightAmbiguitiesDesc());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean old = highlightAmbiguities();
            highlightAmbiguities(!old);
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
                g.setColor(UIManager.getColor("Tree.line"));
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

    private static Preferences prefs() {
        return NbPreferences.forModule(AdhocErrorHighlighter.class);
    }

    public void run() {
        HighlightingInfo info = mgr.lastInfo();
        if (info != null) {
            Document doc = info.doc;
            Runnable doIt = () -> {
                refresh(info);
            };
            // createErrorDescription will lock the document;
            // so will the call to set the errors - so rather than
            // risk deadlock, we do this in a background thread and
            // pre-lock the document so we don't lock/unlock once per
            // error
            //
            // This little hack gives us some further deadlock-proofing
            // by ensuring we don't run when something else has the
            // document locked
            AdhocEditorKit.renderWhenPossible(doc, doIt);
        }
    }

}

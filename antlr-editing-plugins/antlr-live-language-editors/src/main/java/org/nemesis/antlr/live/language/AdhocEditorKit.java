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

import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import static javax.swing.text.DefaultEditorKit.copyAction;
import static javax.swing.text.DefaultEditorKit.cutAction;
import static javax.swing.text.DefaultEditorKit.pasteAction;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.text.StyledDocument;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElement;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElementKind.ROOT;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.TokenAssociated;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.doc.EnhEditorDocument;
import org.nemesis.editor.ops.DocumentOperator;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Extraction;
import org.nemesis.localizers.api.Localizers;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.MultiKeymap;
import org.netbeans.editor.ext.ExtKit;
import org.openide.util.NbBundle.Messages;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseTextUI;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.Mnemonics;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.Line;
import org.openide.text.NbDocument;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocEditorKit extends ExtKit {

    private final String mimeType;
    // We get registered actions and such from the text/plain mime type,
    // so create a fake editor kit that will load and expose those
    private static final FakePlainKit plainTextEditorKit = new FakePlainKit();

    public AdhocEditorKit(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public String getContentType() {
        return mimeType;
    }

    @Override
    public Action getActionByName(String name) {
        switch (name) {
            case ExtKit.buildPopupMenuAction:
                return new PopupBuilder();
        }
        return plainTextEditorKit.getActionByName(name);
    }

    @Override
    protected Action[] getDeclaredActions() {
        return plainTextEditorKit.getDeclaredActions();
    }

    @Override
    protected Action[] getCustomActions() {
        return plainTextEditorKit.getCustomActions();
    }

    @Override
    protected BaseTextUI createTextUI() {
        return super.createTextUI();
    }

    static final RequestProcessor ADHOC_POPULATE_MENU_POOL
            = new RequestProcessor("populate-adhoc-popup", 1, true);

    @Messages({"copySyntaxTree=Copy Syntax Tree Path",
        "copyTokenDetails=Copy Token Sequence Details",
        "parsing=Reparsing for syntax tree path",
        "copiedToClipboard=Copied to clipboard"})
    static final class CopySyntaxTreePathAction extends AbstractAction {

        private final JTextComponent component;
        private StatusDisplayer.Message message;

        public CopySyntaxTreePathAction(JTextComponent component) {
            // set by PreviewPanel
            boolean isLexer = Boolean.TRUE.equals(component.getDocument().getProperty("isLexer"));
            putValue(NAME, isLexer ? Bundle.copyTokenDetails() : Bundle.copySyntaxTree());
            this.component = component;
        }

        private void failureMessage(String msg) {
            this.message = StatusDisplayer.getDefault().setStatusText(msg, StatusDisplayer.IMPORTANCE_FIND_OR_REPLACE);
        }

        @Messages({
            "errDocTooShort=Document too short",
            "# {0} - caretPosition",
            "errNoToken=No token at caret position {0}",
            "errNothingToCopy=Nothing to copy (at start?)",
            "# {0} - caretPosition",
            "noTreeElement=No parse tree at caret position {0} (routed to another channel?)"
        })
        @Override
        public void actionPerformed(ActionEvent e) {
            int caret = component.getCaret().getDot();
            Document doc = component.getDocument();
            String mime = NbEditorUtilities.getMimeType(doc);
            Cursor oldCursor = component.getCursor();
            component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            ADHOC_POPULATE_MENU_POOL.submit(() -> {
                try {
                    EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mime);
                    Segment seg = DocumentOperator.render(doc, () -> {
                        Segment s = new Segment();
                        doc.getText(0, doc.getLength(), s);
                        return s;
                    });
                    if (seg.length() < 3) {
                        failureMessage(Bundle.errDocTooShort());
                        return;
                    }
                    EmbeddedAntlrParserResult res = parser.parse(seg);
                    ParseTreeProxy proxy = res.proxy();
                    ProxyToken tok = proxy.tokenAtPosition(caret);
                    if (tok == null) {
                        failureMessage(Bundle.errNoToken(caret));
                        return;
                    }
                    if (proxy != null && !proxy.isUnparsed()) {
                        StringBuilder content = new StringBuilder();
                        if (proxy.isLexerGrammar()) {
                            List<String> infos = new ArrayList<>();
                            List<CharSequence> texts = new ArrayList<>();
                            List<ProxyToken> tokens = proxy.tokens();
                            for (ProxyToken pt : tokens) {
                                CharSequence text = Escaper.CONTROL_CHARACTERS.escape(proxy.textOf(tok));
                                if (tok.isWhitespace()) {
                                    text = '\'' + text.toString() + '\'';
                                }

                                StringBuilder info = new StringBuilder();
                                ProxyTokenType type = proxy.tokenTypeForInt(pt.getType());

                                info.append(type.name()).append('(').append(pt.getType())
                                        .append(") ").append(pt.getStartIndex()).append('-')
                                        .append(pt.getEndIndex()).append('=').append(pt.length());
                                infos.add(info.toString());
                                texts.add(text.toString());
                                if (pt.equals(tok)) {
                                    break;
                                }
                            }
                            if (!infos.isEmpty()) {
                                StringBuilder textLine = new StringBuilder();
                                StringBuilder infoLine = new StringBuilder();
                                int maxLine = 120;
                                for (int i = 0; i < infos.size(); i++) {
                                    CharSequence text = texts.get(i);
                                    String info = infos.get(i);
                                    textLine.append(text);
                                    infoLine.append(info);
                                    int maxLength = Math.max(text.length(), info.length());
                                    if (textLine.length() + maxLength > maxLine) {
                                        content.append('\n').append(textLine);
                                        content.append('\n').append(infoLine);
                                        textLine.setLength(0);
                                        infoLine.setLength(0);
                                    }
                                    if (text.length() > info.length()) {
                                        char[] pad = new char[text.length() - info.length()];
                                        Arrays.fill(pad, ' ');
                                        infoLine.append(pad);
                                    } else if (text.length() < info.length()) {
                                        char[] pad = new char[text.length() - info.length()];
                                        Arrays.fill(pad, ' ');
                                        textLine.append(pad);
                                    }
                                    if (textLine.length() + 3 > maxLine) {
                                        content.append('\n').append(textLine);
                                        content.append('\n').append(infoLine);
                                        textLine.setLength(0);
                                        infoLine.setLength(0);
                                    } else if (i != infos.size() - 1) {
                                        textLine.append(" / ");
                                        infoLine.append(" / ");
                                    }
                                }
                                if (textLine.length() > 0) {
                                    content.append('\n').append(textLine);
                                    content.append('\n').append(infoLine);
                                }
                            } else {
                                return;
                            }
                        } else {
                            int tix = tok.getTokenIndex();
                            int bestDepth = Integer.MIN_VALUE;
                            ParseTreeElement bestElement = null;
                            for (ParseTreeElement el : proxy.allTreeElements()) {
                                if (el instanceof TokenAssociated) {
                                    TokenAssociated ta = (TokenAssociated) el;
                                    if (ta.startTokenIndex() >= tix && ta.stopTokenIndex() <= tix) {
                                        if (el.depth() > bestDepth) {
                                            bestDepth = el.depth();
                                            bestElement = el;
                                        }
                                    }
                                }
                            }
                            if (bestElement != null) {
                                ParseTreeElement el = bestElement;
                                while (el.kind() != ROOT) {
                                    char[] pad = new char[el.depth() * 4];
                                    Arrays.fill(pad, ' ');
                                    String txt = new String(pad) + el.stringify(proxy) + "\n";
                                    content.insert(0, txt);
                                    el = el.parent();
                                }
                            } else {
                                failureMessage(Bundle.noTreeElement(caret));
                                return;
                            }
                        }
                        if (content.length() > 0) {
                            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                            StringSelection string = new StringSelection(content.toString());
                            clip.setContents(string, string);
                            StatusDisplayer.getDefault().setStatusText(Bundle.copiedToClipboard());
                        } else {
                            failureMessage(Bundle.errNothingToCopy());
                        }
                    }
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                } finally {
                    EventQueue.invokeLater(() -> {
                        component.setCursor(oldCursor);
                    });
                }
            });
        }

    }

    private class PopupBuilder extends AbstractAction {

        PopupBuilder() {
            putValue(NAME, ExtKit.buildPopupMenuAction);
        }

        @Messages({"copy=&Copy",
            "cut=Cu&t",
            "paste=&Paste",
            "importMenu=Import"
        })
        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = (JTextComponent) e.getSource();
            EditorUI ui = Utilities.getEditorUI(target);
            JPopupMenu menu = new JPopupMenu();
            try {
                menu.add(ImportIntoSampleAction.submenu(target));
                menu.add(new CopySyntaxTreePathAction(target));
                menu.add(new CopyTokenSequenceAction(target));
                menu.add(new JSeparator());
                JMenuItem cutItem = new JMenuItem(getActionByName(cutAction));
                Mnemonics.setLocalizedText(cutItem, Bundle.cut());
                menu.add(cutItem);
                JMenuItem copyItem = new JMenuItem(getActionByName(copyAction));
                Mnemonics.setLocalizedText(copyItem, Bundle.copy());
                menu.add(copyItem);
                JMenuItem pasteItem = new JMenuItem(getActionByName(pasteAction));
                Mnemonics.setLocalizedText(pasteItem, Bundle.paste());
                menu.add(pasteItem);
                menu.add(new JSeparator());
                menu.add(AdhocErrorHighlighter.toggleHighlightParserErrorsAction(false).getPopupPresenter());
                menu.add(AdhocErrorHighlighter.toggleHighlightLexerErrorsAction(false).getPopupPresenter());
                menu.add(AdhocErrorHighlighter.toggleHighlightAmbiguitiesAction(false).getPopupPresenter());
                menu.add(SetPredictionModeAction.createMenuItem());
                menu.add(new JSeparator());
                menu.add(createLazyGotoSubmenu(target));
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
            ui.setPopupMenu(menu);
        }

        @Messages({"goto=Go To",
            "waitFor=Please wait...",
            "# {0} - tokenName",
            "tokenDefinition=Token Definition for ''{0}''",
            "noNav=Could not find items to navigate to"
        })
        JMenuItem createLazyGotoSubmenu(JTextComponent target) {
            JMenu sub = new JMenu(Bundle._goto());
            JMenuItem waitItem = new JMenuItem(Bundle.waitFor());
            waitItem.setEnabled(false);
            sub.add(waitItem);
            PopulateListener pl = new PopulateListener(sub, waitItem, target);
            sub.addItemListener(pl);
            return sub;
        }

        class PopulateListener implements ItemListener, Runnable {

            private final AtomicLinkedQueue<Action> actions = new AtomicLinkedQueue<>();
            private final JMenu sub;
            private final JMenuItem waitItem;
            private final JTextComponent target;
            private final RequestProcessor.Task task = ADHOC_POPULATE_MENU_POOL.create(this, false);
            private volatile boolean completed;

            public PopulateListener(JMenu sub, JMenuItem waitItem, JTextComponent target) {
                this.sub = sub;
                this.waitItem = waitItem;
                this.target = target;
            }

            @Override
            public void itemStateChanged(ItemEvent e) {
                boolean done = completed;
                if (done) {
                    e.getItemSelectable().removeItemListener(this);
                }
                Object[] obs = e.getItemSelectable().getSelectedObjects();
                if (obs != null && obs.length > 0 && !done) {
                    task.cancel();
                    task.schedule(350);
                } else {
                    task.cancel();
                }
            }

            @Override
            public void run() {
                if (!EventQueue.isDispatchThread()) {
                    if (Thread.interrupted()) {
                        return;
                    }
                    Segment seg = new Segment();
                    target.getDocument().render(() -> {
                        try {
                            target.getDocument().getText(0, target.getDocument().getLength(), seg);
                        } catch (BadLocationException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    });
                    if (Thread.interrupted()) {
                        return;
                    }
                    if (seg.length() > 0) {
                        EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(getContentType());
                        if (Thread.interrupted()) {
                            return;
                        }
                        try {
                            EmbeddedAntlrParserResult res = parser.parse(seg);
                            if (res != null && res.isUsable()) {
                                if (Thread.interrupted()) {
                                    return;
                                }
                                ParseTreeProxy prox = res.proxy();
                                if (prox != null && !prox.isUnparsed()) {
                                    int caret = target.getCaretPosition();
                                    ProxyToken tok = prox.tokenAtPosition(caret);
                                    if (!prox.isErroneousToken(tok)) {
                                        ProxyTokenType tokenType = prox.tokenTypeForInt(tok.getType());
                                        Path grammarPath = AdhocMimeTypes.grammarFilePathForMimeType(getContentType());
                                        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(grammarPath.toFile()));
                                        Extraction ext = NbAntlrUtils.extractionFor(fo);
                                        if (ext != null && !ext.isPlaceholder()) {
                                            if (Thread.interrupted()) {
                                                return;
                                            }
                                            List<Action> actionsLocal = new ArrayList<>();
                                            if (tokenType.symbolicName != null) {
                                                Action gotoToken = createActionFor(tokenType.symbolicName, ext);
                                                if (gotoToken != null) {
                                                    actionsLocal.add(gotoToken);
                                                }
                                            }
                                            Set<String> seen = new HashSet<>();
                                            for (AntlrProxies.ParseTreeElement pte : prox.allTreeElements()) {
                                                switch (pte.kind()) {
                                                    case RULE:
                                                        if (Thread.interrupted()) {
                                                            return;
                                                        }
                                                        if (seen.contains(pte.name(prox))) {
                                                            continue;
                                                        }
                                                        List<ProxyToken> toks = prox.tokensForElement(pte);
                                                        if (!toks.isEmpty()) {
                                                            ProxyToken first = toks.get(0);
                                                            ProxyToken last = toks.get(toks.size() - 1);
                                                            int start = first.getStartIndex();
                                                            int end = last.getEndIndex();
                                                            if (caret >= start && caret <= end) {
                                                                String name = pte.name(prox);
                                                                seen.add(name);
                                                                Action act = createActionFor(name, ext);
                                                                if (act != null) {
                                                                    actionsLocal.add(act);
                                                                }
                                                            }
                                                        }
                                                }
                                            }
                                            for (Action a : actionsLocal) {
                                                this.actions.add(a);
                                            }
                                        }
                                    }
                                    completed = true;
                                    EventQueue.invokeLater(this);
                                }
                            }
                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                } else {
                    if (!sub.isPopupMenuVisible()) {
                        completed = false;
                        return;
                    }
                    synchronized (sub.getTreeLock()) {
                        boolean hasAny = !actions.isEmpty();
                        sub.removeAll();
                        if (hasAny) {
                            actions.drain(act -> {
                                sub.add(act);
                            });
                        } else {
                            JMenuItem noNav = new JMenuItem(Bundle.noNav());
                            noNav.setEnabled(false);
                            sub.add(noNav);
                        }
                    }
                    // Hack hack hack - menu popups don't expect their
                    // contents to change on the fly, and we get 4px tall menu items
                    Container parent = sub.getParent();
                    if (parent != null) {
                        JPopupMenu subPop = sub.getPopupMenu();
                        subPop.invalidate();
                        subPop.doLayout();
                        subPop.revalidate();
                        subPop.repaint();
                        sub.setPopupMenuVisible(false);
                        sub.setPopupMenuVisible(true);
                    }
                    sub.invalidate();
                    sub.revalidate();
                    sub.repaint();
                }
            }
        }

        private Action createActionFor(String name, Extraction ext) {
            AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved
                    = ext.resolveName(AntlrKeys.RULE_NAME_REFERENCES, name, true);
            if (resolved != null) {
                Extraction originExt = resolved.attributedTo();
                DataObject dob = originExt.source().lookupOrDefault(DataObject.class, null);
                if (dob != null) {
                    CloneableEditorSupport ck = dob.getLookup().lookup(CloneableEditorSupport.class);
                    if (ck != null) {
                        PositionRef start = ck.createPositionRef(resolved.element().start(), Position.Bias.Backward);
                        PositionRef end = ck.createPositionRef(resolved.element().end(), Position.Bias.Forward);
                        PositionBounds pb = new PositionBounds(start, end);
                        try {
                            return new GotoRegion(resolved.element().name(), resolved.element().kind(), originExt.source().name(), pb);
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            }
            return null;
        }
    }

    @Messages({
        "# {0} - definitionOfWhat",
        "# {1} - ruleType",
        "# {2} - fileName",
        "gotoDefinitionOf=Go To ''{0}'' ({1}) in {2}",
        "# {0} - whatFailed",
        "failed=Failed: {0}"
    })
    static final class GotoRegion extends AbstractAction {

        private final PositionBounds bounds;

        GotoRegion(String ruleName, RuleTypes type, String sourceName, PositionBounds bounds) throws IOException {
            String typeName = Localizers.displayName(type);
            putValue(NAME, Bundle.gotoDefinitionOf(ruleName, typeName, sourceName));
            this.bounds = bounds;
        }

        private void defaultNavigate() throws IOException {
            StyledDocument doc = bounds.getBegin().getCloneableEditorSupport().openDocument();
            Line ln = NbEditorUtilities.getLine(doc, bounds.getBegin().getOffset(), false);
            if (ln != null) {
                StatusDisplayer.getDefault().setStatusText((String) getValue(NAME));
                ln.show(Line.ShowOpenType.REUSE_NEW, Line.ShowVisibilityType.FOCUS);
            } else {
                StatusDisplayer.getDefault().setStatusText(Bundle.failed(getValue(NAME)));
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                StyledDocument doc = bounds.getBegin().getCloneableEditorSupport().getDocument();
                if (doc == null) {
                    defaultNavigate();
                } else {
                    // Use EditorRegistry, for the sake of the Antlr preview, which hacks its
                    // custom editor panes into it
                    JTextComponent comp = EditorRegistry.findComponent(doc);
                    if (comp != null) {
                        TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, comp);
                        comp.setCaretPosition(bounds.getBegin().getOffset());
                        if (TopComponent.getRegistry().getActivated() != tc) {
                            tc.requestActive();
                        }
                        comp.requestFocus();
                    } else {
                        defaultNavigate();
                    }
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    @Messages({"copyTokenSequence=Copy Token Sequence",
        "copyTokenSequenceDesc=Copies the token names in the selection or to the end of the current line, creating a rough approximation of a rule definition",
        "noTokens=No token sequence found",
        "# {0} - theTokenSequence",
        "addedToClipboard=Token sequence added to clipboard: {0}"
    })
    public static final class CopyTokenSequenceAction extends AbstractAction {

        private final JTextComponent comp;

        CopyTokenSequenceAction(JTextComponent comp) {
            this.comp = comp;
            putValue(NAME, Bundle.copyTokenSequence());
            putValue(SHORT_DESCRIPTION, Bundle.copyTokenSequenceDesc());
        }

        @Override
        @SuppressWarnings("unchecked")
        public void actionPerformed(ActionEvent e) {
            StyledDocument doc = (StyledDocument) comp.getDocument();
            StringBuilder tokens = new StringBuilder();
            String mime = NbEditorUtilities.getMimeType(doc);
            doc.render(() -> {
                int start = comp.getSelectionStart();
                int end = comp.getSelectionEnd();
                if (start == end) {
                    int lineCount = NbDocument.findLineNumber(doc, doc.getLength() - 1);
                    int line = NbDocument.findLineNumber(doc, start);
                    start = NbDocument.findLineOffset(doc, line);
                    if (line >= lineCount) {
                        end = doc.getLength() - 1;
                    } else {
                        end = NbDocument.findLineOffset(doc, line + 1);
                    }
                }
                TokenHierarchy<Document> hier = TokenHierarchy.get(doc);
                if (hier == null) {
                    return;
                }
                Language<AdhocTokenId> lang = (Language<AdhocTokenId>) Language.find(mime);
                if (lang == null) {
                    DynamicLanguages.ensureRegistered(mime);
                    lang = (Language<AdhocTokenId>) Language.find(mime);
                    if (lang == null) {
                        // our lock bypassing makes this possible
                        return;
                    }
                }
                TokenSequence<AdhocTokenId> seq = hier.tokenSequence(lang);
                if (seq == null || seq.isEmpty() || !seq.isValid()) {
                    // do it the hard way - the token sequence held by the lexer infrastructure
                    // was garbage collected
                    EmbeddedAntlrParser par = AdhocLanguageHierarchy.parserFor(mime);
                    if (par != null) {
                        Segment seg = new Segment();
                        try {
                            doc.getText(0, doc.getLength(), seg);
                            EmbeddedAntlrParserResult res = par.parse(seg);
                            if (res != null && res.isUsable() && res.proxy() != null && !res.proxy().isUnparsed()) {
                                ParseTreeProxy prx = res.proxy();
                                ProxyToken tok = prx.tokenAtPosition(start);
                                ProxyTokenType last = null;
                                while (tok != null && tok.getStartIndex() < end) {
                                    ProxyTokenType type = prx.tokenTypeForInt(tok.getType());
                                    CharSequence tokTxt = prx.textOf(tok);
                                    if (!Strings.isBlank(tokTxt) && !tok.isEOF()) {
                                        if (Objects.equals(last, type)) {
                                            if (tokens.length() > 0 && tokens.charAt(tokens.length() - 1) != '+') {
                                                tokens.append('+');
                                            }
                                        } else {
                                            if (tokens.length() > 0) {
                                                tokens.append(' ');
                                            }
                                            if (type.literalName != null) {
                                                tokens.append('\'').append(type.literalName).append('\'');
                                            } else {
                                                tokens.append(type.symbolicName);
                                            }
                                        }
                                    }
                                    if (tok.getTokenIndex() + 1 >= prx.tokenCount()) {
                                        break;
                                    }
                                    tok = prx.tokens().get(tok.getTokenIndex() + 1);
                                }
                            }
                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                    return;
                }
                seq.move(start);
                if (!seq.moveNext()) {
                    return;
                }
                int count = seq.tokenCount();
                AdhocTokenId last = null;
                do {
                    Token<AdhocTokenId> tok = seq.offsetToken();
                    if (tok == null) {
                        break;
                    }
                    // Assume whitespace is on another channel and noise
                    // if the user is attempting to figure out a token sequence
                    // to form a rule from
                    if (Strings.isBlank(tok.text())) {
                        seq.moveNext();
                        continue;
                    }
                    AdhocTokenId id = tok.id();
                    if (Objects.equals(id, last)) {
                        tokens.append('+');
                    } else {
                        if (tokens.length() > 0) {
                            tokens.append(' ');
                        }
                        tokens.append(id.toTokenString());
                    }
                    if (!seq.moveNext()) {
                        break;
                    }
                } while (seq.index() < count && seq.offset() < end);
            });
            if (tokens.length() == 0) {
                StatusDisplayer.getDefault().setStatusText(Bundle.noTokens());
                Toolkit.getDefaultToolkit().beep();
            } else {
                StatusDisplayer.getDefault().setStatusText(Bundle.addedToClipboard(tokens));
                StringSelection sel = new StringSelection(tokens.toString());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            }
        }
    }

    @Override
    protected Action[] createActions() {
        Action[] result = plainTextEditorKit.createActions();
        if (result != null) {
            // XXX why are we doing this?
            for (int i = 0; i < result.length; i++) {
                if (result[i] instanceof PasteAction) {
                    result[i] = new AsyncPasteAction(false);
                }
            }
        } else {
            result = new Action[0];
        }
        return result;
    }

    @Override
    protected void initDocument(BaseDocument doc) {
        super.initDocument(doc);
        doc.putProperty("mimeType", mimeType);
    }

    @Override
    public MultiKeymap getKeymap() {
        return plainTextEditorKit.getKeymap();
    }

    @Override
    public Document createDefaultDocument() {
        return new Doc(mimeType);
    }

    // Give the lexer a way to find out what document it's lexing
    private static final ThreadLocal<Doc> RENDERING_DOCUMENT
            = new ThreadLocal<>();

    public static Document currentDocument() {
        return RENDERING_DOCUMENT.get();
    }

    public static FileObject currentFileObject() {
        Document doc = currentDocument();
        return doc != null ? NbEditorUtilities.getFileObject(doc) : null;
    }

    public static AdhocDataObject currentDataObject() {
        Document doc = currentDocument();
        DataObject dob = doc == null ? NbEditorUtilities.getDataObject(doc) : null;
        return dob == null ? null : dob.getLookup().lookup(AdhocDataObject.class);
    }

    static final class AsyncPasteAction extends PasteAction {

        public AsyncPasteAction(boolean formatted) {
            super(formatted);
        }

        @Override
        public void actionPerformed(ActionEvent evt, JTextComponent target) {
            EventQueue.invokeLater(() -> {
                super.actionPerformed(evt, target);
            });
        }
    }

    static void renderWhenPossible(Document doc, Runnable run) {
        if (doc instanceof Doc) {
            ((Doc) doc).renderWhenPossible(run);
        } else {
            doc.render(run);
        }
    }

    // We have to subclass this in order to supply a toolbar, or the infrastructure
    // throws an exception, since there is no toolbar provider registered
    static final class Doc extends EnhEditorDocument {

        private JToolBar bar;
        private final String mimeType;

        @SuppressWarnings("LeakingThisInConstructor")
        Doc(String mimeType) {
            super(mimeType);
            this.mimeType = mimeType;
            putProperty("mimeType", mimeType);
            putProperty(InputAttributes.class, new LangPropertyEvaluator(this));
        }

        @Override
        public String toString() {
            return "AdhocEditorDoc(" + System.identityHashCode(this)
                    + " for " + getProperty(StreamDescriptionProperty) + ")";
        }

        static final class LangPropertyEvaluator implements PropertyEvaluator {

            // Looking up the language during Document creation can deadlock
            // by reentering LanguageHierarchy.language(), so initialize that
            // on demand
            private final InputAttributes attrs = new InputAttributes();
            private final Doc doc;
            private volatile int createdAtLastCall = -1;

            public LangPropertyEvaluator(Doc doc) {
                this.doc = doc;
            }

            @Override
            public Object getValue() {
                int created = AdhocLanguageHierarchy.hierarchiesCreated();
                Language<?> lang = Language.find(doc.mimeType);
                attrs.setValue(lang, Document.class, doc, false);
                if (created > createdAtLastCall) {
                    if (lang != null) {
                        attrs.setValue(lang, "doc", doc, false);
                    }
                    FileObject fo = NbEditorUtilities.getFileObject(doc);
                    if (fo != null) {
                        attrs.setValue(lang, FileObject.class, fo, false);
                    }
                    createdAtLastCall = created;
                }
                return attrs;
            }

        }

        static final class LazyLanguageMap extends LazyPropertyMap {

            private final Doc doc;

            public LazyLanguageMap(Dictionary dict, Doc doc) {
                super(dict);
                this.doc = doc;
            }

            @Override
            public Object get(Object key) {
                if (InputAttributes.class.equals(key)) {
                    InputAttributes attrs = new InputAttributes();
                    FileObject fo = NbEditorUtilities.getFileObject(doc);
                    Language<?> lang = Language.find(doc.mimeType);
                    if (lang != null) {
                        attrs.setValue(lang, "doc", doc, false);
                    }
                    if (fo != null) {
                        attrs.setValue(lang, FileObject.class, fo, false);
                    }
                    return attrs;
                }
                Object result = super.get(key);
                return result;
            }
        }

        @Override
        public void render(Runnable r) {
            // This is just evil, but may help diagnose things
            Runnable removeThisThreadFromDeadlockBreaker = DocumentDeadlockBreaker.enqueue();
            try {
                super.render(wrap(r));
            } finally {
                removeThisThreadFromDeadlockBreaker.run();
            }
        }

        private Runnable wrap(Runnable r) {
            return new Wrap(this, r);
        }

        static class Wrap implements Runnable {

            private final Doc doc;
            private final Runnable r;

            public Wrap(Doc doc, Runnable r) {
                this.doc = doc;
                this.r = r;
            }

            @Override
            public void run() {
                Doc old = RENDERING_DOCUMENT.get();
                try {
                    RENDERING_DOCUMENT.set(doc);
                    r.run();
                } finally {
                    RENDERING_DOCUMENT.set(old);
                }
            }

        }
    }

    /**
     * We just want to load all of the keyboard and popup actions any plain text
     * file would have, for any adhoc file type - cut, paste, etc.
     */
    static final class FakePlainKit extends NbEditorKit {

        public static final String PLAIN_MIME_TYPE = "text/plain"; // NOI18N

        public String getContentType() {
            return PLAIN_MIME_TYPE;
        }

        @Override
        public Action[] getDeclaredActions() {
            return super.getDeclaredActions();
        }

        @Override
        public Action[] getCustomActions() {
            return super.getCustomActions();
        }

        @Override
        public Action[] createActions() {
            return super.createActions();
        }
    }
}

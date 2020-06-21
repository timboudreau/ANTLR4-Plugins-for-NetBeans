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
package org.nemesis.antlr.live.preview;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import static javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
import javax.swing.text.EditorKit;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.AdhocParserResult;
import org.nemesis.antlr.live.language.AdhocReparseListeners;
import org.nemesis.antlr.live.language.coloring.AdhocColoringsRegistry;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.TokenAssociated;
import org.nemesis.antlr.spi.language.AntlrParseResult;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.swing.Scroller;
import org.nemesis.swing.cell.TextCellLabel;
import org.netbeans.api.editor.caret.CaretInfo;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.awt.Mnemonics;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Tim Boudreau
 */
@Messages({"rulesList=&Rules", "syntaxTree=S&yntax Tree"})
public final class PreviewPanel extends JPanel implements ChangeListener,
        ListSelectionListener, DocumentListener, PropertyChangeListener,
        Lookup.Provider, BiConsumer<Document, EmbeddedAntlrParserResult>,
        FocusListener {

    static final Comparator<String> RULE_COMPARATOR = new RuleNameComparator();
    private static final java.util.logging.Logger LOG
            = java.util.logging.Logger.getLogger(PreviewPanel.class.getName());

    private final JList<String> rulesList = new ParentCheckingFastJList<>();
    private final JScrollPane rulesScroll = new JScrollPane(rulesList);
    private final SyntaxTreeListModel syntaxModel = new SyntaxTreeListModel();
    private final JList<SyntaxTreeListModel.ModelEntry> syntaxTreeList
            = syntaxModel.createList();
    private final JScrollPane syntaxTreeScroll = new JScrollPane(syntaxTreeList);
    private final JSplitPane listsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final AdhocColorings colorings;
    private final AdhocColoringPanel customizer;

    private final JEditorPane editorPane = new JEditorPane();
//    private final SimpleHtmlLabel breadcrumb = new SimpleHtmlLabel();
    private final TextCellLabel breadcrumb = new TextCellLabel("position");
//    private final JPanel breadcrumbPanel = new JPanel(new BorderLayout());
    private final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private boolean initialAdd = true;
    private Lookup lookup = Lookup.EMPTY;
    private final RequestProcessor asyncUpdateOutputWindowPool = new RequestProcessor(
            "antlr-preview-error-update", 1, true);
    private RequestProcessor.Task updateOutputWindowTask;
    private final ErrorUpdater outputWindowUpdaterRunnable;
    private RequestProcessor.Task triggerRerunHighlighters;
    private final RulePathStringifier stringifier = new RulePathStringifierImpl();
    private static final String DIVIDER_LOCATION_FILE_ATTRIBUTE = "splitPosition";
    private static final String SIDE_DIVIDER_LOCATION_FILE_ATTRIBUTE = "sidebarSplitPosition";
    private static final String SIDE_SPLIT_SIZE_FILE_ATTRIBUTE = "sidebarSize";
    private static final String SAMPLE_EDITOR_CARET_POSITION_FILE_ATTRIBUTE = "sampleEditorCaretPos";
    private static final String GRAMMAR_EDITOR_CARET_POSITION_FILE_ATTRIBUTE = "grammarEditorCaretPos";
    private static final String GRAMMAR_SCROLL_POSITION = "grammarScrollPosition";
    private static final String EDITOR_SCROLL_POSITION = "editorScrollPosition";
    private LayoutInfoSaver splitLocationSaver;
    private final InstanceContent content = new InstanceContent();
    private final AbstractLookup internalLookup = new AbstractLookup(content);
    private final String mimeType;
    private final JEditorPane grammarEditorClone = new JEditorPane();
    private final DataObject sampleFileDataObject;
    private final JPanel rulesContainer = new JPanel(new BorderLayout());
    private final JPanel syntaxTreeContainer = new JPanel(new BorderLayout());
    private final JLabel rulesLabel = new JLabel();
    private final JLabel syntaxTreeLabel = new JLabel();
    private final RequestProcessor.Task reparseTask = asyncUpdateOutputWindowPool.create(this::reallyReparse);
    private final JScrollPane sampleScroll;
    private final JScrollPane grammarScroll;
    private Component lastFocused;

    @SuppressWarnings("LeakingThisInConstructor")
    public PreviewPanel(final String mimeType, Lookup lookup, DataObject sampleFileDataObject,
            StyledDocument doc, AdhocColorings colorings, EditorKit kit, Lookup lkp) {
        // We preload anything we can on the background thread so as not to
        // block the UI with I/O intensive stuff, hence the constructor arguments
        this.mimeType = mimeType;
        this.sampleFileDataObject = sampleFileDataObject;
        this.colorings = colorings;
        customizer = new AdhocColoringPanel(colorings);
        // A few labels for usability
        Mnemonics.setLocalizedText(rulesLabel, Bundle.rulesList());
        Mnemonics.setLocalizedText(syntaxTreeLabel, Bundle.syntaxTree());
        rulesContainer.add(rulesLabel, BorderLayout.NORTH);
        rulesContainer.add(rulesScroll, BorderLayout.CENTER);
        rulesLabel.setLabelFor(rulesList);
        syntaxTreeContainer.add(syntaxTreeLabel, BorderLayout.NORTH);
        syntaxTreeContainer.add(syntaxTreeScroll, BorderLayout.CENTER);
        syntaxTreeLabel.setLabelFor(syntaxTreeList);

        Border underline = BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("controlDkShadow"));
        Border paddedUnderline = BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0),
                BorderFactory.createCompoundBorder(underline, BorderFactory.createEmptyBorder(5, 0, 5, 0)));
        rulesLabel.setBorder(paddedUnderline);
        syntaxTreeLabel.setBorder(paddedUnderline);

        setLayout(new BorderLayout());
        rulesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Add the selected coloring customizer at the top
        add(customizer, BorderLayout.NORTH);

        // "border buildup" avoidance
        Border empty = BorderFactory.createEmptyBorder();
        // Clean up gratuitous Swing borders
        rulesScroll.setBorder(empty);
        rulesScroll.setViewportBorder(empty);

        syntaxTreeScroll.setBorder(empty);
        syntaxTreeScroll.setViewportBorder(empty);

        listsSplit.setTopComponent(rulesContainer);
        listsSplit.setBottomComponent(syntaxTreeContainer);
        listsSplit.setBorder(empty);

        // Add the rules pane
        add(listsSplit, BorderLayout.EAST);
        // Create a runnable that will run asynchronously to beginScroll the
        // output window after the sample text has been altered or the
        // grammar has
        this.outputWindowUpdaterRunnable
                = new ErrorUpdater(editorPane, new RulePathStringifierImpl());

        // We will include its lookup in our own, so it can be saved
        this.lookup = new ProxyLookup(sampleFileDataObject.getLookup(), internalLookup);
        // Now find the editor kit (should be an AdhocEditorKit) from
        // our mime type
        // Configure the editor pane to use it
        editorPane.setEditorKit(kit);
        // Open the document and set it on the editor pane
        content.add(doc);
        editorPane.setDocument(doc);
        LOG.log(Level.FINE, "PreviewPanel content type is {0}", editorPane.getContentType());
        // EditorUI gives us line number gutter, error gutter, etc.
        EditorUI editorUI = Utilities.getEditorUI(editorPane);
        if (editorUI != null) {
            // This gives us the line number bar, etc.
            Component sampleEditorUIComponent = editorUI.getExtComponent();
            split.setTopComponent(sampleEditorUIComponent);
            if (sampleEditorUIComponent instanceof JScrollPane) {
                sampleScroll = (JScrollPane) sampleEditorUIComponent;
            } else {
                sampleScroll = findScrollPane(sampleEditorUIComponent);
            }
        } else {
            split.setTopComponent(sampleScroll = new JScrollPane(editorPane));
        }
        EditorCookie grammarFileEditorCookie = lookup.lookup(EditorCookie.class);
        // There will be an opened pane or this code would not
        // be running
        JEditorPane grammarFileOriginalEditor = grammarFileEditorCookie.getOpenedPanes()[0];
        // Create our own editor

        EditorKit grammarKit = grammarFileOriginalEditor.getEditorKit();
        // XXX if we could access ExtKit.createUI, we could probably fix the problem
        // that navigator clicks always switch to the Source tab
        grammarEditorClone.setEditorKit(grammarKit);
        grammarEditorClone.setDocument(grammarFileOriginalEditor.getDocument());

        EditorUI grammarFileEditorUI = Utilities.getEditorUI(grammarEditorClone);
        split.setOneTouchExpandable(true);
        // This sometimes gets us an invisible component
        if (grammarFileEditorUI != null) {
            Component grammarEditorUIComponent = grammarFileEditorUI.getExtComponent();
            split.setBottomComponent(grammarEditorUIComponent);
            if (grammarEditorUIComponent instanceof JScrollPane) {
                grammarScroll = (JScrollPane) grammarEditorUIComponent;
            } else {
                grammarScroll = findScrollPane(grammarEditorUIComponent);
            }
        } else {
            split.setBottomComponent(grammarScroll = new JScrollPane(grammarEditorClone));
        }
        // The splitter is our central component, showing the sample content in
        // the top and the grammar file in the bottom
        add(split, BorderLayout.CENTER);
        // Listen to the rule list to beginScroll the rule customizer
        rulesList.getSelectionModel().addListSelectionListener(this);
        rulesList.setCellRenderer(new RuleCellRenderer(colorings, stringifier::listBackgroundColorFor, this::currentProxy));

        // More border munging
        breadcrumb.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
        // The breadcrumb shows the rule path the caret is in
        add(breadcrumb, BorderLayout.SOUTH);
        // listen on the caret to beginScroll the breadcrumb
        syntaxModel.listenForClicks(syntaxTreeList, this::proxyTokens, this::onSyntaxTreeClick, () -> selectingRange);
        editorPane.getCaret().addChangeListener(this);
        grammarEditorClone.getCaret().addChangeListener(this);
        if (sampleScroll != null) {
            sampleScroll.getVerticalScrollBar().getModel().addChangeListener(this);
        }
        if (grammarScroll != null) {
            grammarScroll.getVerticalScrollBar().getModel().addChangeListener(this);
        }

        RulesListClickOrEnter ruleClick = new RulesListClickOrEnter();
        rulesList.addKeyListener(ruleClick);
        rulesList.addMouseListener(ruleClick);
        rulesList.addFocusListener(this);
        grammarEditorClone.addFocusListener(this);
        editorPane.addFocusListener(this);
        syntaxTreeList.addFocusListener(this);
        breadcrumb.addMouseListener(new BreadcrumbClickListener());
    }

    private static JScrollPane findScrollPane(Component comp) {
        if (comp instanceof Container) {
            for (Component c : ((Container) comp).getComponents()) {
                if (c instanceof JScrollPane) {
                    return (JScrollPane) c;
                }
            }
        }
        return null;
    }

    private ParseTreeProxy currentProxy() {
        EmbeddedAntlrParserResult res = internalLookup.lookup(EmbeddedAntlrParserResult.class);
        if (res != null) {
            return res.proxy();
        }
        return null;
    }

    private class BreadcrumbClickListener extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                String text = breadcrumb.textAt(e.getPoint());
                if (text != null) {
                    navigateToRule(text, true);
                    e.consume();
                }
            }
        }
    }

    private void navigateToRule(String ruleName, boolean focus) {
        try {
            Document doc = grammarEditorClone.getDocument();
            ParsingUtils.parse(doc, res -> {
                if (res instanceof AntlrParseResult) {
                    Extraction ext = ((AntlrParseResult) res).extraction();
                    if (ext != null) {
                        NamedSemanticRegions<RuleTypes> regions = ext.namedRegions(AntlrKeys.RULE_NAMES);
                        if (regions != null && regions.contains(ruleName)) {
                            NamedSemanticRegion<?> region = regions.regionFor(ruleName);
                            Caret caret = grammarEditorClone.getCaret();
                            positionCaret(caret, region.start(), doc);
                            if (focus) {
                                grammarEditorClone.requestFocus();
                            }
                        }
                    }
                }
                return null;
            });
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private class RulesListClickOrEnter extends MouseAdapter implements KeyListener {

        @Override
        public void keyTyped(KeyEvent e) {
            // do nothing
        }

        @Override
        public void keyPressed(KeyEvent e) {
            // do nothing
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                String rule = rulesList.getSelectedValue();
                e.consume();
                navigateToNearestExampleOf(rule);
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                String rule = rulesList.getSelectedValue();
                e.consume();
                navigateToNearestExampleOf(rule);
            }
        }
    }

    private void navigateToNearestExampleOf(String rule) {
        if (rule == null) {
            return;
        }
        EmbeddedAntlrParserResult res = internalLookup.lookup(EmbeddedAntlrParserResult.class);
        if (res == null) {
            return;
        }
        ParseTreeProxy ptp = res.proxy();
        if (ptp.isUnparsed()) {
            return;
        }
        int caretPosition = editorPane.getCaretPosition();
        boolean isLexer = Character.isUpperCase(rule.charAt(0));
        if (isLexer) {
            ProxyToken tok = ptp.tokenAtPosition(caretPosition);
            int ix = tok.getTokenIndex();
            List<ProxyToken> all = ptp.tokens();
            for (int i = ix + 1; i < all.size(); i++) {
                ProxyToken next = all.get(i);
                ProxyTokenType type = ptp.tokenTypeForInt(next.getType());
                if (type.name().equals(rule)) {
                    selectRangeInPreviewEditor(next.getStartIndex(), next.getEndIndex());
                    return;
                }
            }
            for (int i = ix; i >= 0; i--) {
                ProxyToken prev = all.get(i);
                ProxyTokenType type = ptp.tokenTypeForInt(prev.getType());
                if (type.name().equals(rule)) {
                    selectRangeInPreviewEditor(prev.getStartIndex(), prev.getEndIndex());
                    return;
                }
            }
        } else {
            for (ParseTreeElement el : ptp.allTreeElements()) {
                switch (el.kind()) {
                    case ERROR:
                    case ROOT:
                    case TERMINAL:
                        continue;
                }
                if (el.name().equals(rule)) {
                    List<ProxyToken> toks = new ArrayList<>(20);
                    Collections.sort(toks);
                    allElements(el, toks, ptp);
                    if (!toks.isEmpty()) {
                        ProxyToken first = toks.get(0);
                        ProxyToken last = toks.get(toks.size() - 1);
                        selectRangeInPreviewEditor(first.getStartIndex(), last.getEndIndex());
                        return;
                    }
                }
            }
        }
    }

    private void allElements(ParseTreeElement el, List<ProxyToken> into, ParseTreeProxy prox) {
        if (el instanceof TokenAssociated) {
            TokenAssociated ta = (TokenAssociated) el;
            int start = ta.startTokenIndex();
            int end = ta.endTokenIndex();
            into.addAll(prox.tokens().subList(start, end));
        }
    }

    public String toString() {
        return "PreviewPanel(" + editorPane.getDocument().getProperty(StreamDescriptionProperty) + " - displayable "
                + isDisplayable() + " showing " + isShowing() + ")";
    }

    @Override
    public void focusGained(FocusEvent e) {
        lastFocused = e.getComponent();
    }

    public void requestFocus() {
        super.requestFocus();
        if (lastFocused != null) {
            lastFocused.requestFocus();
        } else {
            editorPane.requestFocus();
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
        // do nothing
    }

    List<ProxyToken> proxyTokens() {
        EmbeddedAntlrParserResult res = internalLookup.lookup(EmbeddedAntlrParserResult.class);
        ParseTreeProxy proxy = res == null ? null : res.proxy();
        if (proxy == null) {
            return Collections.emptyList();
        }
        return proxy.tokens();
    }

    @SuppressWarnings("deprecation")
    void selectRangeInPreviewEditor(int... range) {
        if (selectingRange) {
            // ensure we don't re-enter because we noticed the
            // selection change and wind up grabbing the parent
            // element or some other element that has the
            // same span
            return;
        }
        selectingRange = true;
        // Must make the selection start the head of the
        // element, or the synax tree will get two notifications
        // and the second will put it at the *next* element
        try {
            Caret caret = editorPane.getCaret();
            if (caret instanceof EditorCaret) {
                Document doc = editorPane.getDocument();
                // If we grabbed traiiling whitespace, eliminate it
                String txt = doc.getText(range[0], range[1] - range[0]);
                int subtract = 0;
                for (int i = txt.length() - 1; i >= 0; i--) {
                    char c = txt.charAt(i);
                    if (Character.isWhitespace(c)) {
                        subtract++;
                    } else {
                        break;
                    }
                }
                int endPoint = range[1];
                if (subtract > 0 && subtract < txt.length()) {
                    endPoint -= subtract;
                }
                EditorCaret ec = (EditorCaret) caret;
                // We need to set the dot to the *beginning* of the token or we can accidentally
                // beginScroll the context to the token that abuts the new caret position
                Position begin = NbDocument.createPosition(editorPane.getDocument(), endPoint, Position.Bias.Backward);
                Position end = NbDocument.createPosition(editorPane.getDocument(), range[0], Position.Bias.Forward);
                org.netbeans.api.editor.caret.CaretInfo info = ec.getLastCaret();
                ec.moveCarets((CaretMoveContext context) -> {
                    // This seems to usually run later in the event
                    // queue, so try to preserve the selection the user chose
                    selectingRange = true;
                    try {
                        context.setDotAndMark(info, end, Position.Bias.Forward, begin, Position.Bias.Backward);
                    } finally {
                        selectingRange = false;
                    }
                });
            } else {
                editorPane.setSelectionStart(Math.max(range[0], range[1] - 1));
                editorPane.setSelectionEnd(range[0]);
            }
            // XXX can't use modelToView2D until we can depend on JDK 10
            Rectangle a = editorPane.getUI().modelToView(editorPane, range[0]);
            Rectangle b = editorPane.getUI().modelToView(editorPane, range[1]);
            a.add(b);
            editorPane.scrollRectToVisible(a);
            editorPane.requestFocusInWindow();
        } catch (BadLocationException ex) {
            LOG.log(Level.INFO, null, ex);
        } finally {
            selectingRange = false;
        }
    }

    private boolean selectingRange;

    void onSyntaxTreeClick(int clickCount, SyntaxTreeListModel.ModelEntry entry,  int start, int end) {
        selectRangeInPreviewEditor(new int[]{start, end});
        if (clickCount == 2) {
            navigateToRule(entry.name(), false);
        }
    }

    @Override
    public void accept(Document a, EmbeddedAntlrParserResult res) {
        ParseTreeProxy newProxy = res.proxy();
        if (!newProxy.mimeType().equals(mimeType)) {
            new Error("WTF? " + newProxy.mimeType() + " but expecting " + mimeType).printStackTrace();
            return;
        }
        Debug.message("preview-new-proxy " + Long.toString(newProxy.id(), 36)
                + " errs " + newProxy.syntaxErrors().size(), newProxy::toString);
        Mutex.EVENT.readAccess(() -> {
            if (AdhocColoringsRegistry.getDefault().ensureAllPresent(newProxy)) {
                updateColoringsList();
            }
            customizer.indicateActivity();// XXX invisible?
            Debug.run(this, "preview-update " + mimeType, () -> {
                StringBuilder sb = new StringBuilder("Mime: " + mimeType).append('\n');
                sb.append("Proxy mime: ").append(newProxy.mimeType()).append('\n');
                sb.append("Proxy grammar file: ").append(newProxy.grammarPath());
                sb.append("Proxy grammar name: ").append(newProxy.grammarName());
                sb.append("Document: ").append(a).append('\n');
                sb.append("RunResult").append(res.runResult()).append('\n');
                sb.append("GrammarHash").append(res.grammarTokensHash()).append('\n');
                sb.append("Text: ").append(newProxy.text()).append('\n');
                return "";
            }, () -> {
                EmbeddedAntlrParserResult old = internalLookup.lookup(EmbeddedAntlrParserResult.class);
                if (old != null && res != old) {
                    content.add(res);
                    content.remove(old);
                } else {
                    content.add(res);
                }
                if (old != null) {
                    Debug.message("replacing " + old.proxy().loggingInfo()
                            + " with " + res.proxy().loggingInfo(), old.proxy()::toString);
                }
                syntaxModel.update(newProxy);
                updateBreadcrumb(editorPane.getCaret(), newProxy);
            });
        });
        enqueueRehighlighting();
    }

    @Override
    public void addNotify() {
        super.addNotify();
//        if (initialAdd) {
        doNotifyShowing();
//        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        initialAdd = false;
        notifyHidden();
    }

    private boolean showing;

    public void notifyShowing() {
        EventQueue.invokeLater(this::requestFocus);
        if (initialAdd) {
            return;
        }
        doNotifyShowing();
    }

    private void doNotifyShowing() {
        if (showing) {
            return;
        }
        // Sync the colorings JList with the stored list of colorings
        updateColoringsList();
        // Listen for changes in it
        colorings.addChangeListener(this);
        colorings.addPropertyChangeListener(this);
        if (rulesList.getSelectedIndex() < 0 && rulesList.getModel().getSize() > 0) {
            rulesList.setSelectedIndex(0);
        }
        updateSplitScrollAndCaretPositions();
        AdhocReparseListeners.listen(mimeType, editorPane.getDocument(), this);
        stateChanged(new ChangeEvent(editorPane.getCaret()));
        editorPane.requestFocusInWindow();
        editorPane.getDocument().addDocumentListener(this);
        grammarEditorClone.getDocument().addDocumentListener(this);
        reparseTask.schedule(500);
        showing = true;
    }

    private final UserTask ut = new UserTask() {
        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            Parser.Result res = resultIterator.getParserResult();
            if (res instanceof AdhocParserResult) {
                AdhocParserResult ahpr = (AdhocParserResult) res;
                accept(editorPane.getDocument(), ahpr.result());
            }
        }
    };

    Future<Void> lastFuture;

    void reallyReparse() {
        if (lastFuture != null && !lastFuture.isDone()) {
            lastFuture.cancel(true);
            lastFuture = null;
        }
        try {
            lastFuture = ParserManager.parseWhenScanFinished(Collections.singleton(Source.create(editorPane.getDocument())), ut);
        } catch (ParseException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    void forceReparse() {
        reparseTask.schedule(500);
    }

    public void notifyHidden() {
        if (!showing) {
            return;
        }
        showing = false;
        Future<Void> lastFuture = this.lastFuture;
        if (lastFuture != null && !lastFuture.isDone()) {
            lastFuture.cancel(true);
        }
        if (reparseTask != null) {
            this.reparseTask.cancel();
        }
        if (updateOutputWindowTask != null) {
            this.updateOutputWindowTask.cancel();
        }
        AdhocReparseListeners.unlisten(mimeType, editorPane.getDocument(), this);
        colorings.removeChangeListener(this);
        colorings.removePropertyChangeListener(this);
        split.removePropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
        editorPane.getCaret().removeChangeListener(this);
        editorPane.getDocument().removeDocumentListener(this);
        grammarEditorClone.getDocument().removeDocumentListener(this);
        EmbeddedAntlrParserResult res = internalLookup.lookup(EmbeddedAntlrParserResult.class);
        // Holding the parser result can be a fairly big leak, and this component will
        // be held until the window system serializes the component to disk
        if (res != null) {
            content.remove(res);
        }
    }

    private void positionCaret(Caret caret, int pos, Document doc) {
        try {
            // The unbelievable lengths the editor api makes one go through to
            // simply set the caret position
            Position position = doc.createPosition(pos);
            if (caret instanceof EditorCaret) {
                EditorCaret ec = (EditorCaret) caret;
                ec.moveCarets((CaretMoveContext cmc) -> {
                    CaretInfo info = ec.getLastCaret();
                    cmc.setDotAndMark(info, position, Position.Bias.Forward, position, Position.Bias.Forward);
                });
            } else {
                caret.setDot(pos);
            }
        } catch (BadLocationException ex) {
            LOG.log(Level.INFO, null, ex);
        }
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    private volatile boolean haveGrammarChange;

    private void docChanged(boolean fromGrammarDoc) {
        haveGrammarChange = fromGrammarDoc;
        if (fromGrammarDoc) {
            rulesList.repaint(2000);
        }
//        enqueueRehighlighting();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        docChanged(e.getDocument() == grammarEditorClone.getDocument());
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        docChanged(e.getDocument() == grammarEditorClone.getDocument());
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
//        docChanged(e.getDocument() == grammarEditorClone.getDocument());
    }

    void enqueueRehighlighting() {
        if (triggerRerunHighlighters != null) {
            triggerRerunHighlighters.schedule(400);
            return;
        }
        Object trigger = editorPane.getClientProperty("trigger");
        if (trigger instanceof Runnable) {
            // see AdhocHighlightLayerFactory - we need this
            // to trigger a re-highlighting because highlights were
            // edited when the document was not
            // XXX could fetch the colorings object and listen on
            // it directly
            triggerRerunHighlighters = RequestProcessor.getDefault()
                    .create(new HLTrigger((Runnable) trigger));
            triggerRerunHighlighters.schedule(500);
        }
    }

    class HLTrigger implements Runnable {

        private final Runnable realTrigger;

        public HLTrigger(Runnable realTrigger) {
            this.realTrigger = realTrigger;
        }

        @Override
        public void run() {
            boolean hadGrammarChange = haveGrammarChange;
            if (hadGrammarChange) {
                haveGrammarChange = false;
                forceReparse();
            }
            realTrigger.run();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof JSplitPane
                && JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(
                        evt.getPropertyName())) {
            splitPaneDividerLocationUpdated((JSplitPane) evt.getSource(), (int) evt.getNewValue());
        } else if (evt.getSource() instanceof JScrollBar) {

        } else if (evt.getSource() instanceof AdhocColorings) {
            enqueueRehighlighting();
        }
    }

    private void updateBreadcrumb(Caret caret) {
        if (caret.getMark() != caret.getDot()) {
            breadcrumb.cell().withText(" ");
            return;
        }
        if (editorPane.getDocument().getLength() > 0) {
            EventQueue.invokeLater(() -> {
                EmbeddedAntlrParserResult res = lookup.lookup(EmbeddedAntlrParserResult.class);
                ParseTreeProxy prx
                        = res == null ? null : res.proxy();
                if (prx != null) {
                    updateBreadcrumb(caret, prx);
                    updateErrors(res);
                }
            });
        }
    }

    private void updateErrors(EmbeddedAntlrParserResult prx) {
        if (updateOutputWindowTask == null) {
            updateOutputWindowTask = asyncUpdateOutputWindowPool.create(outputWindowUpdaterRunnable);
        }
        outputWindowUpdaterRunnable.update(prx);
        updateOutputWindowTask.schedule(300);
    }

    private void updateBreadcrumb(Caret caret, ParseTreeProxy prx) {
//        StringBuilder sb = new StringBuilder();
        AntlrProxies.ProxyToken tok = prx.tokenAtPosition(caret.getDot());
        if (tok != null) {
            stringifier.configureTextCell(breadcrumb, prx, tok, rulesList);
            List<ParseTreeElement> referenceChain = prx.referencedBy(tok);
            if (!referenceChain.isEmpty()) {
                ParseTreeElement rule = referenceChain.get(referenceChain.size() - 1);
                int ix = syntaxModel.select(rule);
                if (ix >= 0) {
                    Scroller.get(syntaxTreeList).beginScroll(syntaxTreeList, ix);
                }
                if (referenceChain.size() > 1) {
                    rule = referenceChain.get(referenceChain.size() - 2);
                    String ruleName = rule.name();
                    for (int i = 0; i < rulesList.getModel().getSize(); i++) {
                        String el = rulesList.getModel().getElementAt(i);
                        if (ruleName.equals(el) || el.contains(">" + ruleName + "<")) {
                            rulesList.setSelectionInterval(i, i);
                            Scroller.get(rulesList).beginScroll(rulesList, i);
                            break;
                        }
                    }
                }
            }
        }
        // Updating the stringifier may change what background colors are
        // used for some cells, so repaint it
        rulesList.repaint();
    }

    static Color color(String s, Color fallback) {
        Color result = UIManager.getColor(s);
        return result == null ? fallback : result;
    }

    Dimension origPosition;

    void updateSplitScrollAndCaretPositions() {
        if (initialAdd) {
            // Until the first layout has happened, which will be after the
            // event queue cycle that calls addNotify() completes, it is useless
            // to set the split location, because the UI delegate will change it
            // So use invokeLater to get out of our own way here.
            EventQueue.invokeLater(() -> {
                DataObject dob = getLookup().lookup(DataObject.class);
                if (dob != null) {
                    double pos = loadOriginalDividerLocation(DIVIDER_LOCATION_FILE_ATTRIBUTE, dob);
                    double lsPos = loadOriginalDividerLocation(SIDE_DIVIDER_LOCATION_FILE_ATTRIBUTE, dob);
                    split.setDividerLocation(pos);
                    listsSplit.setDividerLocation(lsPos);
                    split.addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
                    listsSplit.addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
                    listsSplit.addComponentListener(new CL());
                    int editorCaret = loadPosition(SAMPLE_EDITOR_CARET_POSITION_FILE_ATTRIBUTE, dob);
                    if (editorCaret > 0) {
                        int length = editorPane.getDocument().getLength();
                        if (length > editorCaret) {
                            positionCaret(editorPane.getCaret(), editorCaret, editorPane.getDocument());
                        }
                    }
                    int grammarCaret = loadPosition(GRAMMAR_EDITOR_CARET_POSITION_FILE_ATTRIBUTE, dob);
                    if (grammarCaret > 0) {
                        int length = grammarEditorClone.getDocument().getLength();
                        if (length > grammarCaret) {
                            positionCaret(grammarEditorClone.getCaret(), grammarCaret, grammarEditorClone.getDocument());
                        }
                    }
                    if (sampleScroll != null) {
                        int scrollTo = loadPosition(EDITOR_SCROLL_POSITION, dob);
                        if (scrollTo > 0) {
                            BoundedRangeModel bmr = sampleScroll.getVerticalScrollBar().getModel();
                            if (scrollTo > bmr.getMinimum() && scrollTo < bmr.getMaximum()) {
                                bmr.setValue(scrollTo);
                            }
                        }
                    }
                    if (grammarScroll != null) {
                        int scrollTo = loadPosition(GRAMMAR_SCROLL_POSITION, dob);
                        if (scrollTo > 0) {
                            BoundedRangeModel bmr = grammarScroll.getVerticalScrollBar().getModel();
                            if (scrollTo > bmr.getMinimum() && scrollTo < bmr.getMaximum()) {
                                bmr.setValue(scrollTo);
                            }
                        }
                    }
                }
            });
        }
    }

    private Dimension previousSideComponentSize;

    @Override
    public void doLayout() {
        // If we want to honor the previously saved size so the UI doesn't jump once
        // the model loads and the rules tree needs more width, there is no nice way
        // to do that with BorderLayout.  So, a clever simulacrum, and it can still
        // be used to compute the preferred size.
        if (previousSideComponentSize == null) {
            DataObject dob = getLookup().lookup(DataObject.class);
            if (dob != null) {
                previousSideComponentSize = loadOriginalLeftSideSize(dob);
                if (previousSideComponentSize == null) {
                    previousSideComponentSize = new Dimension(0, 0);
                }
            } else {
                previousSideComponentSize = new Dimension(0, 0);
            }
        }
        Dimension leftSideSize;
        if (syntaxTreeList.getModel().getSize() == 0 && previousSideComponentSize.width > 0 && previousSideComponentSize.height > 0) {
            leftSideSize = previousSideComponentSize;
        } else {
            leftSideSize = listsSplit.getPreferredSize();
        }
        Dimension topSize = customizer.getPreferredSize();
        Dimension bottomSize = breadcrumb.getPreferredSize();
//        Dimension centerSize = split.getPreferredSize();

        Insets ins = getInsets();
        customizer.setBounds(ins.left, ins.top, getWidth() - (ins.left + ins.right), topSize.height);
        listsSplit.setBounds(getWidth() - (ins.right + leftSideSize.width), ins.top + topSize.height, leftSideSize.width, getHeight() - (ins.top + ins.bottom + topSize.height));
        breadcrumb.setBounds(ins.left, getHeight() - (ins.bottom + bottomSize.height), getWidth() - (ins.left + ins.right + leftSideSize.width), bottomSize.height);
        split.setBounds(ins.left,
                ins.top + topSize.height,
                getWidth() - (ins.left + ins.right + leftSideSize.width),
                getHeight() - (ins.top + ins.bottom + topSize.height + bottomSize.height));

//        super.doLayout(); //To change body of generated methods, choose Tools | Templates.
    }

    class CL extends ComponentAdapter {

        @Override
        public void componentResized(ComponentEvent e) {
            if (isShowing()) {
                Dimension d = e.getComponent().getSize();
                if (d.width >= 75 && d.height >= 100) {
                    saveComponentSize(SIDE_SPLIT_SIZE_FILE_ATTRIBUTE, d.width, d.height);
                }
            }
        }
    }

    private void updateColoringsList() {
        String sel = rulesList.getSelectedValue();
        DefaultListModel<String> mdl = new DefaultListModel<>();
        int ix = 0;
        int selectedIndex = -1;
        List<String> keys = new ArrayList<>(colorings.keys());
        Collections.sort(keys, RULE_COMPARATOR);
        for (String key : keys) {
            if (key.equals(sel)) {
                selectedIndex = ix;
            }
            mdl.addElement(key);
            ix++;
        }
        rulesList.setModel(mdl);
        if (selectedIndex != -1) {
            rulesList.setSelectedIndex(selectedIndex);
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() instanceof Caret && e.getSource() == editorPane.getCaret()) {
            if (!selectingRange) {
                if (isShowing() && showing) {
                    savePosition(SAMPLE_EDITOR_CARET_POSITION_FILE_ATTRIBUTE, ((Caret) e.getSource()).getDot());
                }
                EventQueue.invokeLater(() -> {
                    updateBreadcrumb((Caret) e.getSource());
                });
            }
        } else if (e.getSource() instanceof Caret && grammarEditorClone != null && e.getSource() == grammarEditorClone.getCaret()) {
            Caret caret = (Caret) e.getSource();
            if (isShowing() && showing) {
                savePosition(GRAMMAR_EDITOR_CARET_POSITION_FILE_ATTRIBUTE, caret.getDot());
            }
        } else if (e.getSource() instanceof BoundedRangeModel) {
            if (isShowing() && showing) {
                BoundedRangeModel bmr = (BoundedRangeModel) e.getSource();
                if (!bmr.getValueIsAdjusting()) {
                    int pos = bmr.getValue();
                    if (sampleScroll != null && e.getSource() == sampleScroll.getVerticalScrollBar().getModel()) {
                        savePosition(GRAMMAR_SCROLL_POSITION, pos);
                    } else if (grammarScroll != null && e.getSource() == grammarScroll.getVerticalScrollBar().getModel()) {
                        savePosition(EDITOR_SCROLL_POSITION, pos);
                    }
                }
            }
            // editorCloneScroll.getVerticalScrollBar().getModel().
        } else {
            // a reparse will fire changes on the parse thread
            // if rules have been added
            Mutex.EVENT.readAccess(this::updateColoringsList);
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        String sel = rulesList.getSelectedValue();
        if (sel != null) {
            customizer.setAdhocColoring(colorings, sel);
        }
    }

    private Dimension loadOriginalLeftSideSize(DataObject sampleFile) {
        Object result = sampleFile.getPrimaryFile().getAttribute(SIDE_SPLIT_SIZE_FILE_ATTRIBUTE + ".width");
        if (result instanceof Number) {
            int width = ((Number) result).intValue();
            result = sampleFile.getPrimaryFile().getAttribute(SIDE_SPLIT_SIZE_FILE_ATTRIBUTE + ".height");
            if (result instanceof Number) {
                return new Dimension(width, ((Number) result).intValue());
            }
        }
        return null;
    }

    private double loadOriginalDividerLocation(String prop, DataObject sampleFile) {
        Object result = sampleFile.getPrimaryFile().getAttribute(prop);
        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        }
        return 0.6825D;
    }

    private int loadPosition(String prop, DataObject sampleFile) {
        Object result = sampleFile.getPrimaryFile().getAttribute(prop);
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        return -1;
    }

    private void splitPaneDividerLocationUpdated(JSplitPane split, int i) {
        if (i <= 0 || !split.isDisplayable() || split.getWidth() == 0 || split.getHeight() == 0) {
            return;
        }
        if (splitLocationSaver == null) {
            splitLocationSaver = new LayoutInfoSaver();
        }
        int height = split.getHeight();
        Insets ins = split.getInsets();
        if (ins != null) {
            height -= ins.top + ins.bottom;
        }
        String attr = split == listsSplit ? SIDE_DIVIDER_LOCATION_FILE_ATTRIBUTE : DIVIDER_LOCATION_FILE_ATTRIBUTE;
        splitLocationSaver.set(attr, i, height);
    }

    private void saveComponentSize(String attr, int width, int height) {
        if (splitLocationSaver == null) {
            splitLocationSaver = new LayoutInfoSaver();
        }
        splitLocationSaver.setSize(attr, width, height);
    }

    private void savePosition(String attr, int position) {
        if (splitLocationSaver == null) {
            splitLocationSaver = new LayoutInfoSaver();
        }
        splitLocationSaver.setPosition(attr, position);
    }

    private final class LayoutInfoSaver implements Runnable {

        RequestProcessor.Task task;
        final Map<String, int[]> splitInfos = new HashMap<>();
        final Map<String, Dimension> compInfos = new HashMap<>();
        final Map<String, Integer> positions = new HashMap<>();
        private static final int DELAY = 5000;

        synchronized void setPosition(String attr, int position) {
            positions.put(attr, position);
            if (task == null) {
                task = asyncUpdateOutputWindowPool.create(this);
            }
            task.schedule(DELAY);
        }

        synchronized void setSize(String attr, int width, int height) {
            Dimension d = compInfos.get(attr);
            if (d == null) {
                d = new Dimension(width, height);
                compInfos.put(attr, d);
            }
            if (task == null) {
                task = asyncUpdateOutputWindowPool.create(this);
            }
            task.schedule(DELAY);
        }

        synchronized void set(String attr, int lastPosition, int lastHeight) {
            int[] values = splitInfos.get(attr);
            if (values == null) {
                values = new int[]{lastPosition, lastHeight};
                splitInfos.put(attr, values);
            } else {
                values[0] = lastPosition;
                values[1] = lastHeight;
            }
            if (task == null) {
                task = asyncUpdateOutputWindowPool.create(this);
            }
            task.schedule(DELAY);
        }

        @Override
        public synchronized void run() {
            FileObject file = sampleFileDataObject.getPrimaryFile();
            for (Map.Entry<String, int[]> e : splitInfos.entrySet()) {
                double lastPosition = e.getValue()[0];
                double lastHeight = e.getValue()[1];
                double value = Math.min(0.8D, Math.max(0.2D, lastPosition / lastHeight));
                try {
                    file.setAttribute(e.getKey(), Double.valueOf(value));
                } catch (IOException ex) {
                    LOG.log(Level.WARNING,
                            "Exception saving split '" + e.getKey() + "' position for "
                            + sampleFileDataObject.getPrimaryFile().getPath(), ex);
                }
            }
            splitInfos.clear();
            for (Map.Entry<String, Dimension> e : compInfos.entrySet()) {
                try {
                    file.setAttribute(e.getKey() + ".width", e.getValue().width);
                    file.setAttribute(e.getKey() + ".height", e.getValue().height);
                } catch (IOException ex) {
                    LOG.log(Level.WARNING,
                            "Exception saving split position '" + e.getKey() + "' for "
                            + sampleFileDataObject.getPrimaryFile().getPath(), ex);
                }
            }
            for (Map.Entry<String, Integer> e : positions.entrySet()) {
                try {
                    file.setAttribute(e.getKey(), e.getValue());
                } catch (IOException ex) {
                    LOG.log(Level.WARNING,
                            "Exception saving split position '" + e.getKey() + "' for "
                            + sampleFileDataObject.getPrimaryFile().getPath(), ex);
                }

            }
            compInfos.clear();
        }
    }
}

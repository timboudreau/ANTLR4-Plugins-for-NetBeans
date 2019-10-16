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
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Insets;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPanel;
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
import javax.swing.text.EditorKit;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.AdhocParserResult;
import org.nemesis.antlr.live.language.AdhocReparseListeners;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.debug.api.Debug;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.caret.CaretMoveHandler;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Mutex;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Tim Boudreau
 */
public final class PreviewPanel extends JPanel implements ChangeListener,
        ListSelectionListener, DocumentListener, PropertyChangeListener,
        Lookup.Provider, BiConsumer<Document, EmbeddedAntlrParserResult> {

    static final Comparator<String> RULE_COMPARATOR = new RuleNameComparator();
    private static final java.util.logging.Logger LOG
            = java.util.logging.Logger.getLogger(PreviewPanel.class.getName());

    private final JList<String> rules = new JList<>();
    private final JScrollPane rulesPane = new JScrollPane(rules);
    private final SyntaxTreeListModel syntaxModel = new SyntaxTreeListModel();
    private final JList<SyntaxTreeListModel.ModelEntry> syntaxTreeList
            = syntaxModel.createList();
    private final JScrollPane syntaxTreeScroll = new JScrollPane(syntaxTreeList);
    private final JSplitPane listsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final AdhocColorings colorings;
    private final AdhocColoringPanel customizer = new AdhocColoringPanel();

    private final JEditorPane editorPane;
    private final SimpleHtmlLabel breadcrumb = new SimpleHtmlLabel();
    private final JPanel breadcrumbPanel = new JPanel(new BorderLayout());
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
    private SplitLocationSaver splitLocationSaver;
    private final InstanceContent content = new InstanceContent();
    private final AbstractLookup internalLookup = new AbstractLookup(content);
    private final String mimeType;
    private final JEditorPane grammarEditorClone = new JEditorPane();
    private final DataObject sampleFileDataObject;
    private final Indicator indicator = new Indicator(24);

    @SuppressWarnings("LeakingThisInConstructor")
    public PreviewPanel(final String mimeType, Lookup lookup, DataObject sampleFileDataObject,
            StyledDocument doc, AdhocColorings colorings) {
        this.mimeType = mimeType;
        this.sampleFileDataObject = sampleFileDataObject;
        this.colorings = colorings;
        setLayout(new BorderLayout());
        rules.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Add the selected coloring customizer at the top
        add(customizer, BorderLayout.NORTH);
        // Put the rules list in a scroll pane

        // "border buildup" avoidance
        Border empty = BorderFactory.createEmptyBorder();
        Border line = BorderFactory.createMatteBorder(1, 1, 0, 0,
                color("controlShadow", Color.DARK_GRAY));
        rulesPane.setBorder(line);
        rulesPane.setViewportBorder(empty);

        syntaxTreeScroll.setBorder(line);
        syntaxTreeScroll.setViewportBorder(empty);

        listsSplit.setTopComponent(rulesPane);
        listsSplit.setBottomComponent(syntaxTreeScroll);

        // Add the rules pane
        add(listsSplit, BorderLayout.EAST);
        // Create an editor pane
        editorPane = new JEditorPane();
        // Create a runnable that will run asynchronously to beginScroll the
        // output window after the sample text has been altered or the
        // grammar has
        this.outputWindowUpdaterRunnable
                = new ErrorUpdater(editorPane, new RulePathStringifierImpl());

        // We will include its lookup in our own, so it can be saved
        this.lookup = new ProxyLookup(sampleFileDataObject.getLookup(), internalLookup);
        // Now find the editor kit (should be an AdhocEditorKit) from
        // our mime type
        Lookup lkp = MimeLookup.getLookup(MimePath.parse(mimeType));
        EditorKit kit = lkp.lookup(EditorKit.class);
        // Configure the editor pane to use it
        editorPane.setEditorKit(kit);
        // Open the document and set it on the editor pane
        content.add(doc);
        editorPane.setDocument(doc);
        LOG.log(Level.INFO, "PreviewPanel content type is {0}", editorPane.getContentType());
        // EditorUI gives us line number gutter, error gutter, etc.
        EditorUI editorUI = Utilities.getEditorUI(editorPane);
        if (editorUI != null) {
            // This gives us the line number bar, etc.
            split.setTopComponent(editorUI.getExtComponent());
        } else {
            split.setTopComponent(new JScrollPane(editorPane));
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
            // XXX what is this?
//            JToolBar tb = grammarFileEditorUI.getToolBarComponent();
//            if (tb == null) {
            split.setBottomComponent(grammarFileEditorUI.getExtComponent());
//            } else {
//                JPanel grammarContainer = new JPanel(new BorderLayout());
//                grammarContainer.add(tb, BorderLayout.NORTH);
//                grammarContainer.add(grammarFileEditorUI.getExtComponent(), BorderLayout.CENTER);
//                split.setBottomComponent(grammarContainer);
//            }
        } else {
            split.setBottomComponent(new JScrollPane(grammarEditorClone));
        }
        // The splitter is our central component, showing the sample content in
        // the top and the grammar file in the bottom
        add(split, BorderLayout.CENTER);
        // Listen to the rule list to beginScroll the rule customizer
        rules.getSelectionModel().addListSelectionListener(this);
        rules.setCellRenderer(new RuleCellRenderer(colorings, stringifier::listBackgroundColorFor));
        // Listen for changes to force re-highlighting if necessary

        // More border munging
        breadcrumbPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
        breadcrumb.setMinimumSize(new Dimension(100, 24));
        // The breadcrumb shows the rule path the caret is in
        breadcrumbPanel.add(breadcrumb, BorderLayout.CENTER);
        breadcrumbPanel.add(indicator, BorderLayout.EAST);
        add(breadcrumbPanel, BorderLayout.SOUTH);
        // listen on the caret to beginScroll the breadcrumb
        syntaxModel.listenForClicks(syntaxTreeList, this::proxyTokens, this::onSyntaxTreeClick, () -> selectingRange);
        editorPane.getCaret().addChangeListener(WeakListeners.change(this, editorPane.getCaret()));
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
    void selectRangeInPreviewEditor(int[] range) {
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
                ec.moveCarets(new CaretMoveHandler() {
                    @Override
                    public void moveCarets(CaretMoveContext context) {
                        // This seems to usually run later in the event
                        // queue, so try to preserve the selection the user chose
                        selectingRange = true;
                        try {
                            context.setDotAndMark(info, end, Position.Bias.Forward, begin, Position.Bias.Backward);
                        } finally {
                            selectingRange = false;
                        }
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

    void onSyntaxTreeClick(int[] range) {
        selectRangeInPreviewEditor(range);
    }

    @Override
    public void accept(Document a, EmbeddedAntlrParserResult res) {
        ParseTreeProxy newProxy = res.proxy();
        StatusDisplayer.getDefault().setStatusText("Woo hoo!!! " + (newProxy == null ? "null" : newProxy.loggingInfo()));
        if (!newProxy.mimeType().equals(mimeType)) {
            new Error("WTF? " + newProxy.mimeType() + " but expecting " + mimeType).printStackTrace();
            return;
        }
        Debug.message("preview-new-proxy " + Long.toString(newProxy.id(), 36)
                + " errs " + newProxy.syntaxErrors().size(), newProxy::toString);
        Mutex.EVENT.readAccess(() -> {
            indicator.trigger();
//            Debug.run(this, "preview-update " + mimeType, () -> {
//                StringBuilder sb = new StringBuilder("Mime: " + mimeType).append('\n');
//                sb.append("Proxy mime: ").append(newProxy.mimeType()).append('\n');
//                sb.append("Proxy grammar file: ").append(newProxy.grammarPath());
//                sb.append("Proxy grammar name: ").append(newProxy.grammarName());
//                sb.append("Document: ").append(a).append('\n');
//                sb.append("RunResult").append(res.runResult()).append('\n');
//                sb.append("GrammarHash").append(res.grammarTokensHash()).append('\n');
//                sb.append("Text: ").append(newProxy.text()).append('\n');
//                return "";
//            }, () -> {
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
//            });
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
        if (initialAdd) {
            return;
        }
        doNotifyShowing();
    }

    private EmbeddedAntlrParser parser;

    private void doNotifyShowing() {
        if (showing) {
            return;
        }
        showing = true;
        // Sync the colorings JList with the stored list of colorings
        updateColoringsList();
        // Listen for changes in it
        colorings.addChangeListener(this);
        colorings.addPropertyChangeListener(this);
        if (rules.getSelectedIndex() < 0 && rules.getModel().getSize() > 0) {
            rules.setSelectedIndex(0);
        }
        updateSplitPosition();
        AdhocReparseListeners.listen(mimeType, editorPane.getDocument(), this);
        stateChanged(new ChangeEvent(editorPane.getCaret()));
        editorPane.requestFocusInWindow();
        editorPane.getDocument().addDocumentListener(this);
        grammarEditorClone.getDocument().addDocumentListener(this);
        FileObject grammarFileObject = NbEditorUtilities.getFileObject(grammarEditorClone.getDocument());
        if (grammarFileObject != null) {
//            parser = EmbeddedAntlrParsers.forGrammar("preview-" + sampleFileDataObject, grammarFileObject);
        }
        asyncUpdateOutputWindowPool.post(() -> {
            try {
                ParserManager.parseWhenScanFinished(Collections.singleton(Source.create(editorPane.getDocument())), new UserTask() {
                    @Override
                    public void run(ResultIterator resultIterator) throws Exception {
                        Parser.Result res = resultIterator.getParserResult();
                        if (res instanceof AdhocParserResult) {
                            AdhocParserResult ahpr = (AdhocParserResult) res;
                            accept(editorPane.getDocument(), ahpr.result());
                        }
                    }
                });;
            } catch (ParseException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }

    final RequestProcessor.Task reparseTask = asyncUpdateOutputWindowPool.create(this::reallyReparse);

    void reallyReparse() {
        try {
//            String txt = grammarEditorClone.getText();
//            Debug.runThrowing("preview-reparse-grammar", txt, () -> {
            ParsingUtils.parse(grammarEditorClone.getDocument(), res -> {
                Debug.message("parser-result " + res, res::toString);
                return null;
//                });
            });
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    void forceReparse() {
//        reparseTask.schedule(400);
        /*
            if (parser != null) {
            try {
            System.out.println("FORFCE REPARSE");
            EmbeddedAntlrParserResult res = parser.parse(editorPane.getText());
            AdhocReparseListeners.reparsed(mimeType, editorPane.getDocument(), res);
            accept(getLookup().lookup(DataObject.class).getPrimaryFile(), res);
            } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            }
            }*/
    }

    public void notifyHidden() {
        if (!showing) {
            return;
        }
        showing = false;
        AdhocReparseListeners.unlisten(mimeType, editorPane.getDocument(), this);
        colorings.removeChangeListener(this);
        colorings.removePropertyChangeListener(this);
        split.removePropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
        editorPane.getCaret().removeChangeListener(this);
        editorPane.getDocument().removeDocumentListener(this);
        grammarEditorClone.getDocument().removeDocumentListener(this);
        parser = null;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    private volatile boolean haveGrammarChange;

    private void docChanged(boolean fromGrammarDoc) {
        haveGrammarChange = fromGrammarDoc;
        enqueueRehighlighting();
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
        docChanged(e.getDocument() == grammarEditorClone.getDocument());
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
            System.out.println("TRIGGER");
            realTrigger.run();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof JSplitPane
                && JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(
                        evt.getPropertyName())) {
            splitPaneLocationUpdated((int) evt.getNewValue());
            return;
        }
        enqueueRehighlighting();
    }

    private void updateBreadcrumb(Caret caret) {
        if (caret.getMark() != caret.getDot()) {
            breadcrumb.setText(" ");
        }
        if (editorPane.getDocument().getLength() > 0) {
            EventQueue.invokeLater(() -> {
                EmbeddedAntlrParserResult res = lookup.lookup(EmbeddedAntlrParserResult.class);
                ParseTreeProxy prx
                        = res == null ? null : res.proxy();
                System.out.println("Update breadcrumb has proxy " + (prx != null));
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
        StringBuilder sb = new StringBuilder();
        AntlrProxies.ProxyToken tok = prx.tokenAtPosition(caret.getDot());
        System.out.println("tok at pos; " + tok + " at " + caret.getDot());
        if (tok != null) {
            stringifier.tokenRulePathString(prx, tok, sb, true);
            List<ParseTreeElement> referenceChain = prx.referencedBy(tok); //tok.referencedBy();
            if (!referenceChain.isEmpty()) {
                ParseTreeElement rule = referenceChain.get(referenceChain.size() - 1);
                int ix = syntaxModel.select(rule);
                if (ix >= 0) {
                    Scroller.get(syntaxTreeList).beginScroll(syntaxTreeList, ix);
                }
                if (referenceChain.size() > 1) {
                    rule = referenceChain.get(referenceChain.size() - 2);
                    String ruleName = rule.name();
                    for (int i = 0; i < rules.getModel().getSize(); i++) {
                        String el = rules.getModel().getElementAt(i);
                        if (ruleName.equals(el) || el.contains(">" + ruleName + "<")) {
                            rules.setSelectionInterval(i, i);
                            Scroller.get(rules).beginScroll(rules, i);
                            break;
                        }
                    }
                }
            }
        }
        breadcrumb.setText(sb.toString());
        breadcrumb.invalidate();
        breadcrumb.revalidate();
        breadcrumb.repaint();
        // Updating the stringifier may change what background colors are
        // used for some cells, so repaint it
        rules.repaint();
    }

    static Color color(String s, Color fallback) {
        Color result = UIManager.getColor(s);
        return result == null ? fallback : result;
    }

    void updateSplitPosition() {
        if (initialAdd) {
            // Until the first layout has happened, which will be after the
            // event queue cycle that calls addNotify() completes, it is useless
            // to set the split location, because the UI delegate will change it
            // So use invokeLater to get out of our own way here.
            EventQueue.invokeLater(() -> {
                double pos = loadOriginalDividerLocation(getLookup().lookup(DataObject.class));
                split.setDividerLocation(pos);
                split.addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
            });
        }
    }

    private void updateColoringsList() {
        String sel = rules.getSelectedValue();
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
        rules.setModel(mdl);
        if (selectedIndex != -1) {
            rules.setSelectedIndex(selectedIndex);
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() instanceof Caret) {
            System.out.println("\n\nCARET STATE CHANGE selectingRange " + selectingRange);
            if (!selectingRange) {
                updateBreadcrumb((Caret) e.getSource());
            }
        } else {
            // a reparse will fire changes on the parse thread
            // if rules have been added
            Mutex.EVENT.readAccess(this::updateColoringsList);
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        String sel = rules.getSelectedValue();
        if (sel != null) {
            customizer.setAdhocColoring(colorings, sel);
        }
    }

    private double loadOriginalDividerLocation(DataObject sampleFile) {
        Object result = sampleFile.getPrimaryFile().getAttribute(DIVIDER_LOCATION_FILE_ATTRIBUTE);
        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        }
        return 0.6825D;
    }

    private void splitPaneLocationUpdated(int i) {
        if (i <= 0 || !split.isDisplayable() || split.getWidth() == 0 || split.getHeight() == 0) {
            return;
        }
        if (splitLocationSaver == null) {
            splitLocationSaver = new SplitLocationSaver();
        }
        int height = split.getHeight();
        Insets ins = split.getInsets();
        if (ins != null) {
            height -= ins.top + ins.bottom;
        }
        splitLocationSaver.set(i, height);
    }

    private final class SplitLocationSaver implements Runnable {

        double lastPosition;
        double lastHeight;
        RequestProcessor.Task task;

        void set(int lastPosition, int lastHeight) {
            this.lastPosition = lastPosition;
            this.lastHeight = lastHeight;
            if (task == null) {
                task = asyncUpdateOutputWindowPool.create(this);
            }
            task.schedule(1000);
        }

        @Override
        public void run() {
            double value = Math.min(0.8D, Math.max(0.2D, lastPosition / lastHeight));
            FileObject file = sampleFileDataObject.getPrimaryFile();
            try {
                file.setAttribute(DIVIDER_LOCATION_FILE_ATTRIBUTE, Double.valueOf(value));
            } catch (IOException ex) {
                LOG.log(Level.WARNING,
                        "Exception saving split position for "
                        + sampleFileDataObject.getPrimaryFile().getPath(), ex);
            }
        }
    }
}

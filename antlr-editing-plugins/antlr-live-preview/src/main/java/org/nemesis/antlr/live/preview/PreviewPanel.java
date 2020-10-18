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

import org.nemesis.antlr.live.language.PriorityWakeup;
import com.mastfrog.function.state.Int;
import com.mastfrog.range.Range;
import com.mastfrog.util.strings.Strings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import static javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import static javax.swing.SwingUtilities.getAncestorOfClass;
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
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import static javax.swing.text.Document.StreamDescriptionProperty;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.ParsingUtils;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.language.AdhocLanguageHierarchy;
import org.nemesis.antlr.live.language.AdhocParserResult;
import org.nemesis.antlr.live.language.AdhocReparseListeners;
import org.nemesis.antlr.live.language.UndoRedoProvider;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.language.coloring.AdhocColoringsRegistry;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.TokenAssociated;
import org.nemesis.antlr.live.preview.SyntaxTreeListModel.ModelEntry;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.debug.api.Debug;
import org.nemesis.editor.util.EditorSelectionUtils;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.misc.utils.ActivityPriority;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.swing.Scroller;
import org.nemesis.swing.cell.TextCellLabel;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.caret.CaretInfo;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.editor.BaseKit;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.Utilities;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.awt.Mnemonics;
import org.openide.awt.UndoRedo;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.RequestProcessor.Task;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Tim Boudreau
 */
@Messages({"rulesList=&Rules", "syntaxTree=S&yntax Tree"})
public final class PreviewPanel extends JPanel implements ChangeListener,
        ListSelectionListener, DocumentListener, PropertyChangeListener,
        Lookup.Provider, BiConsumer<Document, EmbeddedAntlrParserResult>,
        FocusListener {

    // File attributes for saving layout information to avoid layout jumps
    // on first open over a file that has been opened in this component
    // in the past
    private static final String DIVIDER_LOCATION_FILE_ATTRIBUTE = "splitPosition";
    private static final String SIDE_DIVIDER_LOCATION_FILE_ATTRIBUTE = "sidebarSplitPosition";
    private static final String SIDE_SPLIT_SIZE_FILE_ATTRIBUTE = "sidebarSize";
    private static final String SAMPLE_EDITOR_CARET_POSITION_FILE_ATTRIBUTE = "sampleEditorCaretPos";
    private static final String GRAMMAR_EDITOR_CARET_POSITION_FILE_ATTRIBUTE = "grammarEditorCaretPos";
    private static final String GRAMMAR_SCROLL_POSITION = "grammarScrollPosition";
    private static final String EDITOR_SCROLL_POSITION = "editorScrollPosition";
    private static final Comparator<String> RULE_COMPARATOR = new RuleNameComparator();
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

    private final JEditorPane editorPane;
    private final TextCellLabel breadcrumb = new TextCellLabel("position");
    private final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private boolean initialAdd = true;
    private final Lookup lookup;
    // A single threaded pool for updating the output window and a few other
    // things
    private final RequestProcessor asyncUpdateOutputWindowPool = new RequestProcessor(
            "antlr-preview-error-update", 1, true);
    // A task for updating the output window with error links
    // if a parse contains errors
    private RequestProcessor.Task updateOutputWindowTask;
    private final ErrorUpdater outputWindowUpdaterRunnable;
    private RequestProcessor.Task triggerRerunHighlighters;
    private final RulePathStringifier stringifier = new RulePathStringifierImpl();
    private LayoutInfoSaver splitLocationSaver;
    private final ReparseState reparseState = new ReparseState();
    private volatile int stateAtDocumentModification = -1;
    private final InstanceContent content = new InstanceContent();
    private final AbstractLookup internalLookup = new AbstractLookup(content);
    private final String mimeType;
    private final JEditorPane grammarEditorClone;
    private final DataObject sampleFileDataObject;
    private final JPanel rulesContainer = new JPanel(new BorderLayout());
    private final JPanel syntaxTreeContainer = new JPanel(new BorderLayout());
    private final JLabel rulesLabel = new JLabel();
    private final JLabel syntaxTreeLabel = new JLabel();
    // A task for reparsing the document
    private final RequestProcessor.Task reparseTask = asyncUpdateOutputWindowPool.create(this::reallyReparse);
    private final RequestProcessor.Task reparseGrammarTask = asyncUpdateOutputWindowPool.create(this::reallyReparseGrammar);
    private final JScrollPane sampleScroll;
    private final JScrollPane grammarScroll;
    private Future<Void> lastFuture;
    private boolean selectingRange;
    private Component lastFocused;
    private boolean showing;
    // For performing layout, we cache the saved last size of the component,
    // as a file attribute, so that on restart with the preview editor focused,
    // we can lay it out ahead of time with the right amount of space reserved,
    // so the layout does not jump once the initial parses are completed
    private Dimension previousSideComponentSize;
    private volatile boolean haveGrammarChange;
    private static final Border underline = BorderFactory.createMatteBorder(0, 0, 1, 0,
            UIManager.getColor("controlDkShadow"));
    private static final Border paddedUnderline = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(5, 5, 5, 0),
            BorderFactory.createCompoundBorder(underline,
                    BorderFactory.createEmptyBorder(5, 0, 5, 0)));
    /**
     * An undo provider that switches its contents between the preview pane and
     * the grammar pane, depending which has focus.
     */
    private final UndoRedoProviderImpl switchingUndoRedo;
    /**
     * We track the hash code of the last parser result to avoid updating the
     * output window on every caret change.
     */
    private int lastEmbeddedParserResultIdHash;
    /**
     * We detach listeners on hide and reattach on show, to quite a few things;
     * ensures that the state is what we think it is.
     */
    private boolean listeningToColoringsDocumentsAndCarets;

    // need to hold a reference, or it will be gc'd and not get added/removed
    // Will not be otherwise used.  This coalesces the SaveCookie from the
    // preview and the grammar if both are modified
    private MetaSaveCookie saveCookie;
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
        editorPane = new ErrorDetectingEditorPane(this::editorPaneIsMisbehaving);
        // We need to make sure we are the VERY FIRST focus listener
        // on the editor pane, so we have swapped the DataObject in the
        // lookup before actions evaluate their enablement state
        editorPane.addFocusListener(this);

        // We have to set the document AFTER creating the editor
        // kit.  This DOES mean a zombie document can exist long
        // enough to get a zombie highlighter attached to it.  The code
        // in AdhocHighightLayerFactory that checks StreamDescriptionProperty
        // for null should catch this and avoid one getting created
        // Now find the editor kit (should be an AdhocEditorKit) from
        // our mime type
        // Configure the editor pane to use it
        editorPane.setEditorKit(kit);
        editorPane.setName("preview");
        // Open the document and set it on the editor pane
        content.add(doc);
        editorPane.setDocument(doc);
        LOG.log(Level.FINE, "PreviewPanel content type is {0}", editorPane.getContentType());
        // EditorUI gives us line number gutter, error gutter, etc.
        EditorUI editorUI = Utilities.getEditorUI(editorPane);

//        editorUI.
        if (editorUI != null) {
            // This gives us the line number bar, etc.
            JComponent sampleEditorUIComponent = editorUI.getExtComponent();
            split.setTopComponent(sampleEditorUIComponent);
            if (sampleEditorUIComponent instanceof JScrollPane) {
                sampleScroll = (JScrollPane) sampleEditorUIComponent;
            } else {
                sampleScroll = findScrollPane(editorUI, sampleEditorUIComponent);
            }
        } else {
            split.setTopComponent(sampleScroll = new JScrollPane(editorPane));
        }
        grammarEditorClone = new JEditorPane();
        grammarEditorClone.setName("preview");
        // Same for the grammar editor - need to make sure we are the VERY FIRST
        // focus listener so we can update our lookup before anything tries to
        // use it to determine action enablement state
        grammarEditorClone.addFocusListener(this);
        EditorCookie grammarFileEditorCookie = lookup.lookup(EditorCookie.class);

        if (grammarFileEditorCookie != null && grammarFileEditorCookie.getOpenedPanes() != null
                && grammarFileEditorCookie.getOpenedPanes().length > 0) {
            JEditorPane grammarFileOriginalEditor = grammarFileEditorCookie.getOpenedPanes()[0];
            BaseKit grammarKit = Utilities.getKit(grammarFileOriginalEditor);
            grammarEditorClone.setEditorKit(grammarKit);
            grammarEditorClone.setDocument(grammarFileOriginalEditor.getDocument());
        } else {
            DataObject dob = lookup.lookup(DataObject.class);
            if (grammarFileEditorCookie == null) {
                grammarFileEditorCookie = dob.getLookup().lookup(EditorCookie.class);
            }
            if (grammarFileEditorCookie != null) {
                String mime = dob.getPrimaryFile().getMIMEType();
                EditorKit ek = MimeLookup.getLookup(mime).lookup(EditorKit.class);
                if (ek != null) {
                    grammarEditorClone.setEditorKit(ek);
                    try {
                        grammarEditorClone.setDocument(grammarFileEditorCookie.openDocument());
                    } catch (Exception | Error ioe) {
                        // If we get here, most likely cause is that an exception was thrown
                        // while initializing the Antlr editor - exceptions when attaching
                        // highlighters can do this
                        LOG.log(Level.WARNING, null, ioe);
                        grammarEditorClone.setText(Strings.toString(ioe));
                    }
                } else {
                    // Antlr module not present?  Huh?
                    grammarEditorClone.setText(Strings.toString(new Exception("No editor cookie "
                            + "and no editor kit for " + mime)));
                }
            }
        }
        // Create our own editor

        EditorUI grammarFileEditorUI = Utilities.getEditorUI(grammarEditorClone);

        split.setOneTouchExpandable(true);
        // This sometimes gets us an invisible component
        if (grammarFileEditorUI != null) {
            JComponent grammarEditorUIComponent = grammarFileEditorUI.getExtComponent();
            split.setBottomComponent(grammarEditorUIComponent);
            if (grammarEditorUIComponent instanceof JScrollPane) {
                grammarScroll = (JScrollPane) grammarEditorUIComponent;
            } else {
                grammarScroll = findScrollPane(grammarFileEditorUI, grammarEditorUIComponent);
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

        UndoRedoProvider grammarUndoRedo = undoRedoFor(lookup);
        UndoRedoProvider editorUndoRedo = undoRedoFor(sampleFileDataObject.getLookup());
        switchingUndoRedo = new UndoRedoProviderImpl(grammarUndoRedo, editorUndoRedo);
        content.add(switchingUndoRedo);

        // We will include its lookup in our own, so it can be saved
        this.lookup = createLookup(content, sampleFileDataObject, sampleFileDataObject, lookup);

        // More border munging
        breadcrumb.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
        // The breadcrumb shows the rule path the caret is in
        add(breadcrumb, BorderLayout.SOUTH);
        // listen on the caret to beginScroll the breadcrumb
        syntaxModel.listenForClicks(syntaxTreeList, this::proxyTokens, this::onSyntaxTreeClick, () -> selectingRange);
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
        syntaxTreeList.addFocusListener(this);
        breadcrumb.addMouseListener(new BreadcrumbClickListener());
        breadcrumb.useFullTextAsToolTip();
        AdhocReparseListeners.listen(mimeType, editorPane.getDocument(), this);
        // Create a runnable that will run asynchronously to beginScroll the
        // output window after the sample text has been altered or the
        // grammar has
        this.outputWindowUpdaterRunnable
                = new ErrorUpdater(editorPane, new RulePathStringifierImpl(),
                        this::parseWithParser);

        lastFocused = editorPane;
        setActionMap(editorPane.getActionMap());
        hackTextComponentsIntoEditorRegistry();
    }

    static Method editorRegistryDotRegister;
    static boolean noRegisterMethod;

    void hackTextComponentsIntoEditorRegistry() {

        // THIS is what keeps error icons in the margin from
        // vanishing forever when the editor is hidden for the
        // first time - it causes HintsUI to ignore ancestor
        // changes and not treat that as an invitation to discard them
        editorPane.putClientProperty("usedByCloneableEditor", true);
        grammarEditorClone.putClientProperty("usedByCloneableEditor", true);
        // We need to do this, otherwise:
        //   - HintsController.setErrors() will show errors only until
        //      the editor loses focus for the first time
        //   - Navigator clicks will switch away from the preview editor
        //     to the main editor, which is irritating behavior
        //   - Some context-dependent popup actions in the editors
        //     won't work
        if (noRegisterMethod) {
            return;
        }
        if (editorRegistryDotRegister == null) {
            try {
                editorRegistryDotRegister = EditorRegistry.class.getDeclaredMethod("register", JTextComponent.class);
                editorRegistryDotRegister.setAccessible(true);
            } catch (NoSuchMethodException | SecurityException ex) {
                noRegisterMethod = true;
                LOG.log(Level.INFO, null, ex);
                return;
            }
        }
        try {
            editorRegistryDotRegister.invoke(null, editorPane);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            LOG.log(Level.INFO, "Hacking component into editor registry", ex);
        }
        try {
            editorRegistryDotRegister.invoke(null, grammarEditorClone);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            LOG.log(Level.INFO, "Hacking component into editor registry", ex);
        }
    }

    private Lookup createLookup(InstanceContent internalContent, DataObject sampleFileDataObject,
            DataObject grammarFileDataObject, Lookup internalLookup) {
        Lookup sampleFileLookup = sampleFileDataObject.getLookup();
        Lookup grammarFileLookup = grammarFileDataObject.getLookup();
        saveCookie = MetaSaveCookie.attach(internalContent, sampleFileLookup, grammarFileLookup);
        sampleFileLookup = Lookups.exclude(sampleFileLookup, SaveCookie.class);
        grammarFileLookup = Lookups.exclude(sampleFileLookup, SaveCookie.class);
        FocusSwitchingProxyLookup mpl = new FocusSwitchingProxyLookup(editorPane,
                sampleFileLookup, grammarEditorClone, internalLookup);
        return mpl;
    }

    @Override
    public void paint(Graphics g) {
        ensureListening();
        super.paint(g);
    }

    RequestProcessor outputThreadPool() {
        return asyncUpdateOutputWindowPool;
    }

    private static JScrollPane findScrollPane(EditorUI ui, Component comp) {
        JTextComponent jtc = ui.getComponent();
        JScrollPane result = (JScrollPane) getAncestorOfClass(JScrollPane.class, jtc);
        if (result != null) {
            return result;
        }
        if (comp instanceof Container) {
            for (Component c : ((Container) comp).getComponents()) {
                if (c instanceof JScrollPane) {
                    return (JScrollPane) c;
                }
            }
        }
        return null;
    }

    Lookup internalLookup() {
        return internalLookup;
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
            if (e.getClickCount() == 1 && !e.isPopupTrigger()) {
                String text = breadcrumb.textAt(e.getPoint());
                if (text != null) {
                    navigateToRuleInGrammarOrImport(text, true);
                    e.consume();
                }
            }
        }
    }

    private void navigateToRule(String ruleName, boolean focus) {
        try {
            Document doc = grammarEditorClone.getDocument();
            Extraction ext = NbAntlrUtils.extractionFor(doc);
            if (ext != null && !ext.isPlaceholder() && !ext.isDisposed()) {
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
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Messages({
        "# {0} - itemName",
        "goTo=Go To {0}"})
    void onSyntaxTreePopupRequested(Component target, int x, int y, ModelEntry rule) {
        if (rule == null || rule.isError()) {
            return;
        }
        ParseTreeProxy prx = currentProxy();
        if (prx == null) {
            return;
        }
        ModelEntry curr = rule;
        Set<String> seen = new HashSet<>();
        JPopupMenu menu = new JPopupMenu();
        int iters = 0;
        while (curr != null && curr.element() != null && !curr.element().isRoot() && menu.getComponentCount() < 7) {
            String name = curr.element().name(prx);
            if (!seen.contains(name) && !curr.isError()) {
                seen.add(name);
                JMenuItem item = new JMenuItem(Bundle.goTo(name));
                ModelEntry en = curr;
                item.addActionListener(ae -> {
                    if (en.isParserRule()) {
                        navigateToRule(en.name(), true);
                    } else if (en.isTerminal()) {
                        navigateToRuleInGrammarOrImport(en.lexerRuleName(), true);
                    }
                });
                menu.add(item);
            }
            curr = syntaxModel.parent(curr);
            if (iters++ >= 14) {
                break;
            }
        }
        menu.show(target, x, y);
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
                navigateToNearestExampleOf(rule, true);
            }
        }

        @Override
        @Messages({
            "# {0} - ruleName",
            "navigateToExample=Go to First Use of {0}",
            "# {0} - ruleName",
            "navigateToRule=Go to Definition of {0}"
        })
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                String rule = rulesList.getSelectedValue();
                e.consume();
                navigateToNearestExampleOf(rule, true);
                navigateToRule(rule, false);
            } else if (e.isPopupTrigger()) {
                mouseReleased(e);
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            mouseReleased(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                Point p = e.getPoint();
                int ix = rulesList.locationToIndex(p);
                if (ix >= 0) {
                    String rule = rulesList.getModel().getElementAt(ix);
                    if (rule != null) {
                        JPopupMenu popup = new JPopupMenu();
                        JMenuItem navExample = new JMenuItem();
                        Mnemonics.setLocalizedText(navExample,
                                Bundle.navigateToExample(rule));
                        navExample.addActionListener((ActionEvent e1) -> {
                            navigateToNearestExampleOf(rule, true);
                        });
                        JMenuItem navRule = new JMenuItem();
                        Mnemonics.setLocalizedText(navRule,
                                Bundle.navigateToRule(rule));
                        navRule.addActionListener((ActionEvent e1) -> {
                            navigateToRuleInGrammarOrImport(rule, true);
                        });
                        popup.add(navExample);
                        popup.add(navRule);
                        popup.show((Component) e.getSource(), p.x, p.y);
                        e.consume();
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void navigateToRuleInGrammarOrImport(String rule, boolean focus) {
        if (rule == null) {
            return;
        }
        EventQueue.invokeLater(() -> {
            // Ensure we're outside the AWT tree lock before we do something that
            // could grab the giant lock that is ParserManager
            Extraction ext = NbAntlrUtils.extractionFor(grammarEditorClone.getDocument());
            NamedSemanticRegions<RuleTypes> rules = ext.namedRegions(AntlrKeys.RULE_NAMES);
            NamedSemanticRegions<RuleTypes> ruleBounds = ext.namedRegions(AntlrKeys.RULE_BOUNDS);
            NamedSemanticRegion<RuleTypes> region = rules.regionFor(rule);
            NamedSemanticRegion<RuleTypes> bounds = ruleBounds.regionFor(rule);
            if (region != null) {
                // The requested rule is in the document we are showing - use the preview
                // editor
                Int offset = Int.of(region.end());
                Document doc = grammarEditorClone.getDocument();
                doc.render(() -> {
                    Segment seg = new Segment();
                    try {
                        doc.getText(bounds.start(), bounds.end());
                        int ix = seg.toString().indexOf(':');
                        if (ix > 0) {
                            while (ix + 1 < seg.length()) {
                                ix++;
                                if (!Character.isWhitespace(seg.charAt(ix))) {
                                    break;
                                }
                            }
                            offset.set(bounds.start() + ix);
                        }
                        if (focus) {
                            grammarEditorClone.requestFocus();
                        }
                    } catch (BadLocationException ex) {
                        LOG.log(Level.INFO, "Navigating to " + rule, ex);
                    }
                });
                int charPos = offset.getAsInt();
                this.positionCaret(grammarEditorClone.getCaret(), charPos, doc);
                centerRect(grammarEditorClone, charPos);
            } else {
                Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved = ext.resolveAll(AntlrKeys.RULE_NAME_REFERENCES);
                if (resolved != null && resolved.hasResolved()) {
                    SemanticRegions<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes>> att
                            = resolved.attributed();

                    SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes>> found
                            = att.find(key -> rule.equals(key.name()));

                    if (found != null) {
                        GrammarSource<?> src = found.key().attributedTo().source();
                        Document doc = src.lookupOrDefault(Document.class, () -> {
                            FileObject fo = src.lookupOrDefault(FileObject.class, () -> null);
                            if (fo != null) {
                                try {
                                    DataObject dob = DataObject.find(fo);
                                    EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                                    if (ck == null) {
                                        return null;
                                    }
                                    ck.open();
                                    return ck.openDocument();
                                } catch (IOException ex) {
                                    LOG.log(Level.INFO, null, ex);
                                    return null;
                                }
                            }
                            return null;
                        });
                        if (doc != null) {
                            try {
                                EditorSelectionUtils.openAndSelectRange(doc, found);
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    }
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void centerRect(JTextComponent comp, int charOffset) {
        EditorSelectionUtils.centerTextRegion(comp, Range.ofCoordinates(Math.max(0, charOffset),
                charOffset));
    }

    private void navigateToNearestExampleOf(String rule, boolean focus) {
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
            if (tok == null) {
                // broken source
                return;
            }
            int ix = tok.getTokenIndex();
            List<ProxyToken> all = ptp.tokens();
            for (int i = ix + 1; i < all.size(); i++) {
                ProxyToken next = all.get(i);
                ProxyTokenType type = ptp.tokenTypeForInt(next.getType());
                if (type.name().equals(rule)) {
                    selectRangeInPreviewEditor(next.getStartIndex(), next.getEndIndex());
                    if (focus) {
                        editorPane.requestFocus();
                    }
                    return;
                }
            }
            for (int i = ix; i >= 0; i--) {
                ProxyToken prev = all.get(i);
                ProxyTokenType type = ptp.tokenTypeForInt(prev.getType());
                if (type.name().equals(rule)) {
                    selectRangeInPreviewEditor(prev.getStartIndex(), prev.getEndIndex());
                    if (focus) {
                        editorPane.requestFocus();
                    }
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
                if (el.name(ptp).equals(rule)) {
                    List<ProxyToken> toks = new ArrayList<>(20);
                    Collections.sort(toks);
                    allElements(el, toks, ptp);
                    if (!toks.isEmpty()) {
                        ProxyToken first = toks.get(0);
                        ProxyToken last = toks.get(toks.size() - 1);
                        selectRangeInPreviewEditor(first.getStartIndex(), last.getEndIndex());
                        if (focus) {
                            editorPane.requestFocus();
                        }
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

    @Override
    public String toString() {
        return "PreviewPanel(" + editorPane.getDocument()
                .getProperty(StreamDescriptionProperty) + " - displayable "
                + isDisplayable() + " showing " + isShowing() + ")";
    }

    // ensure no priority wakeups too soon
    private long lastWakeup = System.currentTimeMillis() + 30000;

    @Override
    public void focusGained(FocusEvent e) {
        lastFocused = e.getComponent();
        if (e.getComponent() == grammarEditorClone) {
            switchingUndoRedo.setGrammar(true);
            setActionMap(grammarEditorClone.getActionMap());
        } else if (e.getComponent() == editorPane) {
            switchingUndoRedo.setGrammar(false);
            setActionMap(editorPane.getActionMap());
        }
        // Try to get ahead of any keystrokes or reparse requests from
        // any other components by touching the JFS, causing it to be
        // rebuilt if it was killed due to inactivity, and if so,
        // immediately forcing full parses of both the grammar and the
        // sample text
        long now = System.currentTimeMillis();
        long sinceLastWakeup = now - lastWakeup;
        // Only do this if we may be approaching JFS expiration
        if (sinceLastWakeup > RebuildSubscriptions.JFS_EXPIRATION / 2) {
            lastWakeup = now;
            PriorityWakeup.runImmediately(() -> {
                if (RebuildSubscriptions.touched(grammarEditorClone.getDocument())) {
                    reallyReparseGrammar();
                    // Get out of the way of events generated by the grammar
                    // reparse
                    EventQueue.invokeLater(() -> {
                        PriorityWakeup.runImmediately(this::reallyReparse);
                    });

                }
            });
            Thread.yield();
        }
    }

    @Override
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
                Position begin = NbDocument.createPosition(editorPane.getDocument(),
                        endPoint, Position.Bias.Backward);
                Position end = NbDocument.createPosition(editorPane.getDocument(),
                        range[0], Position.Bias.Forward);
                org.netbeans.api.editor.caret.CaretInfo info = ec.getLastCaret();
                ec.moveCarets((CaretMoveContext context) -> {
                    // This seems to usually run later in the event
                    // queue, so try to preserve the selection the user chose
                    selectingRange = true;
                    try {
                        context.setDotAndMark(info, end, Position.Bias.Forward, begin,
                                Position.Bias.Backward);
                    } finally {
                        selectingRange = false;
                    }
                });
            } else {
                editorPane.setSelectionStart(Math.min(range[0], range[1] - 1));
                editorPane.setSelectionEnd(range[0]);
            }
            EditorSelectionUtils.centerTextRegion(editorPane,
                    Range.ofCoordinates(range[0], range[1]));
            editorPane.requestFocusInWindow();
        } catch (BadLocationException ex) {
            LOG.log(Level.INFO, null, ex);
        } finally {
            selectingRange = false;
        }
    }

    void onSyntaxTreeClick(int clickCount, SyntaxTreeListModel.ModelEntry entry, int start, int end) {
        selectRangeInPreviewEditor(new int[]{start, end});
        if (clickCount == 2) {
            navigateToRule(entry.name(), false);
        }
    }

    private boolean docPropertySet;

    @Override
    public void accept(Document a, EmbeddedAntlrParserResult res) {
        ParseTreeProxy newProxy = res.proxy();
        if (!docPropertySet && !newProxy.isUnparsed()) {
            a.putProperty("isLexer", newProxy.isLexerGrammar());
        }
        if (!newProxy.mimeType().equals(mimeType)) {
            LOG.log(Level.INFO, a.toString(), new Error("WTF? "
                    + newProxy.mimeType() + " but expecting " + mimeType));
            return;
        }
        if (a != editorPane.getDocument()) {
            LOG.log(Level.WARNING, "Passed document for {0} Expected {1}",
                    new Object[]{NbEditorUtilities.getFileObject(a),
                        sampleFileDataObject.getPrimaryFile()});
            return;
        }
        LOG.log(Level.FINE, "Preview receives new embedded result for {0}"
                + " grammar hash {1} preview hash {2} proxy grammar tokens hash {3}"
                + " token names checksum {4}",
                new Object[]{res.grammarName(), res.grammarTokensHash(),
                    (res.proxy() == null ? null : res.proxy().tokenSequenceHash()),
                    (res.proxy() == null ? null : res.proxy().grammarTokensHash()),
                    (res.proxy() == null ? 0 : res.proxy().tokenNamesChecksum())
                });
        Debug.message("preview-new-proxy " + Long.toString(newProxy.id(), 36)
                + " errs " + newProxy.syntaxErrors().size(), newProxy::toString);
        Mutex.EVENT.readAccess(() -> {
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
                maybeUpdateErrors(res);
                EmbeddedAntlrParserResult old = internalLookup.lookup(EmbeddedAntlrParserResult.class);
                if (old != null) {
                    if (res.isEquivalent(old)) {
                        LOG.log(Level.FINER, "Embedded result equivalent to previous - do nothing");
                        return;
                    }
                    if (res != old) {
                        content.add(res);
                        content.remove(old);
                    } else {
                        LOG.log(Level.FINER, "Got same parse result we already had");
                        return;
                    }
                } else {
                    content.add(res);
                }
                reparseState.notifyUpdate();
                customizer.indicateActivity();// XXX invisible?
                if (old != null) {
                    Debug.message("replacing " + old.proxy().loggingInfo()
                            + " with " + res.proxy().loggingInfo(), old.proxy()::toString);
                }
                if (AdhocColoringsRegistry.getDefault().ensureAllPresent(newProxy)) {
                    updateColoringsList();
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
        ensureListening();
        doNotifyShowing();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        initialAdd = false;
        notifyHidden();
        ensureNotListening();
    }

    public void notifyShowing() {
        EventQueue.invokeLater(this::requestFocus);
        doNotifyShowing();
    }

    private void doNotifyShowing() {
        if (showing) {
            return;
        }
        // Sync the colorings JList with the stored list of colorings
        updateColoringsList();
        // Listen for changes in it
        if (rulesList.getSelectedIndex() < 0 && rulesList.getModel().getSize() > 0) {
            rulesList.setSelectedIndex(0);
        }
        updateSplitScrollAndCaretPositions();
        stateChanged(new ChangeEvent(editorPane.getCaret()));
        editorPane.requestFocusInWindow();
        showing = true;
    }

    void reallyReparse() {
        Future<?> last = lastFuture;
        if (last != null && !last.isDone()) {
            last.cancel(true);
            lastFuture = null;
        }
        try {
            ActivityPriority.DEFERRABLE.wrapThrowing(() -> {
                lastFuture = ParserManager.parseWhenScanFinished(
                        Collections.singleton(Source.create(editorPane.getDocument())), ut);
            });
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    void reallyReparseGrammar() {
        try {
            ParsingUtils.parse(grammarEditorClone.getDocument());
//            if (!reparseState.wasRefreshed(stateAtDocumentModification)) {
            reparseTextTask.schedule(200);
//            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private void ensureListening() {
        if (listeningToColoringsDocumentsAndCarets) {
            return;
        }
        LOG.log(Level.FINER, "Preview start listening");
        assert EventQueue.isDispatchThread() : "Not on EDT";
        listeningToColoringsDocumentsAndCarets = true;
        colorings.addChangeListener(this);
        colorings.addPropertyChangeListener(this);
        AdhocReparseListeners.listen(mimeType, editorPane.getDocument(), this);
        editorPane.getDocument().addDocumentListener(this);
        editorPane.getCaret().addChangeListener(this);
        grammarEditorClone.getDocument().addDocumentListener(this);
        editorPane.getCaret().addChangeListener(this);
        grammarEditorClone.getCaret().addChangeListener(this);
        reparseTask.schedule(500);
    }

    private void ensureNotListening() {
        if (!listeningToColoringsDocumentsAndCarets) {
            return;
        }
        LOG.log(Level.FINER, "Preview stop listening");
        assert EventQueue.isDispatchThread() : "Not on EDT";
        listeningToColoringsDocumentsAndCarets = false;
        Future<Void> lastFuture = this.lastFuture;
        if (lastFuture != null && !lastFuture.isDone()) {
            lastFuture.cancel(true);
            this.lastFuture = null;
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
        grammarEditorClone.getCaret().removeChangeListener(this);
    }

    void forceReparse() {
        reparseTask.schedule(500);
    }

    public void notifyHidden() {
        if (!showing) {
            return;
        }
        showing = false;
        ensureNotListening();
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

    private void docChanged(boolean fromGrammarDoc) {
        haveGrammarChange = fromGrammarDoc;
        if (fromGrammarDoc) {
            stateAtDocumentModification = reparseState.state();
            // XXX should not be necessary
            reparseGrammarTask.schedule(350);
            reparseTextTask.schedule(750);
            rulesList.repaint(2_000);
        } else {
            enqueueRehighlighting();
//            reparseTask.schedule(350);
            reparseTextTask.schedule(450);
        }
    }

    private final Task reparseTextTask = asyncUpdateOutputWindowPool.create(this::parseWithParser, false);

    private void parseWithParser() {
        try {
            if (reparseState.wasRefreshed(stateAtDocumentModification)) {
//                return;
            }
            CharSequence seq = DocumentUtilities.getText(editorPane.getDocument(),
                    0, editorPane.getDocument().getLength());
            EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mimeType);
            EmbeddedAntlrParserResult result = parser.parse(seq);
            this.accept(editorPane.getDocument(), result);
            syntaxTreeList.repaint(300);
            rulesList.repaint(300);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
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

        HLTrigger(Runnable realTrigger) {
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
        if (editorPane.getDocument().getLength() > 0) {
            EventQueue.invokeLater(() -> {
                EmbeddedAntlrParserResult res = internalLookup.lookup(EmbeddedAntlrParserResult.class);
                boolean shouldUpdateBreadcrumb = caret.getMark() == caret.getDot()
                        && res != null;
                if (!shouldUpdateBreadcrumb) {
                    breadcrumb.cell().withText(" ");
                    rulesList.repaint(300);
                    return;
                }
                if (shouldUpdateBreadcrumb) {
                    updateBreadcrumb(caret, res.proxy());
                }
                if (res != null) {
                    maybeUpdateErrors(res);
                }
            });
        }
    }

    /**
     * Update the error output only if we were really passed a parser result the
     * output window writer has not already been passed.
     *
     * @param res A parser result
     */
    private void maybeUpdateErrors(@NonNull EmbeddedAntlrParserResult res) {
        assert res != null;
        assert EventQueue.isDispatchThread();
        int newHash = res.hashCode();
        if (newHash != lastEmbeddedParserResultIdHash) {
            lastEmbeddedParserResultIdHash = newHash;
            updateErrors(res);
        }
    }

    private void updateErrors(EmbeddedAntlrParserResult result) {
        if (updateOutputWindowTask == null) {
            updateOutputWindowTask = asyncUpdateOutputWindowPool.create(outputWindowUpdaterRunnable);
        }
        if (outputWindowUpdaterRunnable.update(result)) {
            updateOutputWindowTask.schedule(300);
        }
    }

    private void updateBreadcrumb(Caret caret, ParseTreeProxy prx) {
        if (prx == null) {
            return;
        }
        AntlrProxies.ProxyToken tok = prx.tokenAtPosition(caret.getDot());
        if (tok != null) {
            stringifier.configureTextCell(breadcrumb, prx, tok, rulesList);
            List<ParseTreeElement> referenceChain = prx.referencedBy(tok);
            if (!referenceChain.isEmpty()) {
                ParseTreeElement rule = referenceChain.get(referenceChain.size() - 1);
                int ix = syntaxModel.select(rule);
                // One, not zero here, because we don't want to furiously
                // scroll up to the top and back down again if the user exits
                // a method or something
                if (ix >= 1) {
                    Scroller.get(syntaxTreeList).beginScroll(syntaxTreeList, ix);
                }
                if (referenceChain.size() > 1) {
                    rule = referenceChain.get(referenceChain.size() - 2);
                    String ruleName = rule.name(prx);
                    for (int i = 0; i < rulesList.getModel().getSize(); i++) {
                        String el = rulesList.getModel().getElementAt(i);
                        boolean match = ruleName.equals(el) || el.contains(">" + ruleName + "<");
                        if (match) {
                            rulesList.setSelectionInterval(i, i);
                            Scroller.get(rulesList).beginScroll(rulesList, i);
                            break;
                        }
                    }
                }
            } else {
                // A token in lexer grammar mode
                int ix = syntaxModel.select(tok);
                if (ix >= 0) {
                    Scroller.get(syntaxTreeList).beginScroll(syntaxTreeList, ix);
                }
                for (int i = 0; i < rulesList.getModel().getSize(); i++) {
                    String el = rulesList.getModel().getElementAt(i);
                    boolean match = prx.tokenTypeForInt(tok.getType()).name().equals(el);
                    if (match) {
                        rulesList.setSelectionInterval(i, i);
                        Scroller.get(rulesList).beginScroll(rulesList, i);
                        break;
                    }
                }
            }
        }
        // Updating the stringifier may change what background colors are
        // used for some cells, so repaint it
        rulesList.repaint(300);
    }

    static Color color(String s, Color fallback) {
        Color result = UIManager.getColor(s);
        return result == null ? fallback : result;
    }

    private boolean splitAndScrollPositionsInitialized;

    void updateSplitScrollAndCaretPositions() {
        if (initialAdd && !splitAndScrollPositionsInitialized) {
            // Until the first layout has happened, which will be after the
            // event queue cycle that calls addNotify() completes, it is useless
            // to set the split location, because the UI delegate will change it
            // So use invokeLater to get out of our own way here.
            EventQueue.invokeLater(() -> {
                if (sampleFileDataObject != null) {
                    double pos = loadOriginalDividerLocation(DIVIDER_LOCATION_FILE_ATTRIBUTE, sampleFileDataObject);
                    double lsPos = loadOriginalDividerLocation(SIDE_DIVIDER_LOCATION_FILE_ATTRIBUTE, sampleFileDataObject);
                    split.setDividerLocation(pos);
                    listsSplit.setDividerLocation(lsPos);
                    split.addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
                    listsSplit.addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
                    listsSplit.addComponentListener(new CL());
                    int editorCaret = loadPosition(SAMPLE_EDITOR_CARET_POSITION_FILE_ATTRIBUTE, sampleFileDataObject);
                    if (editorCaret > 0) {
                        int length = editorPane.getDocument().getLength();
                        if (length > editorCaret) {
                            positionCaret(editorPane.getCaret(), editorCaret, editorPane.getDocument());
                        }
                    }
                    int grammarCaret = loadPosition(GRAMMAR_EDITOR_CARET_POSITION_FILE_ATTRIBUTE, sampleFileDataObject);
                    if (grammarCaret > 0) {
                        int length = grammarEditorClone.getDocument().getLength();
                        if (length > grammarCaret) {
                            positionCaret(grammarEditorClone.getCaret(), grammarCaret, grammarEditorClone.getDocument());
                        }
                    }
                    if (sampleScroll != null) {
                        int scrollTo = loadPosition(EDITOR_SCROLL_POSITION, sampleFileDataObject);
                        if (scrollTo > 0) {
                            BoundedRangeModel bmr = sampleScroll.getVerticalScrollBar().getModel();
                            if (scrollTo > bmr.getMinimum() && scrollTo < bmr.getMaximum()) {
                                bmr.setValue(scrollTo);
                            }
                        }
                    }
                    if (grammarScroll != null) {
                        int scrollTo = loadPosition(GRAMMAR_SCROLL_POSITION, sampleFileDataObject);
                        if (scrollTo > 0) {
                            BoundedRangeModel bmr = grammarScroll.getVerticalScrollBar().getModel();
                            if (scrollTo > bmr.getMinimum() && scrollTo < bmr.getMaximum()) {
                                bmr.setValue(scrollTo);
                            }
                        }
                    }
                    splitAndScrollPositionsInitialized = true;
                }
            });
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension splitPref = split.getPreferredSize();
        Dimension sidePref = listsSplit.getPreferredSize();
        Dimension bc = breadcrumb.getPreferredSize();
        Dimension tool = customizer.getPreferredSize();
        Insets ins = getInsets();
        return new Dimension(
                splitPref.width + sidePref.width + ins.left + ins.right,
                Math.max(splitPref.height, sidePref.height) + bc.height + tool.height + ins.top + ins.bottom);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public void doLayout() {
        // If we want to honor the previously saved size so the UI doesn't jump once
        // the model loads and the rules tree needs more width, there is no nice way
        // to do that with BorderLayout.  So, a clever simulacrum, and it can still
        // be used to compute the preferred size.
        if (previousSideComponentSize == null) {
            if (sampleFileDataObject != null) {
                previousSideComponentSize = loadOriginalLeftSideSize(sampleFileDataObject);
                if (previousSideComponentSize == null) {
                    previousSideComponentSize = new Dimension(0, 0);
                }
            } else {
                previousSideComponentSize = new Dimension(0, 0);
            }
        }
        Dimension leftSideSize;
        int myCurrentWidth = getWidth();
        if (syntaxTreeList.getModel().getSize() == 0 && previousSideComponentSize.width > 0 && previousSideComponentSize.height > 0) {
            leftSideSize = previousSideComponentSize;
        } else {
            leftSideSize = listsSplit.getPreferredSize();
        }
        if (myCurrentWidth > 0) {
            // Ensure that having one really wide element in the
            // parse tree doesn't make the window change sizes
            leftSideSize.width = Math.min(
                    Math.max(40, leftSideSize.width), myCurrentWidth / 3);
        }
        Insets ins = getInsets();
        int fullWidth = getWidth() - (ins.left + ins.right);
        int myCurrentHeight = getHeight();

        Dimension topSize = customizer.getPreferredSize();
        Dimension bottomSize = breadcrumb.getPreferredSize();

        bottomSize.width = Math.min(fullWidth, bottomSize.width);

        customizer.setBounds(
                ins.left,
                ins.top,
                fullWidth,
                topSize.height);

        int editorRight = myCurrentWidth - (ins.right + leftSideSize.width);

        int mainTop = ins.top + topSize.height;
        int mainHeight = myCurrentHeight - (ins.top + ins.bottom + topSize.height + bottomSize.height);

        listsSplit.setBounds(
                editorRight,
                mainTop,
                leftSideSize.width,
                mainHeight);

        breadcrumb.setBounds(
                ins.left,
                myCurrentHeight - (ins.bottom + bottomSize.height),
                fullWidth,
                bottomSize.height);

        split.setBounds(
                ins.left,
                mainTop,
                editorRight,
                mainHeight);
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
                } else {
                    return;
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
        private static final int DELAY = 5_000;

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

    private static UndoRedoProvider undoRedoFor(Lookup lkp) {
        UndoRedoProvider prov = lkp.lookup(UndoRedoProvider.class
        );
        if (prov != null) {
            return prov;
        }
        return new DelegateUndoRedoProvider(lkp);

    }

    class UndoRedoProviderImpl implements UndoRedoProvider {

        private final UndoRedoProvider grammarUndo;
        private final UndoRedoProvider previewUndo;
        private boolean isGrammar;
        private UndoRedo last = UndoRedo.NONE;

        UndoRedoProviderImpl(UndoRedoProvider grammarUndo, UndoRedoProvider previewUndo) {
            this.grammarUndo = grammarUndo;
            this.previewUndo = previewUndo;
        }

        private UndoRedoProvider provider() {
            return isGrammar ? grammarUndo : previewUndo;
        }

        void setGrammar(boolean value) {
            if (value != isGrammar) {
                this.isGrammar = value;
                UndoRedo old = last;
                firePropertyChange("undoRedo", old, last = get());
            }
        }

        @Override
        public UndoRedo get() {
            return provider().get();
        }
    }

    static class DelegateUndoRedoProvider implements UndoRedoProvider {

        private static Method method;
        private final CloneableEditorSupport supp;

        DelegateUndoRedoProvider(Lookup lkp) {
            supp = lkp.lookup(CloneableEditorSupport.class);
            assert supp != null : "Lookup " + lkp + " has no CloneableEditorSupport";
        }

        static synchronized Method method() {
            if (method == null) {
                try {
                    method = CloneableEditorSupport.class.getDeclaredMethod("getUndoRedo");
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException | SecurityException ex) {
                    return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
                }
            }
            return method;
        }

        @Override
        public UndoRedo get() {
            try {
                return (UndoRedo) method().invoke(supp);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
            }
        }
    }

    private void editorPaneIsMisbehaving(Throwable thrown, ErrorDetectingEditorPane pane) {
        // XXX recreate it?  Recreate its view?
        LOG.log(Level.SEVERE, "Misbehaving editor pane", thrown);
        Rectangle r = pane.getVisibleRect();

        pane.setVisible(false);
        Document old = pane.getDocument();
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.putProperty(StreamDescriptionProperty, old.getProperty(StreamDescriptionProperty));
        pane.setDocument(doc);
        EventQueue.invokeLater(() -> {
            pane.setVisible(true);
            pane.setDocument(old);
            pane.scrollRectToVisible(r);
        });
    }

    static class ErrorDetectingEditorPane extends JEditorPane {

        private final BiConsumer<Throwable, ErrorDetectingEditorPane> killMeNow;
        private int fontHeight;
        private int charWidth;
        private Container topLevel;

        public ErrorDetectingEditorPane(BiConsumer<Throwable, ErrorDetectingEditorPane> killMeNow) {
            this.killMeNow = killMeNow;
            setFontHeightWidth(getFont());
        }

        @Override
        public void addNotify() {
            super.addNotify();
            topLevel = getTopLevelAncestor();
        }

        @Override
        public void removeNotify() {
            topLevel = null;
            super.removeNotify();
        }

        public Dimension getPreferredSize() {
            // Ensure the editor doesen't hijack all available
            // width
            // XXX figure out why this is happening
            Dimension result = super.getPreferredSize();
            int topWidth = topLevel.getWidth();
            if (topWidth > 6 && isDisplayable()) {
                result.width = Math.min(result.width, (topWidth / 3) * 2);
            }
            return result;
        }

        public void paint(Graphics g) {
            try {
                super.paint(g);
            } catch (Exception | Error err) {
                killMeNow.accept(err, this);
            }
        }

        @Override
        public void setFont(Font font) {
            super.setFont(font);
            setFontHeightWidth(getFont());
        }

        private void setFontHeightWidth(Font font) {
            FontMetrics metrics = getFontMetrics(font);
            fontHeight = metrics.getHeight();
            charWidth = metrics.charWidth('m');
        }

        /**
         * fix for #38139. returns height of a line for vertical scroll unit or
         * width of a widest char for a horizontal scroll unit
         */
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            switch (orientation) {
                case SwingConstants.VERTICAL:
                    return fontHeight;
                case SwingConstants.HORIZONTAL:
                    return charWidth;
                default:
                    throw new IllegalArgumentException("Invalid orientation: " + orientation);
            }
        }

    }
}

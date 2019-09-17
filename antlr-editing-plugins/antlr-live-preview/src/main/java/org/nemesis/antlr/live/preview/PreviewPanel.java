package org.nemesis.antlr.live.preview;

import com.mastfrog.function.TriConsumer;
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
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.language.AdhocColorings;
import org.nemesis.antlr.live.language.AdhocColoringsRegistry;
import org.nemesis.antlr.live.language.AdhocReparseListeners;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.debug.api.Debug;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.Utilities;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.Mutex;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Tim Boudreau
 */
public final class PreviewPanel extends JPanel implements ChangeListener,
        ListSelectionListener, DocumentListener, PropertyChangeListener,
        Lookup.Provider, TriConsumer<FileObject, GrammarRunResult<?>, AntlrProxies.ParseTreeProxy> {

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

    @SuppressWarnings("LeakingThisInConstructor")
    public PreviewPanel(final String mimeType, Lookup lookup, DataObject sampleFileDataObject, StyledDocument doc) {
        this.mimeType = mimeType;
        this.sampleFileDataObject = sampleFileDataObject;
        setLayout(new BorderLayout());
        // Get the colorings for this grammar's pseudo-mime-type, creating
        // them if necessary
        colorings = AdhocColoringsRegistry.getDefault().get(mimeType);
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
        // Create a runnable that will run asynchronously to update the
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
        grammarEditorClone.setEditorKit((EditorKit) grammarFileOriginalEditor.getEditorKit().clone());
        grammarEditorClone.setDocument(grammarFileOriginalEditor.getDocument());

        EditorUI grammarFileEditorUI = Utilities.getEditorUI(grammarEditorClone);
        split.setOneTouchExpandable(true);
        // This sometimes gets us an invisible component
        if (grammarFileEditorUI != null) {
            split.setBottomComponent(grammarFileEditorUI.getExtComponent());
        } else {
            split.setBottomComponent(new JScrollPane(grammarEditorClone));
        }
        // The splitter is our central component, showing the sample content in
        // the top and the grammar file in the bottom
        add(split, BorderLayout.CENTER);
        // Listen to the rule list to update the rule customizer
        rules.getSelectionModel().addListSelectionListener(this);
        rules.setCellRenderer(new RuleCellRenderer(colorings, stringifier::listBackgroundColorFor));
        // Listen for changes to force re-highlighting if necessary
        editorPane.getDocument().addDocumentListener(this);

        // More border munging
        breadcrumbPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
        breadcrumb.setMinimumSize(new Dimension(100, 24));
        // The breadcrumb shows the rule path the caret is in
        breadcrumbPanel.add(breadcrumb, BorderLayout.CENTER);
        add(breadcrumbPanel, BorderLayout.SOUTH);
        // listen on the caret to update the breadcrumb
        syntaxModel.listenForClicks(syntaxTreeList, this::proxyTokens, this::onSyntaxTreeClick);
    }

    List<ProxyToken> proxyTokens() {
        ParseTreeProxy proxy = getLookup().lookup(ParseTreeProxy.class);
        if (proxy == null) {
            return Collections.emptyList();
        }
        return proxy.tokens();
    }

    void onSyntaxTreeClick(int[] range) {
        editorPane.setSelectionStart(range[0]);
        editorPane.setSelectionEnd(range[1]);
        try {
            Rectangle a = editorPane.getUI().modelToView(editorPane, range[0]);
            Rectangle b = editorPane.getUI().modelToView(editorPane, range[1]);
            a.add(b);
            editorPane.scrollRectToVisible(a);
            editorPane.requestFocusInWindow();
        } catch (BadLocationException ex) {
            LOG.log(Level.INFO, null, ex);
        }
    }

    @Override
    public void apply(FileObject a, GrammarRunResult<?> b, ParseTreeProxy newProxy) {
        StatusDisplayer.getDefault().setStatusText("Woo hoo!!! " + (newProxy == null ? "null" : newProxy.grammarName()));
        System.out.println("\n\n*****************************************************");
        System.out.println("\nPreviewPanel for " + a + " got new proxy \n" + newProxy + "\n\n grr \n" + b);
        System.out.println("\n\n*****************************************************");
        Mutex.EVENT.readAccess(() -> {
            Debug.run(this, "preview-update " + mimeType, () -> {
                StringBuilder sb = new StringBuilder("Mime: " + mimeType).append('\n');
                sb.append("Proxy mime: ").append(newProxy.mimeType()).append('\n');
                sb.append("Proxy grammar file: ").append(newProxy.grammarPath());
                sb.append("Proxy grammar name: ").append(newProxy.grammarName());
                sb.append("Document: ").append(a).append('\n');
                sb.append("RunResult").append(b).append('\n');
                sb.append("Text: ").append(newProxy.text()).append('\n');
                return "";
            }, () -> {
                ParseTreeProxy oldProxy = internalLookup.lookup(ParseTreeProxy.class);
                GrammarRunResult<?> old = internalLookup.lookup(GrammarRunResult.class);
                if (b != null) {
                    content.add(b);
                    if (old != null) {
                        content.remove(old);
                    }
                }
                if (newProxy != null && !newProxy.equals(oldProxy)) {
                    if (!newProxy.mimeType().equals(mimeType)) {
                        new Error("WTF? " + newProxy.mimeType() + " but expecting " + mimeType).printStackTrace();
                    }
                    content.add(newProxy);
                    if (oldProxy != null) {
                        content.remove(oldProxy);
                    }
                    syntaxModel.update(newProxy);
                }
            });
        });
        enqueueRehighlighting();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (initialAdd) {
            doNotifyShowing();
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        initialAdd = false;
    }

    private boolean showing;

    public void notifyShowing() {
        if (initialAdd) {
            return;
        }
        doNotifyShowing();
    }

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
        AdhocReparseListeners.listen(mimeType, this.sampleFileDataObject.getPrimaryFile(), this);
        editorPane.getCaret().addChangeListener(this);
        stateChanged(new ChangeEvent(editorPane.getCaret()));
        editorPane.requestFocusInWindow();
    }

    public void notifyHidden() {
        if (!showing) {
            return;
        }
        showing = false;
        AdhocReparseListeners.unlisten(mimeType, sampleFileDataObject.getPrimaryFile(), this);
        colorings.removeChangeListener(this);
        colorings.removePropertyChangeListener(this);
        split.removePropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
        editorPane.getCaret().removeChangeListener(this);
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    private void docChanged() {
        enqueueRehighlighting();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        docChanged();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        docChanged();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        docChanged();
    }

    void enqueueRehighlighting() {
        if (triggerRerunHighlighters != null) {
            triggerRerunHighlighters.schedule(250);
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
                    .create((Runnable) trigger);
            triggerRerunHighlighters.schedule(250);
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
//        System.out.println("PROPERTY CHANGE " + evt.getSource().getClass().getName() + " " + evt.getPropertyName());
        enqueueRehighlighting();
    }

    private void updateBreadcrumb(Caret caret) {
        if (caret.getMark() != caret.getDot()) {
            breadcrumb.setText(" ");
        }
        if (editorPane.getDocument().getLength() > 0) {
            EventQueue.invokeLater(() -> {
                ParseTreeProxy prx
                        = getLookup().lookup(ParseTreeProxy.class);
                if (prx != null) {
                    updateBreadcrumb(caret, prx);
                }
                GrammarRunResult<?> runResult = getLookup().lookup(GrammarRunResult.class);
                updateErrors(runResult, prx);
            });
        }
    }

    private void updateErrors(GrammarRunResult<?> g, ParseTreeProxy prx) {
        if (updateOutputWindowTask == null) {
            updateOutputWindowTask = asyncUpdateOutputWindowPool.create(outputWindowUpdaterRunnable);
        }
        outputWindowUpdaterRunnable.update(g, prx);
        updateOutputWindowTask.schedule(300);
    }

    private void updateBreadcrumb(Caret caret, ParseTreeProxy prx) {
        StringBuilder sb = new StringBuilder();
        AntlrProxies.ProxyToken tok = prx.tokenAtPosition(caret.getDot());
        if (tok != null) {
            stringifier.tokenRulePathString(prx, tok, sb, true);
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
        initialAdd = false;
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
            updateBreadcrumb((Caret) e.getSource());
        } else {
            System.out.println("StateChanged from " + e.getSource().getClass().getName());
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
            DataObject sampleFileDataObject = getLookup().lookup(DataObject.class);
            if (sampleFileDataObject != null) {
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
}

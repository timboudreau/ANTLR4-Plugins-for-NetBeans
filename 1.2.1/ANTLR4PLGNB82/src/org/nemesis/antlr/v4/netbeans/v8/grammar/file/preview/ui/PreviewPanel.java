package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
import javax.swing.text.Caret;
import static javax.swing.text.Document.StreamDescriptionProperty;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocColorings;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocColoringsRegistry;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.DynamicLanguageSupport;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.Reason;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.SampleFiles;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.Utilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
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
        ListSelectionListener, DocumentListener, Runnable, PropertyChangeListener,
        Lookup.Provider, Consumer<ANTLRv4ParserResult> {

    static final Comparator<String> RULE_COMPARATOR = new RuleNameComparator();
    private static final java.util.logging.Logger LOG
            = java.util.logging.Logger.getLogger(PreviewPanel.class.getName());

    private final JList<String> rules = new JList<>();
    private final AdhocColorings colorings;
    private final AdhocColoringPanel customizer = new AdhocColoringPanel();

    private final RequestProcessor.Task task;
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
    private String previewTextAsOfOnLastDocumentChange;
    private final RulePathStringifier stringifier = new RulePathStringifierImpl();
    private static final String DIVIDER_LOCATION_FILE_ATTRIBUTE = "splitPosition";
    private SplitLocationSaver splitLocationSaver;
    private final InstanceContent content = new InstanceContent();
    private final AbstractLookup internalLookup = new AbstractLookup(content);

    @SuppressWarnings("LeakingThisInConstructor")
    public PreviewPanel(final String mimeType, Lookup lookup) {
        setLayout(new BorderLayout());
        // Get the colorings for this grammar's pseudo-mime-type, creating
        // them if necessary
        colorings = AdhocColoringsRegistry.getDefault().get(mimeType);
        rules.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Add the selected coloring customizer at the top
        add(customizer, BorderLayout.NORTH);
        // Put the rules list in a scroll pane
        JScrollPane rulesPane = new JScrollPane(rules);
        // "border buildup" avoidance
        Border empty = BorderFactory.createEmptyBorder();
        rulesPane.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 0,
                color("controlShadow", Color.DARK_GRAY)));
        rulesPane.setViewportBorder(empty);
        // Add the rules pane
        add(rulesPane, BorderLayout.EAST);
        // Create an editor pane
        editorPane = new JEditorPane();
        // Create a runnable that will run asynchronously to update the
        // output window after the sample text has been altered or the
        // grammar has
        this.outputWindowUpdaterRunnable
                = new ErrorUpdater(editorPane, new RulePathStringifierImpl());

        // Find or create a sample file to work with
        DataObject dob = SampleFiles.sampleFile(mimeType);
        DynamicLanguageSupport.setTextContext(mimeType, new TextSupplier(dob), () -> {
            // We will include its lookup in our own, so it can be saved
            this.lookup = new ProxyLookup(dob.getLookup(), internalLookup);
            // Now find the editor kit (should be an AdhocEditorKit) from
            // our mime type
            Lookup lkp = MimeLookup.getLookup(MimePath.parse(mimeType));
            EditorKit kit = lkp.lookup(EditorKit.class);
            // Configure the editor pane to use it
            editorPane.setEditorKit(kit);

            EditorCookie ck = this.lookup.lookup(EditorCookie.class);
            // Open the document and set it on the editor pane
            StyledDocument doc;
            try {
                doc = ck.openDocument();
                doc.putProperty("mimeType", mimeType);
                doc.putProperty(StreamDescriptionProperty, dob);
                content.add(doc);
                editorPane.setDocument(doc);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            LOG.log(Level.INFO, "PreviewPanel content type is {0}", editorPane.getContentType());
        });
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
        JEditorPane clone = new JEditorPane();
        clone.setEditorKit((EditorKit) grammarFileOriginalEditor.getEditorKit().clone());
        clone.setDocument(grammarFileOriginalEditor.getDocument());

        EditorUI grammarFileEditorUI = Utilities.getEditorUI(clone);
        split.setOneTouchExpandable(true);
        // This sometimes gets us an invisible component
        if (grammarFileEditorUI != null) {
            split.setBottomComponent(grammarFileEditorUI.getExtComponent());
        } else {
            split.setBottomComponent(new JScrollPane(clone));
        }
        // The splitter is our central component, showing the sample content in
        // the top and the grammar file in the bottom
        add(split, BorderLayout.CENTER);
        // Listen to the rule list to update the rule customizer
        rules.getSelectionModel().addListSelectionListener(this);
        rules.setCellRenderer(new RuleCellRenderer(colorings, stringifier::listBackgroundColorFor));
        // Listen for changes to force re-highlighting if necessary
        editorPane.getDocument().addDocumentListener(this);
        // Create a task to be triggered on a resettable delay when the
        // document changes
        task = RequestProcessor.getDefault().create(this);

        // More border munging
        breadcrumbPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
        breadcrumb.setMinimumSize(new Dimension(100, 24));
        // The breadcrumb shows the rule path the caret is in
        breadcrumbPanel.add(breadcrumb, BorderLayout.CENTER);
        add(breadcrumbPanel, BorderLayout.SOUTH);
        // listen on the caret to update the breadcrumb
        editorPane.getCaret().addChangeListener(this);

        // Stores a weak reference - no leak
        NBANTLRv4Parser.notifyOnReparse(clone.getDocument(), this);
    }

    public void accept(ANTLRv4ParserResult res) {
        ANTLRv4ParserResult old = internalLookup.lookup(ANTLRv4ParserResult.class);
        if (old != null) {
            content.remove(old);
        }
        content.add(res);
    }

    static final class TextSupplier implements Supplier<String> {

        private final DataObject dob;

        TextSupplier(DataObject dob) {
            this.dob = dob;
        }

        @Override
        public String get() {
            try {
                return dob.getPrimaryFile().asText();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                return null;
            }
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // Sync the colorings JList with the stored list of colorings
        updateColoringsList();
        // Listen for changes in it
        colorings.addChangeListener(this);
        colorings.addPropertyChangeListener(this);
        if (rules.getSelectedIndex() < 0 && rules.getModel().getSize() > 0) {
            rules.setSelectedIndex(0);
        }
        updateSplitPosition();
    }

    @Override
    public void removeNotify() {
        colorings.removeChangeListener(this);
        colorings.removePropertyChangeListener(this);
        split.removePropertyChangeListener(DIVIDER_LOCATION_PROPERTY, this);
        super.removeNotify();
        initialAdd = false;
    }

    public Lookup getLookup() {
        return lookup;
    }

    private void docChanged() {
        previewTextAsOfOnLastDocumentChange = null;
        task.schedule(1250);
        DataObject dob = this.getLookup().lookup(DataObject.class);
        if (dob != null) {
            dob.setModified(true);
        }
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

    @Override
    public void run() {
        // Grab a snapshot of the text as of the last change, for use in
        // various places
        String[] txt = new String[2];
        editorPane.getDocument().render(() -> {
            txt[0] = editorPane.getText();
            txt[1] = editorPane.getContentType();
        });
        previewTextAsOfOnLastDocumentChange = txt[0];
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof JSplitPane
                && JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(
                        evt.getPropertyName())) {
            splitPaneLocationUpdated((int) evt.getNewValue());
            return;
        }
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
                    .create((Runnable) trigger);
            triggerRerunHighlighters.schedule(400);
        }
    }

    private String getText() {
        return editorPane.getText();
    }

    private void updateBreadcrumb(Caret caret) {
        if (caret.getMark() != caret.getDot()) {
            breadcrumb.setText(" ");
        }
        EventQueue.invokeLater(() -> {
            String text = getText();
            if (text != null && !text.isEmpty()) {
                ParseTreeProxy prx
                        = DynamicLanguageSupport.parseImmediately(editorPane.getContentType(), text, Reason.UPDATE_PREVIEW);
//                    DynamicLanguageSupport.lastParseResult(editorPane.getContentType(), lastText);
                ParseTreeProxy old = internalLookup.lookup(ParseTreeProxy.class);
                if (old != null && !old.equals(prx)) {
                    this.content.remove(old);
                }
                this.content.add(prx);
                if (prx != null) {
                    updateBreadcrumb(text, caret, prx);
                }
                updateErrors(prx);
            }
        });
    }

    private void updateErrors(ParseTreeProxy prx) {
        if (updateOutputWindowTask == null) {
            updateOutputWindowTask = asyncUpdateOutputWindowPool.create(outputWindowUpdaterRunnable);
        }
        outputWindowUpdaterRunnable.proxyRef.set(prx);
        updateOutputWindowTask.schedule(300);
    }

    private void updateBreadcrumb(String text, Caret caret, ParseTreeProxy prx) {
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

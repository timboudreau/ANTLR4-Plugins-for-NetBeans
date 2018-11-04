package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
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
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator.AbstractAntlrNavigatorPanel.parserIcon;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocColorings;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocColoring;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocColoringsRegistry;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.DynamicLanguageSupport;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.SampleFiles;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeElement;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxySyntaxError;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyTokenType;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.RuleNodeTreeElement;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.CompileResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.JavacDiagnostic;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.editor.BaseDocument;
import org.openide.awt.HtmlRenderer;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.FoldHandle;
import org.openide.windows.IOColorLines;
import org.openide.windows.IOColors;
import org.openide.windows.IOColors.OutputType;
import org.openide.windows.IOFolding;
import org.openide.windows.IOProvider;
import org.openide.windows.IOSelect;
import org.openide.windows.IOTab;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;
import org.openide.windows.OutputWriter;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
public final class PreviewPanel extends JPanel implements ChangeListener,
        ListSelectionListener, DocumentListener, Runnable, PropertyChangeListener,
        Lookup.Provider {

    private final JList<String> rules = new JList<>();
    private final AdhocColorings colorings;
    private final AdhocColoringPanel customizer = new AdhocColoringPanel();
    private static final java.util.logging.Logger LOG
            = java.util.logging.Logger.getLogger(PreviewPanel.class.getName());

    private final RequestProcessor.Task task;
    private final JEditorPane editorPane;
    private final SimpleHtmlLabel breadcrumb = new SimpleHtmlLabel();
    private final JPanel breadcrumbPanel = new JPanel(new BorderLayout());
    private final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private boolean initialAdd = true;
    private Lookup lookup = Lookup.EMPTY;
    private Component editorComponent;

    @SuppressWarnings("LeakingThisInConstructor")
    public PreviewPanel(final String mimeType, Lookup lookup) {
        setLayout(new BorderLayout());
        colorings = AdhocColoringsRegistry.getDefault().get(mimeType);
        rules.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(customizer, BorderLayout.NORTH);
        JScrollPane rulesPane = new JScrollPane(rules);
        Border empty = BorderFactory.createEmptyBorder();
        rules.setBorder(empty);
        rulesPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, color("controlShadow", Color.DARK_GRAY)));
        rulesPane.setViewportBorder(empty);
        add(rulesPane, BorderLayout.EAST);
        editorPane = new JEditorPane();
        this.editorComponent = editorPane;
        DynamicLanguageSupport.forceLanguage(mimeType, () -> {
            DataObject dob = SampleFiles.sampleFile(mimeType);

            System.out.println("PREVIEW MIME TYPE " + mimeType);
            this.lookup = dob.getLookup();
            Lookup lkp = MimeLookup.getLookup(MimePath.parse(mimeType));
            EditorKit kit = lkp.lookup(EditorKit.class);
            System.out.println("PREVIEW EDITOR KIT " + kit);
//            Document doc = kit.createDefaultDocument();
            editorPane.setEditorKit(kit);
            
            EditorCookie ck = this.lookup.lookup(EditorCookie.class);
            EditorCookie.Observable ok = this.lookup.lookup(EditorCookie.Observable.class);
            
            System.out.println("PREVIEW EDITORCOOKIE " + ck);
            StyledDocument doc;
            try {
                doc = ck.openDocument();
                doc.putProperty("mimeType", mimeType);
                doc.putProperty(StreamDescriptionProperty, dob);
                editorPane.setDocument(doc);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            LOG.log(Level.INFO, "PreviewPanel content type is {0}", editorPane.getContentType());
        });

        split.setTopComponent(new JScrollPane(editorPane));
        EditorCookie ck = lookup.lookup(EditorCookie.class);
        JEditorPane origEditor = ck.getOpenedPanes()[0];
        JEditorPane clone = new JEditorPane();
        clone.setEditorKit(origEditor.getEditorKit());
        clone.setDocument(origEditor.getDocument());
        split.setBottomComponent(new JScrollPane(clone));
        split.setDividerLocation(0.75);

        add(split, BorderLayout.CENTER);
        rules.getSelectionModel().addListSelectionListener(this);
        rules.setCellRenderer(new ItemCellRenderer());
        editorPane.getDocument().addDocumentListener(this);
        task = RequestProcessor.getDefault().create(this);

        breadcrumbPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
        breadcrumb.setMinimumSize(new Dimension(100, 24));
        breadcrumbPanel.add(breadcrumb, BorderLayout.CENTER);
        add(breadcrumbPanel, BorderLayout.SOUTH);
        editorPane.getCaret().addChangeListener(this);
    }

    public Lookup getLookup() {
        return lookup;
    }

    private void docChanged() {
        lastText = null;
        task.schedule(10000);
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
        String[] txt = new String[2];
        editorPane.getDocument().render(() -> {
            txt[0] = editorPane.getText();
            txt[1] = editorPane.getContentType();
        });
//        NbPreferences.forModule(PreviewPanel.class)
//                .put("sample." + txt[1], txt[0]);
    }

    private RequestProcessor.Task triggerTask;

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (triggerTask != null) {
            triggerTask.schedule(400);
        }
        Object trigger = editorPane.getClientProperty("trigger");
        if (trigger instanceof Runnable) {
            // see AdhocHighlightLayerFactory - we need this
            // to trigger a re-highlighting because highlights were
            // edited when the document was not
            // XXX could fetch the colorings object and listen on
            // it directly
            triggerTask = RequestProcessor.getDefault().create((Runnable) trigger);
            triggerTask.schedule(400);
        }
    }

    private String lastText;
    private boolean firstOutput = true;

    private String getText() {
        if (lastText != null) {
            return lastText;
        }
        lastText = editorPane.getText();
        return lastText;
    }

    private void updateBreadcrumb(Caret caret) {
        if (caret.getMark() != caret.getDot()) {
            breadcrumb.setText(" ");
            return;
        }
        String text = getText();
        if (text != null) {
            ParseTreeProxy prx
                    = DynamicLanguageSupport.parseImmediately(editorPane.getContentType(), text);
//                    DynamicLanguageSupport.lastParseResult(editorPane.getContentType(), lastText);
            if (prx != null) {
                updateBreadcrumb(text, caret, prx);
            }
            updateErrors(prx);
        }
    }

    @Messages({
        "# {0} - The grammar name",
        "io_tab={0} (antlr)",
        "success=Antlr parse successful",
        "unparsed=\tParse failed; code generation or compile unsuccessful.",
        "tip=Shows parse errors from Antlr parsing",
        "generationFailed=\tAntlr generation failed (errors in grammar?)",
        "compileFailed=\tFailed to compile generated parser/lexer/extractor",
        "exception=\tException thrown:",})
    private void updateErrors(ParseTreeProxy prx) {
        if (updaterTask == null) {
            updaterTask = updatePool.create(updater);
        }
        updater.proxyRef.set(prx);
        updaterTask.schedule(300);
    }

    private final RequestProcessor updatePool = new RequestProcessor("antlr-preview-error-update", 1, true);
    private RequestProcessor.Task updaterTask;
    private final ErrorUpdater updater = new ErrorUpdater();
    class ErrorUpdater implements Runnable {
        private AtomicReference<ParseTreeProxy> proxyRef = new AtomicReference<>();

        public void run() {
            ParseTreeProxy px = proxyRef.get();
            if (px != null) {
                bgUpdateErrors(px);
            }
        }
    }

    private void bgUpdateErrors(ParseTreeProxy prx) {
        if (prx == null) {
            return;
        }
        if (Thread.interrupted()) {
            return;
        }
        InputOutput io = IOProvider.getDefault().getIO(Bundle.io_tab(prx.grammarName()), false);
        if (IOTab.isSupported(io)) {
            IOTab.setToolTipText(io, Bundle.tip());
            IOTab.setIcon(io, parserIcon());
        }
        boolean failure = prx.isUnparsed() || !prx.syntaxErrors().isEmpty();
        boolean folds = IOFolding.isSupported(io);
        if (firstOutput && failure) {
            if (IOSelect.isSupported(io)) {
                IOSelect.select(io, EnumSet.of(IOSelect.AdditionalOperation.OPEN,
                        IOSelect.AdditionalOperation.REQUEST_VISIBLE));
            } else {
                io.setOutputVisible(true);
                io.setFocusTaken(true);
            }
            firstOutput = false;
        }
        try (OutputWriter writer = io.getOut()) {
            writer.reset();
            if (prx.isUnparsed()) {
                // XXX get the full result and print compiler diagnostics?
                ioPrint(io, Bundle.unparsed(), OutputType.ERROR);
                GenerateBuildAndRunGrammarResult buildResult = DynamicLanguageSupport.lastBuildResult(editorPane.getContentType(), lastText);
                if (buildResult != null) {
                    boolean wasGenerate = !buildResult.generationResult().isSuccess();
                    if (wasGenerate) {
                        ioPrint(io, Bundle.generationFailed(), OutputType.LOG_DEBUG);
                    } else {
                        boolean wasCompile = buildResult.compileResult().isPresent()
                                && !buildResult.compileResult().get().ok();
                        if (wasCompile) {
                            CompileResult res = buildResult.compileResult().get();
                            if (!res.diagnostics().isEmpty()) {
                                for (JavacDiagnostic diag : res.diagnostics()) {
                                    ioPrint(io, diag.toString(), OutputType.LOG_FAILURE);
                                    writer.println();
                                }
                            }
                        }
                    }
                    if (buildResult.thrown().isPresent()) {
                        ioPrint(io, Bundle.exception(), OutputType.LOG_FAILURE);
                        buildResult.thrown().get().printStackTrace(writer);
                    }
                }
            } else if (prx.syntaxErrors().isEmpty()) {
                ioPrint(io, Bundle.success(), OutputType.LOG_SUCCESS);
            } else {
                FoldHandle fold = null;
                if (folds) {
                    fold = IOFolding.startFold(io, true);
                }
                for (AntlrProxies.ProxySyntaxError e : prx.syntaxErrors()) {
                    ErrOutputListener listener = listenerForError(io, prx, e);
                    assert listener != null;
                    ioPrint(io, e.message(), OutputType.HYPERLINK_IMPORTANT, listener);
                    FoldHandle innerFold = null;
                    if (folds && fold != null) {
                        innerFold = fold.startFold(true);
                    }
                    listener.printDescription(writer);
                    if (innerFold != null) {
                        innerFold.finish();
                    }
                }
                if (fold != null) {
                    fold.finish();
                }
            }
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
        }
    }

    private static void ioPrint(InputOutput io, String s, IOColors.OutputType type, OutputListener l) throws IOException {
        if (IOColors.isSupported(io) && IOColorLines.isSupported(io)) {
            Color c = IOColors.getColor(io, type);
            if (l != null) {
                IOColorLines.println(io, s, l, true, c);
            } else {
                IOColorLines.println(io, s, c);
            }
        } else {
            io.getOut().println(s);
        }
    }

    private static void ioPrint(InputOutput io, String s, IOColors.OutputType type) throws IOException {
        ioPrint(io, s, type, null);
    }

    private ErrOutputListener listenerForError(InputOutput io, ParseTreeProxy prx, ProxySyntaxError e) {
        return new ErrOutputListener(io, prx, e);
    }

    final class ErrOutputListener implements OutputListener {

        private final InputOutput io;

        private final ParseTreeProxy prx;

        private final ProxySyntaxError e;

        public ErrOutputListener(InputOutput io, ParseTreeProxy prx, ProxySyntaxError e) {
            this.io = io;
            this.prx = prx;
            this.e = e;
        }

        private void print(CharSequence sb, IOColors.OutputType type) throws IOException {
            ioPrint(io, sb.toString(), type);
            if (sb instanceof StringBuilder) {
                ((StringBuilder) sb).setLength(0);
            }
        }

        @Messages({
            "# {0} - the line number",
            "# {1} - the character position within the line",
            "lineinfo=\tat {0}:{1}",
            "# {0} - The token type (symbolic, literal, display names and code)",
            "type=\tType: {0}",
            "# {0} - the list of rules this token particpates in",
            "rules=\tRules: {0}",
            "# {0} - the token text",
            "text=\tText: '{0}'"
        })
        void printDescription(OutputWriter out) throws IOException {
            ProxyToken tok = null;
            print(Bundle.lineinfo(Integer.valueOf(e.line()), Integer.valueOf(e.charPositionInLine())), OutputType.LOG_FAILURE);
            if (e.hasFileOffsetsAndTokenIndex()) {
                int ix = e.tokenIndex();
                tok = prx.tokens().get(ix);
            } else if (e.line() >= 0 && e.charPositionInLine() >= 0) {
                tok = prx.tokenAtLinePosition(e.line(), e.charPositionInLine());
            } else {
                return;
            }
            if (tok != null) {
                ProxyTokenType type = prx.tokenTypeForInt(tok.getType());
                ParseTreeElement rule = tok.referencedBy().isEmpty() ? null
                        : tok.referencedBy().get(0);
                print(Bundle.type(type.names()), OutputType.LOG_DEBUG);
                if (rule != null) {
                    StringBuilder sb = new StringBuilder();
                    tokenRulePathString(prx, tok, sb, false);
                    print(Bundle.rules(sb), OutputType.LOG_DEBUG);
                }
                print(Bundle.text(tok.getText()), OutputType.OUTPUT);
            }
        }

        @Override
        public void outputLineAction(OutputEvent oe) {
            boolean activate = false;
            if (e.hasFileOffsetsAndTokenIndex()) {
                int ix = e.tokenIndex();
                if (ix >= 0) {
                    ProxyToken pt = prx.tokens().get(ix);
                    int start = pt.getStartIndex();
                    int end = pt.getEndIndex();
                    editorPane.setSelectionStart(start);
                    editorPane.setSelectionEnd(end);
                    activate = true;
                }
            } else {
                Element lineRoot = ((BaseDocument) editorPane.getDocument()).getParagraphElement(0).getParentElement();
                Element line = lineRoot.getElement(e.line());
                if (line == null) {
                    Toolkit.getDefaultToolkit().beep();
                } else {
                    int docOffset = line.getStartOffset() + e.charPositionInLine();
                    editorPane.setSelectionStart(docOffset);
                    editorPane.setSelectionEnd(docOffset);
                    activate = true;
                }
            }
            if (activate) {
                TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, editorPane);
                if (tc != null) {
                    tc.requestActive();
                    editorPane.requestFocus();
                }
            }
        }

        @Override
        public void outputLineSelected(OutputEvent oe) {
            // do nothing
        }

        @Override
        public void outputLineCleared(OutputEvent oe) {
            // do nothing
        }

    }

    private Color listBackgroundColorFor(String ruleName) {
        Integer val = distances.get(ruleName);
        if (val == null) {
            return null;
        }
        return colorForDistance(val);
    }

    private Color relatedToCaretItemHighlightColor() {
        return new Color(255, 196, 80);
    }

    private Color caretItemHighlightColor() {
        return new Color(180, 180, 255);
    }

    private static final int MAX_HIGHLIGHTABLE_DISTANCE = 7;

    private Color colorForDistance(float dist) {
        if (maxDist == 0) {
            return null;
        }
        if (dist == 0) {
            caretItemHighlightColor();
        }
        int mx = Math.min(MAX_HIGHLIGHTABLE_DISTANCE, maxDist);
        if (dist > MAX_HIGHLIGHTABLE_DISTANCE) {
            return null;
        }
        float alpha = Math.max(1f, dist / (float) mx);
        Color hl = relatedToCaretItemHighlightColor();
        return new Color(hl.getRed(), hl.getGreen(), hl.getBlue(),
                (int) (255 * alpha));
    }

    private static final String DELIM = " &gt; ";
    private static final String TOKEN_DELIM = " | ";

    private Map<String, Integer> distances = new HashMap<>();
    private int maxDist = 1;

    private void updateBreadcrumb(String text, Caret caret, ParseTreeProxy prx) {
        StringBuilder sb = new StringBuilder();
        AntlrProxies.ProxyToken tok = prx.tokenAtPosition(caret.getDot());
        if (tok != null) {
            tokenRulePathString(prx, tok, sb, true);
        }
        breadcrumb.setText(sb.toString());
        breadcrumb.invalidate();
        breadcrumb.revalidate();
        breadcrumb.repaint();
        rules.repaint();
    }

    public void tokenRulePathString(ParseTreeProxy prx, ProxyToken tok, StringBuilder into, boolean html) {
        ProxyTokenType type = prx.tokenTypeForInt(tok.getType());
        if (type != null) {
            if (html) {
                into.append("<b>");
            }
            into.append(type.name());
            if (html) {
                into.append("</b>");
            }
            into.append(TOKEN_DELIM);
        }
        int count = tok.referencedBy().size() - 1;
        maxDist = count + 1;
        distances.clear();
        for (int i = count; i >= 0; i--) { // zeroth will be the token
            ParseTreeElement el = tok.referencedBy().get(i);
            if (el instanceof AntlrProxies.RuleNodeTreeElement) {
                boolean sameSpan = ((RuleNodeTreeElement) el).isSameSpanAsParent();
                if (i != count) {
                    into.append(DELIM);
                }
                if (sameSpan) {
                    if (html) {
                        into.append("<font color=#888888><i>");
                    }
                    into.append(el.name());
                    if (html) {
                        into.append("</i></font>");
                    }
                } else {
                    into.append(el.name());
                }
                distances.put(el.name(), i);
            } else if (el instanceof AntlrProxies.TerminalNodeTreeElement) {
                if (i != count) {
                    into.append(DELIM);
                }
                if (html) {
                    into.append("<font color='#0000cc'><b>'");
                }
                into.append(el.name());
                if (html) {
                    into.append("'</b></font>");
                }
            } else {
                if (i != count) {
                    into.append(DELIM);
                }
                if (html) {
                    String nm = el.name().replaceAll("<", "&lt;")
                            .replaceAll(">", "&gt;");
                    into.append(nm);
                } else {
                    into.append(el.name());
                }
            }
        }
    }

    static Color color(String s, Color fallback) {
        Color result = UIManager.getColor(s);
        return result == null ? fallback : result;
    }

    static final Comparator<String> RULE_COMPARATOR = new Comparator<String>() {
        private boolean isCapitalized(String s) {
            if (s.isEmpty()) {
                return false;
            }
            int len = s.length();
            if (len == 1) {
                return Character.isTitleCase(s.charAt(0));
            }
            return Character.isTitleCase(s.charAt(0))
                    && !Character.isTitleCase(s.charAt(1));
        }

        private boolean isUpperCase(String s) {
            if (s.isEmpty()) {
                return false;
            }
            int len = s.length();
            for (int i = 0; i < len; i++) {
                if (!Character.isTitleCase(s.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int compare(String o1, String o2) {
            boolean title1 = isCapitalized(o1);
            boolean title2 = isCapitalized(o2);
            if (title1 && !title2) {
                return 1;
            } else if (title2 && !title1) {
                return -1;
            }
            boolean ac1 = isUpperCase(o1);
            boolean ac2 = isUpperCase(o2);
            if (ac1 && !ac2) {
                return -1;
            } else if (ac2 && !ac1) {
                return 1;
            }
            return o1.compareTo(o2);
        }
    };

    class ItemCellRenderer implements ListCellRenderer<String> {

//        private final HtmlRenderer.Renderer ren = HtmlRenderer.createRenderer();
        private final HtmlRendererImpl ren = new HtmlRendererImpl();

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            Component result = ren.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            ren.setHtml(true);
            String prefix = "";
            AdhocColoring col = colorings.get(value);
            if (!col.isActive()) {
                prefix += "<font color=#aaaaaa>";
            }
            if (col.isBackgroundColor()) {
                prefix += "<b>";
            }
            if (col.isItalic()) {
                prefix += "<i>";
            }
            Color back = listBackgroundColorFor(value);
            if (back != null) {
                ren.setCellBackground(back);
            } else if (isSelected) {
                ren.setCellBackground(list.getSelectionBackground());
            }
            ren.setText(prefix.isEmpty() ? value : prefix + value);
            ren.setIndent(5);
            return result;
        }
    }

    void updateSplitPosition() {
        if (initialAdd) {
            EventQueue.invokeLater(() -> {
                split.setDividerLocation(0.75);
            });
        }
        initialAdd = false;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        updateColoringsList();
        colorings.addChangeListener(this);
        colorings.addPropertyChangeListener(this);
        if (rules.getSelectedIndex() < 0 && rules.getModel().getSize() > 0) {
            rules.setSelectedIndex(0);
        }
        updateSplitPosition();
    }

    public void removeNotify() {
        colorings.removeChangeListener(this);
        colorings.removePropertyChangeListener(this);
        super.removeNotify();
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

    static final class SimpleHtmlLabel extends JComponent {

        private String text = " ";
        private Dimension cachedSize;

        SimpleHtmlLabel() {
            setOpaque(true);
            setBackground(UIManager.getColor("control"));
            setForeground(UIManager.getColor("textText"));
        }

        public void setText(String text) {
            if (!this.text.equals(text)) {
                cachedSize = null;
                this.text = text;
                invalidate();
                revalidate();
                repaint();
            }
        }

        @Override
        public Dimension getMinimumSize() {
            if (isMinimumSizeSet()) {
                return super.getMinimumSize();
            }
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        public Dimension getPreferredSize() {
            if (cachedSize != null) {
                return cachedSize;
            }
            Graphics2D g = HtmlRendererImpl.scratchGraphics();
            Font font = getFont();
            FontMetrics fm = g.getFontMetrics(font);
            Insets ins = getInsets();
            int y = fm.getMaxAscent();
            int h = fm.getHeight();
            int w = (int) Math.ceil(
                    HtmlRenderer.renderHTML(text, g, 0, y,
                            Integer.MAX_VALUE, h, font, getForeground(), HtmlRenderer.STYLE_TRUNCATE, false)
            );
            if (ins != null) {
                w += ins.left + ins.right;
                h += ins.top + ins.bottom;
            }
            return cachedSize = new Dimension(Math.max(24, w), Math.max(16, h));
        }

        @Override
        public void doLayout() {
            cachedSize = null;
            super.doLayout();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void reshape(int x, int y, int w, int h) {
            cachedSize = null;
            super.reshape(x, y, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (isOpaque() && getBackground() != null) {
                Color c = getBackground();
                g.setColor(c);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            Font font = getFont();
            g.setColor(getForeground());
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics(font);
            int x = 0;
            int y = fm.getMaxAscent();
            int h = getHeight();
            Insets ins = getInsets();
            if (ins != null) {
                x += ins.left;
            }
            if (h > y) {
                y += (h - y) / 2;
            }
            HtmlRenderer.renderHTML(text, g, x, y, getWidth(), h,
                    font, getForeground(), HtmlRenderer.STYLE_TRUNCATE, true);
        }
    }
}

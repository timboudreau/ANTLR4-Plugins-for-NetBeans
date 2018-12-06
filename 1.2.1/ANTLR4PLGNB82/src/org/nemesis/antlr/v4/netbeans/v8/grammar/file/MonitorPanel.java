package org.nemesis.antlr.v4.netbeans.v8.grammar.file;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.MonitorPanel.Attempt.AttemptState;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.CulpritFinder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.ParsedAntlrError;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.CompileResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.JavacDiagnostic;
import org.openide.awt.HtmlRenderer;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Tim Boudreau
 */
public class MonitorPanel extends JPanel implements CulpritFinder.Monitor {

    private final JEditorPane pane = new JEditorPane();
    private final JToolBar toolbar = new JToolBar();
    private final ButtonAction action = new ButtonAction();
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel status = new JLabel();
    private final JLabel amount = new JLabel();
    private final DefaultListModel<Attempt> tried = new DefaultListModel<>();
    private final JList<Attempt> list = new JList<>(tried);
    private final JTextArea successes = new JTextArea(Bundle.placeholder());
    private final JCheckBox stopOnSuccess = new JCheckBox(Bundle.stopOnSuccess(), true);


    @Messages({"attempts=Attempts", //NOI18N
        "placeholder=(rules which, when omitted, result in a successful parse will appear here)",
        "stopOnSuccess=Stop on Success"
    }) //NOI18N
    MonitorPanel() {
        Border emptyBorder = BorderFactory.createEmptyBorder();
        setLayout(new BorderLayout());
        add(BorderLayout.NORTH, toolbar);
        status.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        amount.setBorder(status.getBorder());
        progress.setBorder(status.getBorder());
        stopOnSuccess.setBorder(status.getBorder());

        toolbar.add(action);
        toolbar.add(stopOnSuccess);
        toolbar.add(status);
        toolbar.add(amount);
        toolbar.add(progress);
        pane.setMinimumSize(new Dimension(500, 300));
        pane.setEditable(false);
        pane.setBorder(emptyBorder);
        progress.setMaximumSize(new Dimension(120, 20));
        progress.setPreferredSize(new Dimension(120, 20));
        Font f = UIManager.getFont("controlFont"); //NOI18N
        if (f == null) {
            f = status.getFont();
        }
        pane.setFont(f);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setViewportBorder(emptyBorder);
        list.setCellRenderer(new Ren());
        listScroll.setBorder(BorderFactory.createTitledBorder(Bundle.attempts()));
        add(listScroll, BorderLayout.WEST);
        JScrollPane paneScroll = new JScrollPane(pane);
        paneScroll.setViewportBorder(emptyBorder);
        paneScroll.setBorder(emptyBorder);
        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, paneScroll));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int ix = list.locationToIndex(e.getPoint());
                if (ix >= 0 && ix < tried.size()) {
                    Attempt attempt = tried.get(ix);
                    scrollToTextOffset(attempt.textOffset);
                    pane.requestFocus();
                }
            }
        });
        successes.setBorder(emptyBorder);
        JScrollPane succScroll = new JScrollPane(successes);
        succScroll.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        succScroll.setViewportBorder(emptyBorder);
        succScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        succScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        successes.setEditable(false);
        successes.setBackground(UIManager.getColor("control")); //NOI18N
        successes.setFont(f);
        add(succScroll, BorderLayout.SOUTH);
    }

    @Messages("monitorWindow=Culprit Finder") //NOI18N
    public static MonitorPanel showDialog() {
        JDialog dlg = new JDialog(WindowManager.getDefault().getMainWindow());
        dlg.setMinimumSize(new Dimension(500, 300));
        dlg.setTitle(Bundle.monitorWindow());
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        MonitorPanel pnl = new MonitorPanel();
        dlg.setContentPane(pnl);
        dlg.setModal(false);
        dlg.setAlwaysOnTop(true);
        dlg.pack();
        dlg.setVisible(true);
        return pnl;
    }

    TopComponent dock() {
        if (getParent() instanceof TopComponent) {
            return (TopComponent) getParent();
        }
        boolean oldKeepGoing = keepGoing.get();
        MonitorTC tc = new MonitorTC(this, oldKeepGoing);
        Container w = getTopLevelAncestor();
        if (w instanceof JDialog) {
            ((JDialog) w).setVisible(false);
        }
        tc.open();
        tc.requestActive();
        return tc;
    }

    static final class MonitorTC extends TopComponent {

        private final MonitorPanel pnl;
        private final boolean oldKeepGoing;
        private boolean firstAdd = true;

        MonitorTC(MonitorPanel pnl, boolean oldKeepGoing) {
            this.pnl = pnl;
            setLayout(new BorderLayout());
            add(pnl, BorderLayout.CENTER);
            this.oldKeepGoing = oldKeepGoing;
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (firstAdd) {
                pnl.keepGoing.set(oldKeepGoing);
                firstAdd = false;
            }
        }

        @Override
        public void open() {
            Mode mode = WindowManager.getDefault().findMode("output"); // NOI18N
            if (mode != null) {
                mode.dockInto(this);
            }
            super.open();
        }

        @Override
        protected void componentClosed() {
            super.componentClosed();
            pnl.keepGoing.set(false);
        }

        @Override
        public String getDisplayName() {
            return Bundle.monitorWindow();
        }

        @Override
        public int getPersistenceType() {
            return TopComponent.PERSISTENCE_NEVER;
        }
    }

    private void scrollToTextOffset(int charOffset) {
        try {
            Rectangle r = pane.modelToView(charOffset);
            r.width = 1;
            r.height = 1;
            pane.scrollRectToVisible(r);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private int append(String text) {
        try {
            Document doc = pane.getDocument();
            int length = doc.getLength();
            pane.getDocument().insertString(length, text, null);
            if (!pane.hasFocus()) {
                EventQueue.invokeLater(() -> {
                    scrollToTextOffset(length);
                });
            }
            return length;
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        return -1;
    }

    private String toRuleList(Set<RuleDeclaration> decls) {
        List<RuleDeclaration> all = new ArrayList<>(decls);
        Collections.sort(all);
        StringBuilder sb = new StringBuilder();
        for (RuleDeclaration d : all) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(d.getRuleID());
        }
        return sb.toString();
    }

    @Override
    public void onAttempt(Set<RuleDeclaration> omitted, long attempt, long of) {
        String rules = toRuleList(omitted);
        StringBuilder sb = new StringBuilder();
        sb.append("\n--------------------- OMITTING: ").append(rules).append(" --------------------------\n");
        amount.setText((attempt + 1) + " / " + of);
        sb.append(attempt).append(" / ").append(of);
        int offset = append(sb.toString());
        progress.setIndeterminate(true);
        Attempt currAttempt = new Attempt(rules, offset);
        tried.addElement(currAttempt);
        int loc = tried.size() - 1;
        Rectangle r = list.getCellBounds(loc, loc);
        list.repaint(r);
        list.scrollRectToVisible(r);
    }

    @Override
    public void onCompleted(boolean success, Set<RuleDeclaration> omitted, GenerateBuildAndRunGrammarResult result, Runnable runNext) {
        StringBuilder sb = new StringBuilder(omitted.size() + " rules omitted. ");
        String ruleList = toRuleList(omitted);
        AttemptState state;
        if (!result.generationResult().isSuccess()) {
            if (!result.generationResult().diagnostics().isEmpty()) {
                sb.append("\n\nSource generation errors:\n");
                int ct = 0;
                for (ParsedAntlrError d : result.generationResult().diagnostics()) {
                    if (d.isError()) {
                        ct++;
                        sb.append('\t').append(d).append('\n');
                    }
                }
                if (ct == 0) {
                    for (ParsedAntlrError d : result.generationResult().diagnostics()) {
                        sb.append('\t').append(d).append('\n');
                    }
                }
            }
            success = false;
//        } else {
//            if (!result.generationResult().diagnostics().isEmpty()) {
//                sb.append("\n\nSOURE GENERATION WARNINGS:\n");
//                for (ParsedAntlrError d : result.generationResult().diagnostics()) {
//                    sb.append(d).append('\n');
//                }
//            }
        }

        if (result.compileResult().isPresent()) {
            CompileResult cr = result.compileResult().get();
            if (cr.compileFailed()) {
                state = AttemptState.ERROR;
                sb.append("\nCompile failed:\n");
                for (JavacDiagnostic diag : cr.diagnostics()) {
                    sb.append('\t').append(diag.toString()).append('\n');
                }
            }
        }
        if (result.thrown().isPresent()) {
            state = AttemptState.ERROR;
            sb.append("\nException thrown:\n");
            append(result.thrown().get(), sb);
            sb.append('\n');
        }
        if (result.parseResult().isPresent() && result.parseResult().get().parseTree().isPresent()) {
            ParseTreeProxy px = result.parseResult().get().parseTree().get();
            if (!px.syntaxErrors().isEmpty()) {
                state = AttemptState.FAILURE;
                sb.append("\nParsed with ").append(px.syntaxErrors().size()).append(" syntax errors:\n");
                for (AntlrProxies.ProxySyntaxError err : px.syntaxErrors()) {
                    sb.append('\t').append(err.toString()).append("\n");
                }
                success = false;
            } else {
                if (!px.isUnparsed()) {
                    sb.append("\nParse result - no syntax errors omitting ").append(ruleList).append('\n');
                    state = AttemptState.SUCCESS;
                    String stxt = "";
                    if (!Bundle.placeholder().equals(successes.getText())) {
                        stxt = successes.getText() + " | ";
                    }
                    stxt += ruleList;
                    successes.setText(stxt);
                } else {
                    state = AttemptState.ERROR;
                    sb.append("\nNo parse result returned\n");
                    success = false;
                }
            }
        } else {
            state = AttemptState.FAILURE;
            success = false;
            sb.append("\nNo parse result returned omitting ").append(ruleList);
        }
        StringBuilder sb2 = new StringBuilder(" ");

        sb2.append(state.name()).append(" omitting '").append(ruleList).append("'\n\n");
        sb2.append(sb);
        append(sb2.toString());
        Attempt currAttempt = tried.elementAt(tried.size() - 1);
        currAttempt.setAttemptState(state);
        int loc = tried.size() - 1;
        Rectangle r = list.getCellBounds(loc, loc);
        list.repaint(r);
        list.scrollRectToVisible(r);
        if (success && stopOnSuccess.isSelected()) {
            progress.setIndeterminate(false);
            action.setRunnable(Bundle.go(), runNext);
        } else {
            if (runNext != null) {
                if (keepGoing.get()) {
                    progress.setIndeterminate(true);
                    action.setRunnable(Bundle.stop(), stopRunnable);
                    runNext.run();
                } else {
                    progress.setIndeterminate(false);
                    action.setRunnable(Bundle.keepGoing(), new KGRunnable(runNext));
                    keepGoing.set(true);
                }
            } else {
                progress.setIndeterminate(false);
                action.putValue(Action.NAME, Bundle.done());
            }
        }
        action.setEnabled(runNext != null);
    }

    @Override
    public void removeNotify() {
        keepGoing.set(false);
        super.removeNotify();
    }

    static final class Ren implements ListCellRenderer<Attempt> {

        private final HtmlRenderer.Renderer renderer = HtmlRenderer.createRenderer();

        @Override
        public Component getListCellRendererComponent(JList<? extends Attempt> list, Attempt value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            renderer.setHtml(true);
            renderer.setText(value.state.prefix() + value.ruleList);
            return c;
        }
    }

    static final class Attempt {

        private final String ruleList;
        private AttemptState state = AttemptState.PENDING;
        private final int textOffset;

        public Attempt(String ruleList, int textOffset) {
            this.ruleList = ruleList;
            this.textOffset = textOffset;
        }

        void setAttemptState(AttemptState state) {
            this.state = state;
        }

        enum AttemptState {
            PENDING("#bbbbbb"), //NOI18N
            SUCCESS("#22ee22"), //NOI18N
            FAILURE("#cc2222"), //NOI18N
            ERROR("#ff0000"); //NOI18N
            private final String colorString;

            private AttemptState(String colorString) {
                this.colorString = colorString;
            }

            public String prefix() {
                return "<font color='" + colorString + "'>"; //NOI18N
            }
        }
    }

    private void append(Throwable thrown, StringBuilder sb) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            try (PrintStream ps = new PrintStream(out, true, "UTF-8")) { //NOI18N
                thrown.printStackTrace(ps);
            }
            sb.append(new String(out.toByteArray(), UTF_8));
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
    }

    @Override
    public void onStatus(String status) {
        append(status);
    }
    private AtomicBoolean keepGoing = new AtomicBoolean();

    private class KGRunnable implements Runnable {

        private final Runnable run;

        public KGRunnable(Runnable run) {
            this.run = run;
        }

        @Override
        public void run() {
            if (keepGoing.get()) {
                progress.setIndeterminate(true);
                run.run();
            } else {
                progress.setIndeterminate(false);
                if (action.runnable != run) {
                    action.setRunnable(run);
                }
            }
        }

    }

    private Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            keepGoing.set(false);
        }
    };

    private static class ButtonAction extends AbstractAction {

        private Runnable runnable;

        @Messages({"go=Try Again",
            "keepGoing=Keep Going",
            "stop=Stop",
            "done=Done"
        })
        ButtonAction() {
            putValue(NAME, Bundle.go());
            setEnabled(false);
        }

        void setRunnable(String name, Runnable run) {
            putValue(NAME, name);
            runnable = run;
        }

        void setRunnable(Runnable run) {
            setRunnable(Bundle.go(), run);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setEnabled(false);
            runnable.run();
        }
    }
}

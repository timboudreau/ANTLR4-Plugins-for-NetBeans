package org.nemesis.antlr.v4.netbeans.v8.grammar.file;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.CulpritFinder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.CompileResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.JavacDiagnostic;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
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

    MonitorPanel() {
        setLayout(new BorderLayout());
        add(BorderLayout.NORTH, toolbar);
        add(new JScrollPane(pane), BorderLayout.CENTER);
        toolbar.add(action);
        toolbar.add(status);
        toolbar.add(amount);
        toolbar.add(progress);
        pane.setMinimumSize(new Dimension(500, 300));
        progress.setMaximumSize(new Dimension(80, 20));
        progress.setPreferredSize(new Dimension(80, 20));
        Font f = UIManager.getFont("controlFont");
        if (f == null) {
            f = status.getFont();
        }
        pane.setFont(f);
    }

    public static MonitorPanel showDialog() {
        JDialog dlg = new JDialog(WindowManager.getDefault().getMainWindow());
        MonitorPanel pnl = new MonitorPanel();
        dlg.setContentPane(pnl);
        dlg.setModal(false);
        dlg.setAlwaysOnTop(true);
        dlg.pack();
        dlg.setVisible(true);
        return pnl;
    }

    private void append(String text) {
        try {
            pane.getDocument().insertString(0, text, null);
            pane.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
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
        append("\n-----------------------------\n");

        amount.setText((attempt + 1) + " / " + of);
        append("Trying omitting rules: " + toRuleList(omitted));
        progress.setIndeterminate(true);
    }

    @Override
    public void onCompleted(boolean success, Set<RuleDeclaration> omitted, GenerateBuildAndRunGrammarResult result, Runnable runNext) {
        StringBuilder sb = new StringBuilder();
        sb.append(success ? "SUCCESS" : "FAILURE").append(" omitting ").append(toRuleList(omitted)).append("\n\n");

        if (result.compileResult().isPresent()) {
            CompileResult cr = result.compileResult().get();
            if (cr.compileFailed()) {
                append("Compile failed");
                for (JavacDiagnostic diag : cr.diagnostics()) {
                    append(diag.toString());
                }
            }
        }
        if (result.thrown().isPresent()) {
            sb.append("\nException thrown:\n");
            append(result.thrown().get(), sb);
            sb.append('\n');
        }
        if (result.parseResult().isPresent() && result.parseResult().get().parseTree().isPresent()) {
            ParseTreeProxy px = result.parseResult().get().parseTree().get();
            if (!px.syntaxErrors().isEmpty()) {
                sb.append("PARSED WITH ").append(px.syntaxErrors().size()).append(" SYNTAX ERRORS:\n");
                for (AntlrProxies.ProxySyntaxError err : px.syntaxErrors()) {
                    sb.append(err.toString()).append("\n");
                }
                success = false;
            }
        }
        sb.append("More to try? " + (runNext != null)).append("\n");
        append(sb.toString());
        if (success) {
            action.setRunnable(runNext);
        } else {
            if (keepGoing.get()) {
                action.setRunnable(Bundle.stop(), stopRunnable);
                runNext.run();
            } else {
                action.setRunnable(new KGRunnable(runNext));
                keepGoing.set(true);
            }
        }
        action.setEnabled(true);
        progress.setIndeterminate(false);
    }

    private void append(Throwable thrown, StringBuilder sb) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            try (PrintStream ps = new PrintStream(out, true, "UTF-8")) {
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
    private Runnable keepGoingRunnable = new Runnable() {
        @Override
        public void run() {
            keepGoing.set(true);
        }
    };

    private class KGRunnable implements Runnable {

        private final Runnable run;

        public KGRunnable(Runnable run) {
            this.run = run;
        }

        @Override
        public void run() {
            if (keepGoing.get()) {
                run.run();
            } else {
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
            "stop=Stop"
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

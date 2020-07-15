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
package org.nemesis.editor.utils;

import org.nemesis.editor.ops.CaretInformation;
import org.nemesis.editor.ops.DocumentOperator;
import org.nemesis.editor.edit.EditBag;
import com.mastfrog.function.state.Obj;
import com.mastfrog.util.strings.Escaper;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.editor.edit.Applier;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.api.editor.caret.EditorCaretEvent;
import org.netbeans.api.editor.caret.EditorCaretListener;
import org.netbeans.api.editor.caret.MoveCaretsOrigin;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorDocument;
import org.netbeans.spi.editor.caret.CaretMoveHandler;
import org.openide.text.NbDocument;
import org.nemesis.editor.function.DocumentConsumer;

/**
 *
 * @author Tim Boudreau
 */
public class EditBagTest {

    static boolean headless;
    private BaseDocument doc;
    private StyledDocument styled;
    private String initialText;
    private DocumentOperator op;
    private JEditorPane docPane;
    private JEditorPane styledPane;
    private CaretWait docCaretWait;
    private CaretWait styledCaretWait;

    private Position docTargetCaretPosition;
    private Position styledTargetCaretPosition;

    @BeforeAll
    public static void headlessCheck() {
        headless = GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance();
        if (headless) {
            System.err.println("cannot run " + EditBagTest.class.getSimpleName()
                    + " in a headless environment");
        }
    }

    @Test
    public void basicEdits() throws Exception {
        if (headless) {
            return;
        }
        String txt = styled.getText(0, styled.getLength());
        assertEquals(startText(), txt);

        performEdits(bag -> {
            styledTargetCaretPosition = NbDocument.createPosition(styled, 10, Position.Bias.Forward);
            docTargetCaretPosition = NbDocument.createPosition(doc, 10, Position.Bias.Forward);
            bag.insert(15, "\n(insertion at character 5)\n");
            bag.insert(23, "\n(insertion at character 23)");
            bag.delete(40, 5);
            bag.delete(30, 10);
            bag.replace(50, 5, () -> "\n(replace 5 chars with this)\n");
            bag.insert(55, "\n(insert more at 55)\n");
            bag.insert(57, "\n(and insert at 57)\n");
            bag.replace(txt.length() / 2, 20, "\n(The original halfway point)\n");
        });

        String txt2 = styled.getText(0, styled.getLength());
        performEdits(bag -> {
            styledTargetCaretPosition = NbDocument.createPosition(styled, 80, Position.Bias.Forward);
            docTargetCaretPosition = NbDocument.createPosition(doc, 80, Position.Bias.Forward);

            bag.insert(txt2.length() - 1, "\nThis is some stuff stuck at the end\n");
            bag.delete(txt2.length() / 2, 7);

            bag.replace(0, 7, "\nSome new intro text\n");
            bag.delete(txt2.length() - 30, 5);

            bag.insert(70, "\nAnd some new stuff at 70\n");
        });
        String txt3 = styled.getText(0, styled.getLength());
        performEdits(bag -> {
            styledTargetCaretPosition = NbDocument.createPosition(styled, txt3.length() - 12, Position.Bias.Forward);
            docTargetCaretPosition = NbDocument.createPosition(doc, txt3.length() - 12, Position.Bias.Forward);
//            bag.insert(txt3.length()/2, "\nIn the middle\n");
            bag.delete(0, 10);
            bag.replace(txt3.length() / 2, 12, "\nNow this is the middle\n");
        });
    }

    private void performEdits(DocumentConsumer<EditBag> c) throws Exception {
        int docCaretNotifs = docCaretWait.notifications();
        int styledCaretNotifs = styledCaretWait.notifications();
        StringBuilder sb = new StringBuilder();
        sb.append(styled.getText(0, styled.getLength()));
        if (sb.charAt(sb.length() - 1) != '\n') {
            // swing documents always end with \n whether they do or not
//            sb.append('\n');
        }
        Applier styledApplier = new Applier(op);
        EditBag edits = new EditBag(styled, styledApplier);
        c.accept(edits);
        edits.applyToStringBuilder(sb);

        Applier docApplier = new Applier(op);
        EditBag docEdits = new EditBag((StyledDocument) doc, docApplier);

        c.accept(docEdits);
        styledApplier.apply(op);
        docApplier.apply(op);

        AssertionError ae = null;
        try {
            assertText("Styled ", sb.toString(), styled.getText(0, styled.getLength()));
        } catch (AssertionError e) {
            ae = e;
        }
        try {
            assertText("NbEditorDocument ", sb.toString(), doc.getText(0, doc.getLength()));
        } catch (AssertionError e) {
            if (ae == null) {
                ae = e;
            } else {
                ae.addSuppressed(e);
            }
        }
        if (ae != null) {
            throw ae;
        }

        docCaretWait.await(docCaretNotifs);
        styledCaretWait.await(styledCaretNotifs);

        EventQueue.invokeAndWait(() -> {
            docPane.getTopLevelAncestor().invalidate();
            docPane.getTopLevelAncestor().validate();
            docPane.getTopLevelAncestor().repaint();
            if (docTargetCaretPosition.getOffset() != docPane.getCaret().getDot()) {
                System.out.println("NbEditorDocument caret not updated: " + docTargetCaretPosition.getOffset() + " got " + docPane.getCaret().getDot());
            }
            if (styledTargetCaretPosition.getOffset() != styledPane.getCaret().getDot()) {
                System.out.println("StyledDocument caret not updated: " + styledTargetCaretPosition.getOffset() + " got " + styledPane.getCaret().getDot());
            }
        });
//        Thread.sleep(1300);
        assertEquals(docTargetCaretPosition.getOffset(), docPane.getCaret().getDot(), "NbEditorDocument caret not updated");
    }

    private void assertText(String msg, String a, String b) {
        String[] al = a.split("\n");
        String[] bl = b.split("\n");
        for (int i = 0; i < Math.max(al.length, bl.length); i++) {
            if (i < al.length && i < bl.length) {
                String la = al[i];
                String lb = al[i];
                if (!la.equals(lb)) {
                    fail(msg + ": Lines diverge at line " + (i + 1) + ":\n" + la + "\n" + lb);
                }
            } else if (i < al.length) {
                fail(msg + ": Added line in text b at " + i + ": " + Escaper.CONTROL_CHARACTERS.escape(al[i]));
            } else if (i < bl.length) {
                fail(msg + ": Added line in text a at " + i + ": " + Escaper.CONTROL_CHARACTERS.escape(bl[i]));
            }
        }
        if (a.length() != b.length()) {
            String remainder;
            if (a.length() < b.length()) {
                remainder = b.substring(a.length());
            } else {
                remainder = a.substring(b.length());
            }
            fail(msg + ": Lengths diverge: " + a.length() + " " + b.length()
                    + " remainder: " + Escaper.CONTROL_CHARACTERS.escape(remainder));
        }
        assertEquals(a, b);
    }

    @BeforeEach
    @SuppressWarnings("deprecation")
    public void before() throws BadLocationException, InterruptedException, InvocationTargetException, Exception {
        if (headless) {
            return;
        }
        op = DocumentOperator.builder().blockIntermediateRepaints().acquireAWTTreeLock()
                .disableTokenHierarchyUpdates().lockAtomic()
                .restoringCaretPosition((CaretInformation caret, JTextComponent comp, Document doc1) -> {
                    Position p;
                    if (doc == styled) {
                        p = styledTargetCaretPosition;
                    } else {
                        p = docTargetCaretPosition;
                    }
                    Position pp = p;
                    return c -> {
                        c.accept(pp.getOffset(), pp.getOffset());
                    };
                })
                .build();
        doc = new NbEditorDocument(org.netbeans.modules.editor.plain.PlainKit.class);
        styled = new DefaultStyledDocument();
        initialText = startText();
        doc.insertString(0, initialText, null);
        styled.insertString(0, initialText, null);
        org.netbeans.modules.editor.plain.PlainKit pk = new org.netbeans.modules.editor.plain.PlainKit();
        Obj<Exception> thrown = Obj.create();
        CountDownLatch latch = new CountDownLatch(2);
        // A number of caret operations don't work until the window is
        // fully realized on screen, so ensure that that happens before
        // we exit this method
        ComponentAdapter adap = new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                latch.countDown();
            }
        };

        WindowAdapter wi = new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                latch.countDown();
            }
        };
        EventQueue.invokeAndWait(() -> {
            try {
                docPane = new JEditorPane();
                docPane.setEditorKit(pk);
                docPane.setDocument(doc);

                styledPane = new JEditorPane();
                styledPane.setEditorKit(pk);
                styledPane.setDocument(styled);

//                CellRendererPane cellR = new CellRendererPane();
//                cellR.setVisible(true);
                // Sigh - CellRendererPane does not ake enough of the
                // environment to allow caret updates to work, since they
                // are paint-triggered - so this will remain a test that
                // cannot run headless
                JFrame cellR = new JFrame();
                cellR.addComponentListener(adap);
                cellR.addWindowListener(wi);
                cellR.setLayout(new BorderLayout());
                JScrollPane docScroll = new JScrollPane(docPane);
                cellR.add(docScroll, BorderLayout.CENTER);
                JScrollPane styledScroll = new JScrollPane(styledPane);
                cellR.add(styledScroll, BorderLayout.EAST);
                cellR.pack();

                cellR.setBounds(0, 0, 800, 300);
                cellR.setVisible(true);

                Caret docCaret = docPane.getCaret();
                Caret styledCaret = styledPane.getCaret();

                styledCaret.setVisible(true);
                docCaret.setVisible(true);

                moveCaret(doc, docCaret, 7);
                moveCaret(styled, styledCaret, 57);

                docTargetCaretPosition = NbDocument.createPosition(doc, 41, Position.Bias.Forward);
                styledTargetCaretPosition = NbDocument.createPosition(styled, 41, Position.Bias.Forward);

                docCaretWait = new CaretWait("NbEditorDocument");
                if (docCaret instanceof EditorCaret) {
                    EditorCaret ec = (EditorCaret) docPane.getCaret();
                    ec.addEditorCaretListener(docCaretWait);
                }
                styledCaretWait = new CaretWait("StyledDocument");
                if (styledCaret instanceof EditorCaret) {
                    EditorCaret ec = (EditorCaret) styledPane.getCaret();
                    ec.addEditorCaretListener(styledCaretWait);
                }
                docCaret.addChangeListener(docCaretWait);
                styledCaret.addChangeListener(styledCaretWait);
            } catch (Exception ex) {
                thrown.accept(ex);
            }
        });
        if (thrown.isSet()) {
            throw thrown.get();
        }
        latch.await(3, TimeUnit.SECONDS);
    }

    static class CaretWait implements ChangeListener, EditorCaretListener {

        private String name;
        private int notifCount;

        public CaretWait(String name) {
            this.name = name;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            doNotif();
        }

        private void doNotif() {
            synchronized (this) {
                notifCount++;
                notifyAll();
            }
        }

        int notifications() {
            return notifCount;
        }

        void await(int exp) throws InterruptedException {
            int notifs = -1;
            synchronized (this) {
                for (int i = 0; i < 10; i++) {
                    if ((notifs = notifCount) <= exp) {
                        wait(85);
                    } else {
                        break;
                    }
                }
            }
            assertTrue(notifs > exp);
        }

        @Override
        public void caretChanged(EditorCaretEvent evt) {
            doNotif();
        }

    }

    private String startText() {
        StringBuilder sb = new StringBuilder();
        for (int line = 1; line < 30; line++) {
            int start = sb.length();
            sb.append(start).append(": ");
            String txt = "line-" + line;
            for (int j = 1; j < 6; j++) {
                sb.append(txt).append('-').append(line).append(' ');
            }
            sb.append("end-").append(line).append("-s");
            int len = sb.length();
            sb.append(len);
            sb.append('\n');
        }
        return sb.toString();
    }

    private void moveCaret(Document doc, Caret caret, int dotMark) throws BadLocationException {
        Position pos = NbDocument.createPosition(doc, dotMark, Position.Bias.Forward);
        if (caret instanceof EditorCaret) {
            ((EditorCaret) caret).moveCarets(new CaretMoveHandler() {
                @Override
                public void moveCarets(CaretMoveContext context) {
                    context.setDotAndMark(context.getOriginalLastCaret(), pos, Position.Bias.Forward, pos, Position.Bias.Forward);
                }
            }, MoveCaretsOrigin.DISABLE_FILTERS);
        } else {
            caret.setDot(dotMark);
        }
    }
}

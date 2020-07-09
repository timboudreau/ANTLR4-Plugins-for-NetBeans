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

import com.mastfrog.function.IntBiConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.util.strings.Strings;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.NavigationFilter;
import javax.swing.text.Position;
import javax.swing.text.Position.Bias;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoableEdit;
import org.netbeans.api.editor.caret.CaretInfo;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.spi.editor.caret.CaretMoveHandler;
import org.netbeans.spi.lexer.MutableTextInput;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.awt.KeyboardFocusManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.Consumer;
import javax.swing.JScrollPane;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import org.nemesis.editor.utils.DocumentOperator.DocumentLockProcessor3.AWTTreeLocker;
import org.nemesis.editor.utils.DocumentOperator.DocumentLockProcessor3.CaretPositionUndoableEdit;
import org.nemesis.editor.utils.DocumentOperator.DocumentLockProcessor3.UndoTransaction;
import static org.nemesis.editor.utils.DocumentOperator.DocumentLockProcessor3.render;
import static org.nemesis.editor.utils.DocumentPreAndPostProcessor.NO_OP;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.caret.MoveCaretsOrigin;
import org.netbeans.api.editor.document.CustomUndoDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.lexer.TokenHierarchyControl;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;

/**
 * Run reentrant operations against a document, employing a number of settable
 * features (use <code>builder()</code> to enable them).
 * <ul>
 * <li><b>Block repaints</b> &mdash; Try to avoid editor repaints until all
 * operations are completed</li>
 * <li><b>locAtomic</b> &mdash; Write lock the document, the document operation
 * inside a call to <code>NbDocument.runAtomic()</code> (mutually exclusive with
 * <i>lockAtomic() as user, which handles guarded blocks differently</i>)</li>
 * <li><b>Read lock</b> &mdash; Read-lock the document, running the operation
 * inside of <code>Document.render()</code>. <i>Not</i> mutually exclusive with
 * write locking.</li>
 * <li><b>One undoable edit</b> &mdash; If multiple distinct changes are made to
 * the document, a single undoable edit will be generated to undo all of them as
 * a single edit.</li>
 * <li><b>Preserve caret position</b> &mdash; Try to restore the caret position
 * after performing changes, so that the editor caret position is restored to
 * its current location (otherwise, inserts or deletions will cause it to move);
 * in combination with (note, DocumentOperatorBuilder also allows for providing
 * an implementation which recomputes the caret position after modification).
 * <i>block repaints</i> the editor on-screen will "jump" minimally if at all.
 * (this also generates a re-scrolling undo event which repositions the caret
 * and scroll position)</li>
 * <li><b>Disable token hierarchy updates</b> &mdash; This blocks the lexer
 * infrastructure from initiating a re-lex or re-parse until all operations have
 * been completed, which considerably speeds up a series of multiple
 * modifications, since multiple things won't be trying to re-parse the document
 * .</li>
 * </ul>
 * <p>
 * Reentrant calls will not acquire the same lock or similar for the same
 * document twice; reentry is handled with ThreadLocals, so that is the case
 * whether or not code is reentering the same or a different document operator.
 * <i>Same document</i> in this case means identity equality, not
 * <code>equals()</code> equality.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class DocumentOperator {

    static final Logger LOG = Logger.getLogger(DocumentOperator.class.getName());

    private final Set<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> props;

    /**
     * A default instance for modifying a document which is open in the editor,
     * while avoiding temporary changes to the scroll position and unexpected
     * caret moves.
     */
    public static final DocumentOperator NON_JUMP_REENTRANT_UPDATE_DOCUMENT
            = new DocumentOperator(EnumSet.of(BuiltInDocumentOperations.WRITE_LOCK,
                    BuiltInDocumentOperations.PRESERVE_CARET_POSITION,
                    BuiltInDocumentOperations.ACQUIRE_AWT_TREE_LOCK,
                    BuiltInDocumentOperations.ATOMIC_AS_USER,
                    BuiltInDocumentOperations.WRITE_LOCK,
                    BuiltInDocumentOperations.DISABLE_MTI,
                    BuiltInDocumentOperations.ONE_UNDOABLE_EDIT,
                    BuiltInDocumentOperations.BLOCK_REPAINTS));

    DocumentOperator(Set<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> props) {
        this.props = props;
//        this.props = props instanceof EnumSet<?>
//                ? new LinkedHashSet<>(CollectionUtils.reversed(new ArrayList<>(props))) : props;
    }

    private static void eqRun(BadLocationRunnable r) throws BadLocationException {
        if (EventQueue.isDispatchThread()) {
            r.run();
        } else {
            EventQueue.invokeLater(() -> {
                try {
                    r.run();
                } catch (BadLocationException ex) {
                    LOG.log(Level.INFO, null, ex);
                }
            });
        }
    }

    private static void runOnEq(Runnable r) {
        if (EventQueue.isDispatchThread()) {
            r.run();
        } else {
            EventQueue.invokeLater(r);
        }
    }

    /**
     * Run the operation on a document, logging any exceptions, on the AWT event
     * queue.
     *
     * @param doc A document
     * @param run A runnable
     */
    void runOnEventQueue(StyledDocument doc, Runnable run) {
        DocumentOperation x = operateOn(doc);
        runOnEq(() -> {
            x.run(run);
        });
    }

    /**
     * Run the operation on a document, logging any exceptions.
     *
     * @param doc A document
     * @param run A runnable
     */
    public void run(StyledDocument doc, Runnable run) {
        operateOn(doc).run(run);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + Strings.join(", ", props) + ")";
    }

    /**
     * Create a single-use runner which can be used to perform your operation.
     *
     * @param <T> The return type of the operation
     * @param <E> The exception type of the operation (use RuntimeException if
     * the operation does not throw any checked exception)
     * @param doc A document to runPostOperationsOnEventQueue against
     *
     * @return An operation
     */
    public <T, E extends Exception> DocumentOperation<T, E> operateOn(StyledDocument doc) {
        return new DocumentOperation<>(notNull("doc", doc), props);
    }

    static <T, E extends Exception> BleFunction<T, E> runner(StyledDocument doc, Iterable<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> props) {
//        Arrays.sort(props, Comparator.<Props>naturalOrder().reversed());
        return (DocumentProcessor<T, E> supp) -> {
            DocumentOperationContext ctx = new DocumentOperationContext(doc, DocumentOperator::sendUndoableEdit);
            LOG.log(Level.FINE, "Apply {0} to {1} with {2}", new Object[]{supp, doc, props});
            for (Function<StyledDocument, DocumentPreAndPostProcessor> p : props) {
                supp = p.apply(doc).wrap(supp);
            }
            supp = new PostRunOperations<>(supp);
            return supp.get(ctx);
        };
    }

    static <T, E extends Exception> BleFunction<T, E> runner(StyledDocument doc, BuiltInDocumentOperations... props) {
        DocumentOperationContext ctx = new DocumentOperationContext(doc, DocumentOperator::sendUndoableEdit);
        Arrays.sort(props, Comparator.<BuiltInDocumentOperations>naturalOrder().reversed());
        return (DocumentProcessor<T, E> supp) -> {
            LOG.log(Level.FINE, "Apply {0} to {1} with {2}", new Object[]{supp, doc, Arrays.asList(props)});
            for (BuiltInDocumentOperations p : props) {
                supp = p.apply(doc).wrap(supp);
            }
            supp = new PostRunOperations<>(supp);
            return supp.get(ctx);
        };
    }

    static void sendUndoableEdit(Document doc, UndoableEdit edit) {
        CustomUndoDocument customUndoDocument
                = LineDocumentUtils.as(doc,
                        CustomUndoDocument.class);
        if (customUndoDocument != null) {
            customUndoDocument.addUndoableEdit(edit);
        }
    }

    static class PostRunOperations<T, E extends Exception> implements DocumentProcessor<T, E> {

        private final DocumentProcessor<T, E> delegate;

        public PostRunOperations(DocumentProcessor<T, E> delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressWarnings("FinallyDiscardsException")
        public T get(DocumentOperationContext ctx) throws E, BadLocationException {
            try {
                T result = delegate.get(ctx);
                return result;
            } finally {
                eqRun(() -> {
                    ctx.enterExitAtomicLock(ctx.document(), () -> {
                        ctx.runPostOperationsOnEventQueue(LOG);
                        ctx.flushPendingEdits();
                    });
                });
            }
        }
    }

    public static DocumentOperationBuilder builder() {
        return new DocumentOperationBuilder();
    }

    interface BleFunction<T, E extends Exception> extends ThrowingFunction<DocumentProcessor<T, E>, T> {

        @Override
        T apply(DocumentProcessor<T, E> arg) throws BadLocationException, E;

    }

    /**
     * Built in wrapper before/after operations.
     */
    enum BuiltInDocumentOperations implements Function<StyledDocument, DocumentPreAndPostProcessor> {
        // These are in a very specific order they need to be
        // applied in (this list is the reverse order)
        DISABLE_MTI, // mti must be touched under read and write lock
        PRESERVE_CARET_POSITION, // Try to reset the caret position to someplace sane
        ONE_UNDOABLE_EDIT, // need to be in the undo transaction before we add our caret restoring edit
        RENDER, // render after atomic?
        ATOMIC_AS_USER, // NbDocument.runAtomicAsUser
        ATOMIC, // NbDocument.runAtomicAsUser
        WRITE_LOCK, // basedocument.extwriteLock()
        ACQUIRE_AWT_TREE_LOCK, // dangerous off EQ but blocks all revalidation - runPostOperationsOnEventQueue in synchronized(comp.getTreeLock())
        BLOCK_REPAINTS, // this should runPostOperationsOnEventQueue first - does not depend on doc contents
        ;

        @Override
        public DocumentPreAndPostProcessor apply(StyledDocument doc) {
            switch (this) {
                case ATOMIC:
                    return new DocumentLockProcessor3(doc, false, false);
                case WRITE_LOCK:
                    if (!(doc instanceof BaseDocument)) {
                        return NO_OP;
                    }
                    return new DocumentLockProcessor3(doc, true, false);
                case ATOMIC_AS_USER:
                    return new DocumentLockProcessor3(doc, false, true);
                case RENDER:
                    return new DocumentReadLocker(doc);
                case BLOCK_REPAINTS:
                    return new BlockRepaints(doc);
                case ONE_UNDOABLE_EDIT:
                    return new UndoTransaction(doc);
                case DISABLE_MTI:
                    return new DisableMTI(doc);
                case PRESERVE_CARET_POSITION:
                    return new PreserveCaret(doc, new DefaultCaretPositionCalculator());
                case ACQUIRE_AWT_TREE_LOCK:
                    return new AWTTreeLocker(doc);
                default:
                    throw new AssertionError(this);
            }
        }
    }

    static abstract class SingleEntryBeforeAfter implements DocumentPreAndPostProcessor {

        protected final StyledDocument doc;
        protected final int idHash;

        protected SingleEntryBeforeAfter(StyledDocument doc) {
            this.doc = doc;
            idHash = System.identityHashCode(doc);
        }

        public String toString() {
            return getClass().getSimpleName();
        }

        String id() {
            return getClass().getSimpleName() + "-" + idHash;
        }

        private <T, E extends Exception> DocumentProcessor<T, E> superWrap(DocumentProcessor<T, E> toWrap) {
            return DocumentPreAndPostProcessor.super.wrap(toWrap);
        }

        @Override
        public <T, E extends Exception> DocumentProcessor<T, E> wrap(DocumentProcessor<T, E> toWrap) {
            return new DocumentProcessor<T, E>() {
                @Override
                public T get(DocumentOperationContext ctx) throws E, BadLocationException {
                    if (ctx.wasEntered(id())) {
                        return toWrap.get(ctx);
                    }
                    ctx.markEntered(id());
                    return superWrap(toWrap).get(ctx);
                }

                public String toString() {
                    return id() + "-" + toWrap.getClass().getSimpleName();
                }
            };
        }
    }

    static class DisableMTI extends SingleEntryBeforeAfter {

        private MutableTextInput mti;
        private boolean active;

        public DisableMTI(StyledDocument doc) {
            super(doc);
        }

        @Override
        public String toString() {
            return "DISABLE-MTI";
        }

        @Override
        public void before(DocumentOperationContext ctx) throws BadLocationException {
            mti = (MutableTextInput) doc.getProperty(MutableTextInput.class);
            LOG.log(Level.FINER, "{0} before on {1}",
                    new Object[]{this, Thread.currentThread()});
            if (mti != null) {
                TokenHierarchyControl ctrl = mti.tokenHierarchyControl();
                active = ctrl.isActive();
                ctrl.setActive(false);
            }
        }

        @Override
        public void after(DocumentOperationContext ctx) throws BadLocationException {
            LOG.log(Level.FINER, "{0} after on {1}",
                    new Object[]{this, Thread.currentThread()});
            if (mti != null) {
                LOG.log(Level.FINEST, "Set active {0} on {1}",
                        new Object[]{active, mti});
                mti.tokenHierarchyControl().setActive(active);
            }
        }
    }

    /**
     * Find the most recently used text editor of a document, <i>including ones
     * which are not in the editor registry if they are the focus owner</i>.
     *
     * @param doc A document
     * @return A text component
     */
    public static JTextComponent findComponent(Document doc) {
        // For the Antlr preview, in which the document does not show up as
        // a member of EditorRegistry, because it's just a JEditorPane with
        // the kit set on it, we need to first try to find the component
        // as the current focus onwer - otherwise we wind up getting the
        // caret position from the wrong JTextComponent
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner instanceof JTextComponent) {
            JTextComponent jtc = (JTextComponent) focusOwner;
            if (jtc.getDocument() == doc) {
                return jtc;
            }
        }
        focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        if (focusOwner instanceof JTextComponent) {
            JTextComponent jtc = (JTextComponent) focusOwner;
            if (jtc.getDocument() == doc) {
                return jtc;
            }
        }
        JTextComponent comp = EditorRegistry.lastFocusedComponent();
        if (comp != null && comp.getDocument() == doc) {
            return comp;
        }
        // Pending - scan current focus cycle root?
        return EditorRegistry.findComponent(doc);
    }

    private static class DefaultCaretPositionCalculator implements CaretPositionCalculator {

        @Override
        public Consumer<IntBiConsumer> createPostEditPositionFinder(CaretInformation caret, JTextComponent comp, Document doc) {
            return bc -> {
                bc.accept(caret.dot(), caret.mark());
            };
        }

        public String toString() {
            return getClass().getSimpleName();
        }
    }

    static final class EditorCaretInformation implements CaretInformation {

        final CaretInfo info;

        public EditorCaretInformation(CaretInfo info) {
            this.info = info;
        }

        public String toString() {
            return getClass().getSimpleName() + "(" + info.getDot() + " / " + info.getMark() + ")";
        }

        @Override
        public int dot() {
            return info.getDot();
        }

        @Override
        public int mark() {
            return info.getMark();
        }

        @Override
        public Bias dotBias() {
            return info.getDotBias();
        }

        @Override
        public Bias markBias() {
            return info.getMarkBias();
        }

        @Override
        public int selectionStart() {
            return info.getSelectionStart();
        }

        @Override
        public int selectionEnd() {
            return info.getSelectionEnd();
        }
    }

    static final class SwingCaretInformation implements CaretInformation {

        private final Position dotPos;
        private final Position markPos;
        private final boolean forward;

        SwingCaretInformation(Caret caret, Document doc) throws BadLocationException {
            int mark = caret.getMark();
            int dot = caret.getDot();
            dotPos = doc.createPosition(dot);
            forward = mark >= dot;
            if (mark == dot) {
                markPos = dotPos;
            } else {
                markPos = doc.createPosition(mark);
            }
        }

        @Override
        public int dot() {
            return dotPos.getOffset();
        }

        @Override
        public int mark() {
            return markPos.getOffset();
        }

        @Override
        public Bias dotBias() {
            return forward ? Position.Bias.Backward
                    : Position.Bias.Forward;
        }

        @Override
        public Bias markBias() {
            return forward ? Position.Bias.Forward
                    : Position.Bias.Backward;
        }

        public String toString() {
            return getClass().getSimpleName() + "(" + dot() + " / " + mark() + ")";
        }
    }

    static class PreserveCaret extends SingleEntryBeforeAfter implements DocumentListener {

        private DocumentPreAndPostProcessor handler;
        UndoableEdit edit;
        JTextComponent comp;
        private final CaretPositionCalculator calc;
        private boolean beforeRun;
        private boolean afterRun;

        public PreserveCaret(StyledDocument doc, CaretPositionCalculator calc, JTextComponent comp) {
            this(doc, calc);
            this.comp = comp;
        }

        public PreserveCaret(StyledDocument doc, CaretPositionCalculator calc) {
            super(doc);
            this.calc = calc == null ? new DefaultCaretPositionCalculator() : calc;
        }

        @Override
        String id() {
            JTextComponent c = comp();
            return c == null ? super.id() : super.id() + "-" + System.identityHashCode(c);
        }

        private JTextComponent comp() {
            return this.comp == null ? this.comp = findComponent(doc) : this.comp;
        }

        private boolean isEditorCaret() {
            JTextComponent comp = comp();
            return comp != null && (comp.getCaret() instanceof EditorCaret);
        }

        @Override
        public String toString() {
            return "PRESERVE-CARET(" + (isEditorCaret() ? "EDITOR" : "SWING") + ")";
        }

        @Override
        public <T, E extends Exception> DocumentProcessor<T, E> wrap(DocumentProcessor<T, E> toWrap) {
            if (beforeRun) {
                return toWrap;
            } else {
                return super.wrap(toWrap);
            }
        }

        @Override
        public void before(DocumentOperationContext ctx) throws BadLocationException {
            if (beforeRun) {
                return;
            }
            beforeRun = true;
            if (comp == null) {
                comp = this.comp == null ? findComponent(doc) : comp;
            }
            LOG.log(Level.FINER, "{0} before with {1} on {2}",
                    new Object[]{this, doc, Thread.currentThread()});
            JTextComponent c = comp();
            if (c != null) {
                doc.addDocumentListener(this);
                Caret caret = c.getCaret();
                if (caret != null) {
                    edit = new CaretPositionUndoableEdit(c, doc);
//                    ctx.sendUndoableEdit(edit);
//                    EditorUtilities.addCaretUndoableEdit(doc, comp.getCaret());
                    LOG.log(Level.FINEST, "Added caret position undo");
                    ctx.sendUndoableEdit(edit);
                    if (caret instanceof EditorCaret) {
                        LOG.log(Level.FINEST, "Using editor caret strategy");
                        handler = new EditorCaretHandler(doc, c, (EditorCaret) caret, calc);
                    } else {
                        LOG.log(Level.FINEST, "Using swing caret strategy");
                        handler = new SwingCaretHandler(c, caret, calc);
                    }
                }
                if (handler != null) {
                    handler.before(ctx);
                }
            } else {
            }
        }

        @Override
        public void after(DocumentOperationContext ctx) throws BadLocationException {
            if (afterRun) {
                return;
            }
            afterRun = true;
            LOG.log(Level.FINER, "{0} after on {1}", new Object[]{this, Thread.currentThread()});
            if (handler != null) {
                doc.removeDocumentListener(this);
                handler.after(ctx);
//                ctx.sendUndoableEdit(edit);
            } else {
                ctx.add(() -> {
                    doc.removeDocumentListener(this);
                });
            }
        }

        private void docChanged() {
            if (comp != null) {
                JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, comp);
                if (pane != null) {
                    RepaintManager.currentManager(pane).removeInvalidComponent(pane);
                    RepaintManager.currentManager(pane.getViewport()).removeInvalidComponent(pane.getViewport());
                    RepaintManager.currentManager(pane).markCompletelyClean(pane);
                    RepaintManager.currentManager(pane.getViewport()).markCompletelyClean(pane.getViewport());
                }
                RepaintManager.currentManager(comp).removeInvalidComponent(comp);
                RepaintManager.currentManager(comp).markCompletelyClean(comp);
                LOG.log(Level.FINEST,
                        "{0} got doc change, attempting to "
                        + "preempt repaint and revalidate from "
                        + "RepaintManager for {1}",
                        new Object[]{this, comp});
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
            // do nothing
        }

        static final class EditorCaretHandler extends NavigationFilter implements DocumentPreAndPostProcessor, CaretMoveHandler {

            final JTextComponent comp;
            final EditorCaret caret;
            List<CaretInfo> infos = new ArrayList<>(5);
            private final Map<CaretInfo, Integer> origCaretPositions = new HashMap<>();
            private NavigationFilter filter1;
            private NavigationFilter filter2;
            private boolean wasCaretUpdated;
            private final CaretPositionCalculator calc;
            private final List<Consumer<IntBiConsumer>> positionCalcs = new ArrayList<>(5);
            private final StyledDocument doc;
            private boolean caretsUpdated;

            public EditorCaretHandler(StyledDocument doc, JTextComponent comp, EditorCaret caret, CaretPositionCalculator calc) {
                this.comp = comp;
                this.doc = doc;
                this.caret = caret;
                this.calc = calc == null ? new DefaultCaretPositionCalculator() : calc;
            }

            @Override
            public String toString() {
                return "EDITOR-CARET";
            }

            String id() {
                return "carets-" + System.identityHashCode(doc)
                        + "-" + System.identityHashCode(comp)
                        + "-" + System.identityHashCode(caret);
            }

            @Override
            public void before(DocumentOperationContext ctx) throws BadLocationException {
                LOG.log(Level.FINER, "{0} before on {1} with {2}", new Object[]{this, Thread.currentThread(), calc});
                this.infos.addAll(caret.getSortedCarets());
                for (CaretInfo info : infos) {
                    origCaretPositions.put(info, info.getDot());
//                    positionsForCaret.put(info, new CaretPositions(info, comp.getDocument()));
                    CaretInformation ci = new EditorCaretInformation(info);
                    positionCalcs.add(calc.createPostEditPositionFinder(ci, comp, comp.getDocument()));
                }
                LOG.log(Level.FINEST, "Editor caret info {0}", infos);

                caretsUpdated = ctx.ifNotEntered(id(), () -> {
                    LOG.log(Level.FINEST, "Add caret navigation filters");
                    filter1 = EditorCaret.getNavigationFilter(comp, MoveCaretsOrigin.DEFAULT);
                    EditorCaret.setNavigationFilter(comp, MoveCaretsOrigin.DEFAULT, this);
                    filter2 = EditorCaret.getNavigationFilter(comp, MoveCaretsOrigin.DISABLE_FILTERS);
                    EditorCaret.setNavigationFilter(comp, MoveCaretsOrigin.DEFAULT, this);
                    caret.setVisible(false);
                });
            }

            @Override
            public void after(DocumentOperationContext ctx) throws BadLocationException {
                LOG.log(Level.FINER, "{0} after restore caret navigation filters {1}, {2}"
                        + " on {3}",
                        new Object[]{this, filter1, filter2, Thread.currentThread()});

                if (caretsUpdated) {
//                    if (filter1 != null) {
                    EditorCaret.setNavigationFilter(comp, MoveCaretsOrigin.DEFAULT, filter1);
//                    }
//                    if (filter2 != null) {
                    EditorCaret.setNavigationFilter(comp, MoveCaretsOrigin.DISABLE_FILTERS, filter2);
//                    }
                    // Do all of this in an end / EQ task?
                    int moveCaretsResult = caret.moveCarets(this, MoveCaretsOrigin.DISABLE_FILTERS);
                    if (!wasCaretUpdated) {
                        ctx.add(() -> {
                            int moveCaretsResult2 = caret.moveCarets(this, MoveCaretsOrigin.DISABLE_FILTERS);
                            if (!wasCaretUpdated) {
                                bruteForceCaretUpdate();
                            }
                        });
                    }
                } else {
                }
            }

            private void bruteForceCaretUpdate() {
                if (!infos.isEmpty()) {
                    int max = this.infos.size();
                    for (int i = 0; i < max; i++) {
                        Consumer<IntBiConsumer> curr = positionCalcs.get(i);
                        CaretInfo info = infos.get(i);
                        curr.accept((dot, mark) -> {
                            LOG.log(Level.INFO, "Attempt brute force update of {0} to {1}, {2} on {3}",
                                    new Object[]{info, dot, mark, caret});
                            caret.setDot(mark);
                            if (mark != dot) {
                                caret.moveDot(dot);
                            }
                            wasCaretUpdated = caret.getDot() == dot;
                        });
                    }
                }
            }

            @Override
            public void moveCarets(CaretMoveContext cmc) {
                int max = this.infos.size();
                assert max == this.positionCalcs.size() : "Mismatched set of calcs";
                Document doc = comp.getDocument();
                if (doc.getLength() == 0) {
                    // don't move it and don't try again
                    wasCaretUpdated = true;
                    return;
                }
                for (int i = 0; i < max; i++) {
                    Consumer<IntBiConsumer> curr = positionCalcs.get(i);
                    CaretInfo info = infos.get(i);
                    curr.accept((dot, mark) -> {
                        dot = Math.max(0, Math.min(doc.getLength() - 1, dot));
                        mark = Math.max(0, Math.min(doc.getLength() - 1, mark));
                        try {
                            Position markPos = doc.createPosition(mark);
                            Position dotPos = doc.createPosition(dot);
                            boolean updated = cmc.setDotAndMark(info, dotPos, info.getDotBias(), markPos, info.getMarkBias());
                            if (!updated) {
//                                // brute force - this will trash multiple
//                                // carets if present
//                                caret.setDot(mark);
//                                if (mark != dot) {
//                                    caret.moveDot(dot);
//                                }
//                                updated = caret.getDot() == dot;
                            }
                            wasCaretUpdated |= updated;
                        } catch (BadLocationException ex) {
                            LOG.log(Level.INFO, "Bad location attempting to restore caret position to " + dot
                                    + ", " + mark + " in " + doc, ex);
                        }
                    });
                }
            }

            @Override
            public int getNextVisualPositionFrom(JTextComponent text, int pos, Position.Bias bias, int direction,
                    Position.Bias[] biasRet) throws BadLocationException {
                if (infos.size() > 0) {
                    return origCaretPositions.get(infos.get(0));
                }
                return pos;
            }

            @Override
            public void moveDot(FilterBypass fb, int dot, Position.Bias bias) {
                // do nothing
                LOG.log(Level.FINE, "{0} Preventing dot move to {1}",
                        new Object[]{this, dot});
            }

            @Override
            public void setDot(FilterBypass fb, int dot, Position.Bias bias) {
                // do nothing
                LOG.log(Level.FINE, "{0} Preventing dot set to {1}",
                        new Object[]{this, dot});
            }
        }

        static final class SwingCaretHandler implements DocumentPreAndPostProcessor, Runnable {

            final JTextComponent comp;
            final Caret caret;
            private Position dot;
            private Position mark;
            private final CaretPositionCalculator calc;
            private final List<Consumer<IntBiConsumer>> finders = new ArrayList<>();

            public SwingCaretHandler(JTextComponent comp, Caret caret, CaretPositionCalculator calc) {
                this.comp = comp;
                this.caret = caret;
                this.calc = calc == null ? new DefaultCaretPositionCalculator() : calc;
            }

            @Override
            public String toString() {
                return "SWING-CARET";
            }

            @Override
            public void before(DocumentOperationContext ctx) throws BadLocationException {
                LOG.log(Level.FINER, "{0} before", this);
                Document doc = comp.getDocument();
                Caret caret = comp.getCaret();
                if (caret != null) {
                    List<CaretInformation> caretInfo = CaretInformation.create(comp);
                    for (CaretInformation info : caretInfo) {
                        Consumer<IntBiConsumer> finder = calc.createPostEditPositionFinder(info, comp, doc);
                        if (finder != null) {
                            finders.add(finder);
                        }
                    }
                    dot = doc.createPosition(caret.getDot());
                    mark = doc.createPosition(caret.getMark());
                }
            }

            @Override
            public void after(DocumentOperationContext ctx) throws BadLocationException {
                LOG.log(Level.FINER, "{0} after", this);
                if (dot != null) {
                    ctx.add(this);
                }
            }

            @Override
            public void run() {
                if (!finders.isEmpty()) {
                    for (Consumer<IntBiConsumer> c : finders) {
                        c.accept((dot, mark) -> {
                            caret.setDot(mark);
                            caret.moveDot(dot);
                        });
                    }
                } else {
                    caret.setDot(mark.getOffset());
                    caret.moveDot(dot.getOffset());
                }
            }
        }
    }

    static class BlockRepaints extends SingleEntryBeforeAfter implements Runnable {

        private JTextComponent comp;

        public BlockRepaints(StyledDocument doc) {
            super(doc);
        }

        public String toString() {
            return "BLOCK-REPAINTS";
        }

        private JTextComponent comp() {
            return comp == null ? comp = findComponent(doc) : comp;
        }

        @Override
        String id() {
            JTextComponent c = comp();
            return c == null ? super.id() : super.id() + "-" + System.identityHashCode(c);
        }

        Point viewPosition;
        boolean caretVisible;
        private int distanceToTop = -1;

        @Override
        public void before(DocumentOperationContext ctx) {
            LOG.log(Level.FINER, "{0} before on {1}", new Object[]{this, Thread.currentThread()});
            JTextComponent textComp = comp();
            if (textComp != null) {
                boolean visibilityChanged = false;
                boolean paintsDisabled = false;
                JScrollPane pane = null;
                try {
                    LOG.log(Level.FINER, "{0} before", this);
                    int caretPos = textComp.getCaretPosition();
                    caretVisible = textComp.getCaret().isVisible();
                    LOG.log(Level.FINEST, "Caret position {0} visible {1}",
                            new Object[]{caretPos, caretVisible});
                    textComp.getCaret().setVisible(false);
                    visibilityChanged = true;
                    pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, textComp);
                    if (pane != null) {
                        pane.setIgnoreRepaint(true);
                        pane.getViewport().setIgnoreRepaint(true);
                        paintsDisabled = true;
//                    pane.getViewport().addChangeListener(ce -> {
//                        new Exception("VP CHANGE: " + pane.getViewport().getViewPosition()).printStackTrace();
//                    });
//                    comp.getCaret().addChangeListener(ce -> {
//                        new Exception("CARET CHANGE: " + comp.getCaret().getDot()).printStackTrace();
//                    });
                        viewPosition = pane.getViewport().getViewPosition();
                        LOG.log(Level.FINEST, "Repaints blocked; view-position {0}", viewPosition);
                        try {
                            Rectangle r = textComp.modelToView(caretPos);
                            distanceToTop = r.y - viewPosition.y;
                            LOG.log(Level.FINEST, "Collectioned component info {0}, {1}, {2}",
                                    new Object[]{viewPosition, distanceToTop, r});
                        } catch (BadLocationException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    }
                    textComp.setIgnoreRepaint(true);
                } catch (Exception ex) {
                    if (visibilityChanged) {
                        textComp.getCaret().setVisible(caretVisible);
                    }
                    if (paintsDisabled) {
                        pane.setIgnoreRepaint(false);
                        pane.getViewport().setIgnoreRepaint(false);
                    }
                    LOG.log(Level.INFO, "Exception disabling repaints", ex);
                }
            }
        }

        @Override
        public void after(DocumentOperationContext ctx) {
            LOG.log(Level.FINER, "{0} after on {1}", new Object[]{this, Thread.currentThread()});
            if (comp != null) {
                ctx.add(this);
//                run();
            }
        }

        @Override
        public void run() {
            try {
                JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, comp);
                int caretPos = comp.getCaretPosition();
                LOG.log(Level.FINER, "{0} after-on-eq", this);
                if (pane != null) {
                    if (distanceToTop > 0) {
                        try {
                            Rectangle newCaretBounds = comp.modelToView(caretPos);
                            LOG.log(Level.FINEST, "view position was {0} old "
                                    + "distance to top {1} new caret bounds {2}",
                                    new Object[]{viewPosition, distanceToTop, newCaretBounds});
                            newCaretBounds.y -= distanceToTop;
                            viewPosition = new Point(viewPosition.x, Math.max(0, newCaretBounds.y));
                            LOG.log(Level.FINEST, "view position now {0}", viewPosition);
                        } catch (BadLocationException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    } else {
                    }
                    pane.getViewport().setViewPosition(viewPosition);
                    comp.setIgnoreRepaint(false);
                    pane.setIgnoreRepaint(false);
                    pane.getViewport().setIgnoreRepaint(false);
                    pane.repaint();
                } else {
                    comp.setIgnoreRepaint(false);
                    comp.repaint();
                }
            } finally {
                comp.getCaret().setVisible(true);
            }
        }
    }

    static class DocumentReadLocker implements DocumentPreAndPostProcessor {

        private final Document doc;

        public DocumentReadLocker(Document doc) {
            this.doc = doc;
        }

        @Override
        public <T, E extends Exception> DocumentProcessor<T, E> wrap(DocumentProcessor<T, E> toWrap) {
            return new DocumentProcessor<T, E>() {
                @Override
                @SuppressWarnings("UseSpecificCatch")
                public T get(DocumentOperationContext ctx) throws E, BadLocationException {
                    LOG.log(Level.FINE, "Enter document-read-lock {0}", doc);
//                    if (doc instanceof BaseDocument && ((BaseDocument) doc).isAtomicLock()) {
//                        LOG.log(Level.FINER, "Already in atomic lock, don't need read lock, run {0} on {1}",
//                                new Object[]{toWrap, doc});
//                        return toWrap.get(ctx);
//                    }
                    return ctx.enterReadLockIfNotAlreadyLocked(doc, (shouldLock, onLockAcquired) -> {
                        if (shouldLock) {
                            T result = render(doc, () -> {
                                return toWrap.get(ctx);
                            });
                            return result;
                        } else {
                            T result = toWrap.get(ctx);
                            onLockAcquired.run();
                            return result;
                        }
                    });
                }

                @Override
                public String toString() {
                    return "read-lock-by-render(" + toWrap + ")";
                }
            };
        }
    }

    static class DocumentLockProcessor3 extends SingleEntryBeforeAfter {

        final boolean writeLock;
        final boolean asUser;
        volatile boolean didLock;

        DocumentLockProcessor3(StyledDocument doc, boolean writeLock, boolean asUser) {
            super(doc);
            this.writeLock = writeLock;
            this.asUser = asUser;
        }

        @Override
        public <T, E extends Exception> DocumentProcessor<T, E> wrap(DocumentProcessor<T, E> toWrap) {
            return new LockProcessor(toWrap);
        }

        public String toString() {
            if (writeLock) {
                return "WRITE-LOCK";
            } else if (asUser) {
                return "ATOMIC-AS-USER";
            } else {
                return "ATOMIC";
            }
        }

        private class LockProcessor<T, E extends Exception> implements DocumentProcessor<T, E> {

            private final DocumentProcessor<T, E> toWrap;

            public LockProcessor(DocumentProcessor<T, E> toWrap) {
                this.toWrap = toWrap;
            }

            @Override
            public String toString() {
                return DocumentLockProcessor3.this + "(" + toWrap + ")";
            }

            @Override
            public T get(DocumentOperationContext ctx) throws E, BadLocationException {
                if (writeLock && ctx.document() instanceof BaseDocument) {
                    return ctx.enterWriteLockIfNotAlreadyLocked(ctx.document(), new AtomicLockFunction<T, E>() {
                        @Override
                        public T apply(boolean needLock, Runnable onLocked) throws E, BadLocationException {
                            if (!needLock) {
                                T result = toWrap.get(ctx);
                                // This will probably always be a no-op in the write lock,
                                // but for consistency...
                                onLocked.run();
                                return result;
                            }
                            T result = runWriteLocked(doc, () -> {
                                return toWrap.get(ctx);
                            });
                            return result;
                        }

                        public String toString() {
                            return "ALF(" + DocumentLockProcessor3.this.toString()
                                    + "-" + toWrap.getClass().getSimpleName() + ")";
                        }
                    });
                } else if (writeLock) {
                    // XXX - do what?  writeLock() is protected in AbstractDocument
                    // render() is perhaps as good as it gets, but in practice,
                    // it will likely almost always be a BaseDocument unless
                    // someone implements their own EditorKit and Document from
                    // scratch
                    T result = render(ctx.document(), () -> {
                        return toWrap.get(ctx);
                    });
                    return result;
                } else if (asUser) {
                    return ctx.enterAtomicLockIfNotAlreadyLocked(ctx.document(), new AtomicLockFunction<T, E>() {
                        @Override
                        public T apply(boolean needLock, Runnable onLockAcquired) throws E, BadLocationException {
                            if (needLock) {
                                return runAtomicAsUser(ctx.document(), () -> {
                                    try {
                                        return toWrap.get(ctx);
                                    } finally {
                                        onLockAcquired.run();
                                    }
                                });
                            } else {
                                T result = toWrap.get(ctx);
                                onLockAcquired.run();
                                return result;
                            }
                        }

                        public String toString() {
                            return "ALF(" + DocumentLockProcessor3.this.toString()
                                    + "-" + toWrap.getClass().getSimpleName() + ")";
                        }

                    });
                } else {
                    return ctx.enterAtomicLockIfNotAlreadyLocked(ctx.document(), new AtomicLockFunction<T, E>() {
                        @Override
                        public T apply(boolean needLock, Runnable onLockAcquired) throws E, BadLocationException {
                            if (needLock) {
                                return runAtomic(ctx.document(), () -> {
                                    try {
                                        return toWrap.get(ctx);
                                    } finally {
                                        onLockAcquired.run();
                                    }
                                });
                            } else {
                                T result = toWrap.get(ctx);
                                onLockAcquired.run();
                                return result;
                            }
                        }

                        public String toString() {
                            return "ALF(" + DocumentLockProcessor3.this.toString()
                                    + "-" + toWrap.getClass().getSimpleName() + ")";
                        }
                    });
                }
            }
        }

        static final class AWTTreeLocker extends SingleEntryBeforeAfter {

            AWTTreeLocker(StyledDocument doc) {
                super(doc);
            }

            @Override
            public <T, E extends Exception> DocumentProcessor<T, E> wrap(DocumentProcessor<T, E> toWrap) {
                DocumentProcessor<T, E> superWrap = super.wrap(toWrap);
                if (superWrap != toWrap) {
                    // non-reentrant
                    Component c = findComponent(doc);
                    if (c != null) {
                        return new WithTreeLock(superWrap, c);
                    }
                }
                // reentrant call, just do the thing
                return toWrap;
            }

            @Override
            public String toString() {
                return "AWT-TREE-LOCK";
            }

            static class WithTreeLock<T, E extends Exception> implements DocumentProcessor<T, E> {

                private final DocumentProcessor<T, E> delegate;
                private final Component comp;

                public WithTreeLock(DocumentProcessor<T, E> delegate, Component comp) {
                    this.delegate = delegate;
                    this.comp = comp;
                }

                @Override
                public T get(DocumentOperationContext ctx) throws E, BadLocationException {
                    T result;
                    LOG.log(Level.FINER, "{0} on {1} with {2} ENTER",
                            new Object[]{this, Thread.currentThread(), comp});
                    synchronized (comp.getTreeLock()) {
                        result = delegate.get(ctx);
                    }
                    LOG.log(Level.FINER, "{0} on {1} with {2} EXIT",
                            new Object[]{this, Thread.currentThread(), comp});
//                EventQueue.invokeLater(() -> {
//                comp.invalidate();
//                comp.revalidate();
//                comp.repaint();
//                });
                    return result;
                }

                @Override
                public String toString() {
                    return "AWT-TREE-LOCK(" + delegate + ")";
                }
            }
        }

        static class Hold<T, E extends Exception> implements Supplier<T> {

            T obj;
            Exception thrown;

            void set(T obj) {
                this.obj = obj;
            }

            void thrown(Exception e) {
                this.thrown = e;
            }

            void rethrow() {
                if (thrown != null) {
                    com.mastfrog.util.preconditions.Exceptions.chuck(thrown);
                }
            }

            public T get() {
                rethrow();
                return obj;
            }
        }

        static <T, E extends Exception> T render(Document doc, BadLocationSupplier<T, E> supp) throws BadLocationException, E {
            Hold<T, E> hold = new Hold<>();
            doc.render(() -> {
                try {
                    hold.set(supp.get());
                } catch (Exception ex) {
                    hold.thrown(ex);
                }
            });
            return hold.get();
        }

        static <T, E extends Exception> T runAtomic(StyledDocument doc, BadLocationSupplier<T, E> supp) throws BadLocationException, E {
            Hold<T, E> hold = new Hold<>();
            NbDocument.runAtomic(doc, () -> {
                try {
                    hold.set(supp.get());
                } catch (Exception ex) {
                    hold.thrown(ex);
                }
            });
            return hold.get();
        }

        static <T, E extends Exception> T runAtomicAsUser(StyledDocument doc, BadLocationSupplier<T, E> supp) throws BadLocationException, E {
            Hold<T, E> hold = new Hold<>();
            NbDocument.runAtomicAsUser(doc, () -> {
                try {
                    hold.set(supp.get());
                } catch (Exception ex) {
                    hold.thrown(ex);
                }
            });
            return hold.get();
        }

        static <T, E extends Exception> T runWriteLocked(StyledDocument doc, BadLocationSupplier<T, E> supp) throws BadLocationException, E {
            if (!(doc instanceof BaseDocument)) {
                return supp.get();
            }
            BaseDocument bd = (BaseDocument) doc;
            bd.extWriteLock();;
            try {
                return supp.get();
            } finally {
                bd.extWriteUnlock();
            }
        }

        static boolean isAtomicLocked(Document doc) {
            return doc instanceof BaseDocument && ((BaseDocument) doc).isAtomicLock();
        }

        static final class UndoTransaction extends SingleEntryBeforeAfter {

            public UndoTransaction(StyledDocument doc) {
                super(doc);
            }

            @Override
            String id() {
                return getClass().getSimpleName();
            }

            @Override
            public void before(DocumentOperationContext ctx) {
                LOG.log(Level.FINER, "{0} before on {1}", new Object[]{this, Thread.currentThread()});
                ctx.sendUndoableEdit(CloneableEditorSupport.BEGIN_COMMIT_GROUP);
            }

            @Override
            public void after(DocumentOperationContext ctx) {
                LOG.log(Level.FINER, "{0} after on {1}", new Object[]{this, Thread.currentThread()});

//            ctx.sendUndoableEdit(CloneableEditorSupport.END_COMMIT_GROUP);
            }

            @Override
            public String toString() {
                return "ONE-UNDO-TRANSACTION";
            }
        }

        /**
         * We could use the method in EditorUtilities, except that at the time
         * it would add the event, we don't know if the reason for having this
         * edit has succeeded or not; with this, we can create the event before
         * any modifications are made, and only apply it if the transaction
         * succeeds.
         */
        static final class CaretPositionUndoableEdit extends AbstractUndoableEdit {

            private int dot;
            private int mark;
            private Rectangle undoRect;
            private final JTextComponent comp;
            private Caret caret;
            private Position redoPosition;
            private Position redoMark;
            private Rectangle redoRect;
            private Document doc;
            private boolean undone;

            public CaretPositionUndoableEdit(JTextComponent comp, Document doc) throws BadLocationException {
                this.caret = comp.getCaret();
                // We do NOT want to use a Position instance for these - Antlr
                // reformatting, and potentially other things do a wholesale replacement
                // of the document contents, in which case all Positions created
                // prior to the operation are either 0 or document-length.
                this.dot = caret.getDot();
                this.mark = caret.getMark();
                this.undoRect = comp.getVisibleRect();
                this.comp = comp;
                this.doc = doc;
            }

            @Override
            public void die() {
                caret = null;
                redoPosition = null;
                redoMark = null;
                doc = null;
                undoRect = null;
                redoRect = null;
            }

            private void updateRedoInfo() {
                try {
                    redoPosition = doc.createPosition(caret.getDot());
                    redoMark = doc.createPosition(caret.getMark());
                    redoRect = comp.getVisibleRect();
                    LOG.log(Level.FINEST, "Collect caret redo info {0}, {1}, {2}",
                            new Object[]{redoPosition, redoMark, redoRect});
                } catch (BadLocationException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }

            @Override
            public void undo() throws CannotUndoException {
                undone = true;
                EventQueue.invokeLater(() -> {
                    try {
                        LOG.log(Level.FINE, "Caret-undo to {0}, {1}", new Object[]{mark, dot});
                        updateRedoInfo();
                        Position dotPos = NbDocument.createPosition(doc, dot, dot < mark ? Bias.Forward : Bias.Backward);
                        Position markPos = dot == mark ? dotPos
                                : NbDocument.createPosition(doc, mark, dot < mark ? Bias.Backward : Bias.Forward);
                        updateCaret(dotPos, markPos);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    comp.scrollRectToVisible(undoRect);
                });
            }

            private void updateCaret(Position dotPosition, Position markPosition) {
                if (caret instanceof EditorCaret) {
                    EditorCaret ec = (EditorCaret) caret;
                    ec.moveCarets((CaretMoveContext context) -> {
                        CaretInfo ct = context.getOriginalLastCaret();
                        context.setDotAndMark(ct, dotPosition, Bias.Forward, markPosition, Bias.Backward);
                    }, MoveCaretsOrigin.DISABLE_FILTERS);
                } else {
                    int pos = dotPosition.getOffset();
                    int mk = markPosition.getOffset();
                    if (mk == pos) {
                        caret.setDot(pos);
                    } else {
                        caret.setDot(mk);
                        if (mk != pos) {
                            caret.moveDot(pos);
                        }
                    }
                }
            }

            @Override
            public boolean canUndo() {
                return !undone && doc != null;
            }

            @Override
            public void redo() throws CannotRedoException {
                undone = false;
                EventQueue.invokeLater(() -> {
                    try {
                        updateCaret(redoPosition, redoMark);
                    } finally {
                        comp.scrollRectToVisible(redoRect);
                    }
                });
            }

            @Override
            public boolean canRedo() {
                return undone && doc != null && redoPosition != null;
            }

            @Override
            public boolean isSignificant() {
                return false;
            }
        }
    }
}

package org.nemesis.antlrformatting.spi;

import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.NavigationFilter;
import javax.swing.text.Position;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import org.nemesis.antlrformatting.impl.CaretInfo;
import static org.nemesis.antlrformatting.spi.DocumentReformatRunner.tryCatch;
import static org.nemesis.antlrformatting.spi.DocumentReformatRunner.tryCatchRun;
import org.nemesis.misc.utils.function.ThrowingConsumer;
import org.nemesis.misc.utils.function.ThrowingRunnable;
import org.netbeans.api.editor.document.CustomUndoDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 * Implements a bunch of evil, tortured stuff to keep the caret in the right
 * place during a reformat when the document contents are getting whacked,
 * avoiding flashing and scrolling around, and repositioning the editor's scroll
 * pane so that the visual position of the caret on screen does not move at all,
 * unless the reformatting is such that that's impossible.
 * <p>
 * The Context interface from the editor.indent does provide some convenience
 * methods for modifying whitespace. However, since we're using Antlr's
 * TokenStreamRewriter to generate the result (which may alter non-whitespace
 * tokens), what we really need to do is just clobber the document or part of
 * it, while disabling any repaints or other responses to our changes until we
 * are done (have not figured out a clean way to disable the parser
 * infrastructure for the file - could block in ParserHelper though). Also, the
 * caret handling in Context will not work, since it is Position based, and
 * nuking the document content zeros out all stored positions. Antlr is more
 * than happy to tell us the new caret position based on the old one, though, so
 * we simply do our own integer based setting of that after document rewriting
 * completes.
 * </p>
 */
final class EditorScrollPositionManager implements Runnable {

    private final JTextComponent comp;
    private final NavigationFilter origNavigationFilter;
    private int runCount;
    private final AtomicReference<CaretInfo> origCaretPos = new AtomicReference<>(CaretInfo.NONE);
    private final Supplier<CaretInfo> newCaretPos;
    private ThrowingConsumer<CaretInfo> cursorPosAcceptor;
    private static final RequestProcessor FORMAT_PROC
            = new RequestProcessor("antlr-formatter", 1);
    private ScrollPositionComputer repositionCaret;
    private final CaretPosUndoableEdit edit = new CaretPosUndoableEdit();

    EditorScrollPositionManager(JTextComponent comp, Supplier<CaretInfo> newCaretPos) {
        this.newCaretPos = newCaretPos;
        this.comp = comp;
        origNavigationFilter = comp == null ? null : comp.getNavigationFilter();
    }

    /**
     * The main entry point - this first disables repaints, automatic scrolling
     * on caret moves and caret repaints, on the event thread, acquiring the
     * caret position at that point. After that, it runs the passed
     * ThrowingIntConsumer which actually rewrites the document on a background
     * thread, and then, back on the AWT thread, it re-enables all those things.
     *
     * @param cursorPosAcceptor
     * @throws Exception
     */
    public Runnable invokeWithEditorDisabled(ThrowingConsumer<CaretInfo> cursorPosAcceptor) throws Exception {
        // Store the consumer to be invoked on the background thread with the
        // caret position retrievd on the even thread
        synchronized (this) {
            this.cursorPosAcceptor = cursorPosAcceptor;
        }
        // Go get the caret position and disable the things we need to disable
        EventQueue.invokeLater(this);
        return this::addCaretPositionUndoableEdit;
    }

    private Optional<JScrollPane> scrollPane() {
        return comp == null ? Optional.empty()
                : Optional.ofNullable((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, comp));
    }

    @Override
    public void run() {
        assert EventQueue.isDispatchThread();
        // Determine if we're on the disable run or the reenable run
        switch (runCount++) {
            case 0:
                // If the component is offscreen, we can avoid fiddling
                // with it, so check isDisplayable() to determine if it has
                // a parent.  It's not likely that we can be called as the
                // editor is being removed from the component hierarchy, but
                // not impossible, and I've seen stranger things.
                boolean disablePhaseSucceeded = comp == null || !comp.isDisplayable();
                if (!disablePhaseSucceeded) {
                    // Regardless if somethibg goes wrong, we want
                    // the re-enable phase to run, so letting this
                    // throw makes no sense
                    disablePhaseSucceeded = tryCatchRun(this::disable);
                }
                if (disablePhaseSucceeded) {
                    // Okay, time to do the work
                    FORMAT_PROC.submit(tryCatch(() -> {
                        try {
                            ThrowingConsumer<CaretInfo> cons;
                            synchronized (this) {
                                cons = cursorPosAcceptor;
                            }
                            // Here is where we trigger the real work,
                            // which is a lambda passed in from
                            // DocumentReformatRunner
                            cons.accept(origCaretPos.get());
                        } finally {
                            EventQueue.invokeLater(this);
                        }
                    }));
                } else {
                    // Failed - just try to reenable everything so we don't
                    // leave a mess
                    EventQueue.invokeLater(this);
                }
                break;

            case 1:
                // Reenable everything and reposition the caret
                if (comp != null) {
                    tryCatchRun(this::enable);
                }
                break;
            default:
                throw new IllegalStateException("Should be run "
                        + "only 2x - " + (runCount + 1));
        }
    }

    /**
     * Adds an edit to those in the current transaction. This is called by the
     * DocumentReformatRunner under the document lock, so it is added to the
     * edit set the replacement is part of.
     *
     * @param caretPos
     */
    void addCaretPositionUndoableEdit() {
        System.out.println("ADDING CUSTOM EDIT");
        CustomUndoDocument customUndoDocument
                = LineDocumentUtils.as(comp.getDocument(),
                        CustomUndoDocument.class);
        if (customUndoDocument != null) {
            customUndoDocument.addUndoableEdit(edit);
        }
    }

    /**
     * This actually supplies the implementation of our undoable edit - it must
     * be added while the document is locked, but the second caret position
     * isn't known until that code has exited and unlocked, and we are on the
     * EDT again to compute it. So we create a stub undoable edit which only
     * works if that succeeds, but can be added immediately.
     *
     * @param undoCaretPositionChange
     */
    private void setEditImplementation(ScrollAdjuster undoCaretPositionChange) {
        System.out.println("SET EDIT IMPL " + undoCaretPositionChange);
        edit.setUndoRunner(undoCaretPositionChange);
    }

    private void disable() throws BadLocationException {
        // Disable everything that could respond while the document is being
        // rewritten, which would otherwise result in scrolling around and
        // repainting and winding up mis-positioned (the caret should stay
        // in the same pixel location on screen whenever possible)
        assert comp != null;
        Caret caret = comp.getCaret();
        if (caret != null) { // Believe it or not, during removal, it can be null
            caret.setVisible(false);
        }
        // Okay, get the caret position
        CaretInfo cp = CaretInfo.create(comp);
        origCaretPos.set(cp);
        // Get an object we can call that will do the initial half of our
        // scroll repositioning computation, and provide a runnable to do
        // the actual reposition once called with the new caret position.
        // We do not need to synchronize on repositionCaret because it is
        // only accessed from the EDT
        repositionCaret = computeScrollTo(cp);
        // Disable any sort of automatic scrolling, and turn off repainting on
        // this, it's parent viewport and scroll pane
        comp.setIgnoreRepaint(true);
        comp.setAutoscrolls(false);
        Optional<JScrollPane> pane = scrollPane();
        pane.ifPresent(js -> {
            js.setIgnoreRepaint(true);
            js.getViewport().setIgnoreRepaint(true);
            // Nuke any already enqueued repaints
            RepaintManager.currentManager(js)
                    .markCompletelyClean(js);
            RepaintManager.currentManager(js.getViewport())
                    .markCompletelyClean(js);
        });
        // Turn off any ability for the caret to move at all
        comp.setNavigationFilter(new NoNavigationFilter());
        RepaintManager.currentManager(comp).markCompletelyClean(comp);
    }

    private void enable() throws Exception {
        assert EventQueue.isDispatchThread();
        assert comp != null;
        Optional<JScrollPane> pane = scrollPane();
        synchronized (comp.getTreeLock()) {
            // Restore the ability for the caret to be moved before
            // we try to move the caret :-)
            comp.setNavigationFilter(origNavigationFilter);
            // Get a runnable which will compute everything we need,
            // and apply our caret move, if any is needed
            ScrollAdjuster scrollIn = updateCaret();
            if (scrollIn != null) {
                setEditImplementation(scrollIn);
            }
            comp.setAutoscrolls(true);
            comp.setIgnoreRepaint(false);
            pane.ifPresent((js) -> {
                js.setIgnoreRepaint(false);
                js.getViewport().setIgnoreRepaint(false);
            });
            Caret caret = comp.getCaret();
            if (caret != null) { // really can be null
                caret.setVisible(true);
            }
        }
        // Now the repainting is turned back on, repaint EVERYTHING
        RepaintManager.currentManager(comp).markCompletelyDirty(comp);
        pane.ifPresent(js -> {
            RepaintManager.currentManager(js)
                    .markCompletelyDirty(js);
            RepaintManager.currentManager(js.getViewport())
                    .markCompletelyDirty(js.getViewport());
        });
    }

    private ScrollAdjuster updateCaret() throws Exception {
        assert comp != null;
        assert EventQueue.isDispatchThread();
        CaretInfo orig = origCaretPos.get();
        if (newCaretPos != null && orig.isViable()) {
            CaretInfo newPos = newCaretPos.get();
            if (newPos.isViable()) {
                // Actually set the caret position
//                comp.setCaretPosition(newPos);
                newPos.apply(comp);
                if (repositionCaret != null) {
                    // Let the caret repositioner figure out how to do a
                    // precise adjustment of the viewports view location
                    return repositionCaret.receiveNewCaretPosition(newPos);
                } else {
                    // Something was missing, and we have a component, but
                    // could not find a scroll pane - just try to work with
                    // the new caret position and make sure it's not off-screen
                    return new SimpleScrollAdjuster(comp, newPos.start(),
                            origCaretPos.get())
                            .doAdjust(comp);
                }
            }
        }
        System.out.println("NO SCROLL ADJUSTER FOR " + newCaretPos + " and " + origCaretPos);
        return null;
    }

    private ScrollPositionComputer computeScrollTo(CaretInfo caretPos) throws BadLocationException {
        Optional<JScrollPane> pane = scrollPane();
        if (pane.isPresent()) {
            // Compare the old and new caret rectangles, and shift the viewport
            // by the difference - this should result in the caret remaining in
            // EXACTLY the same spot on screen, except if that would mean
            // scrolling past the edge of a document
            Rectangle oldCaretBounds = caretPos.bounds(comp);
            return newCaretPos -> {
                if (newCaretPos.isViable()) {
                    Rectangle newCaretBounds = newCaretPos.bounds(comp);

                    int xOff = newCaretBounds.x - oldCaretBounds.x;
                    int yOff = newCaretBounds.y - oldCaretBounds.y;
                    // We aren't necessarily ready to *do* this, so return a
                    // runnable that can take care of it
                    return new ScrollAdjusterImpl(xOff, yOff,
                            pane.get(), caretPos, newCaretPos, comp)
                            .doAdjust(pane.get());
                }
                return new SimpleScrollAdjuster(comp, 0, caretPos);
            };
        }
        return null;
    }

    @FunctionalInterface
    public interface CursorPositionRewriteTrigger {

        /**
         *
         * @param cursorPos
         * @return
         * @throws Exception
         */
        boolean withOriginalCaretPosition(int caretPos) throws Exception;
    }

    /**
     * Gets the original caret bounds before document alteration, and returns a
     * runnable which will use those bounds to compute an offset and take care
     * of adjusting the editor's scroll pane (if any).
     */
    @FunctionalInterface
    interface ScrollPositionComputer {

        /**
         * Compute the scroll position, returning a Supplier which, when called,
         * will set the scroll position and return a Runnable which can restore
         * the scroll position (for the undo action).
         *
         * @param cursorPos The caret position that should be centered on the
         * original caret position if possible
         * @return A supplier which, when its get method is called, will update
         * the editor's viewport's view position and return a runnable which
         * will undo that.
         * @throws Exception If something goes wrong
         */
        public ScrollAdjuster receiveNewCaretPosition(CaretInfo cursorPos) throws Exception;
    }

    interface ScrollAdjuster<T extends JComponent> {

        void undo();

        void redo();

        boolean isAlive();

        ScrollAdjuster doAdjust(T scroll) throws BadLocationException;

        public void undoAdjust(T scroll) throws BadLocationException;
    }

    /**
     * This actually does the caret and scroll position adjustments, and is
     * careful not to hold a reference to the entire reformat job, since it can
     * be on the undo stack for a long time.
     */
    static final class ScrollAdjusterImpl implements ScrollAdjuster<JScrollPane>, ThrowingRunnable {

        private final int xOff, yOff;
        private final Reference<JScrollPane> pane;
        private final Reference<JTextComponent> comp;
        private final CaretInfo oldPosition;
        private final CaretInfo newPosition;

        ScrollAdjusterImpl(int x, int y, JScrollPane pane, CaretInfo oldPosition,
                CaretInfo newPosition, JTextComponent comp) {
            this.xOff = x;
            this.yOff = y;
            this.pane = new WeakReference<>(pane);
            // Evidently sometimes the viewport view gets replaced with an
            // NbEditorUI$LayeredEditorPane, so we cannot rely on accessing
            // the editor component through getViewport().getView() - so
            // keep our own reference to it
            this.comp = new WeakReference<>(comp);
            this.oldPosition = oldPosition;
            this.newPosition = newPosition;
        }

        @Override
        public boolean isAlive() {
            return valid(pane.get(), comp.get());
        }

        private boolean valid(JScrollPane pane, JTextComponent comp) {
            return pane != null && pane.isDisplayable()
                    && comp != null && comp.isDisplayable();
        }

        public void run() throws BadLocationException {
            // Undo implementation here
            JTextComponent comp = this.comp.get();
            JScrollPane pane = this.pane.get();
            if (valid(pane, comp)) {
                System.out.println("  HAVE VALID PANE");
                undoAdjust(pane);
            }

        }

        @Override
        public void undo() {
            JTextComponent comp = this.comp.get();
            if (comp != null) {
                new TriggerDocumentListener(this, comp.getDocument())
                        .start();
            }
        }

        @Override
        public void redo() {
            System.out.println("REDO OFF EDT");
            EventQueue.invokeLater(tryCatch(() -> {
                System.out.println("REDO ON EDT");
                JScrollPane pane = this.pane.get();
                if (valid(pane, comp.get())) {
                    System.out.println("  HAVE VALID PANE");
                    doAdjust(pane);
                }
            }));
        }

        @Override
        public ScrollAdjusterImpl doAdjust(JScrollPane scroll) throws BadLocationException {
            System.out.println("DO ADJUST " + xOff + "," + yOff);
            JTextComponent comp = this.comp.get();
            if (valid(scroll, comp)) {
                newPosition.apply(comp);
                Point p = scroll.getViewport().getViewPosition();
                Rectangle r = scroll.getViewport().getView().getBounds();
                // Ensure we can't scroll beyond the bounds of the
                // component - JScrollPane *will* let you set the
                // X position to a negative number and show a black
                // bar until you hit zero
                p.x = Math.min(r.width - (p.x + xOff), Math.max(0, p.x + xOff));
                p.y = Math.min(r.height - (p.y + yOff), Math.max(0, p.y + yOff));
                pane.get().getViewport().setViewPosition(p);
                System.out.println("DO ADJUST TO " + p);
            }
            return this;
        }

        @Override
        public void undoAdjust(JScrollPane scroll) throws BadLocationException {
            System.out.println("UNDO ADJUST " + xOff + "," + yOff);
            JTextComponent comp = this.comp.get();
            if (valid(scroll, comp)) {
                oldPosition.apply(comp);
                Point p = pane.get().getViewport().getViewPosition();
                Rectangle r = scroll.getViewport().getView().getBounds();
                p.x = Math.min(r.width - (p.x - xOff), Math.max(0, p.x - xOff));
                p.y = Math.min(r.height - (p.y - yOff), Math.max(0, p.y - yOff));
                pane.get().getViewport().setViewPosition(p);
                System.out.println("UNDO ADJUST TO " + p);
            } else {
                System.out.println("NOT VALID");
            }
        }
    }

    /**
     * Alternate implementation if something goes wrong.
     */
    static final class SimpleScrollAdjuster implements ScrollAdjuster<JTextComponent> {

        private final Reference<JTextComponent> comp;
        private final int newCaretPosition;
        private final CaretInfo oldCaretPosition;

        SimpleScrollAdjuster(JTextComponent comp, int newCaretPosition, CaretInfo oldCaretPosition) {
            this.comp = new WeakReference<>(comp);
            this.newCaretPosition = newCaretPosition;
            this.oldCaretPosition = oldCaretPosition;
        }

        boolean valid(JComponent comp) {
            return comp != null && comp.isDisplayable();
        }

        @Override
        public void undo() {
            JTextComponent comp = this.comp.get();
            if (comp != null) {
                new TriggerDocumentListener(() -> {
                    undoAdjust(comp);
                }, comp.getDocument()).start();
            }
        }

        @Override
        public void redo() {
            JTextComponent comp = this.comp.get();
            if (comp != null) {
                new TriggerDocumentListener(() -> {
                    doAdjust(comp);
                }, comp.getDocument()).start();
            }
        }

        @Override
        public boolean isAlive() {
            return valid(comp.get());
        }

        @Override
        public ScrollAdjuster doAdjust(JTextComponent scroll) {
            System.out.println("SIMPLE SCROLL DO ADJUST");
            if (valid(scroll)) {
                try {
                    int pos = newCaretPosition < 0 ? 0 : newCaretPosition;
                    scroll.setCaretPosition(pos);
                    Rectangle rect = scroll.modelToView2D(pos).getBounds();
                    scroll.scrollRectToVisible(rect);
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            return this;
        }

        @Override
        public void undoAdjust(JTextComponent scroll) {
            System.out.println("SIMPLE SCROLL DO UNADJUST");
            if (valid(scroll)) {
                try {
                    oldCaretPosition.apply(scroll);
                    Rectangle rect = oldCaretPosition.bounds(scroll);
                    scroll.scrollRectToVisible(rect);
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    /**
     * The most effective way to ensure the caret adjustment happens *after* the
     * document has been updated is to listen for changes on it, then push the
     * caret adjustment out on the EDT once an insert and a remove have
     * happened.
     */
    static final class TriggerDocumentListener implements DocumentListener {

        private int changeCount;
        private final ThrowingRunnable run;
        private final Document doc;

        TriggerDocumentListener(ThrowingRunnable run, Document doc) {
            this.run = run;
            this.doc = doc;
        }

        void start() {
            doc.addDocumentListener(this);
        }

        private void changed() {
            if (++changeCount == 2) {
                doc.removeDocumentListener(this);
                EventQueue.invokeLater(tryCatch(run));
            }
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            changed();
        }
    }

    // A navigation filter that fully disables caret moves
    static final class NoNavigationFilter extends NavigationFilter {

        @Override
        public int getNextVisualPositionFrom(JTextComponent text,
                int pos, Position.Bias bias, int direction,
                Position.Bias[] biasRet) throws BadLocationException {
            return pos;
        }

        @Override
        public void moveDot(FilterBypass fb, int dot, Position.Bias bias) {
            // do nothing
        }

        @Override
        public void setDot(FilterBypass fb, int dot, Position.Bias bias) {
            // do nothing
        }
    }

    private static class CaretPosUndoableEdit extends AbstractUndoableEdit {

        private ScrollAdjuster caretUndoer;

        @Override
        public void die() {
            System.out.println("DIE!");
            caretUndoer = null;
            super.die();
        }

        @Override
        public boolean isSignificant() {
            return false;
        }

        @Override
        public boolean canRedo() {
            return caretUndoer != null && caretUndoer.isAlive();
        }

        @Override
        public boolean canUndo() {
            return canRedo();
        }

        @Override
        public void undo() throws CannotUndoException {
            System.out.println("UNDO!");
            if (caretUndoer == null) {
                throw new CannotUndoException();
            }
            caretUndoer.undo();
        }

        @Override
        public void redo() throws CannotRedoException {
            System.out.println("REDO!");
            if (caretUndoer == null) {
                throw new CannotRedoException();
            }
            caretUndoer.redo();
        }

        private void setUndoRunner(ScrollAdjuster undoCaretPositionChange) {
            this.caretUndoer = undoCaretPositionChange;
        }
    }
}

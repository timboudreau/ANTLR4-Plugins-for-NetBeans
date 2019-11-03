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

import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import javax.swing.text.Position.Bias;
import javax.swing.text.StyledDocument;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.caret.CaretInfo;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.api.editor.caret.MoveCaretsOrigin;
import org.netbeans.api.editor.document.CustomUndoDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.spi.editor.caret.CaretMoveHandler;
import org.netbeans.spi.lexer.MutableTextInput;
import org.netbeans.spi.lexer.TokenHierarchyControl;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.NbDocument;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Run reentrant operations against a document, employing a number of settable
 * features (use <code>builder()</code> to enable them).
 * <ul>
 * <li><b>Block repaints</b> &mdash; Try to avoid editor repaints until all
 * operations are completed</li>
 * <li><b>Write lock</b> &mdash; Write lock the document, running with
 * <code>NbDocument.runAtomic()</code> (mutually exclusive with <i>write lock as
 * user</i>)</li>
 * <li><b>Write lock as user</b> &mdash; Write lock the document as the user
 * (behaves differently with guarded blocks than plain write lock), running the
 * operation with <code>NbDocument.runAtomicAsUser()</code> (mutually exclusive
 * with <i>write lock</i>)</li>
 * <li><b>Read lock</b> &mdash; Read-lock the document, running the operation
 * inside of <code>Document.render()</code>. <i>Not</i> mutually exclusive with
 * write locking.</li>
 * <li><b>One undoable edit</b> &mdash; If multiple distinct changes are made to
 * the document, a single undoable edit will be generated to undo all of them as
 * a single edit.</li>
 * <li><b>Preserve caret position</b> &mdash; Try to restore the caret position
 * after performing changes, so that the editor caret position is restored to
 * its current location (otherwise, inserts or deletions will cause it to move);
 * in combination with
 * <i>block repaints</i> the editor on-screen will "jump" minimally if at all.
 * (this also generates a re-scrolling undo event which repositions the caret
 * and scroll position)</li>
 * <li><b>Disable token hierarchy updates</b> &mdash; This blocks the lexer
 * infrastructure from initiating a re-lex or re-parse until all operations have
 * been completed.</li>
 * </ul>
 * <p>
 * Reentrant calls will not acquire the same lock or similar for the same
 * document twice; reentry is handled
 * with ThreadLocals, so that is the case whether or not code is reentering the
 * same or a different document operator.  <i>Same document</i> in this case
 * means identity equality, not <code>equals()</code> equality.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class DocumentOperator {

    static final Logger LOG = Logger.getLogger( DocumentOperator.class.getName() );
    private final Set<Props> props;

    /**
     * A default instance for modifying a document which is open in the editor,
     * while avoiding temporary changes to the scroll position and unexpected
     * caret moves.
     */
    public static final DocumentOperator NON_JUMP_REENTRANT_UPDATE_DOCUMENT
            = new DocumentOperator( EnumSet.of(
                    Props.WRITE_LOCK,
                    Props.PRESERVE_CARET_POSITION,
                    Props.ACQUIRE_AWT_TREE_LOCK,
                    Props.READ_LOCK,
                    Props.DISABLE_MTI,
                    Props.ONE_UNDOABLE_EDIT,
                    Props.BLOCK_REPAINTS ) );

    DocumentOperator( Set<Props> props ) {
        this.props = props;
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
    public void runOnEventQueue( StyledDocument doc, Runnable run ) {
        DocumentOperation x = operateOn( doc );
        runOnEq( () -> {
            x.run( run );
        } );
    }

    /**
     * Run the operation on a document, logging any exceptions.
     *
     * @param doc A document
     * @param run A runnable
     */
    public void run( StyledDocument doc, Runnable run ) {
        operateOn( doc ).run( run );
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + Strings.join( ", ", props ) + ")";
    }

    /**
     * Create a single-use runner which can be used to perform your operation.
     *
     * @param <T> The return type of the operation
     * @param <E> The exception type of the operation (use RuntimeException if
     *            the operation does not throw any checked exception)
     * @param doc A document to run against
     *
     * @return An operation
     */
    public <T, E extends Exception> DocumentOperation<T, E> operateOn( StyledDocument doc ) {
        return new DocumentOperation<>( notNull( "doc", doc ), props );
    }

    static <T, E extends Exception> BleFunction<T, E> runner( StyledDocument doc, Props... props ) {
        Arrays.sort( props, Comparator.<Props>naturalOrder().reversed() );
        return ( DocumentProcessor<T, E> supp ) -> {
            LOG.log( Level.FINE, "Apply {0} to {1} with {2}", new Object[]{ supp, doc, Arrays.asList( props ) } );
            for ( Props p : props ) {
                supp = p.apply( doc ).wrap( supp );
            }
            supp = new PostRunOperations<>( supp );
            return supp.get();
        };
    }

    private static final ThreadLocal<List<Runnable>> POST_OPS = new ThreadLocal<>();

    static void addPostRunOperation( Runnable r ) {
        List<Runnable> ops = POST_OPS.get();
        if ( ops == null ) {
            throw new AssertionError( "Called outside scope with " + r );
        }
        ops.add( r );
    }

    static class PostRunOperations<T, E extends Exception> implements DocumentProcessor<T, E> {
        private final DocumentProcessor<T, E> delegate;

        public PostRunOperations( DocumentProcessor<T, E> delegate ) {
            this.delegate = delegate;
        }

        private void postOpRun( Runnable r ) {
//            r.run();
            runOnEq( r );
//            EventQueue.invokeLater( r );
        }

        @Override
        public T get() throws E, BadLocationException {
            List<Runnable> ops = POST_OPS.get();
            boolean wasNull = ops == null;
            if ( wasNull ) {
                ops = new ArrayList<>();
                POST_OPS.set( ops );
            }
            try {
                return delegate.get();
            } finally {
                if ( wasNull ) {
                    POST_OPS.remove();
                }
                if ( !ops.isEmpty() ) {
                    final List<Runnable> all = new ArrayList<>( ops );
                    LOG.log( Level.FINE, "Run {0} post-run operations on eq: {1}",
                             new Object[]{ all.size(), all } );
                    postOpRun( () -> {
                        for ( Runnable run : all ) {
                            run.run();
                        }
                    } );
                }
            }
        }
    }

    public static DocumentOperationBuilder builder() {
        return new DocumentOperationBuilder();
    }

    interface BleFunction<T, E extends Exception> extends ThrowingFunction<DocumentProcessor<T, E>, T> {

        @Override
        T apply( DocumentProcessor<T, E> arg ) throws BadLocationException, E;

    }

    enum Props implements Function<StyledDocument, BeforeAfter> {
        // These are in a very specific order they need to be
        // applied in
        ACQUIRE_AWT_TREE_LOCK, // dangerous off EQ but blocks all revalidation - run in synchronized(comp.getTreeLock())
        BLOCK_REPAINTS, // this should run first - does not depend on doc contents
        WRITE_LOCK, // NbDocument.runAtomic
        WRITE_LOCK_AS_USER, // NbDocument.runAtomicAsUser
        READ_LOCK, // read lock must be acquired after write lock
        DISABLE_MTI, // mti must be touched under read and write lock
        ONE_UNDOABLE_EDIT, // need to be in the undo transaction before we add our caret restoring edit
        PRESERVE_CARET_POSITION, // Try to reset the caret position to someplace sane
        ;

        @Override
        public BeforeAfter apply( StyledDocument doc ) {
            switch ( this ) {
                case READ_LOCK:
                    return new DocumentWriteLocker( doc, false, false );
                case WRITE_LOCK:
                    return new DocumentWriteLocker( doc, true, false );
                case WRITE_LOCK_AS_USER:
                    return new DocumentWriteLocker( doc, true, true );
                case BLOCK_REPAINTS:
                    return new BlockRepaints( doc );
                case ONE_UNDOABLE_EDIT:
                    return new UndoTransaction( doc );
                case DISABLE_MTI:
                    return new DisableMTI( doc );
                case PRESERVE_CARET_POSITION:
                    return new PreserveCaret( doc );
                case ACQUIRE_AWT_TREE_LOCK:
                    return new AWTTreeLocker( doc );
                default:
                    throw new AssertionError( this );
            }
        }
    }

    static abstract class SingleEntryBeforeAfter implements BeforeAfter {

        static final Map<Class<?>, ThreadLocal<Set<Integer>>> ACTIVES
                = CollectionUtils.concurrentSupplierMap( ()
                        -> ThreadLocal.withInitial( HashSet::new )
                );
        protected final StyledDocument doc;
        protected final int idHash;

        protected SingleEntryBeforeAfter( StyledDocument doc ) {
            this.doc = doc;
            idHash = System.identityHashCode( doc );
        }

        protected Set<Integer> actives() {
            ThreadLocal<Set<Integer>> actives = ACTIVES.get( getClass() );
            return actives.get();
        }

        protected boolean alreadyActive() {
            return actives().contains( idHash );
        }

        @Override
        public <T, E extends Exception> DocumentProcessor<T, E> wrap( DocumentProcessor<T, E> toWrap ) {
            Set<Integer> active = actives();
            if ( active.contains( idHash ) ) {
                return toWrap;
            } else {
                DocumentProcessor<T, E> wrapped = BeforeAfter.super.wrap( toWrap );
                return new Wrapper<>( wrapped, active );
            }
        }

        final class Wrapper<T, E extends Exception> implements DocumentProcessor<T, E> {

            private final DocumentProcessor<T, E> wrapped;
            private final Set<Integer> active;

            public Wrapper( DocumentProcessor<T, E> wrapped, Set<Integer> active ) {
                this.wrapped = wrapped;
                this.active = active;
            }

            @Override
            public T get() throws E, BadLocationException {
                active.add( idHash );
                try {
                    return wrapped.get();
                } finally {
                    active.remove( idHash );
                }
            }

            @Override
            public String toString() {
                return "Wrapper(" + SingleEntryBeforeAfter.this.toString() + ")";
            }
        }
    }

    static class DisableMTI extends SingleEntryBeforeAfter {

        private MutableTextInput mti;
        private boolean active;

        public DisableMTI( StyledDocument doc ) {
            super( doc );
        }

        @Override
        public String toString() {
            return "DISABLE-MTI";
        }

        @Override
        public void before() throws BadLocationException {
            mti = ( MutableTextInput ) doc.getProperty( MutableTextInput.class );
            LOG.log( Level.FINER, "{0} before on {1}",
                     new Object[]{ this, Thread.currentThread() } );
            if ( mti != null ) {
                TokenHierarchyControl ctrl = mti.tokenHierarchyControl();
                active = ctrl.isActive();
                ctrl.setActive( false );
            }
        }

        @Override
        public void after() throws BadLocationException {
            LOG.log( Level.FINER, "{0} after on {1}",
                     new Object[]{ this, Thread.currentThread() } );
            if ( mti != null ) {
                LOG.log( Level.FINEST, "Set active {0} on {1}",
                         new Object[]{ active, mti } );
                mti.tokenHierarchyControl().setActive( active );
            }
        }
    }

    static class PreserveCaret extends SingleEntryBeforeAfter implements DocumentListener {

        private BeforeAfter handler;
        UndoableEdit edit;
        JTextComponent comp;

        public PreserveCaret( StyledDocument doc ) {
            super( doc );
        }

        private boolean isEditorCaret() {
            JTextComponent comp = EditorRegistry.findComponent( doc );
            return comp != null && ( comp.getCaret() instanceof EditorCaret );
        }

        @Override
        public String toString() {
            return "PRESERVE-CARET(" + ( isEditorCaret() ? "EDITOR" : "SWING" ) + ")";
        }

        @Override
        public void before() throws BadLocationException {
            comp = EditorRegistry.findComponent( doc );
            LOG.log( Level.FINER, "{0} before with {1} on {2}",
                     new Object[]{ this, doc, Thread.currentThread() } );
            if ( comp != null ) {
                doc.addDocumentListener( this );
                Caret caret = comp.getCaret();
                if ( caret != null ) {
                    edit = new CaretPositionUndoableEdit( comp, doc );
                    LOG.log( Level.FINEST, "Added caret position undo" );
//                    sendUndoableEdit( doc, edit );
                    if ( caret instanceof EditorCaret ) {
                        LOG.log( Level.FINEST, "Using editor caret strategy" );
                        handler = new EditorCaretHandler( comp, ( EditorCaret ) caret );
                    } else {
                        LOG.log( Level.FINEST, "Using swing caret strategy" );
                        handler = new SwingCaretHandler( comp, caret );
                    }
                }
                if ( handler != null ) {
                    handler.before();
                }
            }
        }

        @Override
        public void after() throws BadLocationException {
            LOG.log( Level.FINER, "{0} after on {1}", new Object[]{ this, Thread.currentThread() } );
            if ( handler != null ) {
                sendUndoableEdit( doc, edit );
                doc.removeDocumentListener( this );
                handler.after();
            } else {
                addPostRunOperation( () -> {
                    doc.removeDocumentListener( this );
                } );
            }
        }

        private void docChanged() {
            if ( comp != null ) {
                JScrollPane pane = ( JScrollPane ) SwingUtilities.getAncestorOfClass( JScrollPane.class, comp );
                if ( pane != null ) {
                    RepaintManager.currentManager( pane ).removeInvalidComponent( pane );
                    RepaintManager.currentManager( pane.getViewport() ).removeInvalidComponent( pane.getViewport() );
                    RepaintManager.currentManager( pane ).markCompletelyClean( pane );
                    RepaintManager.currentManager( pane.getViewport() ).markCompletelyClean( pane.getViewport() );
                }
                RepaintManager.currentManager( comp ).removeInvalidComponent( comp );
                RepaintManager.currentManager( comp ).markCompletelyClean( comp );
                LOG.log( Level.FINEST,
                         "{0} got doc change, attempting to "
                         + "preempt repaint and revalidate from "
                         + "RepaintManager for {1}",
                         new Object[]{ this, comp } );
            }
        }

        @Override
        public void insertUpdate( DocumentEvent e ) {
            docChanged();
        }

        @Override
        public void removeUpdate( DocumentEvent e ) {
            docChanged();
        }

        @Override
        public void changedUpdate( DocumentEvent e ) {
            // do nothing
        }

        static final class EditorCaretHandler extends NavigationFilter implements BeforeAfter, CaretMoveHandler {

            private final JTextComponent comp;
            private final EditorCaret caret;
            private List<CaretInfo> infos = new ArrayList<>( 5 );
            private NavigationFilter filter1;
            private NavigationFilter filter2;
            private boolean wasCaretUpdated;
//            Position caretPos;
//            Position markPos;
            private final Map<CaretInfo, CaretPositions> positionsForCaret = new HashMap<>();

            static class CaretPositions {

                private final Position dot;
                private final Position mark;
                private final Bias dotBias;
                private final Bias markBias;

                CaretPositions( CaretInfo info, Document doc ) throws BadLocationException {
                    dot = doc.createPosition( info.getDot() );
                    mark = doc.createPosition( info.getMark() );
                    dotBias = info.getDotBias();
                    markBias = info.getMarkBias();
                }

                boolean apply( CaretInfo info, CaretMoveContext cmc ) {
                    boolean result = cmc.setDotAndMark( info, dot, dotBias,
                                                        mark, markBias );
                    LOG.log( Level.FINEST, "Update caret {0} to {1} {2} / {3}, {4}",
                             new Object[]{ info, dot.getOffset(),
                                 dotBias, mark.getOffset(), markBias } );
                    return result;
                }

                public String toString() {
                    return "CaretPositions(" + dot + " " + dotBias + ","
                           + mark + " " + markBias + ")";
                }
            }

            public EditorCaretHandler( JTextComponent comp, EditorCaret caret ) {
                this.comp = comp;
                this.caret = caret;
            }

            @Override
            public String toString() {
                return "EDITOR-CARET";
            }

            @Override
            public void before() throws BadLocationException {
                LOG.log( Level.FINER, "{0} before on {1}", new Object[]{ this, Thread.currentThread() } );
                this.infos.addAll( caret.getSortedCarets() );
                for ( CaretInfo info : infos ) {
                    positionsForCaret.put( info, new CaretPositions( info, comp.getDocument() ) );
                }
                LOG.log( Level.FINEST, "Editor caret info {0}", positionsForCaret );
                filter1 = EditorCaret.getNavigationFilter( comp, MoveCaretsOrigin.DEFAULT );
                EditorCaret.setNavigationFilter( comp, MoveCaretsOrigin.DEFAULT, this );
                filter2 = EditorCaret.getNavigationFilter( comp, MoveCaretsOrigin.DISABLE_FILTERS );
                EditorCaret.setNavigationFilter( comp, MoveCaretsOrigin.DEFAULT, this );
                caret.setVisible( false );
            }

            @Override
            public void after() throws BadLocationException {
                LOG.log( Level.FINER, "{0} after restore caret navigation filters {1}, {2}"
                                      + " on {3}",
                         new Object[]{ this, filter1, filter2, Thread.currentThread() } );
                EditorCaret.setNavigationFilter( comp, MoveCaretsOrigin.DEFAULT, filter1 );
                EditorCaret.setNavigationFilter( comp, MoveCaretsOrigin.DISABLE_FILTERS, filter1 );
                caret.moveCarets( this, MoveCaretsOrigin.DEFAULT );
                if ( !wasCaretUpdated ) {
                    LOG.log( Level.FINEST, "Caret {0} not updated, use brute force method", caret );
                    addPostRunOperation( this::bruteForceCaretUpdate );
                }
            }

            private void bruteForceCaretUpdate() {
                if ( !positionsForCaret.isEmpty() ) {
                    CaretPositions c = positionsForCaret.entrySet().iterator().next().getValue();
                    LOG.log( Level.FINEST, "Run brute force reposition caret {0} on eq from {1}",
                             new Object[]{ c, Thread.currentThread() } );
                    int mark = c.mark.getOffset();
                    int dot = c.dot.getOffset();
                    LOG.log( Level.FINEST, "On eq update caret {0} / {1} ",
                             new Object[]{ mark, dot } );
                    caret.setDot( mark );
                    if ( mark != dot ) {
                        caret.moveDot( mark );
                    }
                } else {
                    CaretInfo info = infos.isEmpty() ? null : infos.get( 0 );
                    int mark = info.getMark();
                    int dot = info.getDot();
                    caret.setDot( mark );
                    if ( mark != dot ) {
                        caret.moveDot( dot );
                    }
                }
            }

            @Override
            public void moveCarets( CaretMoveContext cmc ) {
                wasCaretUpdated = false;
                if ( !positionsForCaret.isEmpty() ) {
                    for ( Map.Entry<CaretInfo, CaretPositions> e : positionsForCaret.entrySet() ) {
                        wasCaretUpdated |= e.getValue().apply( e.getKey(), cmc );
                    }
                    LOG.log( Level.FINEST, "Update carets with editor carets "
                                           + "api success? {0}", wasCaretUpdated );
                }
            }

            @Override
            public int getNextVisualPositionFrom( JTextComponent text, int pos, Position.Bias bias, int direction,
                    Position.Bias[] biasRet ) throws BadLocationException {
                if ( infos.size() > 0 ) {
                    return infos.get( 0 ).getDot();
                }
                return pos;
            }

            @Override
            public void moveDot( FilterBypass fb, int dot, Position.Bias bias ) {
                // do nothing
                LOG.log( Level.FINE, "{0} Preventing dot move to {1}",
                         new Object[]{ this, dot } );
            }

            @Override
            public void setDot( FilterBypass fb, int dot, Position.Bias bias ) {
                // do nothing
                LOG.log( Level.FINE, "{0} Preventing dot set to {1}",
                         new Object[]{ this, dot } );
            }
        }

        static final class SwingCaretHandler implements BeforeAfter, Runnable {

            private final JTextComponent comp;
            private final Caret caret;
            private Position dot;
            private Position mark;

            public SwingCaretHandler( JTextComponent comp, Caret caret ) {
                this.comp = comp;
                this.caret = caret;
            }

            @Override
            public String toString() {
                return "SWING-CARET";
            }

            @Override
            public void before() throws BadLocationException {
                LOG.log( Level.FINER, "{0} before", this );
                Document doc = comp.getDocument();
                Caret caret = comp.getCaret();
                if ( caret != null ) {
                    dot = doc.createPosition( caret.getDot() );
                    mark = doc.createPosition( caret.getMark() );
                }
            }

            @Override
            public void after() throws BadLocationException {
                LOG.log( Level.FINER, "{0} after", this );
                if ( dot != null ) {
                    addPostRunOperation( this );
                }
            }

            @Override
            public void run() {
                caret.setDot( mark.getOffset() );
                caret.moveDot( dot.getOffset() );
            }
        }
    }

    static class BlockRepaints extends SingleEntryBeforeAfter implements Runnable {

        private JTextComponent comp;

        public BlockRepaints( StyledDocument doc ) {
            super( doc );
        }

        public String toString() {
            return "BLOCK-REPAINTS";
        }

        Point viewPosition;
        boolean caretVisible;
        private int distanceToTop = -1;

        @Override
        public void before() {
            LOG.log( Level.FINER, "{0} before on {1}", new Object[]{ this, Thread.currentThread() } );
            comp = EditorRegistry.findComponent( doc );
            if ( comp != null ) {
                LOG.log( Level.FINER, "{0} before", this );
                int caretPos = comp.getCaretPosition();
                caretVisible = comp.getCaret().isVisible();
                LOG.log( Level.FINEST, "Caret position {0} visible {1}",
                         new Object[]{ caretPos, caretVisible } );
                comp.getCaret().setVisible( false );

                JScrollPane pane = ( JScrollPane ) SwingUtilities.getAncestorOfClass( JScrollPane.class, comp );
                if ( pane != null ) {
                    pane.setIgnoreRepaint( true );
                    pane.getViewport().setIgnoreRepaint( true );
//                    pane.getViewport().addChangeListener(ce -> {
//                        new Exception("VP CHANGE: " + pane.getViewport().getViewPosition()).printStackTrace();
//                    });
//                    comp.getCaret().addChangeListener(ce -> {
//                        new Exception("CARET CHANGE: " + comp.getCaret().getDot()).printStackTrace();
//                    });
                    viewPosition = pane.getViewport().getViewPosition();
                    try {
                        Rectangle r = comp.modelToView( caretPos );
                        distanceToTop = r.y - viewPosition.y;
                        LOG.log( Level.FINEST, "Collectioned component info {0}, {1}, {2}",
                                 new Object[]{ viewPosition, distanceToTop, r } );
                    } catch ( BadLocationException ex ) {
                        LOG.log( Level.SEVERE, null, ex );
                    }
                }
                comp.setIgnoreRepaint( true );
            }
        }

        @Override
        public void after() {
            LOG.log( Level.FINER, "{0} after on {1}", new Object[]{ this, Thread.currentThread() } );
            if ( comp != null ) {
                addPostRunOperation( this );
            }
        }

        @Override
        public void run() {
            JScrollPane pane = ( JScrollPane ) SwingUtilities.getAncestorOfClass( JScrollPane.class, comp );
            int caretPos = comp.getCaretPosition();
            LOG.log( Level.FINER, "{0} after-on-eq", this );
            if ( pane != null ) {
                if ( distanceToTop > 0 ) {
                    try {
                        Rectangle newCaretBounds = comp.modelToView( caretPos );
                        LOG.log( Level.FINEST, "view position was {0} old "
                                               + "distance to top {1} new caret bounds {2}",
                                 new Object[]{ viewPosition, distanceToTop, newCaretBounds } );
                        newCaretBounds.y -= distanceToTop;
                        viewPosition = new Point( viewPosition.x, newCaretBounds.y );
                        LOG.log( Level.FINEST, "view position now {0}", viewPosition );
                    } catch ( BadLocationException ex ) {
                        LOG.log( Level.SEVERE, null, ex );
                    }
                }
                pane.getViewport().setViewPosition( viewPosition );
                comp.setIgnoreRepaint( false );
                pane.setIgnoreRepaint( false );
                pane.getViewport().setIgnoreRepaint( false );
                pane.repaint();
            } else {
                comp.setIgnoreRepaint( false );
                comp.repaint();
            }
            comp.getCaret().setVisible( caretVisible );
        }
    }

    static class DocumentWriteLocker implements BeforeAfter {

        static ThreadLocal<Set<Integer>> alreadyReadLocked = ThreadLocal.withInitial( HashSet::new );
        static ThreadLocal<Set<Integer>> alreadyWriteLocked = ThreadLocal.withInitial( HashSet::new );
        private final StyledDocument doc;
        private final boolean writeLock;
        private final int idHash;
        private final boolean asUser;

        public DocumentWriteLocker( StyledDocument doc, boolean writeLock, boolean asUser ) {
            this.doc = doc;
            this.writeLock = writeLock;
            idHash = System.identityHashCode( doc );
            this.asUser = asUser;
        }

        @Override
        public String toString() {
            return writeLock ? asUser ? "WRITE-LOCK-AS-USER" : "WRITE-LOCK" : "READ-LOCK";
        }

        @Override
        public <T, E extends Exception> DocumentProcessor<T, E> wrap( DocumentProcessor<T, E> toWrap ) {
            return new LockingDocumentProcessor<>( toWrap );
        }

        private class LockingDocumentProcessor<T, E extends Exception> implements DocumentProcessor<T, E> {
            private final DocumentProcessor<T, E> toWrap;

            public LockingDocumentProcessor( DocumentProcessor<T, E> toWrap ) {
                this.toWrap = toWrap;
            }

            @Override
            public String toString() {
                return DocumentWriteLocker.this + "(" + toWrap + ")";
            }

            @Override
            public T get() throws E, BadLocationException {
                ThreadLocal<Set<Integer>> reentrantLockedDocs = writeLock ? alreadyWriteLocked : alreadyReadLocked;
                Set<Integer> currentlyLocked = reentrantLockedDocs.get();
                boolean entry = currentlyLocked.add( idHash );
                try {
                    if ( !entry ) {
                        LOG.log( Level.FINEST, "Reentrant {0}", DocumentWriteLocker.this );
                        return toWrap.get();
                    } else {
                        LOG.log( Level.FINER, "{0} before {1}",
                                 new Object[]{ DocumentWriteLocker.this, Thread.currentThread() } );
                        BleBiConsumer runIt;
                        if ( writeLock ) {
                            if ( asUser ) {
                                LOG.log( Level.FINEST, "Will use NbDocument.runAtomicAsUser()" );
                                runIt = NbDocument::runAtomicAsUser;
                            } else {
                                LOG.log( Level.FINEST, "Will use NbDocument.runAtomic()" );
                                runIt = NbDocument::runAtomic;
                            }
                        } else {
                            LOG.log( Level.FINEST, "Will use Document.render()" );
                            runIt = BleBiConsumer.render();
                        }
                        Hold<T, E> hold = new Hold<T, E>();
                        runIt.accept( doc, () -> {
                                  try {
                                      LOG.log( Level.FINEST, "Invoke under lock: {0}", toWrap );
                                      hold.set( toWrap.get() );
                                  } catch ( Exception ex ) {
                                      hold.thrown( ex );
                                  }
                              } );
                        return hold.get();
                    }
                } finally {
                    if ( entry ) {
                        currentlyLocked.remove( idHash );
                    }
                }
            }
        }
    }

    static final class AWTTreeLocker extends SingleEntryBeforeAfter {
        AWTTreeLocker( StyledDocument doc ) {
            super( doc );
        }

        @Override
        public <T, E extends Exception> DocumentProcessor<T, E> wrap( DocumentProcessor<T, E> toWrap ) {
            DocumentProcessor<T, E> superWrap = super.wrap( toWrap );
            if ( superWrap != toWrap ) {
                // non-reentrant
                Component c = EditorRegistry.findComponent( doc );
                if ( c != null ) {
                    return new WithTreeLock( superWrap, c );
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

            public WithTreeLock( DocumentProcessor<T, E> delegate, Component comp ) {
                this.delegate = delegate;
                this.comp = comp;
            }

            @Override
            public T get() throws E, BadLocationException {
                T result;
                LOG.log( Level.FINER, "{0} on {1} with {2} ENTER",
                         new Object[]{ this, Thread.currentThread(), comp } );
                synchronized ( comp.getTreeLock() ) {
                    result = delegate.get();
                }
                LOG.log( Level.FINER, "{0} on {1} with {2} EXIT",
                         new Object[]{ this, Thread.currentThread(), comp } );
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

        void set( T obj ) {
            this.obj = obj;
        }

        void thrown( Exception e ) {
            this.thrown = e;
        }

        void rethrow() {
            if ( thrown != null ) {
                Exceptions.chuck( thrown );
            }
        }

        public T get() {
            rethrow();
            return obj;
        }
    }

    interface BleBiConsumer extends ThrowingBiConsumer<StyledDocument, Runnable> {

        @Override
        void accept( StyledDocument a, Runnable b ) throws BadLocationException;

        static BleBiConsumer render() {
            return ( a, b ) -> {
                a.render( b );
            };
        }
    }

    interface BeforeAfter {

        default void before() throws BadLocationException {

        }

        default void after() throws BadLocationException {

        }

        default <T, E extends Exception> DocumentProcessor<T, E> wrap( DocumentProcessor<T, E> toWrap ) {
            return new WrappedDocChewingSupplier<>( this, toWrap );
        }

        default BeforeAfter then( BeforeAfter next ) {
            return new ChainedBeforeAfter( this, next );
        }
    }

    static final class WrappedDocChewingSupplier<T, E extends Exception> implements DocumentProcessor<T, E> {

        private final BeforeAfter be;
        private final DocumentProcessor<T, E> delegate;

        public WrappedDocChewingSupplier( BeforeAfter be, DocumentProcessor<T, E> delegate ) {
            this.be = be;
            this.delegate = delegate;
        }

        @Override
        public T get() throws E, BadLocationException {
            LOG.log( Level.FINEST, "Run {0} wrapped by {1}", new Object[]{ delegate, be } );
            be.before();
            try {
                return delegate.get();
            } finally {
                be.after();
            }
        }

        @Override
        public String toString() {
            return be + " -> " + delegate;
        }
    }

    static final class ChainedBeforeAfter implements BeforeAfter {

        private final BeforeAfter a;
        private final BeforeAfter b;

        public ChainedBeforeAfter( BeforeAfter a, BeforeAfter b ) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void before() throws BadLocationException {
            a.before();
            b.before();
        }

        @Override
        public void after() throws BadLocationException {
            a.after();
            b.after();
        }

        @Override
        public String toString() {
            return a + ", " + b;
        }
    }

    static void addCaretUndoableEdit( JTextComponent comp, Document document ) throws BadLocationException {
        sendUndoableEdit( document, new CaretPositionUndoableEdit( comp, document ) );
    }

    static final class UndoTransaction extends SingleEntryBeforeAfter {

        public UndoTransaction( StyledDocument doc ) {
            super( doc );
        }

        @Override
        public void before() throws BadLocationException {
            LOG.log( Level.FINER, "{0} before on {1}", new Object[]{ this, Thread.currentThread() } );
            sendUndoableEdit( doc, CloneableEditorSupport.BEGIN_COMMIT_GROUP );
        }

        @Override
        public void after() throws BadLocationException {
            LOG.log( Level.FINER, "{0} after on {1}", new Object[]{ this, Thread.currentThread() } );
            sendUndoableEdit( doc, CloneableEditorSupport.END_COMMIT_GROUP );
        }

        @Override
        public String toString() {
            return "ONE-UNDO-TRANSACTION";
        }
    }

    private static void sendUndoableEdit( Document doc, UndoableEdit edit ) throws BadLocationException {
        CustomUndoDocument customUndoDocument
                = LineDocumentUtils.as( doc,
                                        CustomUndoDocument.class );
        if ( customUndoDocument != null ) {
            customUndoDocument.addUndoableEdit( edit );
        }
    }

    static final class CaretPositionUndoableEdit extends AbstractUndoableEdit {

        private Position dot;
        private Position mark;
        private Rectangle undoRect;
        private final JTextComponent comp;
        private Caret caret;
        private Position redoPosition;
        private Position redoMark;
        private Rectangle redoRect;
        private Document doc;
        private boolean undone;

        public CaretPositionUndoableEdit( JTextComponent comp, Document doc ) throws BadLocationException {
            this.caret = comp.getCaret();
            this.dot = doc.createPosition( caret.getDot() );
            this.mark = doc.createPosition( caret.getMark() );
            this.undoRect = comp.getVisibleRect();
            this.comp = comp;
            this.doc = doc;
        }

        @Override
        public void die() {
            dot = null;
            mark = null;
            caret = null;
            redoPosition = null;
            redoMark = null;
            doc = null;
            undoRect = null;
            redoRect = null;
        }

        private void updateRedoInfo() {
            try {
                redoPosition = doc.createPosition( caret.getDot() );
                redoMark = doc.createPosition( caret.getMark() );
                redoRect = comp.getVisibleRect();
                LOG.log( Level.FINEST, "Collect caret redo info {0}, {1}, {2}",
                         new Object[]{ redoPosition, redoMark, redoRect } );
            } catch ( BadLocationException ex ) {
                LOG.log( Level.SEVERE, null, ex );
            }
        }

        @Override
        public void undo() throws CannotUndoException {
            undone = true;
            EventQueue.invokeLater( () -> {
                try {
                    LOG.log( Level.FINE, "Caret-undo to {0}, {1}", new Object[]{ mark, dot } );
                    updateRedoInfo();
                    updateCaret( dot, mark );
                } finally {
                    comp.scrollRectToVisible( undoRect );
                }
            } );
        }

        private void updateCaret( Position position, Position mark ) {
            int pos = position.getOffset();
            int mk = mark.getOffset();
            if ( mk == pos ) {
                caret.setDot( pos );
            } else {
                caret.setDot( mk );
                if ( mk != pos ) {
                    caret.moveDot( pos );
                }
            }
        }

        @Override
        public boolean canUndo() {
            return !undone && doc != null && dot != null;
        }

        @Override
        public void redo() throws CannotRedoException {
            undone = false;
            EventQueue.invokeLater( () -> {
                try {
                    updateCaret( redoPosition, redoMark );
                } finally {
                    comp.scrollRectToVisible( redoRect );
                }

            } );
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

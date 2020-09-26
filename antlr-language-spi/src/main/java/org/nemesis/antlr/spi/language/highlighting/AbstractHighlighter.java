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
package org.nemesis.antlr.spi.language.highlighting;

import com.mastfrog.util.strings.Strings;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.lib.editor.util.swing.DocumentListenerPriority;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static javax.swing.text.Document.StreamDescriptionProperty;

/**
 * A generic highlighter or error annotator, which correctly implements several
 * things that can be tricky:
 * <ul>
 * <li>When to start and stop listening on the document</li>
 * <li>Updating highlights without causing flashing</li>
 * </ul>
 * This class makes no particular assumptions about how updating of highlights
 * is (re-)triggered - it simply provides the hooks to detect when to start and
 * stop listening to the highlighting context (editor), and a way to update
 * highlights that will avoid flashing and other bad behavior that are common
 * problems.
 * <p>
 * Implementation: override <code>activated(FileObject, Document)</code>
 * and <code>deactivated(FileObject, Document)</code> to attach and detach
 * listeners; when you want to update highlights due to an event you detected,
 * call <code>updateHighlights()</code> passing it a closure which returns
 * <code>true</code> if any highlights were added to the <code>OffsetsBag</code>
 * it was passed, false if the highlights should be cleared and the bag's
 * contents should be ignored.
 * </p><p>
 * Registration: on your subclass, add a public static factory method that
 * delegates to the method <code>factory(id, zorder, function)</code> to
 * construct an instance, and annotate it <code>&#064;MimeRegistration</code>.
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class AbstractHighlighter {

    @SuppressWarnings( "NonConstantLogger" )
    protected final Logger LOG;
    private static final Map<Class<?>, RequestProcessor> rpByType = new HashMap<>();
    protected final HighlightsLayerFactory.Context ctx;
    private final CompL compl = new CompL();
    private final AlternateBag bag;

    protected AbstractHighlighter( HighlightsLayerFactory.Context ctx ) {
        this( ctx, true );
    }

    @SuppressWarnings( "LeakingThisInConstructor" )
    protected AbstractHighlighter( HighlightsLayerFactory.Context ctx, boolean mergeHighlights ) {
        this.LOG = Logger.getLogger( getClass().getName() );
        this.ctx = ctx;
        Document doc = ctx.getDocument();
        // XXX listen for changes, etc
        JTextComponent theEditor = ctx.getComponent();
        // Listen for component events
        theEditor.addComponentListener( WeakListeners.create(
                ComponentListener.class, compl, theEditor ) );
        bag = new AlternateBag( Strings.lazy( this ) );
        theEditor.addPropertyChangeListener( WeakListeners.propertyChange( compl, theEditor ) );
        LOG.log( Level.FINE, "Create {0} for {1}", new Object[]{ getClass().getName(), doc } );
        // Ensure we are initialized, and don't assume we are constructed in the
        // event thread; calling isShowing() in any other thread is unsafe

        EventQueue.invokeLater( () -> {
//            if ( ctx.getComponent().isShowing() ) {
            LOG.log( Level.FINER, "Component is showing, set active" );
            compl.setActive( true );
//            }
        } );
//        }
    }

    /**
     * Called when the editor this instance is highlighting is made visible.
     * Perform whatever logic you need to begin listening to a file or document
     * for changes that should trigger rerunning highlighting, and enqueue an
     * initial highlighting run, here.
     *
     * @param file The file
     * @param doc  The document
     */
    protected abstract void activated( FileObject file, Document doc );

    /**
     * <i>Fully</i> detach listeners here, cancel any pending tasks, etc.
     *
     * @param file The file
     * @param doc  The document
     */
    protected abstract void deactivated( FileObject file, Document doc );

    private void onAfterDeactivated() {
    }

    private String identifier() {
        Object o = ctx.getDocument().getProperty( StreamDescriptionProperty );
        String fileName;
        if ( o instanceof DataObject ) {
            fileName = ( ( DataObject ) o ).getName();
        } else if ( o instanceof FileObject ) {
            fileName = ( ( FileObject ) o ).getName();
        } else {
            fileName = Objects.toString( o );
        }
        return getClass().getSimpleName() + ":" + fileName + " " + toString();
    }

    /**
     * To update highlighting of the document, call this method with a closure
     * that accepts an OffsetsBag, and returns <code>true</code> if there were
     * any highlights added to the bag, and <code>false</code> if the bag was
     * left empty (any existing highlights created by previous calls will be
     * cleared0.
     *
     * @param highlightsUpdater A predicate which modifies the empty OffsetsBag
     *                          it is passed, adding highlights where needed, and returns true if it
     *                          added any highlights to it, false if not.
     */
    protected final void updateHighlights( Predicate<HighlightConsumer> highlightsUpdater ) {
        bag.update( () -> {
            return highlightsUpdater.test( bag );
        } );
    }

    /**
     * Returns true if the document is visible to the user and highlighting
     * should be performed.
     *
     * @return
     */
    protected final boolean isActive() {
        return compl.active;
    }

    /**
     * Returns a single-thread request processor created for all instances of
     * this class, which can be used for asynchronous tasks while guaranteeing
     * more than one of such tasks cannot be run concurrently.
     *
     * @return A request processor
     */
    protected final RequestProcessor threadPool() {
        if ( true ) {
            return ALL_HIGHLIGHTERS;
        }
        return threadPool( getClass() );
    }
    static final RequestProcessor ALL_HIGHLIGHTERS = new RequestProcessor( "AbstractHighlighter", 1 );

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + id() + ")";
    }

    private String id() {
        Object o = ctx.getDocument().getProperty( StreamDescriptionProperty );
        String fileName;
        if ( o instanceof DataObject ) {
            fileName = ( ( DataObject ) o ).getName();
        } else if ( o instanceof FileObject ) {
            fileName = ( ( FileObject ) o ).getName();
        } else {
            fileName = Objects.toString( o );
        }
        JTextComponent editor = ctx.getComponent();
        int h = editor == null ? 0 : System.identityHashCode( editor );
        return fileName + "-" + h;
    }

    @SuppressWarnings( "DoubleCheckedLocking" )
    private static final RequestProcessor threadPool( Class<?> type ) {
        return rpByType.computeIfAbsent( type, cl -> {
                                     return new RequestProcessor( cl.getName() + "-subscribe", 1, false );
                                 } );
    }

    /**
     * Create a factory for some type of AbstractHighlighter - typical usage is to create
     * a no-argument factory method that calls this and annotate it with
     * mime lookup registration.
     *
     * @param layerTypeId        The layer type id
     * @param zOrder             The z-order
     * @param highlighterCreator A function that returns an instance of AbstractHighlighter
     *
     * @return A generic highlighter factory
     */
    public static final HighlightsLayerFactory factory( String layerTypeId, ZOrder zOrder,
            Function<? super HighlightsLayerFactory.Context, ? extends AbstractHighlighter> highlighterCreator ) {
        return new Factory( layerTypeId, zOrder, highlighterCreator, false );
    }

    /**
     * Create a factory for some type of AbstractHighlighter - typical usage is to create
     * a no-argument factory method that calls this and annotate it with
     * mime lookup registration.
     *
     * @param layerTypeId        The layer type id
     * @param zOrder             The z-order
     * @param highlighterCreator A function that returns an instance of AbstractHighlighter
     *
     * @return A generic highlighter factory
     */
    public static final HighlightsLayerFactory factory( String layerTypeId, ZOrder zOrder,
            Function<? super HighlightsLayerFactory.Context, ? extends AbstractHighlighter> highlighterCreator,
            boolean mustHaveFile ) {
        return new Factory( layerTypeId, zOrder, highlighterCreator, mustHaveFile );
    }

    public final HighlightsContainer getHighlightsBag() {
        return bag;
    }

    /**
     * A basic single-highlighter highlights layer factory that is easy to use
     * from a no-argument overload the <code>factory()</code> method to register
     * a highlight in the module's layer.
     */
    private static class Factory implements HighlightsLayerFactory {

        private static final HighlightsLayer[] EMPTY = new HighlightsLayer[ 0 ];
        private final ZOrder zOrder;
        private final Function<? super Context, ? extends AbstractHighlighter> highlighterCreator;
        private final String layerTypeId;
        private final boolean mustHaveFile;

        Factory( String layerTypeId, ZOrder zorder,
                Function<? super Context, ? extends AbstractHighlighter> highlighterCreator,
                boolean mustHaveFile ) {
            this.zOrder = notNull( "zorder", zorder );
            this.highlighterCreator = notNull( "highlighterCreator", highlighterCreator );
            this.layerTypeId = notNull( "layerTypeId", layerTypeId );
            this.mustHaveFile = mustHaveFile;
        }

        @Override
        public HighlightsLayer[] createLayers( HighlightsLayerFactory.Context ctx ) {
            Document doc = ctx.getDocument();
            if ( mustHaveFile ) {
                FileObject fo = NbEditorUtilities.getFileObject( doc );
                if ( fo == null ) { // preview pane, etc.
                    return EMPTY;
                }
            }
            JTextComponent comp = ctx.getComponent();
            String cp = layerTypeId + "-hl";

            AbstractHighlighter highlighter = ( AbstractHighlighter ) comp.getClientProperty( cp );
            if ( highlighter == null ) {
                highlighter = highlighterCreator.apply( ctx );
            }
            return new HighlightsLayer[]{
                HighlightsLayer.create( layerTypeId, zOrder,
                                        true, highlighter.getHighlightsBag() )
            };
        }
    }

    /**
     * Listens on the editor component, and informs the owning highlighter when
     * the component becomes visible or is hidden, so it can ignore changes
     * when the component is not onscreen.
     */
    private final class CompL extends ComponentAdapter implements Runnable, DocumentListener, PropertyChangeListener {

        // Volatile because while highlighters are only attached and detached from the
        // event thread, it can be read from any thread that checks state
        private volatile boolean active;
        private final RequestProcessor.Task task = threadPool().create( this );

        @Override
        public void componentShown( ComponentEvent e ) {
            LOG.log( Level.FINEST, "Component shown {0}", ctx.getDocument() );
            setActive( true );
        }

        @Override
        public void componentHidden( ComponentEvent e ) {
            LOG.log( Level.FINEST, "Component hidden {0}", ctx.getDocument() );
            setActive( false );
        }

        @Override
        public void propertyChange( PropertyChangeEvent evt ) {
            if ( "ancestor".equals( evt.getPropertyName() ) ) {

            }
        }

        void setActive( boolean active ) {
            boolean act = this.active;
            if ( active != act ) {
                this.active = act = active;
                LOG.log( Level.FINE, "Set active to {0} for {1}",
                         new Object[]{ act, ctx.getDocument() } );
                if ( act ) {
                    task.schedule( 350 );
                } else {
                    task.cancel();
                    deactivate();
                }
            }
        }

        void deactivate() {
            System.out.println( "DEACTIVATE " + identifier() );
            Document doc = ctx.getDocument();
            FileObject fo = NbEditorUtilities.getFileObject( doc );
            try {
                synchronized ( this ) {
                    LOG.log( Level.FINE, "Activating against {0}", fo );
                    try {
                        DocumentUtilities.removePriorityDocumentListener( doc, this,
                                                                          DocumentListenerPriority.AFTER_CARET_UPDATE );
//                        doc.removeDocumentListener( this );
                        deactivated( fo, doc );
                    } finally {
                        onAfterDeactivated();
                    }
                }
            } catch ( Exception ex ) {
                LOG.log( Level.SEVERE, "Exception deactivating against "
                                       + fo + " / " + doc, ex );
            }
        }

        void activate() {
            System.out.println( "ACTIVATE " + identifier() );
            Document doc = ctx.getDocument();
            FileObject fo = NbEditorUtilities.getFileObject( doc );
            if ( active ) {
                try {
                    synchronized ( this ) {
                        LOG.log( Level.FINE, "Activating against {0}", fo );
                        activated( fo, doc );
                        DocumentUtilities.addPriorityDocumentListener( doc, this,
                                                                       DocumentListenerPriority.AFTER_CARET_UPDATE );
//                        doc.addDocumentListener( this );
                    }
                } catch ( Exception ex ) {
                    LOG.log( Level.SEVERE, "Exception activating against "
                                           + fo + " / " + doc, ex );
                }
            } else {
                LOG.log( Level.FINE, "Not active, don't subscribe to rebuilds of {0}",
                         ctx.getDocument() );
            }
        }

        @Override
        public void run() {
            if ( active ) {
                activate();
            }
        }

        @Override
        public void insertUpdate( DocumentEvent e ) {
            bag.onInsertion( e.getLength(), e.getOffset() );
        }

        @Override
        public void removeUpdate( DocumentEvent e ) {
            bag.onDeletion( e.getLength(), e.getOffset() );
        }

        @Override
        public void changedUpdate( DocumentEvent e ) {
            // do nothing
        }
    }
}

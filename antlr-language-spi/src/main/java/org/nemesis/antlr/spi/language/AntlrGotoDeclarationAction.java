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
package org.nemesis.antlr.spi.language;

import com.mastfrog.util.collections.CollectionUtils;

import static com.mastfrog.util.preconditions.Checks.notNull;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.extraction.attribution.ImportKeySupplier;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.editor.EditorActionNames;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.caret.CaretInfo;
import org.netbeans.api.editor.caret.CaretMoveContext;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.api.editor.caret.MoveCaretsOrigin;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseKit;
import org.netbeans.editor.ext.ExtKit;
import org.netbeans.spi.editor.AbstractEditorAction;
import org.netbeans.spi.editor.caret.CaretMoveHandler;
import org.openide.awt.Mnemonics;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;

/**
 * XXX nothing else in this package is UI-related - may want to move this to its
 * own module or other package.
 *
 * @author Tim Boudreau
 */
@Messages("gotoDeclaration=&Go To Declaration")
public final class AntlrGotoDeclarationAction extends AbstractEditorAction implements Presenter.Menu, Presenter.Popup {

    private static final Logger LOGGER
            = Logger.getLogger( AntlrGotoDeclarationAction.class.getName() );
    private final NameReferenceSetKey<?>[] keys;

    AntlrGotoDeclarationAction( String mimeType, NameReferenceSetKey<?>[] keys ) {
        this.keys = notNull( "keys", keys );
        putValue( ASYNCHRONOUS_KEY, true );
        putValue( NAME, EditorActionNames.gotoDeclaration );
        String trimmed
                = NbBundle.getBundle( BaseKit.class ).getString( "goto-declaration-trimmed" );
        putValue( ExtKit.TRIMMED_TEXT, trimmed );
        putValue( BaseAction.POPUP_MENU_TEXT, trimmed );
        putValue( MIME_TYPE_KEY, mimeType );

        LOGGER.log( Level.FINE, "Created a goto-decl action for {0}", keys[ 0 ] );
    }

    @Override
    public JMenuItem getPopupPresenter() {
        return getMenuPresenter();
    }
    
    private JMenuItem presenter;
    @Override
    public JMenuItem getMenuPresenter() {
        // This action is also used in popup menus, and by default it will
        // have no display name at all if we don't implement Presenter.Menu and
        // Presenter.Popup and provide our own implementation
        if (presenter == null) {
            presenter = new JMenuItem(this);
            Mnemonics.setLocalizedText(presenter, Bundle.gotoDeclaration());
        }
        return presenter;
    }

    @Override
    protected void actionPerformed( ActionEvent evt, JTextComponent component ) {
        Caret caret = component.getCaret();
        if ( caret == null ) {
            return;
        }
        int position = caret.getDot();
        LOGGER.log( Level.FINER, "Invoke {0} at {1}", new Object[]{ position,
            "AntlrGotoDeclarationAction" } );

        Extraction ext = NbAntlrUtils.extractionFor( component.getDocument() );
        if ( ext != null ) {
            navigateTo( evt, component, ext, position );
        } else {
            LOGGER.log( Level.INFO, "No extraction for {0}", component.getDocument() );
        }
    }

    private void navigateTo( ActionEvent evt, JTextComponent component,
            Extraction extraction, int position ) {
        LOGGER.log( Level.FINER, "Find ref at {0} in {1}", new Object[]{ position,
            extraction.logString() } );
        for ( NameReferenceSetKey<?> key : keys ) {
            NamedRegionReferenceSets<?> regions = extraction.references( key );
            NamedSemanticRegionReference<?> set = regions.at( position );
            if ( set != null ) {
                LOGGER.log( Level.FINER, "Found ref {0} navigating to {1} at {2}",
                            new Object[]{ set, set.referencing(),
                                set.referencing().start() } );
                navigateTo( component, set.referencing().start() );
                return;
            }
        }
        try {
            navToUnknown( extraction, position );
        } catch ( IOException ex ) {
            Exceptions.printStackTrace( ex );
        }
    }

    private <K extends Enum<K>> boolean tryToResolve( NameReferenceSetKey<K> key, Extraction ext, int pos ) {
        Attributions<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K> at = ext.resolveAll( key );
        LOGGER.log( Level.FINER, "Found attributions for {0}: {1}", new Object[]{ key, at } );
        if ( at.hasResolved() ) {
            LOGGER.log( Level.FINEST, "Has resolved items" );
            SemanticRegions<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K>> att
                    = at.attributed();
            LOGGER.log( Level.FINEST, "Have some resolved: {0}", att );
            SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K>> referenced
                    = att.at( pos );
            LOGGER.log( Level.FINER, "At position {0}", referenced );
            if ( referenced != null ) {
                AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<K>, NamedSemanticRegion<K>, K> info
                        = referenced.key();
                NamedSemanticRegion<K> element = info.element();
                GrammarSource<?> src = info.attributedTo().source();

                LOGGER.log( Level.FINER, "References {0} in {1}", new Object[]{ element, src } );
            }
        }
        return false;
    }

    private void navToUnknown( Extraction extraction, int position ) throws IOException {
        LOGGER.log( Level.FINER, "Try nav to unknown @ {0} in {1}",
                    new Object[]{ position, extraction.source() } );
        for ( NameReferenceSetKey<?> k : keys ) {
            LOGGER.log( Level.FINEST, "Check one unknown {0}", k );
            if ( checkOneUnknown( extraction, k, position ) ) {
                return;
            }
        }

        for ( NameReferenceSetKey<?> nrk : keys ) {
            LOGGER.log( Level.FINE, "Try to resolve against {0}", nrk );
            if ( tryToResolve( nrk, extraction, position ) ) {
                return;
            }
        }

        // Now see if we are on an import name, and try to open the file for that if so
        ImportFinder finder = ImportFinder.forMimeType( extraction.mimeType() );
        if ( finder instanceof ImportKeySupplier ) {
            NamedRegionKey<?>[] ks = ( ( ImportKeySupplier ) finder ).get();
            if ( ks.length > 0 ) {
                LOGGER.log( Level.FINER, "Try import key supplier with {0}", Arrays.asList( ks ) );
                for ( NamedRegionKey k : ks ) {
                    NamedSemanticRegions<?> nr = extraction.namedRegions( k );
                    if ( nr == null ) {
                        continue;
                    }
                    // This is useless - we should not be passing the position of our token
                    // to look for an import key
                    for ( NamedSemanticRegion<?> reg : nr ) {
                        LOGGER.log( Level.FINEST, "Trying {0} got regions {1} and {2} at {3}",
                                    new Object[]{ k, nr, reg, position } );
                        if ( reg != null ) {
                            String name = reg.name();
                            Set<GrammarSource<?>> grammarSources
                                    = finder.allImports( extraction, CollectionUtils.blackHoleSet() );
                            LOGGER.log( Level.FINEST, "Will check {0} GrammarSources: {1}",
                                        new Object[]{ grammarSources.size(), grammarSources } );
                            for ( GrammarSource<?> gs : finder.allImports( extraction, CollectionUtils.blackHoleSet() ) ) {
                                LOGGER.log( Level.FINEST, "Try {0} looking for {1} in name of {2}", new Object[]{
                                    gs.name(), name, gs
                                } );
                                if ( name.equals( gs.name() ) || gs.name().contains( name ) ) { // XXX
                                    openFileOf( gs );
                                    return;
                                }
                            }
                            GrammarSource<?> fallback = extraction.source().resolveImport( name );
                            if ( fallback != null ) {
                                LOGGER.log( Level.FINER, "Use fallback to open {0}", fallback );
                                openFileOf( fallback );
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void openFileOf( GrammarSource<?> gs ) {
        Optional<DataObject> dataObject = gs.lookup( DataObject.class );
        if ( dataObject.isPresent() ) {
            OpenCookie ck = dataObject.get().getLookup().lookup( OpenCookie.class );
            LOGGER.log( Level.FINE, "Opening {0} with {1}",
                        new Object[]{ dataObject.get(), ck } );
            if ( ck != null ) {
                EventQueue.invokeLater( () -> {
                    ck.open();
                    for ( TopComponent tc : TopComponent.getRegistry().getOpened() ) {
                        if ( tc.getLookup().lookup( DataObject.class ) == dataObject.get() ) {
                            tc.requestActive();
                            return;
                        }
                    }
                } );
            } else {
                LOGGER.log( Level.INFO, "Found a match {0} but no DataObject", gs );
            }
        }
    }

    private <T extends Enum<T>> void ensureOpenAndNavigate(
            SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> attributed,
            StyledDocument doc,
            DataObject dob,
            EditorCookie.Observable ck,
            NameReferenceSetKey<T> key, int position ) {
        JTextComponent comp = EditorRegistry.findComponent( doc );
        if ( comp == null ) {
            JEditorPane[] panes = ck.getOpenedPanes();
            if ( panes != null && panes.length > 0 ) {
                comp = panes[ 0 ];
            }
        }
        if ( comp != null ) {
            TopComponent tc = ( TopComponent ) SwingUtilities.getAncestorOfClass( TopComponent.class, comp );
            if ( tc != null ) {
                boolean opening = !tc.isOpened();
                if ( !opening ) {
                    tc.open();
                }
                tc.requestActive();
                // If we are opening, the window system will asynchronously
                // move focus around, etc., so stay out of the way of that
                // with a timer
                if ( opening ) {
                    JTextComponent compFinal = comp;
                    Timer timer = new Timer( 350, evt -> {
                                         compFinal.requestFocus();
                                         navigateTo( compFinal, attributed.key().element().start() );
                                     } );
                    timer.setRepeats( false );
                    timer.start();
                } else {
                    comp.requestFocus();
                    navigateTo( comp, attributed.key().element().start() );
                }
                return;
            }
        }
        ck.addPropertyChangeListener( new PCL( attributed.key().element().start(), ck ) );
        OpenCookie opener = dob.getLookup().lookup( OpenCookie.class );
        if ( opener != null ) {
            opener.open();
        }
    }

    class PCL implements PropertyChangeListener, ActionListener {

        private final int position;
        private final EditorCookie.Observable ck;
        private final Timer timer = new Timer( 10000, this );

        public PCL( int position, EditorCookie.Observable ck ) {
            this.position = position;
            this.ck = ck;
            timer.setRepeats( false );
            timer.start();
        }

        @Override
        public void propertyChange( PropertyChangeEvent evt ) {
            if ( evt == null ) {
                // seems to be null on component close
                timer.stop();
                return;
            }
            if ( EditorCookie.Observable.PROP_OPENED_PANES.equals( evt.getPropertyName() ) ) {
                EditorCookie.Observable src = ( EditorCookie.Observable ) evt.getSource();
                if ( src == null || evt.getNewValue() == null ) {
                    timer.stop();
                    return;
                }
                JTextComponent[] comps = ( JTextComponent[] ) evt.getNewValue();
                if ( comps.length > 0 ) {
                    timer.stop();
                    TopComponent tc = ( TopComponent ) SwingUtilities.getAncestorOfClass(
                            TopComponent.class, comps[ 0 ] );
                    if ( tc != null ) {
                        tc.requestActive();
                    }
                    src.removePropertyChangeListener( this );
                    navigateTo( comps[ 0 ], position );
                }
            }
        }

        @Override
        public void actionPerformed( ActionEvent e ) {
            ck.removePropertyChangeListener( this );
        }
    }

    private <T extends Enum<T>> boolean checkOneUnknown( Extraction extraction, NameReferenceSetKey<T> key, int position )
            throws DataObjectNotFoundException, IOException {
        SemanticRegions<UnknownNameReference<T>> unks = extraction.unknowns( key );
        SemanticRegion<UnknownNameReference<T>> reg = unks.at( position );
        LOGGER.log( Level.FINE, "Check unknowns of {0}: {1} finding ", new Object[]{ key, unks, reg } );
        if ( reg != null ) {
            Attributions<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T> attr = extraction
                    .resolveAll( key );
            if ( attr != null ) {
                SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> attributed
                        = attr.attributed().at( position );
                if ( attributed != null ) {
                    GrammarSource<?> src = attributed.key().source();
                    Optional<FileObject> ofo = src.lookup( FileObject.class );
                    if ( ofo.isPresent() ) {
                        DataObject dob = DataObject.find( ofo.get() );
                        EditorCookie.Observable ck = dob.getLookup().lookup( EditorCookie.Observable.class );
                        if ( ck != null ) {
                            StyledDocument doc = ck.openDocument();
                            Mutex.EVENT.readAccess( () -> {
                                ensureOpenAndNavigate( attributed, doc, dob, ck, key, position );
                            } );
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void navigateTo( JTextComponent component, int position ) {
        Position pos;
        try {
            pos = component.getDocument().createPosition( position );
        } catch ( BadLocationException ex ) {
            LOGGER.log( Level.INFO, "Cannot move caret to " + position
                                    + " in document of "
                                    + component.getDocument().getLength(), ex );
            return;
        }
        LOGGER.log( Level.FINER, "Setting caret to {0} in {1}", new Object[]{
            position, component } );
        Mutex.EVENT.readAccess( () -> {
            Caret caret = component.getCaret();
            if ( caret != null ) {
                if ( caret instanceof EditorCaret ) {
                    EditorCaret ec = ( EditorCaret ) caret;
                    CaretInfo info = ec.getLastCaret();
                    ec.moveCarets( new CaretMoveHandler() {
                        public void moveCarets( @NonNull CaretMoveContext context ) {
                            context.setDotAndMark( info, pos, Position.Bias.Forward, pos,
                                                   Position.Bias.Backward );
                        }
                    }, MoveCaretsOrigin.DISABLE_FILTERS );
                } else {
                    resetCaretMagicPosition( component );
                    caret.setDot( position );
                }
            }
        } );
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Arrays.deepHashCode( this.keys );
        return hash;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        if ( getClass() != obj.getClass() ) {
            return false;
        }
        final AntlrGotoDeclarationAction other = ( AntlrGotoDeclarationAction ) obj;
        return Arrays.deepEquals( this.keys, other.keys );
    }

}

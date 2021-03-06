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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.util.RequestProcessor;

/**
 * A syntax error in source which can be added to a set of fixes to
 * make it visible to the user in the editor - implement ParseResultHooks
 * and register to add errors to a parser result.
 *
 * @author Tim Boudreau
 */
public final class SyntaxError implements Comparable<SyntaxError> {

    private final Optional<Token> token;
    private final int startOffset;
    private final int endOffset;
    private final String message;
    private final RecognitionException originalException;
    private static final Logger LOG = Logger.getLogger( SyntaxError.class.getName() );

    SyntaxError( Optional<Token> token, int startOffset, int endOffset, String message,
            RecognitionException originalException ) {
        this.token = token;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.message = message;
        this.originalException = originalException;
    }

    public String id() {
        return startOffset + ":" + endOffset + ":" + ( message == null ? 0 : message.hashCode() );
    }

    ErrorDescription toErrorDescription( Snapshot snapshot, Vocabulary lexerVocabulary, NbParserHelper helper ) {
        if ( helper != null ) {
            ErrorDescription result = helper.convertError( snapshot, this );
            if ( result != null ) {
                return result;
            }
        }
        LazyFixList fixes = originalException != null && originalException.getOffendingState() != -1
                                    ? new SyntaxFixes( lexerVocabulary, snapshot ) : Fixes.none();

        Document doc = snapshot == null ? null : snapshot.getSource().getDocument( false );
        if ( doc != null ) {
            return forDocument( doc, fixes );
        }
        FileObject fo = snapshot == null ? null : snapshot.getSource().getFileObject();
        if ( fo != null ) {
            return forFileObject( fo, fixes );
        }
        return null;
    }

    private int start() {
        return startOffset < 0 ? 0 : startOffset;
    }

    private int end() {
        return endOffset < start() ? start() + 1 : endOffset;
    }

    private ErrorDescription forFileObject( FileObject fo, LazyFixList fixes ) {
        // Antlr can give us errors from -1 to 0
        return ErrorDescriptionFactory.createErrorDescription( id(), Severity.ERROR,
                                                               message, message, fixes, fo, start(),
                                                               end() );
    }

    private ErrorDescription forDocument( Document doc, LazyFixList fixes ) {
        // Antlr can give us errors from -1 to 0
        PositionFactory fact = PositionFactory.forDocument( doc );
        try {
            return ErrorDescriptionFactory.createErrorDescription( id(), Severity.ERROR,
                                                                   message, message, fixes, doc,
                                                                   fact.createPosition( start(),
                                                                                        Position.Bias.Backward ),
                                                                   fact.createPosition( end(),
                                                                                        Position.Bias.Backward ) );
        } catch ( BadLocationException ex ) {
            Logger.getLogger( SyntaxError.class.getName() ).log( Level.INFO, toString(), ex );
            int len = doc.getLength();
            int st = Math.max( 0, Math.min( len - 1, start() ) );
            int en = Math.max( 0, Math.min( len - 1, end() ) );

            return ErrorDescriptionFactory.createErrorDescription( id(), Severity.ERROR,
                                                                   message, message, fixes, doc,
                                                                   new SimplePosition( st ),
                                                                   new SimplePosition( en ) );
        }
    }

    static final RequestProcessor FIX_COMPUTATION
            = new RequestProcessor( "antlr-fix-computation", 5 );

    private class SyntaxFixes implements LazyFixList, Runnable {

        private final PropertyChangeSupport supp = new PropertyChangeSupport( this );
        private final Vocabulary lexerVocabulary;
        private final Snapshot snapshot;
        private final AtomicBoolean submitted = new AtomicBoolean();

        public SyntaxFixes( Vocabulary lexerVocabulary, Snapshot snapshot ) {
            this.lexerVocabulary = lexerVocabulary;
            this.snapshot = snapshot;
        }

        @Override
        public void run() {
            computeFixes();
        }

        void maybeStart() {
            if ( !_containsFixes() ) {
                submitted.set( true );
                updateFixes( Collections.emptyList() );
            } else if ( submitted.compareAndSet( false, true ) ) {
                FIX_COMPUTATION.submit( this );
            }
        }

        private boolean errored;

        private boolean _containsFixes() {
            synchronized ( this ) {
                if ( fixes != null ) {
                    return !fixes.isEmpty();
                }
            }
            if ( errored ) {
                return false;
            }
            boolean result = startOffset > 0 && endOffset > 0;
            if ( result ) {
                try {
                    // bug - ? - in antlr
                    result &= originalException != null && originalException.getExpectedTokens().size() > 0;
                } catch ( IllegalArgumentException ex ) {
                    errored = true;
                    LOG.log( Level.FINEST, "Exception computing expected "
                                           + "tokens for " + originalException
                                           + " state "
                                           + originalException.getOffendingState(), ex );

                }
            }

            return result;
        }

        @Override
        public void addPropertyChangeListener( PropertyChangeListener l ) {
            supp.addPropertyChangeListener( l );
            maybeStart();
        }

        @Override
        public void removePropertyChangeListener( PropertyChangeListener l ) {
            supp.removePropertyChangeListener( l );
            maybeStart();
        }

        @Override
        public boolean probablyContainsFixes() {
            boolean result = _containsFixes();
            if ( result ) {
                maybeStart();
            }
            return result;
        }

        private List<Fix> fixes;

        @Override
        public synchronized List<Fix> getFixes() {
            if ( fixes == null ) {
                computeFixes();
            }
            return fixes;
        }

        @Override
        public boolean isComputed() {
            return fixes != null;
        }

        private void updateFixes( List<Fix> fixes ) {
            List<Fix> old;
            synchronized ( this ) {
                old = fixes;
                this.fixes = fixes;
            }
            supp.firePropertyChange( PROP_FIXES, old, fixes );
            supp.firePropertyChange( PROP_COMPUTED, false, true );
        }

        private void computeFixes() {
            if ( !probablyContainsFixes() ) {
                updateFixes( fixes );
                return;
            }
            IntervalSet is = originalException.getExpectedTokens();
            int max = is.size();
            List<InsertFix> computedFixes = new ArrayList<>( is.size() );
            for ( int i = 0; i < max; i++ ) {
                int tokenId = is.get( i );
                String literalName = lexerVocabulary.getLiteralName( tokenId );
                if ( literalName != null ) {
                    computedFixes.add( new InsertFix( literalName ) );
                }
            }
            LOG.log( Level.FINER, "Computed fixes in {0} for {1}: {2}",
                     new Object[]{ snapshot, SyntaxError.this, computedFixes } );
            updateFixes( Collections.unmodifiableList( computedFixes ) );
        }

        private class InsertFix implements Fix, Comparable<InsertFix> {

            private final String literalName;

            public InsertFix( String literalName ) {
                this.literalName = literalName;
            }

            @Override
            public String getText() {
                return "Insert '" + literalName + "'";
            }

            @Override
            public ChangeInfo implement() throws Exception {
                FileObject fo = snapshot.getSource().getFileObject();
                Document doc = snapshot.getSource().getDocument( true );
                int end = Math.max( end(), start() + literalName.length() );
                PositionFactory pf = PositionFactory.forDocument( doc );
                // XXX these may be out of date - should set the positions
                // earlier on
                Position startPos = pf.createPosition( start(), Position.Bias.Backward );
                Position endPos = pf.createPosition( end(), Position.Bias.Forward );
                PositionRange range = pf.range( start(), Position.Bias.Backward, end, Position.Bias.Forward );
                PositionBounds rng = PositionFactory.toPositionBounds( range );
                if ( rng == null ) {
                    doc.insertString( startOffset, literalName, null );
                } else {
                    rng.setText( literalName );
                }
                ChangeInfo info = fo == null
                                          ? new ChangeInfo( rng.getBegin(), startPos )
                                          : new ChangeInfo( fo, rng.getBegin(), endPos );
                return info;
            }

            @Override
            public String toString() {
                return getText();
            }

            @Override
            public int compareTo( InsertFix o ) {
                return literalName.compareToIgnoreCase( o.literalName );
            }
        }
    }

    private static final class SimplePosition implements Position {

        private final int pos;

        public SimplePosition( int pos ) {
            this.pos = pos;
        }

        @Override
        public int getOffset() {
            return pos;
        }

        @Override
        public boolean equals( Object o ) {
            return o instanceof SimplePosition && ( ( SimplePosition ) o ).pos == pos;
        }

        @Override
        public int hashCode() {
            return 371 * pos;
        }

        @Override
        public String toString() {
            return Integer.toString( pos );
        }
    }

    public Optional<Token> token() {
        return token;
    }

    public int startOffset() {
        return startOffset;
    }

    public int endOffset() {
        return endOffset;
    }

    public String message() {
        return message;
    }

    public RecognitionException originalException() {
        return originalException;
    }

    @Override
    public String toString() {
        return "SyntaxError{" + "token=" + token + ", startOffset=" + startOffset + ", endOffset=" + endOffset + ", message=" + message + ", originalException=" + originalException + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + ( token.isPresent() ? token.get().getType() : 0 );
        hash = 61 * hash + this.startOffset;
        hash = 61 * hash + this.endOffset;
        hash = 61 * hash + Objects.hashCode( this.message );
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
        final SyntaxError other = ( SyntaxError ) obj;
        if ( this.startOffset != other.startOffset ) {
            return false;
        }
        if ( token.isPresent() != other.token.isPresent() ) {
            return false;
        }
        if ( this.endOffset != other.endOffset ) {
            return false;
        }
        if ( !Objects.equals( this.message, other.message ) ) {
            return false;
        }
        return !( token.isPresent() && Objects.equals( token.get(), other.token.get() ) );
    }

    @Override
    public int compareTo( SyntaxError o ) {
        int result = startOffset == o.startOffset ? 0 : startOffset > o.startOffset ? 1 : -1;
        if ( result == 0 ) {
            result = endOffset == o.endOffset ? 0 : endOffset < o.endOffset ? 1 : -1;
        }
        return result;
    }
}

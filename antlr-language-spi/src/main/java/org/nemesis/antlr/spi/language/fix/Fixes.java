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
package org.nemesis.antlr.spi.language.fix;

import com.mastfrog.range.IntRange;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.editor.function.DocumentConsumer;
import org.nemesis.editor.function.DocumentRunnable;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 * Makes adding error descriptions with hints to a
 * parsed document much more straightforward - if you register a ParseResultHook,
 * one of these will be passed to your code on every reparse, to allow editor hints
 * (lightbulb in the margin) and errors to be attached to the document.
 * <p>
 * <b>Important:</b>:Hint computation is <i>lazy</i> - which is to say that
 * the document may have changed by the time your hint is invoked.  Use
 * Position, PositionRange or PositionBounds instances to represent offsets
 * in the document, rather than the values from the raw Extraction you are
 * passed
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class Fixes {

    static final Logger LOG = Logger.getLogger( Fixes.class.getName() );

    static StyledDocument findDocument( Extraction extraction ) throws IOException {
        GrammarSource<?> src = extraction.source();
        Optional<StyledDocument> doc = src.lookup( StyledDocument.class );
        if ( !doc.isPresent() ) {
            Optional<FileObject> file = src.lookup( FileObject.class );
            if ( !file.isPresent() ) {
                throw new IllegalStateException( "Cannot find a document or a file from " + src + " with " + src );
            }
            FileObject fo = file.get();
            DataObject dob = DataObject.find( fo );
            EditorCookie ck = dob.getCookie( EditorCookie.class );
            if ( ck != null ) {
                return ck.openDocument();
            } else {
                throw new IllegalStateException( "Could not find an editor cookie for " + src );
            }
        } else {
            return doc.get();
        }

    }

    abstract StyledDocument document();

    abstract PositionFactory positions();

    /**
     * In some cases, the same error handler may be called more than once, or there
     * may be two paths by which the same hint may be generated; as long as identical
     * hints will use the same id, that situation can be avoided by wrapping error
     * generation in a call to this method.
     *
     * @param id  The proposed id
     * @param run A runnable that can throw a BadLocationException
     *
     * @throws BadLocationException If something goes wrong
     */
    public void ifUnusedErrorId( String id, DocumentRunnable run ) throws BadLocationException {
        run.run();
    }

    public boolean isUsedErrorId( String id ) {
        return false;
    }

    /**
     * Add an error.
     *
     * @param id          The (optional) error id
     * @param severity    The error severity
     * @param description The mandatory text to display in the hint
     * @param range       The bounds of the area to be altered
     * @param details     Optional details of the change
     * @param lazyFixes   Optional consumer which will be called (possibly asynchronously - store
     *                    locations in edit-safe Position objects!)
     */
    public abstract void add( String id, Severity severity, String description, PositionRange range,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes );

    /**
     * Utility method to return an empty lazy fix list.
     *
     * @return An empty lazy fix list
     */
    public static LazyFixList none() {
        return NoFixes.NO_FIXES;
    }

    /**
     * Create a builder for a new fix of severity HINT.
     *
     * @return A builder
     */
    public final FixBuilder newHint() {
        return new FixBuilder( this, Severity.HINT, positions() );
    }

    /**
     * Create a builder for a new fix of severity WARNING.
     *
     * @return A builder
     */
    public final FixBuilder newWarning() {
        return new FixBuilder( this, Severity.WARNING, positions() );
    }

    /**
     * Create a builder for a new fix of severity ERROR.
     *
     * @return A builder
     */
    public final FixBuilder newError() {
        return new FixBuilder( this, Severity.ERROR, positions() );
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( PositionRange item, String message,
            DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.HINT, message, item, null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( IntRange<? extends IntRange> item, String message,
            DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.HINT, message, positions().range( item ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( int start, int end, String message, DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( null, Severity.HINT, message, positions().range( start, end ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        The error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, PositionRange item, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, item, null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        The error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, IntRange<? extends IntRange> item, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, positions().range( item ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        The error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, PositionRange item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, item, details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        The error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, IntRange<? extends IntRange> item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, positions().range( item ), details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        The error id
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, int start, int end, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, positions().range( start, end ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        The error id
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, int start, int end, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, positions().range( start, end ), details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, PositionRange item, String message ) {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, item, null, null );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, IntRange<? extends IntRange> item, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, positions().range( item ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, PositionRange item,
            Supplier<? extends CharSequence> details, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, item, details, null );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, IntRange<? extends IntRange> item,
            Supplier<? extends CharSequence> details, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, positions().range( item ), details, null );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param start   The start offset in the document
     * @param end     The end offset in the document
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, int start, int end, String message ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, positions().range( start, end ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param start   The start offset in the document
     * @param end     The end offset in the document
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( String errorId, int start, int end, Supplier<? extends CharSequence> details,
            String message ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.HINT, message, positions().range( start, end ), details, null );
        return this;
    }

    /**
     * Add a hint with Severity.HINT.
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( PositionRange item, String message ) {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.HINT, message, item, null, null );
        return this;
    }

    /**
     * Add a hint with Severity.HINT.
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( IntRange<? extends IntRange> item, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.HINT, message, positions().range( item ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.HINT.
     *
     * @param start   The start offset in the document
     * @param end     The end offset in the document
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addHint( int start, int end, String message ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( null, Severity.HINT, message, positions().range( start, end ), null, null );
        return this;
    }

    /**
     * Ad a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( PositionRange item, String message,
            DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.WARNING, message, item, null, lazyFixes );
        return this;
    }

    /**
     * Ad a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( IntRange<? extends IntRange> item, String message,
            DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.WARNING, message, positions().range( item ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( PositionRange item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.WARNING, message, item, details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( IntRange<? extends IntRange> item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.WARNING, message, positions().range( item ), details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( PositionRange item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.ERROR, message, item, details, lazyFixes );
        return this;
    }

    /**
     * Ad a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( IntRange<? extends IntRange> item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.ERROR, message, positions().range( item ), details, lazyFixes );
        return this;
    }

    /**
     * Ad a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( int start, int end, String message, DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( null, Severity.WARNING, message, positions().range( start, end ), null, lazyFixes );
        return this;
    }

    /**
     * Ad a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( int start, int end, String message, Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes )
            throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( null, Severity.WARNING, message, positions().range( start, end ), details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, PositionRange item, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, item, null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, IntRange<? extends IntRange> item, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, positions().range( item ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, PositionRange item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, item, details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, IntRange<? extends IntRange> item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, positions().range( item ), details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, int start, int end, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, positions().range( start, end ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, int start, int end, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, positions().range( start, end ), details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id      An error id
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, PositionRange item, String message ) {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, item, null, null );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id      An error id
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, IntRange<? extends IntRange> item, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, positions().range( item ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id      An error id
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, PositionRange item,
            Supplier<? extends CharSequence> details, String message ) {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, item, details, null );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id      An error id
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, IntRange<? extends IntRange> item,
            Supplier<? extends CharSequence> details, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, positions().range( item ), details, null );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id      An error id
     * @param start   The start offset in the document
     * @param end     The end offset in the document
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, int start, int end, String message ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, positions().range( start, end ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id      An error id
     * @param start   The start offset in the document
     * @param end     The end offset in the document
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( String errorId, int start, int end, Supplier<? extends CharSequence> details,
            String message ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.WARNING, message, positions().range( start, end ), details, null );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( IntRange<? extends IntRange> item, String message ) throws BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.WARNING, message, positions().range( item ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param start   The start offset in the document
     * @param end     The end offset in the document
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addWarning( int start, int end, String message ) throws BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( null, Severity.WARNING, message, positions().range( start, end ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( PositionRange item, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.ERROR, message, item, null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( IntRange<? extends IntRange> item, String message,
            DocumentConsumer<FixConsumer> lazyFixes )
            throws BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.ERROR, message, positions().range( item ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( int start, int end, String message, DocumentConsumer<FixConsumer> lazyFixes )
            throws BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( null, Severity.ERROR, message, positions().range( start, end ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String id, PositionRange item, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) {
        if ( !active() ) {
            return this;
        }
        add( id, Severity.ERROR, message, item, null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String id, IntRange<? extends IntRange> item, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( id, Severity.ERROR, message, positions().range( item ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String id, IntRange<? extends IntRange> item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( id, Severity.ERROR, message, positions().range( item ), details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param item      The item the fix relates to
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String id, PositionRange item, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) {
        if ( !active() ) {
            return this;
        }
        add( id, Severity.ERROR, message, item, details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String id, int start, int end, String message,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( id, Severity.ERROR, message, positions().range( start, end ), null, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id        the error id
     * @param start     The start offset in the document
     * @param end       The end offset in the document
     * @param message   The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     *                  alter the document in some way
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String id, int start, int end, String message,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( id, Severity.ERROR, message, positions().range( start, end ), details, lazyFixes );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR.
     *
     * @param id      the error id
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String errorId, IntRange<? extends IntRange> item, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.ERROR, message, positions().range( item ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR.
     *
     * @param id      the error id
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String errorId, PositionRange item, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.ERROR, message, positions().range( item ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR.
     *
     * @param id      the error id
     * @param start   The start offset in the document
     * @param end     The end offset in the document
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String errorId, int start, int end, String message ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.ERROR, message, positions().range( start, end ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR.
     *
     * @param id      the error id
     * @param start   The start offset in the document
     * @param end     The end offset in the document
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String errorId, int start, int end, String message,
            Supplier<? extends CharSequence> details ) throws
            BadLocationException {
        assert start <= end : "start > end";
        if ( !active() ) {
            return this;
        }
        add( errorId, Severity.ERROR, message, positions().range( start, end ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( IntRange<? extends IntRange> item, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.ERROR, message, positions().range( item ), null, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( PositionRange item, String message ) {
        if ( !active() ) {
            return this;
        }
        add( null, Severity.ERROR, message, item, null, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String id, IntRange<? extends IntRange> item, String message,
            Supplier<? extends CharSequence> details ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        add( id, Severity.ERROR, message, positions().range( item ), details, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR
     *
     * @param item    The item the fix relates to
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( String id, PositionRange item, String message,
            Supplier<? extends CharSequence> details ) {
        if ( !active() ) {
            return this;
        }
        add( id, Severity.ERROR, message, item, details, null );
        return this;
    }

    /**
     * Add a hint with Severity.ERROR
     *
     * @param start   the start
     * @param end     the end
     * @param message The message to display in the editor margin
     *
     * @return this
     *
     * @throws BadLocationException if the coordinates supplied are outside the
     *                              document
     */
    public final Fixes addError( int start, int end, String message ) throws
            BadLocationException {
        if ( !active() ) {
            return this;
        }
        assert start <= end : "start > end";
        add( null, Severity.ERROR, message, positions().range( start, end ), null, null );
        return this;
    }

    public static Fixes create( Extraction extraction, ParseResultContents contents ) throws IOException {
        return new FixesImpl( extraction, contents );
    }

    /**
     * There are occasionally reasons to perform a parse in the middle of running
     * an action, where updating hints doesn't make much sense; test this before
     * computeing hints to determine if no action is needed.
     *
     * @return True if the Fixes will not throw away whatever is added to it
     */
    public boolean active() {
        return true;
    }

    /**
     * Needed for ParseResultHooks run initially against a stale parser result.
     *
     * @return A fixes
     */
    public static Fixes empty() {
        return Empty.INSTANCE;
    }

    private static final class Empty extends Fixes {

        static final Empty INSTANCE = new Empty();

        @Override
        public void add( String id, Severity severity, String description, PositionRange range,
                Supplier<? extends CharSequence> details,
                DocumentConsumer<FixConsumer> lazyFixes ) {
            // do nothing
        }

        @Override
        public StyledDocument document() {
            throw new UnsupportedOperationException( "Not supported yet." );
        }

        @Override
        PositionFactory positions() {
            throw new UnsupportedOperationException( "Not supported yet." );
        }

        public boolean active() {
            return false;
        }

        public boolean isUsedErrorId( String id ) {
            return true;
        }

        public void ifUnusedErrorId( String id, DocumentRunnable run ) throws BadLocationException {
            // do nothing
        }
    }
}

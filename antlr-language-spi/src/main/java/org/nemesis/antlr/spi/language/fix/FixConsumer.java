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

import java.util.Collections;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.nemesis.editor.edit.Applier;
import org.nemesis.editor.edit.EditBag;
import org.nemesis.editor.function.DocumentConsumer;
import org.nemesis.editor.ops.CaretPositionCalculator;
import org.nemesis.editor.ops.DocumentOperator;
import org.netbeans.spi.editor.hints.Fix;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Consumer for proposed fixes which will apply hints.
 *
 * @author Tim Boudreau
 */
public final class FixConsumer {
    private static final DocumentOperator CARET_PRESERVING = DocumentOperator.NON_JUMP_REENTRANT_UPDATE_DOCUMENT;

    private static final DocumentOperator NO_SPECIAL_CARET_HANDLING = DocumentOperator.builder()
            .acquireAWTTreeLock()
            .blockIntermediateRepaints()
            .disableTokenHierarchyUpdates()
            .lockAtomicAsUser()
            .singleUndoTransaction()
            .writeLock()
            .build();

    private List<Fix> fixes;
    final StyledDocument doc;

    FixConsumer( StyledDocument doc ) {
        this.doc = doc;
    }

    List<Fix> entries() {
        return fixes == null ? Collections.emptyList() : fixes;
    }

    public FixConsumer addFix( Fix fix ) {
        if ( fixes == null ) {
            fixes = new CopyOnWriteArrayList<>();
        }
        fixes.add( fix );
        return this;
    }

    public FixConsumer addFix( String description,
            DocumentConsumer<EditBag> bagConsumer ) {
        return addFix( false, () -> description, bagConsumer );
    }

    public FixConsumer addFix( Supplier<? extends CharSequence> description,
            DocumentConsumer<EditBag> bagConsumer ) {
        addFix( false, description, bagConsumer );
        return this;
    }

    public FixConsumer addFix( boolean selectAlteredRegion, String description,
            DocumentConsumer<EditBag> bagConsumer ) {
        return addFix( selectAlteredRegion, () -> description, bagConsumer );
    }

    public FixConsumer addFix( boolean selectAlteredRegion, Supplier<? extends CharSequence> description,
            DocumentConsumer<EditBag> bagConsumer ) {
        Applier applier = new Applier( selectAlteredRegion
                                               ? NO_SPECIAL_CARET_HANDLING
                                               : CARET_PRESERVING );
        EditBag bag = new EditBag( doc, applier );
        try {
            bagConsumer.accept( bag );
            if ( !bag.isEmpty() ) {
                addFix( new FixImpl( applier, description, bag, selectAlteredRegion ) );
            }
        } catch ( BadLocationException ex ) {
            Exceptions.printStackTrace( ex );
        }
        return this;
    }

    public FixConsumer addFix( CaretPositionCalculator calc, String description,
            DocumentConsumer<EditBag> bagConsumer ) {
        return addFix( calc, () -> description, bagConsumer );
    }

    public FixConsumer addFix( CaretPositionCalculator calc, Supplier<? extends CharSequence> description,
            DocumentConsumer<EditBag> bagConsumer ) {
        Applier applier = new Applier( () -> {
            return DocumentOperator.builder()
                    .acquireAWTTreeLock()
                    .blockIntermediateRepaints()
                    .disableTokenHierarchyUpdates()
                    .lockAtomicAsUser()
                    .singleUndoTransaction()
                    .writeLock()
                    .restoringCaretPosition( notNull( "calc", calc ) ).build();
        } );
        EditBag bag = new EditBag( doc, applier );
        try {
            bagConsumer.accept( bag );
            if ( !bag.isEmpty() ) {
                addFix( new FixImpl( applier, description, bag, false ) );
            }
        } catch ( BadLocationException ex ) {
            Exceptions.printStackTrace( ex );
        }
        return this;
    }

    /**
     * Convenience method, since hints often mention a line number;
     * find the line number of an offset in a document, with the caveat
     * that NbDocument.findLineNumber will return the <i>previous</i>
     * line for a character that starts a line; so this simply tests
     * if the offset is > 0 and the preceding character is a newline,
     * and if so, adds one to the result.
     *
     * @param doc
     * @param offset
     *
     * @return
     */
    public final int lineNumberForDisplay( int offset ) throws BadLocationException {
        int line = NbDocument.findLineNumber( ( StyledDocument ) doc, offset );
        if ( offset > 0 && doc.getText( offset - 1, 1 ).charAt( 0 ) == '\n' ) {
            line++;
        }
        return line;
    }
}

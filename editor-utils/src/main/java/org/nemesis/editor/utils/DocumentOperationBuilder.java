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

import com.mastfrog.util.strings.Strings;
import java.util.EnumSet;
import java.util.Set;

import static org.nemesis.editor.utils.DocumentOperator.Props.*;

/**
 * A builder for document mutations with various features for how the edit * and other parts of the system respond to
 * those edits as they happen. All locking and similar operations are applied
 * in a specific, necessary order, before calling your document processor - the order
 * you call methods on the builder does not change behavior.
 */
public final class DocumentOperationBuilder {

    private final Set<DocumentOperator.Props> props = EnumSet.noneOf( DocumentOperator.Props.class );

    DocumentOperationBuilder() {
    }

    private DocumentOperationBuilder addProp( DocumentOperator.Props prop ) {
        props.add( prop );
        return this;
    }

    /**
     * Attempt to block the editor from repainting until all operations are
     * complete.
     *
     * @return this
     */
    public DocumentOperationBuilder blockIntermediateRepaints() {
        return addProp( BLOCK_REPAINTS );
    }

    /**
     * Read-lock the document while running.  Note this does not preclude
     * also write locking the document (and some operations will require both);
     * the DocumentOperation will take care of locking in the correct order.
     *
     * @return this
     */
    public DocumentOperationBuilder readLock() {
        return addProp( READ_LOCK );
    }

    /**
     * Ensure that only a single undo operation is generated for potentially
     * multiple operations.  <b>Note:</b> You <i>must</i> call either
     * <code>writeLockAsUser()</code> or <code>writeLock()</code> if you
     * use this method - adding an undoable edit to a NetBeans document will throw
     * an exception if attempted outside a write lock. The builder will fail
     * early in this condition, but cannot decide for you which is the correct
     * locking mode.
     *
     * @return this
     */
    public DocumentOperationBuilder singleUndoTransaction() {
        return addProp( ONE_UNDOABLE_EDIT );
    }

    /**
     * If edits modify the caret position (many will by default), replace
     * the caret to a location as close as possible to its original
     * location.
     *
     * @return this
     */
    public DocumentOperationBuilder preserveCaretPosition() {
        return addProp( PRESERVE_CARET_POSITION );
    }

    /**
     * Run with <code>NbDocument.runAtomicAsUser()</code>. Mutually
     * exclusive with <code>writeLock()</code> - an exception will be thrown
     * if both are set.
     *
     * @return this
     */
    public DocumentOperationBuilder writeLockAsUser() {
        if ( props.contains( WRITE_LOCK ) ) {
            throw new IllegalStateException( "Cannot use both write lock and write lock as user" );
        }
        return addProp( WRITE_LOCK_AS_USER );
    }

    /**
     * Run with <code>NbDocument.runAtomicAsUser()</code>. Mutually
     * exclusive with <code>writeLockAsUser()</code> - an exception will be
     * thrown if both are set.
     *
     * @return this
     */
    public DocumentOperationBuilder writeLock() {
        if ( props.contains( WRITE_LOCK_AS_USER ) ) {
            throw new IllegalStateException( "Cannot use both write lock and write lock as user" );
        }
        return addProp( WRITE_LOCK );
    }

    /**
     * Disable re-lex and reparse operations until exit - this can
     * considerably speed up performing multiple small edits to a
     * document. Disabling token hierarchy updates implicitly adds
     * read-locking, as it will throw an exception otherwise.
     *
     * @return this
     */
    public DocumentOperationBuilder disableTokenHierarchyUpdates() {
        // MTI must have read lock
        addProp( READ_LOCK );
        return addProp( DISABLE_MTI );
    }

    /**
     * Run the operation under <code>synchronnized(theEditorPane.getTreeLock())</code> -
     * this will block all repaints and revalidation of the component until the
     * operation is completed, <i>but is highly deadlock-prone if invoked from any
     * thread but the AWT event thread</i> (if the entire UI stops painting,
     * that's what happened). Caveat emptor. Either do your operations on the
     * event thread if you use this, or be <i>100% sure</i> you are not called under
     * any locks of any sort.
     *
     * @return this
     */
    public DocumentOperationBuilder acquireAWTTreeLock() {
        return addProp( ACQUIRE_AWT_TREE_LOCK );
    }

    @Override
    public String toString() {
        return Strings.join( ", ", props );
    }

    /**
     * Build a document operator which can be held and reused for multiple
     * documents and operations.
     *
     * @return A document operator
     */
    public DocumentOperator build() {
        if ( props.contains( ONE_UNDOABLE_EDIT )
             && !( props.contains( WRITE_LOCK ) || props.contains( WRITE_LOCK_AS_USER ) ) ) {
            throw new IllegalStateException( "When using one-undoable-edit, write lock"
                                             + " must already set (adding undo options will"
                                             + "throw an exception otherwise) - call either "
                                             + "writeLock or writeLockAsUser "
                                             + "in the builder." );
        }
        return new DocumentOperator( props );
    }

}

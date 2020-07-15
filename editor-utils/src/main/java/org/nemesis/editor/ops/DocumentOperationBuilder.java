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
package org.nemesis.editor.ops;

import com.mastfrog.util.strings.Strings;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import javax.swing.text.StyledDocument;
import static org.nemesis.editor.ops.DocumentOperator.BuiltInDocumentOperations.*;

/**
 * A builder for document mutations with various features for how the edit * and
 * other parts of the system respond to those edits as they happen. All locking
 * and similar operations are applied in a specific, necessary order, before
 * calling your document processor - the order you call methods on the builder
 * does not change behavior.
 * <p>
 * </p>
 * The builder can now accept ad-hoc functions which process a document. The
 * order these are run is affected by what standard operations (document
 * locking, etc.) that have a well defined order they <i>must</i> run in, so
 * that they run subsequently.
 *
 */
public final class DocumentOperationBuilder {

    private final Set<Function<StyledDocument, DocumentPreAndPostProcessor>> props = new LinkedHashSet<>(
            EnumSet.noneOf(DocumentOperator.BuiltInDocumentOperations.class));

    DocumentOperationBuilder() {
    }

    private DocumentOperationBuilder addProp(DocumentOperator.BuiltInDocumentOperations prop) {
        props.add(prop);
        return this;
    }

    /**
     * Add a custom processor which will be run <i>before whatever operation you add next</i>:
     * That is, locking operations have a certain order they need to run in to avoid deadlocking,
     * and some other operations need to run only when certain locks have been acquired.  If
     * added last, it will be wrapped around whatever DocumentOperation you pass to build().
     *
     * @param procesorFactory A custom processor whose begin and end methods will be invoked
     * prior to whatever you call next - so if you call lockAtomic() next, then this processor
     * will run before/after atomic locking is performed, and so forth
     * @return this
     */
    public DocumentOperationBuilder add(Function<StyledDocument, DocumentPreAndPostProcessor> procesorFactory) {
        props.add(procesorFactory);
        return this;
    }

    /**
     * Attempt to block the editor from repainting until all operations are
     * complete.
     *
     * @return this
     */
    public DocumentOperationBuilder blockIntermediateRepaints() {
        return addProp(BLOCK_REPAINTS);
    }

    /**
     * Read-lock the document while running. Note this does not preclude also
     * write locking the document (and some operations will require both); the
     * DocumentOperation will take care of locking in the correct order.
     *
     * @return this
     */
    public DocumentOperationBuilder lockAtomic() {
        if (props.contains(ATOMIC_AS_USER)) {
            props.remove(ATOMIC_AS_USER);
        }
        return addProp(ATOMIC);
    }

    /**
     * Ensure that only a single undo operation is generated for potentially
     * multiple operations.  <b>Note:</b> You <i>must</i> call either
     * <code>lockAtomicAsUser()</code> or <code>writeLock()</code> if you use
     * this method - adding an undoable edit to a NetBeans document will throw
     * an exception if attempted outside a write lock. The builder will fail
     * early in this condition, but cannot decide for you which is the correct
     * locking mode.
     *
     * @return this
     */
    public DocumentOperationBuilder singleUndoTransaction() {
        return addProp(ONE_UNDOABLE_EDIT);
    }

    /**
     * If edits modify the caret position (many will by default), replace the
     * caret to a location as close as possible to its original location.
     *
     * @return this
     */
    public DocumentOperationBuilder preserveCaretPosition() {
        for (Function<StyledDocument, DocumentPreAndPostProcessor> f : props) {
            // May be a caret restorer - don't clobber it and have two competing
            // things trying to restore the caret position
            if (f instanceof DocumentOperator.PreserveCaret) {
                return this;
            }
        }
        return addProp(PRESERVE_CARET_POSITION);
    }

    public DocumentOperationBuilder restoringCaretPosition(CaretPositionCalculator calc) {
        for (Iterator<Function<StyledDocument, DocumentPreAndPostProcessor>> it = props.iterator(); it.hasNext();) {
            if (it.next() == PRESERVE_CARET_POSITION) {
                it.remove();
            }
        }
        return add(new Function<StyledDocument, DocumentPreAndPostProcessor>() {
            @Override
            public DocumentPreAndPostProcessor apply(StyledDocument doc) {
                return new DocumentOperator.PreserveCaret(doc, calc);
            }

            public String toString() {
                return "restore-caret-position(" + calc + ")";
            }
        });
    }

    /**
     * Run with <code>NbDocument.runAtomicAsUser()</code>. Mutually exclusive
     * with <code>writeLock()</code> - an exception will be thrown if both are
     * set.
     *
     * @return this
     */
    public DocumentOperationBuilder lockAtomicAsUser() {
        if (props.contains(ATOMIC)) {
//            throw new IllegalStateException("Cannot use both write lock and write lock as user");
            props.remove(ATOMIC);
        }
        return addProp(ATOMIC_AS_USER);
    }

    /**
     * Run with <code>NbDocument.runAtomicAsUser()</code>. Mutually exclusive
     * with <code>lockAtomicAsUser()</code> - an exception will be thrown if both
     * are set.
     *
     * @return this
     */
    public DocumentOperationBuilder writeLock() {
        return addProp(WRITE_LOCK);
    }

    /**
     * Run inside the Document's <code>render()</code> method.
     *
     * @return this
     */
    public DocumentOperationBuilder readLock() {
        return addProp(RENDER);
    }

    /**
     * Disable re-lex and reparse operations until exit - this can considerably
     * speed up performing multiple small edits to a document. Disabling token
     * hierarchy updates implicitly adds read-locking, as it will throw an
     * exception otherwise.
     *
     * @return this
     */
    public DocumentOperationBuilder disableTokenHierarchyUpdates() {
        // MTI must have read lock
        addProp(ATOMIC);
        return addProp(DISABLE_MTI);
    }

    /**
     * Run the operation under
     * <code>synchronnized(theEditorPane.getTreeLock())</code> - this will block
     * all repaints and revalidation of the component until the operation is
     * completed, <i>but is highly deadlock-prone if invoked from any thread but
     * the AWT event thread</i> (if the entire UI stops painting, that's what
     * happened). Caveat emptor. Either do your operations on the event thread
     * if you use this, or be <i>100% sure</i> you are not called under any
     * locks of any sort.
     *
     * @return this
     */
    public DocumentOperationBuilder acquireAWTTreeLock() {
        return addProp(ACQUIRE_AWT_TREE_LOCK);
    }

    @Override
    public String toString() {
        return Strings.join(", ", props);
    }

    /**
     * Build a document operator which can be held and reused for multiple
     * documents and operations.
     *
     * @return A document operator
     */
    public DocumentOperator build() {
        if (props.contains(ONE_UNDOABLE_EDIT)
                && !props.contains(WRITE_LOCK) && !props.contains(ATOMIC_AS_USER)) {
            new IllegalStateException("When using one-undoable-edit, write lock"
                    + " must already set (adding undo options will"
                    + "throw an exception otherwise) - call either "
                    + "writeLock(), lockAtomic() or lockAtomicAsUser() "
                    + "in the builder, which cannot know which one is right for "
                    + "your use case. Adding lockAtomicAsUser() as a guess.").printStackTrace(System.err);
            props.add(ATOMIC_AS_USER);
        }
//        if (props.contains(BuiltInDocumentOperations.WRITE_LOCK) && (props.contains(BuiltInDocumentOperations.ATOMIC) || props.contains(BuiltInDocumentOperations.ATOMIC_AS_USER))) {
//            props.remove(BuiltInDocumentOperations.WRITE_LOCK);
//        }
        return new DocumentOperator(props);
    }

}

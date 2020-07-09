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

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoableEdit;
import org.netbeans.api.editor.document.CustomUndoDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.openide.text.CloneableEditorSupport;

/**
 * Undo across multiple documents; the undoable event gets tied to the UndoRedo
 * of the document passed to the constructor.  <code>close()</code> must be
 * called if any edits are added.
 *
 * @author Tim Boudreau
 */
public final class MultiDocumentUndo implements AutoCloseable {

    private final List<Document> documents = new ArrayList<>();
    private final Document target;

    public MultiDocumentUndo(Document undoStackTarget) {
        this.target = undoStackTarget;
    }

    synchronized void maybeAdd(Document doc) {
        if (!documents.contains(doc)) {
            documents.add(doc);
            if (documents.size() == 1) {
                sendUndoableEdit(target, CloneableEditorSupport.BEGIN_COMMIT_GROUP);
            }
        }
    }

    public static void forDocument(Document undoStackTarget, BadLocationConsumer<MultiDocumentUndo> c) throws BadLocationException {
        try (MultiDocumentUndo mdu = new MultiDocumentUndo(undoStackTarget)) {
            c.accept(mdu);
        }
    }

    /**
     * Add an undoable edit for some document.
     *
     * @param doc The document
     * @param edit The edit
     * @return this
     * @throws BadLocationException
     */
    public MultiDocumentUndo add(Document doc, UndoableEdit edit) throws BadLocationException {
        maybeAdd(doc);
        if (edit == CloneableEditorSupport.END_COMMIT_GROUP) {
            edit = CloneableEditorSupport.MARK_COMMIT_GROUP;
        }
        sendUndoableEdit(target, edit);
        return this;
    }

    /**
     * Close any undo transaction associated with this document.
     */
    @Override
    public void close() {
        if (documents.size() > 0) {
            sendUndoableEdit(target, CloneableEditorSupport.END_COMMIT_GROUP);
        }
    }

    private static void sendUndoableEdit(Document doc, UndoableEdit edit) {
        CustomUndoDocument customUndoDocument
                = LineDocumentUtils.as(doc,
                        CustomUndoDocument.class);
        if (customUndoDocument != null) {
            customUndoDocument.addUndoableEdit(edit);
        }
    }
}

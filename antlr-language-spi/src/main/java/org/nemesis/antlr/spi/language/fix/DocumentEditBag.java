/*
 * To addChange this license header, choose License Headers in Project Properties.
 * To addChange this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.spi.language.fix;

import java.awt.EventQueue;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.misc.utils.function.ThrowingConsumer;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.caret.EditorCaret;
import org.netbeans.editor.BaseDocument;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 * A collection of edits to one or more documents which make up a fix proposed
 * by a hint.
 *
 * @author Tim Boudreau
 */
public abstract class DocumentEditBag {

    private final Optional<FileObject> extractedFile;
    private final Optional<Document> extractedDocument;

    DocumentEditBag(Optional<FileObject> extractedFile, Optional<Document> extractedDocument) {
        this.extractedFile = extractedFile;
        this.extractedDocument = extractedDocument;
    }

    /**
     * Add a record of a change you have already made to the document.
     * <i>You do not need to call this method if you are using one of the
     * methods on this class which alters the document already - it takes care
     * of that.
     *
     * @param document A document
     * @param fileMayBeNull A file
     * @param start A start point
     * @param end An end point
     * @return this
     * @throws Exception
     */
    public abstract DocumentEditBag addChange(BaseDocument document, Optional<FileObject> fileMayBeNull, int start, int end) throws Exception;

    interface BLE {

        void run() throws Exception;
    }
    private boolean locked;

    @SuppressWarnings(value = "null")
    int[] runLocked(BaseDocument doc, BLE ble) throws Exception {
        if (locked) {
            ble.run();
        }
        locked = true;
        try {
            Exception[] ex = new BadLocationException[1];
            int[] len = new int[2];
            EventQueue.invokeLater(() -> {
                doc.runAtomicAsUser(() -> {
                    JTextComponent comp = EditorRegistry.findComponent(doc);
                    try {
                        Position dot = null;
                        Position mark = null;
                        Caret caret = null;
                        if (comp != null) {
                            caret = (EditorCaret) comp.getCaret();
                            if (caret != null) {
                                // during close it can be
                                dot = doc.createPosition(caret.getDot());
                                mark = doc.createPosition(caret.getMark());
                            }
                        }
                        len[0] = doc.getLength();
                        ble.run();
                        len[1] = doc.getLength();
                        if (dot != null) {
                            int dotOffset = dot.getOffset();
                            int markOffset = mark.getOffset();
                            comp.setSelectionStart(dotOffset);
                            comp.setSelectionEnd(markOffset);
                        }
                    } catch (Exception bex) {
                        ex[0] = bex;
                    }
                });
            });
            if (ex[0] != null) {
                throw ex[0];
            }
            return len;
        } finally {
            locked = false;
        }
    }

    /**
     * Make multiple changes to the document, guaranteeing they are all run and
     * succeed or fail together while locking the document only once.
     *
     * @param doc The document
     * @param c A consumer for a collection of related edits
     * @return this
     * @throws Exception If something goes wrong
     */
    public final DocumentEditBag multiple(BaseDocument doc, Consumer<DocumentEditSet> c) throws Exception {
        DocumentEditSet cons = new DocumentEditSet(doc, this);
        runLocked(doc, cons.runner());
        return this;
    }

    /**
     * Make multiple changes to the file, guaranteeing they are all run and
     * succeed or fail together while locking the document only once.
     *
     * @param file A file
     * @param c A consumer for a collection of related edits
     * @return this
     * @throws Exception If something goes wrong
     */
    public final DocumentEditBag multiple(FileObject file, Consumer<DocumentEditSet> c) throws Exception {
        return documentForFile(file, doc -> {
            multiple(doc, c);
        });
    }

    /**
     * Replaces text in the document.
     *
     * @param document The document
     * @param start The start offset
     * @param end The end offset
     * @param text The replacement text
     * @return this
     * @throws Exception If something goes wrong
     */
    public final DocumentEditBag replace(BaseDocument document, int start, int end, String text) throws Exception {
        Position startPos = document.createPosition(start);
        runLocked(document, () -> {
            document.replace(startPos.getOffset(), end - start, text, null);
        });
        return addChange(document, start, Math.max(start + text.length(), end));
    }

    /**
     * Replaces text in the file.
     *
     * @param file A file
     * @param start The start offset
     * @param end The end offset
     * @param text The replacement text
     * @return this
     * @throws Exception If something goes wrong
     */
    public final DocumentEditBag replace(FileObject file, int start, int end, String text) throws Exception {
        return documentForFile(file, doc -> {
            replace(doc, start, end, text);
        });
    }

    /**
     * Deletes text in the document.
     *
     * @param document The document
     * @param start The start offset
     * @param end The end offset
     * @return this
     * @throws Exception If something goes wrong
     */
    public final DocumentEditBag delete(BaseDocument document, int start, int end) throws Exception {
        Position startPos = document.createPosition(start);
        runLocked(document, () -> {
            boolean followedByNewline = characterIsNewline(document, end);
            document.remove(startPos.getOffset(), (end - start) + (followedByNewline ? 1 : 0));
        });
        return addChange(document, start, end);
    }

    private boolean characterIsNewline(BaseDocument doc, int pos) throws Exception {
        if (pos == 0) {
            return false;
        }
        if (pos >= doc.getLength()) {
            return false;
        }
        return doc.getText(pos, 1).charAt(0) == '\n';
    }

    /**
     * Deletes text in the file.
     *
     * @param file A file
     * @param start The start offset
     * @param end The end offset
     * @return this
     * @throws Exception If something goes wrong
     */
    public final DocumentEditBag delete(FileObject file, int start, int end) throws Exception {
        return documentForFile(file, doc -> {
            delete(doc, start, end);
        });
    }

    private DocumentEditBag documentForFile(FileObject fo, ThrowingConsumer<BaseDocument> c) throws Exception {
        // If we are operating on a file and it is the same one that was extracted, we
        // already have the document for it
        if (extractedFile.isPresent() && fo.equals(extractedFile.get()) && extractedDocument.isPresent() && extractedDocument.get() instanceof BaseDocument) {
            c.accept((BaseDocument) extractedDocument.get());
        } else {
            DataObject dob = DataObject.find(fo);
            EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
            if (ck == null) {
                throw new IllegalStateException("No editor cookie for " + fo.getPath() + " - cannot load a document to edit");
            }
            StyledDocument doc = ck.openDocument();
            if (!(doc instanceof BaseDocument)) {
                throw new IllegalStateException("Not a BaseDocument: " + doc);
            }
            c.accept((BaseDocument) doc);
            SaveCookie sck = dob.getLookup().lookup(SaveCookie.class);
            if (sck != null) {
                sck.save();
            }
        }
        return this;
    }

    /**
     * Inserts text into the document.
     *
     * @param document The document
     * @param start The start offset
     * @param end The end offset
     * @param text The replacement text
     * @return this
     * @throws Exception If something goes wrong
     */
    public final DocumentEditBag insert(BaseDocument document, int start, String text) throws Exception {
        Position startPos = document.createPosition(start);
        int[] lengthBeforeAfter = runLocked(document, () -> {
            document.insertString(startPos.getOffset(), text, null);
        });
        return addChange(document, start, lengthBeforeAfter[1]);
    }

    /**
     * Inserts text into the file.
     *
     * @param document The document
     * @param start The start offset
     * @param end The end offset
     * @param text The replacement text
     * @return this
     * @throws Exception If something goes wrong
     */
    public final DocumentEditBag insert(FileObject file, int start, String text) throws Exception {
        return documentForFile(file, doc -> {
            insert(doc, start, text);
        });
    }

    /**
     * Add a record of a change you have already made to the document.
     * <i>You do not need to call this method if you are using one of the
     * methods on this class which alters the document already - it takes care
     * of that.
     *
     * @param file A file
     * @param start A start point
     * @param end An end point
     * @return this
     * @throws Exception
     */
    public final DocumentEditBag addChange(Optional<FileObject> file, int start, int end) throws Exception {
        return addChange((BaseDocument) null, file, start, end);
    }

    /**
     * Add a record of a change you have already made to the document.
     * <i>You do not need to call this method if you are using one of the
     * methods on this class which alters the document already - it takes care
     * of that.
     *
     * @param doc A document
     * @param start A start point
     * @param end An end point
     * @return this
     * @throws Exception
     */
    public final DocumentEditBag addChange(BaseDocument doc, int start, int end) throws Exception {
        return addChange(doc, Optional.empty(), start, end);
    }

    /**
     * Add a record of a change you have already made to the document.
     * <i>You do not need to call this method if you are using one of the
     * methods on this class which alters the document already - it takes care
     * of that.
     *
     * @param start A start point
     * @param end An end point
     * @return this
     * @throws Exception
     */
    public final DocumentEditBag addChange(int start, int end) throws Exception {
        return addChange((BaseDocument) null, Optional.empty(), start, end);
    }
}

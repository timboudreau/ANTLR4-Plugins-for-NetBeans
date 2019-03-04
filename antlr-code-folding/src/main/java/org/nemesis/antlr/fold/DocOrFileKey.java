package org.nemesis.antlr.fold;

import java.util.logging.Level;
import javax.swing.text.Document;
import static org.nemesis.antlr.fold.FoldUtils.documentForFileObject;
import static org.nemesis.antlr.fold.FoldUtils.fileObjectForDocument;
import org.netbeans.modules.parsing.api.Source;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;

/**
 * A map key which holds a weak reference to a document and associated file, and
 * is equal to another instance if it references the same file by path OR
 * document by identity hash code.
 *
 * @author Tim Boudreau
 */
final class DocOrFileKey {

    private final DocumentReference docRef;
    private final FileReference fileRef;

    DocOrFileKey(Document doc, FileObject file) {
        this.docRef = new DocumentReference(doc);
        FileReference fr;
        try {
            fr = file == null ? null : new FileReference(file);
        } catch (FileStateInvalidException ex) {
            FoldTasks.LOG.log(Level.WARNING, "File invalid: " + file, ex);
            fr = null;
        }
        this.fileRef = fr;
    }

    @Override
    public String toString() {
        return (fileRef == null ? "<no-file>" : fileRef.path) + "-doc-"
                + (docRef == null ? 0 : docRef.idHash) + " alive? " + !isDead();
    }

    boolean isDead() {
        return _document() == null && _file() == null;
    }

    Source toSource() {
        Document doc = document();
        if (doc != null) {
            return Source.create(doc);
        }
        FileObject file = file();
        if (file != null && file.isValid()) {
            return Source.create(file);
        }
        return null;
    }

    public Document document() {
        Document result = _document();
        if (result == null && fileRef != null) {
            FileObject file = fileRef.get();
            if (file != null) {
                result = documentForFileObject(file);
            }
        }
        return result;
    }

    public FileObject file() {
        FileObject result = _file();
        if (result == null) {
            Document doc = _document();
            if (doc != null) {
                return fileObjectForDocument(doc);
            }
        }
        return result;
    }

    Document _document() {
        return docRef == null ? null : docRef.get();
    }

    FileObject _file() {
        return fileRef == null ? null : fileRef.get();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof DocOrFileKey) {
            DocOrFileKey other = (DocOrFileKey) o;
            if (docRef != null && other.docRef != null) {
                boolean result = docRef.equals(other.docRef);
                if (result) {
                    return result;
                }
            }
            if (fileRef != null && other.fileRef != null) {
                boolean result = fileRef.equals(other.fileRef);
                if (result) {
                    return result;
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (docRef == null ? 0 : docRef.hashCode())
                + (fileRef == null ? 0 : (51 * fileRef.hashCode()));
    }
}

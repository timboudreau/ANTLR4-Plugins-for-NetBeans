package org.nemesis.antlr.fold;

import java.lang.ref.WeakReference;
import java.util.Objects;
import javax.swing.text.Document;

/**
 * A WeakReference to a document which can do identity comparisons based on the
 * document's identity hash code.
 *
 * @author Tim Boudreau
 */
final class DocumentReference extends WeakReference<Document> {

    int idHash;

    public DocumentReference(Document referent) {
        super(referent);
        idHash = System.identityHashCode(referent);
    }

    boolean isAlive() {
        return super.get() != null;
    }

    @Override
    public int hashCode() {
        return idHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj instanceof DocumentReference) {
            DocumentReference d = (DocumentReference) obj;
            return idHash == d.idHash && Objects.equals(get(), d.get());
        }
        return false;
    }

}

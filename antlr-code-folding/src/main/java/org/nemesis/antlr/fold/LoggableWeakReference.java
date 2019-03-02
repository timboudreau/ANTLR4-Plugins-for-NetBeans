package org.nemesis.antlr.fold;

import java.lang.ref.WeakReference;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
final class LoggableWeakReference<T> extends WeakReference<T> {

    private final String path;
    private final int idHash;

    public LoggableWeakReference(T referent, FileObject fo) {
        super(referent);
        path = fo.getPath();
        idHash = System.identityHashCode(referent);
    }

    @Override
    public String toString() {
        boolean live = get() != null;
        return "Ref{" + idHash + ", " + path + " live? " + live + "}";
    }

}

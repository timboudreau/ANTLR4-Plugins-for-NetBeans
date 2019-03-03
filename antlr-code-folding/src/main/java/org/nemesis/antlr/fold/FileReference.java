package org.nemesis.antlr.fold;

import java.lang.ref.WeakReference;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;

/**
 * A WeakReference to a file which can do equality comparisons based on the file
 * path.
 *
 * @author Tim Boudreau
 */
final class FileReference extends WeakReference<FileObject> {

    final String path;
    private final FileSystem fs;

    FileReference(FileObject fo) throws FileStateInvalidException {
        super(fo);
        path = fo.getPath();
        fs = fo.getFileSystem();
    }

    boolean isAlive() {
        return super.get() != null;
    }

    @Override
    public FileObject get() {
        FileObject result = super.get();
        if (result == null) {
            if (fs.isValid()) {
                result = fs.getRoot().getFileObject(path);
            }
        }
        if (result != null && !result.isValid()) {
            result = null;
        }
        return result;
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj instanceof FileReference) {
            FileReference f = (FileReference) obj;
            return f.path.equals(path);
        }
        return false;
    }

}

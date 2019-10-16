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

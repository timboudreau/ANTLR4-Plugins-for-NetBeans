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
package org.nemesis.antlr.refactoring.common;

import java.util.function.Supplier;
import org.openide.filesystems.FileObject;

/**
 * Provides a way of hiding the FileObject from the refactoring source context -
 * we need it so we can get the extraction for the file, but if it is present in
 * the source lookup, all rename refactorings of elements will be hijacked by
 * FileRenamePlugin implemented in NetBeans' base refactoring API - so we will
 * wind up with a rename of <i>any contents of a file</i> also causing that
 * <i>file</i> to be renamed to the same name, which is a mess.
 *
 * @author Tim Boudreau
 */
public class FileObjectHolder implements Supplier<FileObject> {

    private final FileObject file;

    FileObjectHolder(FileObject file) {
        if (file == null) {
            throw new IllegalArgumentException("File is null");
        }
        this.file = file;
    }

    public static FileObjectHolder of(FileObject file) {
        return new FileObjectHolder(file);
    }

    @Override
    public FileObject get() {
        return file;
    }

    @Override
    public boolean equals(Object o) {
        return o == null ? false : o == this ? true
                : o instanceof FileObjectHolder
                        ? ((FileObjectHolder) o).file.equals(file)
                        : false;
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public String toString() {
        return "FileObjectHolder(" + file + ")";
    }
}

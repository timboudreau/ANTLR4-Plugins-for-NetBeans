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
package org.nemesis.antlr.project;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Wrappers iterables of paths as FileObjects, handling the case that the file
 * objects don't exist so as not to return nulls.
 *
 * @author Tim Boudreau
 */
final class FileObjectIterable implements Iterable<FileObject> {

    private final Iterable<Path> delegate;

    private FileObjectIterable(Iterable<Path> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Iterator<FileObject> iterator() {
        return new FoIterator(delegate.iterator());
    }

    public static Iterable<FileObject> create(Iterable<Path> paths) {
        if (FoldersHelperTrampoline.getDefault().isEmptyIterable(paths)) {
            return FoldersHelperTrampoline.getDefault().newEmptyIterable();
        }
        if (FoldersHelperTrampoline.getDefault().isSingleIterable(paths)) {
            FileObject fo = FileUtil.toFileObject(FileUtil
                    .normalizeFile(paths
                            .iterator()
                            .next()
                            .toFile()));
            if (fo == null) { // virtual file
                return FoldersHelperTrampoline.getDefault().newEmptyIterable();
            }
            return FoldersHelperTrampoline.getDefault().newSingleIterable(fo);
        }
        return new FileObjectIterable(paths);
    }

    static final class FoIterator implements Iterator<FileObject> {

        private final Iterator<Path> delegate;
        private FileObject next;

        public FoIterator(Iterator<Path> delegate) {
            this.delegate = delegate;
            findNext();
        }

        private FileObject findNext() {
            if (!delegate.hasNext()) {
                return next = null;
            }
            FileObject fo = null;
            while (fo == null && delegate.hasNext()) {
                fo = FileUtil.toFileObject(FileUtil.normalizeFile(delegate.next().toFile()));
            }
            return next = fo;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public FileObject next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            FileObject oldNext = next;
            next = null;
            findNext();
            return oldNext;
        }
    }
}

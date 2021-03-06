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
package org.nemesis.antlr.live;

import com.mastfrog.function.state.Obj;
import java.io.IOException;
import org.nemesis.antlr.file.file.AntlrDataObject;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.ViewCookie;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiFileLoader;
import org.openide.text.DataEditorSupport;

/**
 * Subclasses AntlrDataObject to add programmatically generated editor support
 * so we have to initialize less infrastructure.
 *
 * @author Tim Boudreau
 */
public class FakeG4DataObject extends AntlrDataObject {

    public FakeG4DataObject(FileObject fo, MultiFileLoader loader) throws DataObjectExistsException {
        super(fo, loader);
        getCookieSet().add(new EC(this));
    }

    static class EC extends DataEditorSupport implements EditorCookie, EditorCookie.Observable, ViewCookie {

        public EC(DataObject obj) {
            super(obj, new EV(obj));
        }
    }

    static class EV extends DataEditorSupport.Env {

        public EV(DataObject obj) {
            super(obj);
        }

        @Override
        protected FileObject getFile() {
            return getDataObject().getPrimaryFile();
        }

        @Override
        protected FileLock takeLock() throws IOException {
            return getDataObject().getPrimaryFile().lock();
        }
    }

    E e() {
        return new E(getPrimaryFile());
    }

    class E extends Entry {

        public E(FileObject file) {
            super(file);
        }

        @Override
        public FileObject copy(FileObject toFolder, String suffix) throws IOException {
            FileObject file = getFile();
            Obj<FileObject> result = Obj.create();
            try (FileLock lock = file.lock()) {
                file.getFileSystem().runAtomicAction(() -> {
                    result.set(file.copy(toFolder, file.getName(), suffix == null ? file.getExt() : suffix));
                });
            }
            return result.get();
        }

        @Override
        public FileObject rename(String name) throws IOException {
            FileObject file = getFile();
            file.getFileSystem().runAtomicAction(() -> {
                try (FileLock lock = file.lock()) {
                    file.rename(lock, name, getPrimaryFile().getExt());
                }
            });
            return file;
        }

        @Override
        public FileObject move(FileObject targetFolder, String suffix) throws IOException {
            FileObject file = getFile();
            Obj<FileObject> result = Obj.create();
            try (FileLock lock = file.lock()) {
                file.getFileSystem().runAtomicAction(() -> {
                    result.set(file.move(lock, targetFolder, file.getName(), suffix == null ? file.getExt() : suffix));
                });
            }
            return result.get();
        }

        @Override
        public void delete() throws IOException {
            super.getFile().delete();
        }

        @Override
        public FileObject createFromTemplate(FileObject f, String name) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }

    public static class FakeG4MimeResolve extends MIMEResolver {

        @SuppressWarnings("deprecation")
        public FakeG4MimeResolve() {
            super();
        }

        @Override
        public String findMIMEType(FileObject fo) {
            if ("g4".equals(fo.getExt())) {
                return "text/x-g4";
            }
            return null;
        }
    }
}

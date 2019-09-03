package org.nemesis.antlr.live.execution;

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
 * Subclasses AntlrDataObject to add programmatically generated
 * editor support so we have to initialize less infrastructure.
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
        public FileObject copy(FileObject f, String suffix) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FileObject rename(String name) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FileObject move(FileObject f, String suffix) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
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

        @Override
        public String findMIMEType(FileObject fo) {
            if ("g4".equals(fo.getExt())) {
                return "text/x-g4";
            }
            return null;
        }
    }

}

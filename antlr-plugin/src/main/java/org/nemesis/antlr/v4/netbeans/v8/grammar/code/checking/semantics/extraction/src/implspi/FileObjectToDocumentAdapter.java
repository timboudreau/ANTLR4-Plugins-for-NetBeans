/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.implspi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.RelativeResolverAdapter;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.RelativeResolverImplementation;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RelativeResolverAdapter.class)
public class FileObjectToDocumentAdapter extends RelativeResolverAdapter<FileObject, Document> {

    public FileObjectToDocumentAdapter() {
        super(FileObject.class, Document.class);
    }

    @Override
    protected RelativeResolverImplementation<Document> adaptImpl(RelativeResolverImplementation<FileObject> from) {
        return super.adaptSimple(from, FileObjectToDocumentAdapter::toDocument, FileObjectToDocumentAdapter::toFileObject);
    }

    static FileObject toFileObject(Document doc) {
        return NbEditorUtilities.getFileObject(doc);
    }

    static Document toDocument(FileObject fo) {
        try {
            DataObject dob = DataObject.find(fo);
            EditorCookie ck = dob.getCookie(EditorCookie.class);
            if (ck != null) {
                Document doc = ck.getDocument();
                if (doc == null) {
                    doc = ck.openDocument();
                }
                if (doc != null) {
                    return doc;
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

    static Path fileObjectToPath(FileObject fo) {
        File f = FileUtil.toFile(fo);
        if (f != null) {
            return f.toPath();
        }
        return null;
    }

    static FileObject pathToFileObject(Path path) {
        return FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
    }

    @ServiceProvider(service = RelativeResolverAdapter.class)
    public static final class PathToFileObjectAdapter extends RelativeResolverAdapter<Path, FileObject> {

        public PathToFileObjectAdapter() {
            super(Path.class, FileObject.class);
        }

        @Override
        protected RelativeResolverImplementation<FileObject> adaptImpl(RelativeResolverImplementation<Path> from) {
            return adaptSimple(from, FileObjectToDocumentAdapter::pathToFileObject, FileObjectToDocumentAdapter::fileObjectToPath);
        }
    }

    @ServiceProvider(service = RelativeResolverAdapter.class)
    public static final class FileObjectToPathAdapter extends RelativeResolverAdapter<FileObject, Path> {

        public FileObjectToPathAdapter() {
            super(FileObject.class, Path.class);
        }

        @Override
        protected RelativeResolverImplementation<Path> adaptImpl(RelativeResolverImplementation<FileObject> from) {
            return adaptSimple(from, FileObjectToDocumentAdapter::fileObjectToPath, FileObjectToDocumentAdapter::pathToFileObject);
        }
    }

    @ServiceProvider(service = RelativeResolverAdapter.class)
    public static final class DocumentToFileObjectAdapter extends RelativeResolverAdapter<Document, FileObject> {

        public DocumentToFileObjectAdapter() {
            super(Document.class, FileObject.class);
        }

        @Override
        protected RelativeResolverImplementation<FileObject> adaptImpl(RelativeResolverImplementation<Document> from) {
            return super.adaptSimple(from, FileObjectToDocumentAdapter::toFileObject, FileObjectToDocumentAdapter::toDocument);
        }
    }
}

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
package org.nemesis.antlr.nbinput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.source.spi.DocumentAdapter;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Registers conversion functions for all of the myriad things that can
 * represent a source file in NetBeans - Snapshot, Source, DataObject,
 * FileObject, File, Path.  The infrastructure will find the shortest
 * conversion path between types.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = DocumentAdapter.class, position = 0)
public final class FileObjectToDocumentAdapter extends DocumentAdapter<FileObject, Document> {

    public FileObjectToDocumentAdapter() {
        super(FileObject.class, Document.class, FileObjectToDocumentAdapter::toDocument, FileObjectToDocumentAdapter::toFileObject);
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
            Logger.getLogger(FileObjectToDocumentAdapter.class.getName())
                    .log(Level.SEVERE, null, ex);
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

    static FileObject snapshotToFileObject(Snapshot snapshot) {
        return snapshot.getSource().getFileObject();
    }

    static Document snapshotToDocument(Snapshot snapshot) {
        return snapshot.getSource().getDocument(true);
    }

    static Snapshot fileObjectToSnapshot(FileObject fo) {
        return Source.create(fo).createSnapshot();
    }

    static Snapshot documentToSnapshot(Document doc) {
        return Source.create(doc).createSnapshot();
    }

    static Source documentToSource(Document doc) {
        return Source.create(doc);
    }

    static Document sourceToDocument(Source src) {
        return src.getDocument(true);
    }

    static FileObject dataObjectToFileObject(DataObject ob) {
        return ob.getPrimaryFile();
    }

    static DataObject fileObjectToDataObject(FileObject fo) {
        try {
            return DataObject.find(fo);
        } catch (DataObjectNotFoundException ex) {
            Logger.getLogger(FileObjectToDocumentAdapter.class.getName())
                    .log(Level.SEVERE, "Converting " + fo, ex);
            return null;
        }
    }

    @ServiceProvider(service = DocumentAdapter.class, position = 300)
    public static final class PathToFileAdapter
            extends DocumentAdapter<Path, File> {

        public PathToFileAdapter() {
            super(Path.class, File.class,
                    Path::toFile, File::toPath);
        }
    }

    @ServiceProvider(service = DocumentAdapter.class, position = 200)
    public static final class PathToFileObjectAdapter
            extends DocumentAdapter<Path, FileObject> {

        public PathToFileObjectAdapter() {
            super(Path.class, FileObject.class,
                    FileObjectToDocumentAdapter::pathToFileObject,
                    FileObjectToDocumentAdapter::fileObjectToPath);
        }
    }

    @ServiceProvider(service = DocumentAdapter.class, position = 100)
    public static final class DocumentToFileObjectAdapter
            extends DocumentAdapter<Document, FileObject> {

        public DocumentToFileObjectAdapter() {
            super(Document.class, FileObject.class,
                    FileObjectToDocumentAdapter::toFileObject,
                    FileObjectToDocumentAdapter::toDocument);
        }
    }

    @ServiceProvider(service = DocumentAdapter.class, position = 700)
    public static final class SnapshotToFileObjectAdapter
            extends DocumentAdapter<Snapshot, FileObject> {

        public SnapshotToFileObjectAdapter() {
            super(Snapshot.class, FileObject.class,
                    FileObjectToDocumentAdapter::snapshotToFileObject,
                    FileObjectToDocumentAdapter::fileObjectToSnapshot);
        }
    }

    @ServiceProvider(service = DocumentAdapter.class, position = 600)
    public static final class SnapshotToDocumentAdapter
            extends DocumentAdapter<Snapshot, Document> {

        public SnapshotToDocumentAdapter() {
            super(Snapshot.class, Document.class,
                    FileObjectToDocumentAdapter::snapshotToDocument,
                    FileObjectToDocumentAdapter::documentToSnapshot);
        }
    }

    @ServiceProvider(service = DocumentAdapter.class, position = 500)
    public static final class SourceToDocumentAdapter
            extends DocumentAdapter<Source, Document> {

        public SourceToDocumentAdapter() {
            super(Source.class, Document.class,
                    FileObjectToDocumentAdapter::sourceToDocument,
                    FileObjectToDocumentAdapter::documentToSource);
        }
    }

    @ServiceProvider(service = DocumentAdapter.class, position = 400)
    public static final class DataObjectToFileObjectAdapter
            extends DocumentAdapter<DataObject, FileObject> {

        public DataObjectToFileObjectAdapter() {
            super(DataObject.class, FileObject.class,
                    FileObjectToDocumentAdapter::dataObjectToFileObject,
                    FileObjectToDocumentAdapter::fileObjectToDataObject);
        }
    }
}

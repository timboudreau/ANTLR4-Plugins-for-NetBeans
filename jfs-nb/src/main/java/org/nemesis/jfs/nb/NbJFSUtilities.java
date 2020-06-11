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
package org.nemesis.jfs.nb;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.spi.JFSUtilities;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.editor.BaseDocument;
import org.netbeans.lib.editor.util.swing.DocumentListenerPriority;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.WeakListeners;
import org.openide.util.WeakSet;
import org.openide.util.lookup.ServiceProvider;

/**
 * Implementation of JFS Utilities for NetBeans.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = JFSUtilities.class)
public class NbJFSUtilities extends JFSUtilities {

    @Override
    protected Charset getEncodingFor(Path file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        return fo == null ? null : FileEncodingQuery.getEncoding(fo);
    }

    @Override
    protected long getLastModifiedFor(Document document) {
        return PriorityTimestamp.getTimestamp(document);
    }

    @Override
    protected void weakListen(Document document, DocumentListener listener) {
        if (document instanceof BaseDocument) {
            DocumentUtilities.addPriorityDocumentListener(document,
                    WeakListeners.document(listener, document), DocumentListenerPriority.FIRST);
        } else {
            document.addDocumentListener(WeakListeners.document(listener, document));
        }
    }

    @Override
    protected <T> Set<T> createWeakSet() {
        return new WeakSet<>();
    }

    @Override
    protected <T> T convert(JFSFileObject file, Class<T> type, Object obj) {
        // Takes care of no conversion and path-to-file
        T result = super.convert(file, type, obj);
        if (result != null) {
            return result;
        }
        if (obj instanceof Path) {
            Path path = (Path) obj;
            if (FileObject.class == type) {
                return type.cast(FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile())));
            } else if (Document.class == type || StyledDocument.class == type) {
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
                try {
                    DataObject dob = DataObject.find(fo);
                    EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                    if (ck != null) {
                        return type.cast(ck.getDocument());
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        } else if (obj instanceof Document) {
            Document doc = (Document) obj;
            if (FileObject.class == type) {
                return type.cast(NbEditorUtilities.getFileObject(doc));
            } else if (Path.class == type) {
                FileObject fo = NbEditorUtilities.getFileObject(doc);
                if (fo != null) {
                    File jioFile = FileUtil.toFile(fo);
                    if (jioFile != null) {
                        return type.cast(jioFile.toPath());
                    }
                }
            } else if (File.class == type) {
                FileObject fo = NbEditorUtilities.getFileObject(doc);
                if (fo != null) {
                    return type.cast(FileUtil.toFile(fo));
                }
            }
        }
        return null;
    }
}

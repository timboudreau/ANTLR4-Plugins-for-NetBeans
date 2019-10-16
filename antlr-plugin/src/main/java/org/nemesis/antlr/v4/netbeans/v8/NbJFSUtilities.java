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
package org.nemesis.antlr.v4.netbeans.v8;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import org.nemesis.jfs.spi.JFSUtilities;
import org.netbeans.api.queries.FileEncodingQuery;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.openide.util.WeakListeners;
import org.openide.util.WeakSet;
import org.openide.util.lookup.ServiceProvider;
/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service=JFSUtilities.class)
public class NbJFSUtilities extends JFSUtilities {

    @Override
    protected Charset getEncodingFor(Path file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        return fo == null ? null : FileEncodingQuery.getEncoding(fo);
    }

    @Override
    protected long getLastModifiedFor(Document document) {
        return DocumentUtilities.getDocumentTimestamp(document);
    }

    @Override
    protected void weakListen(Document document, DocumentListener listener) {
        document.addDocumentListener(WeakListeners.document(listener, document));
    }

    @Override
    protected <T> Set<T> createWeakSet() {
        return new WeakSet<>();
    }
}

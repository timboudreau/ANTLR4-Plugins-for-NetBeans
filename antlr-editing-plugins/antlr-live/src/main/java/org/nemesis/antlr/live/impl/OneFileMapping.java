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
package org.nemesis.antlr.live.impl;

import com.mastfrog.function.QuadConsumer;
import com.mastfrog.function.TriConsumer;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.antlr.project.Folders;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileObject;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Maintains the mapping of one grammar file on disk, or its associated editor
 * document into the JFS for the project it belongs to.
 */
final class OneFileMapping {

    private static final Logger LOG = Logger.getLogger(OneFileMapping.class.getName());
    private static final String PLACEHOLDER_JFS_ID = "-";
    private final FileObject file;
    final JFSCoordinates path;
    final EditorCookieListener cookieListener;
    private final Lookup.Result<EditorCookie.Observable> lookupResult;
    private final TriConsumer<FileObject, FileObject, OneFileMapping> onPrimaryFileChange;
    private final QuadConsumer<FileObject, String, String, OneFileMapping> onRename;
    private final Supplier<JFS> jfsSupplier;
    private JFSMappingMode mapping = JFSMappingMode.UNMAPPED;
    private String jfsId = PLACEHOLDER_JFS_ID;
    private WeakReference<Document> mappedDocument;
    final Folders owner;

    OneFileMapping(FileObject file, JFSCoordinates path, TriConsumer<FileObject, FileObject, OneFileMapping> onPrimaryFileChange, QuadConsumer<FileObject, String, String, OneFileMapping> onRename, Supplier<JFS> jfsSupplier, Folders owner) throws IOException {
        LOG.log(Level.FINEST, "New JFS mapping {0} -> {1}", new Object[]{file.getPath(), path});
        this.owner = owner;
        this.file = file;
        this.path = path;
        this.onPrimaryFileChange = onPrimaryFileChange;
        this.onRename = onRename;
        this.jfsSupplier = jfsSupplier;
        cookieListener = new EditorCookieListener(this::fileReplaced, this::documentReplaced, this::fileRenamed);
        lookupResult = cookieListener.attachTo(file);
        recheckMapping();
    }

    void addTo(GrammarMappingsImpl mappings) {
        mappings.add(file, path.path());
    }

    void discarded() {
        cookieListener.kill();
        setMappingMode(JFSMappingMode.UNMAPPED, null);
        mappedDocument = null;
    }

    @Override
    public String toString() {
        return file.getPath() + " -> " + path;
    }

    Document currentlyMappedDocument() {
        return mappedDocument == null ? null : mappedDocument.get();
    }

    void recheckMapping() {
        JFSMappingMode mode = mappingMode();
        JFS jfs = jfsSupplier.get();
        if (jfs == null && mode != JFSMappingMode.UNMAPPED) {
            setMappingMode(JFSMappingMode.UNMAPPED, null);
        } else if (jfs != null) {
            Document doc = document();
            if (doc == null) {
                if (file.isValid()) {
                    setMappingMode(JFSMappingMode.FILE, null);
                } else {
                    setMappingMode(JFSMappingMode.UNMAPPED, null);
                }
            } else {
                setMappingMode(JFSMappingMode.DOCUMENT, doc);
            }
        }
    }

    void documentReplaced(Document old, Document nue) {
        if (nue == null) {
            if (file.isValid()) {
                setMappingMode(JFSMappingMode.FILE, null);
            } else {
                setMappingMode(JFSMappingMode.UNMAPPED, null);
            }
        } else {
            setMappingMode(JFSMappingMode.DOCUMENT, nue);
        }
    }

    void fileRenamed(FileObject file, String oldName, String newName) {
        onRename.accept(file, oldName, newName, this);
    }

    final void fileReplaced(FileObject old, FileObject nue) {
        if (nue == null) {
            setMappingMode(JFSMappingMode.UNMAPPED, null);
        }
        onPrimaryFileChange.apply(old, nue, this);
    }

    JFSFileObject file() {
        JFS jfs = jfsSupplier.get();
        return jfs == null ? null : path.resolve(jfs);
    }

    long lastModified() {
        return file().getLastModified();
    }

    Document document() {
        Document result = cookieListener.document();
        if (result == null) {
            Iterator<? extends EditorCookie.Observable> all = lookupResult.allInstances().iterator();
            EditorCookie.Observable ck = all.hasNext() ? all.next() : null;
            if (ck != null) {
                cookieListener.ensureListening(ck);
                result = ck.getDocument();
            }
        }
        return result;
    }

    synchronized JFSMappingMode mappingMode() {
        return mapping;
    }

    synchronized void setMappingMode(JFSMappingMode newMapping, Document mayBeNull) {
        LOG.log(Level.FINEST, "Attempt mapping mode change {0} -> {1} on {2}", new Object[]{this.mapping, newMapping, this});
        JFS jfs = jfsSupplier.get();
        String fsId;
        if (jfs == null) {
            fsId = PLACEHOLDER_JFS_ID;
            newMapping = JFSMappingMode.UNMAPPED;
        } else {
            fsId = jfs.id();
        }
        boolean fsChange = !jfsId.equals(fsId);
        Document currentDocument = currentlyMappedDocument();
        Document newDocument = mayBeNull != null ? mayBeNull : document();
        if (newDocument == null && mapping == JFSMappingMode.DOCUMENT) {
            mapping = JFSMappingMode.FILE;
        }
        if (!file.isValid() && mapping == JFSMappingMode.FILE) {
            mapping = JFSMappingMode.UNMAPPED;
        }
        boolean docChange = newMapping == JFSMappingMode.DOCUMENT ? !Objects.equals(currentDocument, newDocument) : false;
        boolean modeChange = newMapping != this.mapping;
        if (modeChange || fsChange || docChange || mapping == JFSMappingMode.UNMAPPED) {
            LOG.log(Level.FINER, "Mapping mode change from {0} to {1} on " + "{2} fs {3} fsChange {4} docChange {5} modeChange {6}", new Object[]{mapping, newMapping, this, fsId, fsChange, docChange, modeChange});
            this.mapping = newMapping;
            this.jfsId = fsId;
            switch (newMapping) {
                case UNMAPPED:
                    JFSFileObject mappedJfsFile = file();
                    mappedDocument = null;
                    if (mappedJfsFile != null) {
                        mappedJfsFile.delete();
                    }
                    break;
                case FILE:
                    File f = FileUtil.toFile(file);
                    if (f == null) {
                        throw new IllegalStateException("Not a disk file: " + file);
                    }
                    jfs.masquerade(f.toPath(), path.location(), path.path());
                    mappedDocument = null;
                    break;
                case DOCUMENT:
                    jfs.masquerade(newDocument, path.location(), path.path());
                    mappedDocument = new WeakReference<>(newDocument);
                    break;
            }
        }
    }

}

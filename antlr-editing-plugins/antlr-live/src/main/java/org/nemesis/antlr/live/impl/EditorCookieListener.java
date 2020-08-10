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

import com.mastfrog.function.TriConsumer;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.WeakListeners;

/**
 * Keeps track of the opened state of a file/document and notifies when the
 * document is opened/closed so a OneFileMapping can switch itself between
 * mapping the live document into the JFS and mapping the file into the JFS,
 * and notifies on deletions and renames so the mappings can be updated
 * immediately, and the JFS mapped files state remains up-to-date with the
 * physical filesystem state.
 */
class EditorCookieListener extends FileChangeAdapter implements PropertyChangeListener, LookupListener {

    private static final Logger LOG = Logger.getLogger(EditorCookieListener.class.getName());
    private final Object docLock = new Object();
    WeakReference<EditorCookie.Observable> obs;
    WeakReference<Document> documentRef;
    private final BiConsumer<FileObject, FileObject> onFileReplaced;
    private final BiConsumer<Document, Document> onDocumentReplaced;
    private final TriConsumer<FileObject, String, String> onFileRenamed;
    boolean defunct = false;
    boolean listeningToEditorCookie;
    FileObject file;

    EditorCookieListener(BiConsumer<FileObject, FileObject> onFileReplaced, BiConsumer<Document, Document> onDocumentReplaced, TriConsumer<FileObject, String, String> onFileRenamed) {
        this.onFileReplaced = onFileReplaced;
        this.onDocumentReplaced = onDocumentReplaced;
        this.onFileRenamed = onFileRenamed;
    }

    Lookup.Result<EditorCookie.Observable> attachTo(FileObject file) throws DataObjectNotFoundException {
        this.file = file;
        DataObject dob = DataObject.find(file);
        Lookup.Result<EditorCookie.Observable> lookupResult = dob.getLookup().lookupResult(EditorCookie.Observable.class);
        lookupResult.addLookupListener(this);
        lookupResult.allInstances();
        file.addFileChangeListener(FileUtil.weakFileChangeListener(this, file));
        dob.addPropertyChangeListener(WeakListeners.propertyChange(this, dob));
        resultChanged(new LookupEvent(lookupResult));
        return lookupResult;
    }

    Document document() {
        Document result = documentRef == null ? null : documentRef.get();
        if (result == null) {
            EditorCookie.Observable ck = obs == null ? null : obs.get();
            if (ck != null) {
                result = ck.getDocument();
            }
        }
        return result;
    }

    void ensureListening(EditorCookie.Observable ck) {
        if (!listeningToEditorCookie || !Objects.equals(ck, obs == null ? null : obs.get())) {
            setEditorCookie(ck);
        }
    }

    void kill() {
        defunct = true;
        documentRef = null;
        listeningToEditorCookie = false;
        obs = null;
        file = null;
    }

    private void onFileReplaced(FileObject old, FileObject nue) {
        if (!defunct) {
            onFileReplaced.accept(old, nue);
        }
    }

    private void onDocumentReplaced(Document old, Document nue) {
        if (!defunct) {
            onDocumentReplaced.accept(old, nue);
        }
    }

    private void onFileRenamed(FileObject file, String oldNameExt, String newNameExt) {
        if (!defunct) {
            onFileRenamed.accept(file, oldNameExt, newNameExt);
        }
    }
    private volatile boolean ignoreNextDeletionEvent;

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (EditorCookie.Observable.PROP_OPENED_PANES.equals(evt.getPropertyName())) {
            JTextComponent[] comp = (JTextComponent[]) evt.getNewValue();
            Document doc = comp == null || comp.length == 0 ? null : comp[0].getDocument();
            setDocument(null, doc);
            LOG.log(Level.FINEST, "Opened panes change {0}", doc);
        } else if (EditorCookie.Observable.PROP_DOCUMENT.equals(evt.getPropertyName())) {
            Document oldDoc = (Document) evt.getOldValue();
            Document newDoc = (Document) evt.getNewValue();
            LOG.log(Level.FINEST, "Doc change {0} -> {1}", new Object[]{oldDoc, newDoc});
            setDocument(oldDoc, newDoc);
        } else if (DataObject.PROP_PRIMARY_FILE.equals(evt.getPropertyName())) {
            FileObject old = (FileObject) evt.getOldValue();
            FileObject nue = (FileObject) evt.getNewValue();
            if (!Objects.equals(old, nue)) {
                this.file = nue;
                if (old != null) {
                    old.removeFileChangeListener(this);
                }
                if (nue != null) {
                    nue.addFileChangeListener(FileUtil.weakFileChangeListener(this, nue));
                }
                LOG.log(Level.FINEST, "Primary file change {0} -> {1}", new Object[]{old, nue});
                onFileReplaced(old, nue);
            }
        } else if (DataObject.PROP_VALID.equals(evt.getPropertyName()) && evt.getSource() instanceof DataObject) {
            // Deletion was performed on the DataObject
            // If this.file is null, we are likely already dead
            if (this.file != null) {
                onFileReplaced(file, null);
            }
        } else if (DataObject.PROP_FILES.equals(evt.getPropertyName()) && evt.getOldValue() == null && evt.getNewValue() == null) {
            // A rename will generate TWO events, a deletion and then a replacement
            // of the primary file.  We want to ignore the deletion event, which this
            // pattern of property change signals is pending, so that this listener
            // survives long enough to tell its owner the location of the new file.
            if (this.file != null) {
                ignoreNextDeletionEvent = true;
            }
        }
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public void resultChanged(LookupEvent le) {
        Lookup.Result<EditorCookie.Observable> res = (Lookup.Result<EditorCookie.Observable>) le.getSource();
        Iterator<? extends EditorCookie.Observable> all = res.allInstances().iterator();
        EditorCookie.Observable ck = all.hasNext() ? all.next() : null;
        LOG.log(Level.FINEST, "EditorCookie change {0}", ck);
        setEditorCookie(ck);
    }

    private Document listenTo(EditorCookie.Observable nue) {
        obs = new WeakReference<>(nue);
        nue.addPropertyChangeListener(WeakListeners.propertyChange(this, nue));
        listeningToEditorCookie = true;
        return nue.getDocument();
    }

    private synchronized void setEditorCookie(EditorCookie.Observable nue) {
        EditorCookie.Observable old = obs == null ? null : obs.get();
        Document doc = null;
        if (!Objects.equals(old, nue)) {
            if (nue != null) {
                doc = listenTo(nue);
            } else {
                listeningToEditorCookie = false;
            }
        }
        setDocument(null, doc);
    }

    private void setDocument(Document oldDocOrNull, Document doc) {
        Document old;
        boolean change;
        synchronized (docLock) {
            old = oldDocOrNull == null ? documentRef == null ? null : documentRef.get() : oldDocOrNull;
            change = !Objects.equals(old, doc);
            if (change) {
                if (doc != null) {
                    documentRef = new WeakReference<>(doc);
                } else {
                    documentRef = null;
                }
            }
        }
        if (change) {
            onDocumentReplaced(old, doc);
        }
    }

    @Override
    public void fileRenamed(FileRenameEvent fe) {
        String oldName = fe.getName() + "." + fe.getExt();
        String newName = fe.getFile().getNameExt();
        onFileRenamed(fe.getFile(), oldName, newName);
    }

    @Override
    public void fileDeleted(FileEvent fe) {
        if (ignoreNextDeletionEvent) {
            ignoreNextDeletionEvent = false;
        } else {
            onFileReplaced(fe.getFile(), null);
        }
    }

}

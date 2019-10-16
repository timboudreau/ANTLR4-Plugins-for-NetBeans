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
package org.nemesis.antlr.navigator;

import java.util.Collection;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

/**
 *
 * @author Tim Boudreau
 */
final class FileListener extends FileChangeAdapter implements LookupListener {

    private FileObject listeningTo;
    private final Runnable updateForFileChanged;
    private final Runnable updateForFileDeletedOrPanelDeactivated;

    FileListener(Runnable updateForFileChanged, Runnable updateForFileDeletedOrPanelDeactivated) {
        this.updateForFileChanged = updateForFileChanged;
        this.updateForFileDeletedOrPanelDeactivated = updateForFileDeletedOrPanelDeactivated;
    }

    @SuppressWarnings(value = "unchecked")
    public void resultChanged(LookupEvent evt) {
        Lookup.Result<DataObject> result = (Lookup.Result<DataObject>) evt.getSource();
        updateFromResult(result);
    }

    void updateFromResult(Lookup.Result<DataObject> result) {
        Collection<? extends DataObject> dobs = result.allInstances();
        if (dobs == null || dobs.isEmpty()) {
            setFile(null);
        } else {
            setFile(dobs.iterator().next().getPrimaryFile());
        }
    }

    void stopListening() {
        FileObject currentFile;
        synchronized (this) {
            currentFile = this.listeningTo;
        }
        if (currentFile != null) {
            stopListening(currentFile);
            currentFile = null;
        }
    }

    private void setFile(FileObject file) {
        FileObject oldFile;
        synchronized (this) {
            oldFile = this.listeningTo;
        }
        if (oldFile != null && (oldFile == file || oldFile.equals(file))) {
            return;
        } else if (oldFile == null && file == null) {
            return;
        }
        if (oldFile != null) {
            stopListening(oldFile);
        }
        if (file != null) {
            startListening(file);
        }
    }

    private void stopListening(FileObject file) {
        file.removeFileChangeListener(this);
    }

    private void startListening(FileObject file) {
        file.addFileChangeListener(this);
    }

    @Override
    public void fileChanged(FileEvent fe) {
        updateForFileChanged.run();
    }

    @Override
    public void fileDeleted(FileEvent fe) {
        updateForFileDeletedOrPanelDeactivated.run();
    }

}

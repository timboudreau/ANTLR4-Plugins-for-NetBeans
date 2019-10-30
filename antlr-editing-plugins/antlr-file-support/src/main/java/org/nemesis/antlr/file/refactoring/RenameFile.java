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
package org.nemesis.antlr.file.refactoring;

import java.io.IOException;
import java.util.function.Supplier;
import static org.nemesis.antlr.file.refactoring.AbstractRefactoringContext.lookupOf;
import org.netbeans.modules.refactoring.spi.SimpleRefactoringElementImplementation;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Borrowed with modifications from Refactoring API.
 */
class RenameFile extends SimpleRefactoringElementImplementation {

    private final Supplier<String> newNameSupplier;
    private final FileObject fo;
    private String oldName;

    public RenameFile(Supplier<String> newNameSupplier, FileObject fo) {
        this.newNameSupplier = newNameSupplier;
        this.fo = fo;
    }

    @Override
    public String toString() {
        return "Rename " + fo.getPath() + " from " + oldName + " to "
                + newNameSupplier.get();
    }

    @Override
    @NbBundle.Messages(value = {
        "# {0} - original file name",
        "TXT_RenameFile=Rename file {0}",
        "# {0} - grammar file path",
        "TXT_RenameFolder=Rename folder {0}"})
    public String getText() {
        return fo.isFolder() ? Bundle.TXT_RenameFolder(fo.getNameExt()) : Bundle.TXT_RenameFile(fo.getNameExt());
    }

    @Override
    public String getDisplayText() {
        return getText();
    }

    @Override
    public void performChange() {
        try {
            oldName = fo.getName();
            DataObject.find(fo).rename(newNameSupplier.get());
        } catch (DataObjectNotFoundException ex) {
            throw new IllegalStateException(ex);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void undoChange() {
        try {
            if (!fo.isValid()) {
                throw new CannotUndoOneFileChangeException(fo);
            }
            DataObject.find(fo).rename(oldName);
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public Lookup getLookup() {
        return lookupOf(fo);
    }

    @Override
    public FileObject getParentFile() {
        return fo;
    }

    @Override
    public PositionBounds getPosition() {
        return null;
    }
}

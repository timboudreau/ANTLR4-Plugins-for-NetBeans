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
package org.nemesis.antlr.refactoring;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.lookupOf;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
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
class RenameFile extends SimpleRefactoringElementImplementation implements ComparableRefactoringElementImplementation {

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
            Thread.dumpStack();
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

    @Override
    public int compareTo(RefactoringElementImplementation o) {
        if (o instanceof RenameFile) {
            RenameFile other = (RenameFile) o;
            return fo.getPath().compareTo(other.fo.getPath());
        } else if (o instanceof ReplaceRanges || o instanceof ReplaceRanges.RangeChild) {
            return 1;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.fo);
        hash = 97 * hash + Objects.hashCode(this.oldName);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RenameFile other = (RenameFile) obj;
        if (!Objects.equals(this.oldName, other.oldName)) {
            return false;
        }
        return Objects.equals(this.fo, other.fo);
    }
}

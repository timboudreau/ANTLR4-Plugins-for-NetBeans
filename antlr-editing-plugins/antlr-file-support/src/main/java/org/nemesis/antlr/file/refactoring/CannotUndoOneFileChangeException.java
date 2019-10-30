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

import javax.swing.undo.CannotUndoException;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * Exception to thrown when trying to undo rename of a file which has since been
 * deleted.
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"# {0} - the file name", "cannot_undo=Cannot undo - file deleted: {0}"})
final class CannotUndoOneFileChangeException extends CannotUndoException {

    private final FileObject fo;

    public CannotUndoOneFileChangeException(FileObject fo) {
        this.fo = fo;
    }

    @Override
    public String getMessage() {
        return Bundle.cannot_undo(fo.getNameExt());
    }
}

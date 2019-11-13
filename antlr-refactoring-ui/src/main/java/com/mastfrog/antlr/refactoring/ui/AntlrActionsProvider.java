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
package com.mastfrog.antlr.refactoring.ui;

import org.nemesis.antlr.refactoring.common.FileObjectHolder;
import org.nemesis.antlr.refactoring.common.Refactorability;
import org.nemesis.antlr.spi.language.AntlrMimeTypeRegistration;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.api.WhereUsedQuery;
import org.netbeans.modules.refactoring.spi.ui.ActionsImplementationProvider;
import org.netbeans.modules.refactoring.spi.ui.RefactoringUI;
import org.netbeans.modules.refactoring.spi.ui.UI;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Tim Boudreau
 */
@org.openide.util.lookup.ServiceProvider(service = ActionsImplementationProvider.class, position = 101)
public class AntlrActionsProvider extends ActionsImplementationProvider {

    private boolean isRefactorable(Lookup lookup, Class<? extends AbstractRefactoring> ref) {
        FileObject file = file(lookup);
        return file != null && AntlrMimeTypeRegistration.isAntlrLanguage(file)
                && Refactorability.isRefactoringSupported(ref, file, lookup);
    }

    @Override
    public boolean canFindUsages(Lookup lookup) {
        return isRefactorable(lookup, WhereUsedQuery.class);
    }

    @Override
    public boolean canRename(Lookup lookup) {
        return isRefactorable(lookup, RenameRefactoring.class);
    }

    @Override
    public void doFindUsages(Lookup lookup) {
        if (canFindUsages(lookup)) {
            RefactoringUI ui = new FindUsagesUI(excludeFileObject(lookup));
            UI.openRefactoringUI(ui);
        }
    }

    @Override
    public void doRename(Lookup lookup) {
        if (canRename(lookup)) {
            RefactoringUI ui = new RenameUI(excludeFileObject(lookup));
            UI.openRefactoringUI(ui);
        }
    }

    private static Lookup excludeFileObject(Lookup lkp) {
        // We need to prevent the default rename refactoring built into NetBeans'
        // Refactoring API seeing the FileObject, or it will hijack refactorings
        // to refactor a file *element* and rename the file to the name used,
        // which wreaks all sorts of havoc.
        FileObject fo = lkp.lookup(FileObject.class);
        if (fo != null) {
            Lookup excluded = Lookups.exclude(lkp, FileObject.class);
            Lookup hidden = Lookups.singleton(FileObjectHolder.of(fo));
            return new ProxyLookup(hidden, excluded);
        }
        return lkp;
    }

    private FileObject file(Lookup lookup) {
        FileObjectHolder holder = lookup.lookup(FileObjectHolder.class);
        FileObject fo = holder == null ? null : holder.get();
        if (fo == null) {
            fo = lookup.lookup(FileObject.class);
            if (fo == null) {
                DataObject ob = lookup.lookup(DataObject.class);
                if (ob != null) {
                    fo = ob.getPrimaryFile();
                }
            }
        }
        return fo;
    }

}

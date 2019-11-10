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
package org.nemesis.antlr.refactoring.impl;

import javax.swing.Action;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.refactoring.common.BeforeRefactoringTask;
import org.nemesis.antlr.refactoring.common.RefactoringActionsBridge;
import org.netbeans.modules.refactoring.api.ui.RefactoringActionsFactory;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RefactoringActionsBridge.class, position = 100)
public class AntlrRefactoringActionsBridge extends RefactoringActionsBridge {

    @Override
    protected boolean initiateFullRename(FileObject file, Document doc, JTextComponent comp, int caretPosition) {
        try {
            DataObject dob = DataObject.find(file);
            doFullRename(dob, doc, comp, caretPosition);
            return true;
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }

    private static void doFullRename(DataObject obj, Document doc, JTextComponent comp, int caret) {
        Action a = RefactoringActionsFactory.renameAction()
                .createContextAwareInstance(new ProxyLookup(obj.getLookup(), Lookups.fixed(doc, comp)));
        a.actionPerformed(RefactoringActionsFactory.DEFAULT_EVENT);
    }

    @Override
    protected boolean registerBeforeAnyRefactoringTask(BeforeRefactoringTask task) {
        EnsureInstantRenamersAreRemovedPluginFactory.register(task);
        return true;
    }

    @Override
    protected boolean unregisterBeforeAnyRefactoringTask(BeforeRefactoringTask task) {
        return EnsureInstantRenamersAreRemovedPluginFactory.deregister(task);
    }
}

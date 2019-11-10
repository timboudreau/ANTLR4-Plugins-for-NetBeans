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
package org.nemesis.antlr.refactoring.common;

import static com.mastfrog.util.preconditions.Checks.notNull;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Bridge which, if a registered instance is present, can invoke the refactoring
 * actions, from the refactoring API. Exists so that inplace-rename can be
 * implemented without necessarily also having the refactoring API or an
 * implementation present.
 *
 * @author Tim Boudreau
 */
public abstract class RefactoringActionsBridge {

    /**
     * Initiate a rename refactoring operation over the passed file and document
     * at the given caret position.
     *
     * @param file The file
     * @param doc The document
     * @param comp The text component
     * @param caretPosition The caret position
     * @return True if an implementation is installed which <i>may have</i>
     * invoked the refactoring dialog.
     */
    protected abstract boolean initiateFullRename(FileObject file, Document doc, JTextComponent comp, int caretPosition);

    /**
     * Register a task which should be run before <i>any</i> refactoring is
     * invoked. This is used to ensure operations such as instant rename are not
     * left in-progress across a refactoring that could leave them in an
     * inconsistent state.
     *
     * @param task A task
     * @return True if the task may eventually be invoked
     */
    protected abstract boolean registerBeforeAnyRefactoringTask(BeforeRefactoringTask task);

    /**
     * Unregister a task which was previously registered to be run before
     * <i>any</i> refactoring is invoked. This is used to ensure operations such
     * as instant rename are not left in-progress across a refactoring that
     * could leave them in an inconsistent state.
     *
     * @param task A task
     * @return True if the task was removed from some registry - it could have
     * been invoked but now it will not be
     */
    protected abstract boolean unregisterBeforeAnyRefactoringTask(BeforeRefactoringTask task);

    /**
     * Initiate a rename refactoring operation over the passed file and document
     * at the given caret position.
     *
     * @param file The file
     * @param doc The document
     * @param comp The text component
     * @param caretPosition The caret position
     * @return True if an implementation is installed which <i>may have</i>
     * invoked the refactoring dialog.
     */
    public static boolean invokeFullRename(FileObject file, Document doc, JTextComponent comp, int caretPosition) {
        return getDefault().initiateFullRename(file, doc, comp, caretPosition);
    }

    /**
     * Register a task which should be run before <i>any</i> refactoring is
     * invoked. This is used to ensure operations such as instant rename are not
     * left in-progress across a refactoring that could leave them in an
     * inconsistent state.
     *
     * @param task A task
     * @return True if the task may eventually be invoked
     */
    public static boolean beforeAnyRefactoring(BeforeRefactoringTask task) {
        return getDefault().registerBeforeAnyRefactoringTask(notNull("task", task));
    }

    /**
     * Unregister a task which was previously registered to be run before
     * <i>any</i> refactoring is invoked. This is used to ensure operations such
     * as instant rename are not left in-progress across a refactoring that
     * could leave them in an inconsistent state.
     *
     * @param task A task
     * @return True if the task was removed from some registry - it could have
     * been invoked but now it will not be
     */
    public static boolean unregisterBeforeAnyRefactoring(BeforeRefactoringTask task) {
        return getDefault().unregisterBeforeAnyRefactoringTask(notNull("task", task));
    }

    private static RefactoringActionsBridge getDefault() {
        RefactoringActionsBridge result = Lookup.getDefault().lookup(RefactoringActionsBridge.class);
        if (result == null) {
            result = new DummyBridge();
        }
        return result;
    }

    private static final class DummyBridge extends RefactoringActionsBridge {

        @Override
        protected boolean initiateFullRename(FileObject file, Document doc, JTextComponent comp, int caretPosition) {
            return false;
        }

        @Override
        protected boolean registerBeforeAnyRefactoringTask(BeforeRefactoringTask task) {
            return false;
        }

        @Override
        protected boolean unregisterBeforeAnyRefactoringTask(BeforeRefactoringTask task) {
            // Always report true here - it is used to determine if the rename can
            // complete
            return true;
        }
    }
}

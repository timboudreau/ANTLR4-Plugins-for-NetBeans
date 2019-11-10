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

import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Determines if any refactorings of a passed type are registered for a given
 * file / mime type.  Implemented by the antlr refactorings plugin factory and used
 * by the antlr refactoring action provider.
 *
 * @author Tim Boudreau
 */
public interface Refactorability {

    /**
     * Determine if this instance knows of a refactoring registered against the
     * passed refactoring type (assume <code>type</code> is a subtype of
     * AbstractRefactoring).
     *
     * @param type Assumed to be a subtype of AbstractRefactoring, but not
     * specified here so antlr-inplace-rename can be installed even if no
     * refactoring support is.
     *
     * @param file The file
     * @param lookup The refactoring source lookup
     * @return true if a refactoring action can proceed based on the contents of
     * this instance
     */
    boolean canRefactor(Class<? /* extends AbstractRefactoring*/> type, FileObject file, Lookup lookup);

    /**
     * Determine if any installed instance knows of a refactoring registered
     * against the passed refactoring type (assume <code>type</code> is a
     * subtype of AbstractRefactoring).
     *
     * @param type Assumed to be a subtype of AbstractRefactoring, but not
     * specified here so antlr-inplace-rename can be installed even if no
     * refactoring support is.
     *
     * @param file The file
     * @param lookup The refactoring source lookup
     * @return true if a refactoring action can proceed based on any registered
     * instance of Refactorability
     */
    public static boolean isRefactoringSupported(Class<? /*extends AbstractRefactoring*/> type, FileObject file, Lookup lookup) {
        boolean result = false;
        for (Refactorability factory : Lookup.getDefault().lookupAll(Refactorability.class)) {
            if (factory.canRefactor(type, file, lookup)) {
                result = true;
                break;
            }
        }
        return result;
    }
}

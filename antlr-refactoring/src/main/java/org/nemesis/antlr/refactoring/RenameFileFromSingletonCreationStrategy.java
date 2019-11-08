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

import com.mastfrog.abstractions.Stringifier;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
final class RenameFileFromSingletonCreationStrategy<T> implements SingletonRefactoringCreationStrategy<RenameRefactoring, T> {

    @Override
    public RefactoringPlugin createRefactoringPlugin(SingletonKey<T> key, RenameRefactoring refactoring, Extraction extraction, FileObject file, SingletonEncounters.SingletonEncounter<T> item, Stringifier<? super T> optionalStringifier, CharFilter filter) {
        return new RenameFileAndReferencesFromSingletonPlugin<>(key, refactoring, extraction, file, item, optionalStringifier, filter);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}

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

import com.mastfrog.abstractions.Stringifier;
import com.mastfrog.range.IntRange;
import org.nemesis.antlr.file.refactoring.usages.SingletonUsagesFinder;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.WhereUsedQuery;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class SingletonFindUsagesPlugin<T> extends AbstractAntlrRefactoringPlugin<WhereUsedQuery> {

    private final SingletonKey<T> key;
    private final SingletonEncounter<T> singleton;
    private final Stringifier<T> stringifier;

    public SingletonFindUsagesPlugin(WhereUsedQuery refactoring, Extraction extraction, FileObject file, SingletonKey<T> key,
            SingletonEncounter<T> singleton, Stringifier<T> stringifier) {
        super(refactoring, extraction, file);
        this.key = key;
        this.singleton = singleton;
        this.stringifier = stringifier;
    }

    @Override
    protected Problem doPrepare(RefactoringElementsBag bag) {
        SingletonUsagesFinder<T> finder = new SingletonUsagesFinder<>(key, stringifier);
        finder.findUsages(this::isCancelled, file, singleton, (IntRange bounds, String itemName, FileObject inFile, String kindName, Extraction inExtraction) -> {
            bag.add(refactoring, new Usage(inFile, bounds, kindName, itemName));
        });
        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "("
                + key + " " + singleton + " " + stringifier
                + file.getNameExt() + " "
                + refactoring + " "
                + extraction.tokensHash() + " "
                + ")";
    }
}

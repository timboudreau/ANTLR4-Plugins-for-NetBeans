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

import static com.mastfrog.util.preconditions.Checks.notNull;
import org.nemesis.antlr.file.refactoring.usages.SimpleUsagesFinder;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.WhereUsedQuery;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class NamedFindUsagesPlugin<T extends Enum<T>> extends AbstractAntlrRefactoringPlugin<WhereUsedQuery> {

    private final NamedExtractionKey<T> key;
    private final NamedSemanticRegion<T> target;

    public NamedFindUsagesPlugin(WhereUsedQuery refactoring, Extraction extraction, FileObject file,
            NamedExtractionKey<T> key, NamedSemanticRegion<T> target) {
        super(refactoring, extraction, file);
        this.key = notNull("key", key);
        this.target = notNull("target", target);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + key + " " + target + ")";
    }

    @Override
    public Problem doPrepare(RefactoringElementsBag bag) {
        NamedRegionKey<T> regionKey = key instanceof NamedRegionKey<?> ? (NamedRegionKey<T>) key : null;
        NameReferenceSetKey<T> refsKey = key instanceof NameReferenceSetKey<?> ? (NameReferenceSetKey<T>) key : null;
        assert regionKey != null || refsKey != null;
        if (regionKey == null) {
            regionKey = refsKey.referencing();
        }
        new SimpleUsagesFinder<>(regionKey, refsKey).findUsages(this::isCancelled, file, extraction, target, extraction.namedRegions(regionKey),
                (range, name, targetFo, elName, ext) -> {
            bag.add(refactoring, new Usage(targetFo, range, elName, name));
        });
        return null;
        /*
        UsagesFinder finder = UsagesFinder.forMimeType(file.getMIMEType());
        int caretPosition = loc.getBegin().getOffset();
        finder.findUsages(this::isCancelled, file, caretPosition, extraction, (range, name, targetFo, elName, ext) -> {
            bag.add(refactoring, new Usage(targetFo, range, elName, name));
        });
        return null;
        */
    }

}

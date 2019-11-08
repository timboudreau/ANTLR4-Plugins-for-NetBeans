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

import com.mastfrog.function.TriFunction;
import com.mastfrog.range.IntRange;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.refactoring.usages.SimpleUsagesFinder;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
public class RenameNamedPlugin<T extends Enum<T>> extends AbstractAntlrRefactoringPlugin<RenameRefactoring> {

    private final NamedRegionKey<T> key;
    private final NameReferenceSetKey<T> refs;
    private final NamedSemanticRegion<T> toRename;
    private final CharFilter filter;

    public RenameNamedPlugin(RenameRefactoring refactoring,
            Extraction extraction, FileObject file,
            NamedRegionKey<T> key, NameReferenceSetKey<T> refs,
            NamedSemanticRegion<T> toRename, CharFilter filter) {
        super(refactoring, extraction, file);
        this.key = key;
        this.refs = refs;
        this.toRename = toRename;
        this.filter = filter == null ? CharFilter.ALL : filter;
        refactoring.getContext().add(key);
        refactoring.getContext().add(refs);
        refactoring.getContext().add(toRename);
        refactoring.getContext().add(this.filter);

    }

    static <T extends Enum<T>> TriFunction<RenameRefactoring, Extraction, PositionBounds, RefactoringPlugin> generator(NamedRegionKey<T> key, NameReferenceSetKey<T> refs, CharFilter filter) {
        return new RenamedGenerator<>(key, refs, filter);
    }

    static class RenamedGenerator<T extends Enum<T>> extends AbstractRefactoringContext
            implements TriFunction<RenameRefactoring, Extraction, PositionBounds, RefactoringPlugin> {

        private final NamedRegionKey<T> key;
        private final NameReferenceSetKey<T> refs;
        private final CharFilter filter;

        public RenamedGenerator(NamedRegionKey<T> key, NameReferenceSetKey<T> refs, CharFilter filter) {
            this.refs = notNull("refs", refs);
            this.key = key == null ? refs.referencing() : key;
            this.filter = filter == null ? CharFilter.ALL : filter;
        }

        @Override
        public RefactoringPlugin apply(RenameRefactoring t, Extraction extraction, PositionBounds v) {
            Optional<FileObject> fileOpt = extraction.source().lookup(FileObject.class);
            if (!fileOpt.isPresent()) {
                return null;
            }
            FindResult<T> found = findWithAttribution(fileOpt.get(), refs, extraction, v);
            return found == null
                    ? null
                    : new RenameNamedPlugin<>(t, found.extraction(), found.file(), key, refs, found.region(), filter);
        }
    }

    @Override
    @Messages("illegal_characters=Name contains illegal characters")
    public Problem checkParameters() {
        String name = refactoring.getNewName();
        if (!filter.test(name, false)) {
            System.out.println("Illegal characters in '" + name + "' with " + filter);
            return new Problem(true, Bundle.illegal_characters());
        }
        return super.checkParameters();
    }

    @Override
    protected Object[] getLookupContents() {
        return new Object[]{key, toRename, filter};
    }

    @Override
    protected Problem doPrepare(RefactoringElementsBag bag) {
        return inParsingContext(() -> {
            SimpleUsagesFinder<T> uf = new SimpleUsagesFinder<>(key, refs);
            Map<FileObject, List<IntRange<? extends IntRange>>> ranges = CollectionUtils.supplierMap(ArrayList::new);
            String[] regText = new String[1];
            Problem p = uf.findUsages(this::isCancelled, file, extraction, toRename, extraction.namedRegions(key),
                    (IntRange<? extends IntRange> target, String regionText, FileObject targetFile, ExtractionKey<?> key, Extraction sourceExtraction) -> {
                        ranges.get(targetFile).add(target);
                        if (regText[0] == null) {
                            regText[0] = regionText;
                        }
                        return null;
                    }
            );
            if (!ranges.isEmpty() && (p == null || !p.isFatal())) {
                for (Map.Entry<FileObject, List<IntRange<? extends IntRange>>> e : ranges.entrySet()) {
                    try {
                        ReplaceRanges.create(key, e.getKey(), e.getValue(), regText[0], refactoring.getNewName(), rr -> {
                            bag.add(refactoring, rr);
                        });
                    } catch (IOException | BadLocationException ex) {
                        log(ex);
                        return toProblem(ex);
                    }
                }
            }
            return p;
        });
    }

}

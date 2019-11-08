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
package org.nemesis.antlr.refactoring.usages;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.RangeRelation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.nemesis.antlr.refactoring.AbstractRefactoringContext;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.modules.refactoring.api.Problem;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public abstract class UsagesFinder extends AbstractRefactoringContext {

    /*
    So, we have a caret position and an extraction
     */
    public abstract Problem findUsages(BooleanSupplier cancelled,
            FileObject file, int caretPosition, Extraction extraction,
            PetaFunction<IntRange<? extends IntRange>, String, FileObject, ExtractionKey<?>, Extraction> usageConsumer);

    public static UsagesFinder forMimeType(String mimeType) {
        Lookup lkp = MimeLookup.getLookup(mimeType);
        Collection<? extends UsagesFinder> all = lkp.lookupAll(UsagesFinder.class);
        if (all.isEmpty()) {
            return new DummyUsagesFinder();
        } else if (all.size() == 1) {
            return all.iterator().next();
        } else {
            return new PolyUsagesFinder(all);
        }
    }

    private static final class PolyUsagesFinder extends UsagesFinder {

        private final Collection<? extends UsagesFinder> all;

        public PolyUsagesFinder(Collection<? extends UsagesFinder> all) {
            this.all = all;
        }

        @Override
        public Problem findUsages(BooleanSupplier cancelled, FileObject file,
                int caretPosition, Extraction extraction,
                PetaFunction<IntRange<? extends IntRange>, String, FileObject, ExtractionKey<?>, Extraction> usageConsumer) {
            Problem result = null;
            for (UsagesFinder finder : all) {
                if (cancelled.getAsBoolean()) {
                    return result;
                }
                Problem p = finder.findUsages(cancelled, file, caretPosition, extraction, usageConsumer);
                result = chainProblems(result, p);
                if (result.isFatal()) {
                    return result;
                }
            }
            return result;
        }
    }

    static class DummyUsagesFinder extends UsagesFinder {

        @Override
        public Problem findUsages(BooleanSupplier cancelled, FileObject file,
                int caretPosition, Extraction origExtraction,
                PetaFunction<IntRange<? extends IntRange>, String, FileObject, ExtractionKey<?>, Extraction> usageConsumer) {
            // do nothing
//            Set<NamedRegionKey<?>> keys = extraction.regionKeys();
            ImportersFinder impf = ImportersFinder.forFile(file);
            Set<FileObject> scanned = new HashSet<>();

            NamedRegionKey<?> targetKey = null;
            NamedSemanticRegion<?> targetRegion = null;
            for (NamedRegionKey<?> key : origExtraction.regionKeys()) {
                NamedSemanticRegions<?> regions = origExtraction.namedRegions(key);
                NamedSemanticRegion<?> region = regions.at(caretPosition);
                if (region != null) {
                    targetKey = key;
                    targetRegion = region;
                    break;
                }
            }
            int[] countFound = new int[1];
            if (targetKey != null) {
                NamedRegionKey<?> finalTargetKey = targetKey;
                NamedSemanticRegion<?> finalTargetRegion = targetRegion;
                return impf.usagesOf(cancelled, file, null, (a, b, c, d, ext) -> {
                    if (scanned.contains(c) || cancelled.getAsBoolean()) {
                        return null;
                    }
                    scanned.add(c);
                    for (NameReferenceSetKey<?> key : ext.referenceKeys()) {
                        if (key.referencing().equals(finalTargetKey)) {
                            Problem p = attributeOne(cancelled, finalTargetRegion, key,
                                    file, origExtraction, caretPosition, c, ext, usageConsumer);
                            if (p != null && p.isFatal()) {
                                return p;
                            }
                        }
                        if (cancelled.getAsBoolean()) {
                            break;
                        }
                    }
                    return null;
                });
            }
            return null;
        }

        <T extends Enum<T>> Problem attributeOne(BooleanSupplier cancelled, NamedSemanticRegion<?> reg, NameReferenceSetKey<T> key,
                FileObject origFile, Extraction origExtraction, int caretPosition,
                FileObject scanning, Extraction scanningExtraction,
                PetaFunction<IntRange<? extends IntRange>, String, FileObject, ExtractionKey<?>, Extraction> usageConsumer) {

            Problem result = null;
            Attributions<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T> attributions = scanningExtraction.resolveAll((NameReferenceSetKey<T>) key);
            for (SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> attr
                    : attributions.attributed()) {
                Extraction target = attr.key().foundIn();
                if (cancelled.getAsBoolean()) {
                    return result;
                }
                // Try to avoid potential Snapshot -> Source -> Document -> FileObject conversion
                // if we can do a simpler test
                if (isSameSource(origFile, origExtraction, target)) {
                    NamedSemanticRegion<T> found = attr.key().element();
                    if (reg == found || reg.equals(found) || reg.relationTo(found) == RangeRelation.EQUAL) {
                        Optional<FileObject> targetFile = attr.key().attributedTo().source().lookup(FileObject.class);
                        if (targetFile.isPresent()) {
                            Problem p = usageConsumer.accept(attr, attr.key().name(), targetFile.get(), attr.key().key(), scanningExtraction);
                            result = chainProblems(result, p);
                            if (p != null && p.isFatal()) {
                                break;
                            }
                        }
                    }
                }
            }
            return result;
        }
    }
}

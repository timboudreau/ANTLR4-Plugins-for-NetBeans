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
package org.nemesis.antlr.file.refactoring.usages;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.RangeRelation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.nemesis.antlr.file.refactoring.AbstractRefactoringContext;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.extraction.attribution.ImportKeySupplier;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.mimelookup.MimeLookup;
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
    public abstract void findUsages(BooleanSupplier cancelled, FileObject file, int caretPosition, Extraction extraction, PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer);

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
        public void findUsages(BooleanSupplier cancelled, FileObject file, int caretPosition, Extraction extraction, PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer) {
            for (UsagesFinder finder : all) {
                if (cancelled.getAsBoolean()) {
                    return;
                }
                finder.findUsages(cancelled, file, caretPosition, extraction, usageConsumer);
            }
        }
    }

    static class DummyUsagesFinder extends UsagesFinder {

        @Override
        public void findUsages(BooleanSupplier cancelled, FileObject file, int caretPosition, Extraction origExtraction, PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer) {
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
                impf.usagesOf(cancelled, file, null, (a, b, c, d, ext) -> {
                    if (scanned.contains(c) || cancelled.getAsBoolean()) {
                        return;
                    }
                    scanned.add(c);
                    for (NameReferenceSetKey<?> key : ext.referenceKeys()) {
                        if (key.referencing().equals(finalTargetKey)) {
                            countFound[0] += attributeOne(finalTargetRegion, key, file, origExtraction, caretPosition, c, ext, usageConsumer);
                        }
                        if (cancelled.getAsBoolean()) {
                            return;
                        }
                    }
                });
            } else {
                Set<SingletonKey<?>> singletonKeys = origExtraction.singletonKeys();
                impf.usagesOf(cancelled, file, null, (IntRange a, String b, FileObject c, String d, Extraction ext) -> {
                    ImportFinder importFinder = ImportFinder.forMimeType(c.getMIMEType());
                    if (importFinder instanceof ImportKeySupplier) {
                        NamedRegionKey<?>[] importKeys = ((ImportKeySupplier) importFinder).get();

                        for (SingletonKey<?> sing : origExtraction.singletonKeys()) {
                            if (scanned.contains(c)) {
                                return;
                            }

                        }
                    }
                });
//                if (countFound[0] == 0) {
//
//                }
            }
        }

        private boolean isSameSource(FileObject origFile, Extraction origExtraction, Extraction target) {
            boolean isRightFile = target == origExtraction || target.source().equals(origExtraction.source());
            if (!isRightFile) {
                Optional<FileObject> optSourceFile = target.source().lookup(FileObject.class);
                isRightFile = optSourceFile.isPresent() && origFile.equals(optSourceFile.get());
            }
            return isRightFile;
        }

        <T extends Enum<T>> int attributeOne(NamedSemanticRegion<?> reg, NameReferenceSetKey<T> key,
                FileObject origFile, Extraction origExtraction, int caretPosition,
                FileObject scanning, Extraction scanningExtraction,
                PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer) {

            int result = 0;
            Attributions<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T> attributions = scanningExtraction.resolveAll((NameReferenceSetKey<T>) key);
            for (SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> attr : attributions.attributed()) {
                Extraction target = attr.key().from();
                // Try to avoid potential Snapshot -> Source -> Document -> FileObject conversion
                // if we can do a simpler test
                if (isSameSource(origFile, origExtraction, target)) {
                    NamedSemanticRegion<T> found = attr.key().element();
                    if (reg == found || reg.equals(found) || reg.relationTo(found) == RangeRelation.EQUAL) {
                        Optional<FileObject> targetFile = attr.key().target().source().lookup(FileObject.class);
                        if (targetFile.isPresent()) {
                            usageConsumer.accept(attr, attr.key().name(), targetFile.get(), key.name(), scanningExtraction);
                            result++;
                        }
                    }
                }
            }
            return result;
        }
    }
}

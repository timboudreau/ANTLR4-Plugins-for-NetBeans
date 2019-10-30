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
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class SimpleUsagesFinder<T extends Enum<T>> extends UsagesFinder {

    private final NamedRegionKey<T> key;
    private final NameReferenceSetKey<T> refsKey;

    public SimpleUsagesFinder(NameReferenceSetKey<T> refsKey) {
        this(null, refsKey);
    }

    public SimpleUsagesFinder(NamedRegionKey<T> key) {
        this(key, null);
    }

    public SimpleUsagesFinder(NamedRegionKey<T> key, NameReferenceSetKey<T> refsKey) {
        this.key = key == null ? notNull("refsKey", refsKey).referencing() : key;
        this.refsKey = refsKey;
    }

    @Override
    public final void findUsages(BooleanSupplier cancelled, FileObject file, int caretPosition, Extraction extraction, PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer) {

        // See if a region for our key contains the cursor
        NamedSemanticRegions<T> regions = extraction.namedRegions(key);
        // Get the region
        NamedSemanticRegion<T> region = regions.at(caretPosition);
        // If no region, see if we can resolve a reference to an element of
        // this set of regions back to our key
        if (region == null && refsKey != null) {
            NamedRegionReferenceSets<T> refs = extraction.references(refsKey);
            NamedSemanticRegionReference<T> ref = refs.at(caretPosition);
            if (ref != null) {
                region = ref.referencing();
            }
        }
        if (refsKey == null) {

        }
        if (refsKey != null && region != null) {
            NamedRegionReferenceSets<T> refs = extraction.references(refsKey);
            String name = region.name();
            NamedRegionReferenceSet<T> set = refs.references(name);
            for (NamedSemanticRegionReference<T> r : set) {
                usageConsumer.accept(r, name, file, key.name(), extraction);
            }
        }

        if (region != null && !cancelled.getAsBoolean()) {
            doFindUsages(cancelled, file, region, regions, extraction, usageConsumer);
        }
    }

    public final void findUsages(BooleanSupplier cancelled, FileObject file, Extraction extraction, NamedSemanticRegion<T> region, NamedSemanticRegions<T> owner, PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer) {
        if (refsKey != null && region != null) {
            NamedRegionReferenceSets<T> refs = extraction.references(refsKey);
            String name = region.name();
            NamedRegionReferenceSet<T> set = refs.references(name);
            for (NamedSemanticRegionReference<T> r : set) {
                usageConsumer.accept(r, name, file, key.name(), extraction);
                if (cancelled.getAsBoolean()) {
                    return;
                }
            }
        }
        doFindUsages(cancelled, file, region, owner, extraction, usageConsumer);
    }

    private static <T extends Enum<T>> void doFindUsages(BooleanSupplier cancelled, FileObject file, NamedSemanticRegion<T> region, NamedSemanticRegions<T> regions, Extraction origExtraction, PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer) {
        // ImportersFinder will give us the set of files that import this one
        ImportersFinder impf = ImportersFinder.forFile(file);
        int[] countFound = new int[1];
        // We can get multiple callbacks per file for duplicate imports, so
        // keep a set so we only process each file once
        Set<FileObject> scanned = new HashSet<>();
        impf.usagesOf(cancelled, file, null, (IntRange a, String b, FileObject c, String d, Extraction ext) -> {
            if (scanned.contains(c) || cancelled.getAsBoolean()) { // done or already did it
                return;
            }
            scanned.add(c);
            // We don't necessarily know what keys are in the extraction - it might
            // even be a different mime type
            for (NameReferenceSetKey<?> key : ext.referenceKeys()) {
                // If the key matches, attribute any unknown variables - they will
                // be unknown, since they are things that look like references but
                // cannot be resolved within that file - and see
                // if any of them track back to the region we're searching for
                if (key.referencing().equals(key)) {
                    countFound[0] += attributeOne(region, key, file, origExtraction, c, ext, usageConsumer);
                }
                if (cancelled.getAsBoolean()) {
                    return;
                }
            }
        });
    }

    private static boolean isSameSource(FileObject origFile, Extraction origExtraction, Extraction target) {
        // Determine if two files are the same source, trying the cheapest tests first
        boolean isRightFile = target == origExtraction || target.source().equals(origExtraction.source());
        if (!isRightFile) {
            Optional<FileObject> optSourceFile = target.source().lookup(FileObject.class);
            isRightFile = optSourceFile.isPresent() && origFile.equals(optSourceFile.get());
        }
        return isRightFile;
    }

    static <T extends Enum<T>> int attributeOne(NamedSemanticRegion<?> reg, NameReferenceSetKey<T> key,
            FileObject origFile, Extraction origExtraction,
            FileObject scanning, Extraction scanningExtraction,
            PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer) {

        int result = 0;
        // Attribute unknown variables for the passed key in the file that imports
        // the one whose member we're looking for usages in
        Attributions<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T> attributions = scanningExtraction.resolveAll((NameReferenceSetKey<T>) key);
        for (SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> attr : attributions.attributed()) {
            Extraction target = attr.key().from();
            // Try to avoid potential Snapshot -> Source -> Document -> FileObject conversion
            // if we can do a simpler test
            if (isSameSource(origFile, origExtraction, target)) {
                // Get the character range of the original element this reference in a
                // foreign file refers to.  This may or may not be the from the
                // same NamedSemanticRegions as we got from our extraction, depending
                // on caching and whether the source was invalidated
                NamedSemanticRegion<T> found = attr.key().element();
                // again, depending on parse order, we might get literal equality, or
                // object equality or simply a bounds match
                if (reg == found || reg.relationTo(found) == RangeRelation.EQUAL || reg.equals(found)) {
                    // Look up the file
                    usageConsumer.accept(attr, attr.key().name(), scanning, key.name(), scanningExtraction);
                    result++;
                }
            }
        }
        return result;
    }
}

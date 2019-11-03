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
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.HashSet;
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
import org.netbeans.modules.refactoring.api.Problem;
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
    public final Problem findUsages(BooleanSupplier cancelled, FileObject file,
            int caretPosition, Extraction extraction,
            PetaConsumer<IntRange<? extends IntRange>, String, FileObject, String, Extraction> usageConsumer) {
        Problem result = null;
        // See if a region for our key contains the cursor
        NamedSemanticRegions<T> regions = extraction.namedRegions(key);
        // Get the region
        NamedSemanticRegion<T> region = regions.at(caretPosition);

        logFinest("Orig keys {0} and {1} found {2}", key, refsKey, region);
        // If no region, see if we can resolve a reference to an element of
        // this set of regions back to our key
        if (region == null && refsKey != null) {
            NamedRegionReferenceSets<T> refs = extraction.references(refsKey);
            NamedSemanticRegionReference<T> ref = refs.at(caretPosition);
            if (ref != null) {
                region = ref.referencing();
                logFinest("Use original {0} from ref {1}", region, ref);
            }
        }
        if (refsKey != null && region != null) {
            NamedRegionReferenceSets<T> refs = extraction.references(refsKey);
            String name = region.name();
            NamedRegionReferenceSet<T> set = refs.references(name);
            logFine("Add all references to {0} from {1}", name, refsKey);
            for (NamedSemanticRegionReference<T> r : set) {
                Problem p = usageConsumer.accept(r, name, file, key.name(), extraction);
                result = chainProblems(result, p);
                if (cancelled.getAsBoolean() || (p != null && p.isFatal())) {
                    logFinest("Cancelled {0}", this);
                    return result;
                }
            }
        }

        if (region != null && !cancelled.getAsBoolean()) {
            logFine("Search for dependencies");
            Problem p = doFindUsages(cancelled, file, region, regions, extraction, usageConsumer);
            result = chainProblems(p, result);
        }
        return result;
    }

    public final Problem findUsages(BooleanSupplier cancelled, FileObject file,
            Extraction extraction, NamedSemanticRegion<T> region,
            NamedSemanticRegions<T> owner,
            PetaConsumer<IntRange<? extends IntRange>, String, FileObject, String, Extraction> usageConsumer) {
        Problem result = null;
        if (region instanceof NamedSemanticRegionReference<?>) {
            NamedSemanticRegion<?> orig = ((NamedSemanticRegionReference<?>) region).referencing();
            usageConsumer.accept(orig, orig.name(), file, key.name(), extraction);
        } else {
            usageConsumer.accept(region, region.name(), file, key.name(), extraction);
        }
        logFine("Find usages of {0} in {1} with refskey {2] from {3}", owner, file.getNameExt(), refsKey, region);
        if (refsKey != null && region != null) {
            NamedRegionReferenceSets<T> refs = extraction.references(refsKey);
            String name = region.name();
            NamedRegionReferenceSet<T> set = refs.references(name);
            logFine("Found {0} references to {1} in {2}", set.size(), name, file.getNameExt());
            for (NamedSemanticRegionReference<T> r : set) {
                Problem p = usageConsumer.accept(r, name, file, key.name(), extraction);
                result = chainProblems(result, p);
                if (cancelled.getAsBoolean() || (p != null && p.isFatal())) {
                    break;
                }
                if (p != null && p.isFatal()) {
                    return result;
                }
            }
        }
        Problem p = doFindUsages(cancelled, file, region, owner, extraction, usageConsumer);
        return chainProblems(result, p);
    }

    private <T extends Enum<T>> Problem doFindUsages(BooleanSupplier cancelled,
            FileObject file, NamedSemanticRegion<T> region, NamedSemanticRegions<T> regions,
            Extraction origExtraction, PetaConsumer<IntRange<? extends IntRange>, String, FileObject, String, Extraction> usageConsumer) {
        // ImportersFinder will give us the set of files that import this one
        ImportersFinder impf = ImportersFinder.forFile(file);
        // We can get multiple callbacks per file for duplicate imports, so
        // keep a set so we only process each file once
        Set<FileObject> scanned = new HashSet<>();
        return impf.usagesOf(cancelled, file, null, (IntRange<? extends IntRange> a, String b, FileObject c, String d, Extraction ext) -> {
            Problem result = null;
            if (scanned.contains(c) || cancelled.getAsBoolean()) { // done or already did it
                logFinest("Already scanned {0}", c.getNameExt());
                return result;
            }
            logFine("Usage in {0} at {1} type {2}", c.getNameExt(), a, d);
            scanned.add(c);
            // We don't necessarily know what keys are in the extraction - it might
            // even be a different mime type
            for (NameReferenceSetKey<?> k : ext.referenceKeys()) {
                logFinest("Try {0} in {1}", k, c.getNameExt());
                // If the key matches, attribute any unknown variables - they will
                // be unknown, since they are things that look like references but
                // cannot be resolved within that file - and see
                // if any of them track back to the region we're searching for
                if (k.referencing().equals(key)) {
                    logFine("Matched reference key {0} in {1}", new Object[]{k, c.getNameExt()});
                    Problem p = attributeOne(cancelled, region, k, file, origExtraction, c, ext, usageConsumer);
                    result = chainProblems(result, p);
                    if ((p != null && p.isFatal()) || cancelled.getAsBoolean()) {
                        logFinest("Cancelled or problem {0}", p);
                        return result;
                    }
                } else {
                    logFinest("Keys do not reference the same thing: {0}, {1}, {2}", k, k.referencing(), key);
                }
                if (cancelled.getAsBoolean()) {
                    logFine("Cancelled at {0}", k);
                    break;
                }
            }
            return result;
        });
    }

    <T extends Enum<T>> Problem attributeOne(BooleanSupplier cancelled,
            NamedSemanticRegion<?> reg, NameReferenceSetKey<T> key,
            FileObject origFile, Extraction origExtraction,
            FileObject scanning, Extraction scanningExtraction,
            PetaConsumer<IntRange<? extends IntRange>, String, FileObject, String, Extraction> usageConsumer) {

        Problem result = null;
        // Attribute unknown variables for the passed key in the file that imports
        // the one whose member we're looking for usages in
        Attributions<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T> attributions = scanningExtraction.resolveAll((NameReferenceSetKey<T>) key);
        logFine("Attribute {0} getting {1}", scanningExtraction.source(), attributions);

        for (SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> attr : attributions.attributed()) {
            Extraction target = attr.key().attributedTo();
            logFine("Attribution {0} from {1}", attr, target.source());
            if (cancelled.getAsBoolean()) {
                break;
            }
            // Try to avoid potential Snapshot -> Source -> Document -> FileObject conversion
            // if we can do a simpler test
            if (isSameSource(origFile, origExtraction, target)) {
                // Get the character range of the original element this reference in a
                // foreign file refers to.  This may or may not be the from the
                // same NamedSemanticRegions as we got from our extraction, depending
                // on caching and whether the source was invalidated
                NamedSemanticRegion<T> found = attr.key().element();
                logFinest("Matched region {0} in {1}", found, target.source());
                // again, depending on parse order, we might get literal equality, or
                // object equality or simply a bounds match
                if (sameRange(reg, found)) {
                    // Look up the file
                    Problem p = usageConsumer.accept(attr, attr.key().name(), scanning, key.name(), scanningExtraction);
                    result = chainProblems(result, p);
                    if (p != null && p.isFatal()) {
                        break;
                    }
                } else {
                    logFinest("Matched but wrong relation {0} for {1} and {2}", new Object[]{reg.relationTo(found),
                        found, reg});
                }
            } else {
                logFine("Not same source {0} and {1}", new Object[]{scanningExtraction.source(), origExtraction.source()});
            }
        }
        return result;
    }

}

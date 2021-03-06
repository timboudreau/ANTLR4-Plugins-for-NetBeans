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
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.localizers.api.Localizers;
import org.netbeans.modules.refactoring.api.Problem;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class SingletonUsagesFinder<T> extends UsagesFinder {

    private final SingletonKey<T> key;

    public SingletonUsagesFinder(SingletonKey<T> key) {
        this.key = key;
    }

    private String name(SingletonEncounter<T> enc) {
        return Localizers.displayName(enc);
    }

    public final Problem findUsages(BooleanSupplier cancelled, FileObject file,
            SingletonEncounter<T> encounter,
            PetaFunction<IntRange<? extends IntRange<?>>, String, FileObject, ExtractionKey<?>, Extraction> usageConsumer) {
        String name = name(encounter);
        Set<FileObject> seen = new HashSet<>(10);
        ImportersFinder importers = ImportersFinder.forFile(file);
        return importers.usagesOf(cancelled, file, null, (range, name1, fo, name2, ext) -> {
            boolean seenIt = seen.contains(fo);
            boolean cancel = cancelled.getAsBoolean();
            boolean sameName = name1.equals(name);
            logFine("SingletonUsage check {0} looking for {1} in {2} seen? {3}, "
                    + " cancelled? {4}, sameName? {5} in {6}", name1, name, file.getNameExt(),
                    seenIt, cancel, sameName, range);
            if (!seen.contains(fo) && !cancelled.getAsBoolean() && name1.equals(name)) {
                return usageConsumer.accept(range, name1, fo, key, ext);
            }
            return null;
        });
    }

    @Override
    public final Problem findUsages(BooleanSupplier cancelled, FileObject file,
            int caretPosition, Extraction extraction,
            PetaFunction<IntRange<? extends IntRange<?>>, String, FileObject, ExtractionKey<?>, Extraction> usageConsumer) {
        SingletonEncounters<T> encs = extraction.singletons(key);
        SingletonEncounter<T> e = encs.at(caretPosition);
        return e == null ? null : findUsages(cancelled, file, e, usageConsumer);
    }
}

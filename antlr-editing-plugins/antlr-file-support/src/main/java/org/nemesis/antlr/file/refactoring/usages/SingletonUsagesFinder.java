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

import com.mastfrog.abstractions.Named;
import com.mastfrog.abstractions.Stringifier;
import com.mastfrog.range.IntRange;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.modules.refactoring.api.Problem;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class SingletonUsagesFinder<T> extends UsagesFinder {

    private final SingletonKey<T> key;
    private final Stringifier<? super T> stringifier;

    public SingletonUsagesFinder(SingletonKey<T> key, Stringifier<? super T> stringifier) {
        this.key = key;
        this.stringifier = stringifier;
    }

    private String name(SingletonEncounter<T> enc) {
        if (stringifier != null) {
            return stringifier.toString(enc.value());
        }
        if (enc.get() instanceof Named) {
            return ((Named) enc.get()).name();
        }
        return enc.get().toString();
    }

    public final Problem findUsages(BooleanSupplier cancelled, FileObject file,
            SingletonEncounter<T> encounter,
            PetaConsumer<IntRange<? extends IntRange>, String, FileObject, String, Extraction> usageConsumer) {
        String name = name(encounter);
        Set<FileObject> seen = new HashSet<>(10);
        ImportersFinder importers = ImportersFinder.forFile(file);
        return importers.usagesOf(cancelled, file, null, (range, name1, fo, name2, ext) -> {
            if (!seen.contains(fo) && !cancelled.getAsBoolean() && name1.equals(name)) {
                return usageConsumer.accept(range, name1, fo, name2, ext);
            }
            return null;
        });
    }

    @Override
    public final Problem findUsages(BooleanSupplier cancelled, FileObject file,
            int caretPosition, Extraction extraction,
            PetaConsumer<IntRange<? extends IntRange>, String, FileObject, String, Extraction> usageConsumer) {
        SingletonEncounters<T> encs = extraction.singletons(key);
        SingletonEncounter<T> e = encs.at(caretPosition);
        return e == null ? null : findUsages(cancelled, file, e, usageConsumer);
    }
}

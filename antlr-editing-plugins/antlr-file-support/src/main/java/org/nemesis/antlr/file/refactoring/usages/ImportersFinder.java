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
import java.util.Collection;
import java.util.function.BooleanSupplier;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NamedRegionKey;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Performs reverse-lookup to find those files that import a passed file;
 * implementations may be registered in MimeLookup for the file's mime type. A
 * default implementation is provided which does fairly brute-force scanning of
 * the project, parsing and chewing. Whenever possible, provide a more
 * performantimplementation.
 *
 * @author Tim Boudreau
 */
public abstract class ImportersFinder {

    public static ImportersFinder forFile(FileObject fo) {
        return forMimeType(fo.getMIMEType());
    }

    /**
     * A hint to the infrastructure as to whether this usages finder is
     * performant enough to be called in the AWT thread; indexing-based ones
     * should be; file-scanning-based ones not so much.
     *
     * @return True if this finder is IO intensive and not appropriate to run in
     * the event thread
     */
    public boolean isIOIntensive() {
        return true;
    }

    public static ImportersFinder forMimeType(String mimeType) {
        Lookup lkp = MimeLookup.getLookup(mimeType);
        Collection<? extends ImportersFinder> all = lkp.lookupAll(ImportersFinder.class);
        if (all.isEmpty()) {
            return new DefaultUsagesFinder();
        } else if (all.size() == 1) {
            return all.iterator().next();
        } else {
            return new PolyUsagesFinder(all);
        }
    }

    public abstract void usagesOf(
            BooleanSupplier cancelled,
            FileObject file,
            NamedRegionKey<?> optionalImportKey,
            PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer);

    static final class DefaultUsagesFinder extends SimpleImportersFinder {

    }

    static final class PolyUsagesFinder extends ImportersFinder {

        private final Collection<? extends ImportersFinder> all;

        public PolyUsagesFinder(Collection<? extends ImportersFinder> all) {
            this.all = all;
        }

        @Override
        public boolean isIOIntensive() {
            boolean result = false;
            for (ImportersFinder uf : all) {
                result |= uf.isIOIntensive();
            }
            return result;
        }

        @Override
        public void usagesOf(BooleanSupplier cancelled, FileObject file, NamedRegionKey<?> optionalImportKey, PetaConsumer<IntRange, String, FileObject, String, Extraction> usageConsumer) {
            for (ImportersFinder f : all) {
                f.usagesOf(cancelled, file, optionalImportKey, usageConsumer);
                if (cancelled.getAsBoolean()) {
                    return;
                }
            }
        }
    }
}

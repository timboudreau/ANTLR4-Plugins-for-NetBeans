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

import com.mastfrog.function.throwing.io.IOSupplier;
import com.mastfrog.range.IntRange;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.openide.filesystems.FileObject;

/**
 * Supplier for modified document text for any refactoring that needs to change
 * one range in a file.
 *
 * @author Tim Boudreau
 */
final class LazyModificationSupplier implements IOSupplier<String> {

    private final AbstractRefactoring refactoring;
    private final IntRange decl;
    private final FileObject file;
    private final Supplier<String> replacementTextSupplier;
    private String cached;

    public LazyModificationSupplier(AbstractRefactoring refactoring, IntRange decl, FileObject file, Supplier<String> replacementTextSupplier) {
        this.refactoring = notNull("refactoring", refactoring);
        this.decl = notNull("decl", decl);
        this.file = notNull("file", file);
        this.replacementTextSupplier = notNull("replacementTextSupplier",
                replacementTextSupplier);
    }

    @Override
    public String get() throws IOException {
        if (cached != null) {
            return cached;
        }
        StringBuilder sb = new StringBuilder(file.asText());
        sb.delete(decl.start(), decl.end());
        sb.insert(decl.start(), replacementTextSupplier.get());
        return cached = sb.toString();
    }

    @Override
    public String toString() {
        return "Update " + decl.start() + ":" + decl.end() 
                + " to name " + replacementTextSupplier.get()
                + " for " + decl + " in " + file.getPath();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.refactoring);
        hash = 89 * hash + Objects.hashCode(this.decl);
        hash = 89 * hash + Objects.hashCode(this.file);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LazyModificationSupplier other = (LazyModificationSupplier) obj;
        if (!Objects.equals(this.refactoring, other.refactoring)) {
            return false;
        }
        if (!Objects.equals(this.decl, other.decl)) {
            return false;
        }
        return Objects.equals(this.file, other.file);
    }
}

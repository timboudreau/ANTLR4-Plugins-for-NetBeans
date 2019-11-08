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

import com.mastfrog.function.throwing.io.IOSupplier;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.modules.refactoring.spi.ModificationResult;
import org.openide.filesystems.FileObject;

/**
 * A modification to one file that takes a supplier to provide the modified
 * text, and implements equality correctly.
 *
 * @author Tim Boudreau
 */
final class LazyModificationResult implements ModificationResult {

    private final FileObject file;
    private final IOSupplier<String> text;

    LazyModificationResult(FileObject file, IOSupplier<String> text) {
        this.file = file;
        this.text = text;
    }

    @Override
    public String toString() {
        return "LazyModificationResult(" + file.getPath() + " with " + text + ")";
    }

    @Override
    public String getResultingSource(FileObject fo) throws IOException, IllegalArgumentException {
        return file.equals(fo) ? text.get() : null;
    }

    @Override
    public Collection<? extends FileObject> getModifiedFileObjects() {
        return Collections.singleton(file);
    }

    @Override
    public Collection<? extends File> getNewFiles() {
        return Collections.emptySet();
    }

    @Override
    public void commit() throws IOException {
        file.getFileSystem().runAtomicAction(() -> {
            Charset cs = FileEncodingQuery.getEncoding(file);
            try (final OutputStream out = file.getOutputStream()) {
                out.write(text.get().getBytes(cs));
            }
        });
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.file);
        hash = 79 * hash + Objects.hashCode(this.text);
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
        final LazyModificationResult other = (LazyModificationResult) obj;
        if (!Objects.equals(this.file, other.file)) {
            return false;
        }
        return Objects.equals(this.text, other.text);
    }
}

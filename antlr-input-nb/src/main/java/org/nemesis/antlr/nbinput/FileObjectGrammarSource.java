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
package org.nemesis.antlr.nbinput;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.extraction.nb.api.AbstractFileObjectGrammarSourceImplementation;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
final class FileObjectGrammarSource extends AbstractFileObjectGrammarSourceImplementation<FileObject> {

    private final FileObject file;
    private final RelativeResolver<FileObject> resolver;

    public FileObjectGrammarSource(FileObject fob, RelativeResolver<FileObject> resolver) {
        super(FileObject.class);
        this.file = fob;
        this.resolver = resolver;
    }

    @Override
    public String name() {
        return file.getName();
    }

    public String toString() {
        return file.toURI().toString();
    }

    @Override
    public FileObject toFileObject() {
        return file;
    }

    @Override
    public long lastModified() throws IOException {
        return file.lastModified().getTime();
    }

    @Override
    protected <R> R lookupImpl(Class<R> type) {
        if (File.class == type) {
            File f = FileUtil.toFile(file);
            if (f != null) { // virtual filesystem
                return type.cast(f);
            }
        } else if (Source.class == type) {
            return type.cast(Source.create(file));
        } else if (Snapshot.class == type) {
            return type.cast(Source.create(file).createSnapshot());
        }
        return super.lookupImpl(type);
    }

    @Override
    public CharStream stream() throws IOException {
        DataObject dob = DataObject.find(file);
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        if (ck != null) {
            Document doc = ck.getDocument();
            if (doc != null) {
//                return new DocumentGrammarSource(doc, resolver).stream();
                GrammarSource docSource
                        = GrammarSource.find(doc, dob.getPrimaryFile().getMIMEType());
                return docSource.stream();
            }
        }
        return new CharSequenceCharStream(name(), file.asText());
    }

    @Override
    public GrammarSourceImplementation<?> resolveImport(String name) {
        Optional<FileObject> result = resolver.resolve(file, name);
        if (result.isPresent()) {
            return new FileObjectGrammarSource(result.get(), resolver);
        }
        return null;
    }

    @Override
    public FileObject source() {
        return file;
    }
}

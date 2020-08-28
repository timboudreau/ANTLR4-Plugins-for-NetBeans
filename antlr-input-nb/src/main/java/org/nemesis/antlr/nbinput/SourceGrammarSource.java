/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

import java.io.IOException;
import java.util.Optional;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.extraction.nb.api.AbstractFileObjectGrammarSourceImplementation;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.api.RelativeResolver;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.nemesis.source.spi.GrammarSourceImplementationFactory;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.Source;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
public class SourceGrammarSource extends AbstractFileObjectGrammarSourceImplementation<Source> {

    private final Source src;
    private final RelativeResolver<Source> resolver;
    public SourceGrammarSource(Source src, RelativeResolver<Source> resolver) {
        super(Source.class);
        this.src = src;
        this.resolver = resolver;
    }

    @Override
    public FileObject toFileObject() {
        FileObject fo = source().getFileObject();
        if (fo == null) {
            Document doc = source().getDocument(false);
            if (doc != null) {
                fo = NbEditorUtilities.getFileObject(doc);
            }
        }
        return fo;
    }

    @Override
    protected Document document() {
        return source().getDocument(false);
    }

    @Override
    public CharStream stream() throws IOException {
        return new CharSequenceCharStream(name(), source().createSnapshot().getText());
    }

    @Override
    public GrammarSourceImplementation<?> resolveImport(String name) {
        if (name.equals(name())) {
            return this;
        }
        // Take a faster path first
        FileObject fo = source().getFileObject();
        if (fo == null) {
            Document d = source().getDocument(false);
            fo = NbEditorUtilities.getFileObject(d);
        }
        if (fo != null) {
            GrammarSource<?> gs = GrammarSource.find(fo, fo.getMIMEType()).resolveImport(name);
            if (gs != null) {
                Document doc = gs.lookupOrDefault(Document.class, null);
                if (doc != null) {
                    return new SourceGrammarSource(Source.create(doc), resolver);
                }
                FileObject neighbor = gs.lookupOrDefault(FileObject.class, null);
                if (neighbor != null) {
                    return new SourceGrammarSource(Source.create(neighbor), resolver);
                }
            }
        }
        Optional<Source> src = resolver.resolve(this.src, name);
        if (src.isPresent()) {
            return new SourceGrammarSource(src.get(), resolver);
        }
        return null;
    }

    @Override
    public Source source() {
        return src;
    }

    @ServiceProvider(service = GrammarSourceImplementationFactory.class)
    public static final class Factory extends GrammarSourceImplementationFactory<Source> {

        public Factory() {
            super(Source.class);
        }

        @Override
        public GrammarSourceImplementation<Source> create(Source doc, RelativeResolver<Source> resolver) {
            return new SourceGrammarSource(doc, resolver);
        }
    }


}

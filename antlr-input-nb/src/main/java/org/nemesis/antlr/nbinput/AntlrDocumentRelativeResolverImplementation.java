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

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.nemesis.source.spi.DocumentAdapterRegistry;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Pair;
import org.openide.util.lookup.ServiceProvider;

/**
 * Document relative resolver which simply delegates to a
 * FileObject relative resolver.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RelativeResolverImplementation.class, path = "antlr-languages/relative-resolvers/text/x-g4")
public class AntlrDocumentRelativeResolverImplementation extends RelativeResolverImplementation<Document> {

    private final ThreadLocal<Pair<Document, String>> RESOLVING
            = new ThreadLocal<>();

    public AntlrDocumentRelativeResolverImplementation() {
        super(Document.class);
    }

    @Override
    public Optional<Document> resolve(Document relativeTo, String name) {
        Pair<Document, String> lookingFor = RESOLVING.get();
        if (lookingFor != null && lookingFor.first() == relativeTo && lookingFor.second().equals(name)) {
            return Optional.empty();
        }
        RESOLVING.set(Pair.of(relativeTo, name));
        try {
            FileObject fo = NbEditorUtilities.getFileObject(relativeTo);
            if (fo != null) {
                Optional<FileObject> found = DocumentAdapterRegistry.getDefault()
                        .forDocumentAndMimeType(fo, fo.getMIMEType())
                        .resolve(fo, name);
                if (found.isPresent()) {
                    try {
                        DataObject dob = DataObject.find(fo);
                        EditorCookie ck = dob.getCookie(EditorCookie.class);
                        if (ck != null) {
                            Document doc = ck.openDocument();
                            return Optional.of(doc);
                        }
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }

                GrammarSource<FileObject> fogs = GrammarSource.find(fo, fo.getMIMEType());
                if (fogs != null) {
                    GrammarSource<?> gs = fogs.resolveImport(name);
                    if (gs != null) {
                        try {
                            Object src = gs.source();
                            if (src instanceof Document) {
                                return Optional.of((Document) src);
                            } else if (src instanceof FileObject) {
                                FileObject n = (FileObject) src;
                                DataObject dob = DataObject.find(n);
                                EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                                if (ck != null) {
                                    Document d = ck.getDocument();
                                    if (d == null) {
                                        d = ck.openDocument();
                                    }
                                    if (d != null) {
                                        return Optional.of(d);
                                    }
                                }
                            }
                            return gs.lookup(type);
                        } catch (IOException ex) {
                            Logger.getLogger(
                                    AntlrDocumentRelativeResolverImplementation.class.getName())
                                    .log(Level.SEVERE, "Exception resolving " + name, ex);
                        }
                    }
                }
            }
            return Optional.empty();
        } finally {
            RESOLVING.set(lookingFor);
        }
    }
}

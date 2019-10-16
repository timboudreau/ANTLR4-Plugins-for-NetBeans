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

import java.util.Optional;
import javax.swing.text.Document;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RelativeResolverImplementation.class, path = "antlr-languages/relative-resolvers/text/x-g4")
public final class AntlrSnapshotRelativeResolver extends RelativeResolverImplementation<Snapshot> {

    public AntlrSnapshotRelativeResolver() {
        super(Snapshot.class);
    }

    @Override
    public Optional<Snapshot> resolve(Snapshot relativeTo, String name) {
        Source src = relativeTo.getSource();
        FileObject fo = src.getFileObject();
        if (fo == null) {
            return Optional.empty();
        }
        GrammarSource<FileObject> fogs = GrammarSource.find(fo, fo.getMIMEType());
        if (fogs == null) {
            return Optional.empty();
        }
        GrammarSource<?> gs = fogs.resolveImport(name);
        if (gs != null) {
            Optional<Document> odoc = gs.lookup(Document.class);
            if (odoc.isPresent()) {
                return Optional.of(Source.create(odoc.get()).createSnapshot());
            }
            Optional<FileObject> ofo = gs.lookup(FileObject.class);
            if (ofo.isPresent()) {
                return Optional.of(Source.create(ofo.get()).createSnapshot());
            }
        }
        return Optional.empty();
    }

}

package org.nemesis.extraction.nb;

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

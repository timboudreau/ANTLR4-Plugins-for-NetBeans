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
            return type.cast(FileUtil.toFile(file));
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

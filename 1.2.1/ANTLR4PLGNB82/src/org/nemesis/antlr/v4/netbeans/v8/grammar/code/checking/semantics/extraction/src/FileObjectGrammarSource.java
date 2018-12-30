package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
final class FileObjectGrammarSource extends GrammarSource<FileObject> {

    private final FileObject file;
    private final RelativeFileObjectResolver resolver;

    public FileObjectGrammarSource(FileObject fob, RelativeFileObjectResolver resolver) {
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
    public CharStream stream() throws IOException {
        DataObject dob = DataObject.find(file);
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        if (ck != null) {
            Document doc = ck.getDocument();
            if (doc != null) {
                return new DocumentGrammarSource(doc, resolver).stream();
            }
        }
        return CharStreams.fromString(file.asText());
    }

    @Override
    public GrammarSource<?> resolveImport(String name, Extraction extraction) {
        Optional<FileObject> result = resolver.resolve(file, name, extraction);
        if (result.isPresent()) {
            return new FileObjectGrammarSource(result.get(), resolver);
        }
        return null;
    }

    @Override
    public FileObject source() {
        return file;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.file);
        hash = 79 * hash + Objects.hashCode(this.resolver);
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
        final FileObjectGrammarSource other = (FileObjectGrammarSource) obj;
        if (!Objects.equals(this.file, other.file)) {
            return false;
        }
        if (!Objects.equals(this.resolver, other.resolver)) {
            return false;
        }
        return true;
    }

}

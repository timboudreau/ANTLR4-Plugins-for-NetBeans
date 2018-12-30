package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public abstract class GrammarSource<T> implements Serializable {

    public abstract String name();

    public abstract CharStream stream() throws IOException;

    public abstract GrammarSource<?> resolveImport(String name, Extraction extraction);

    public abstract T source();

    public static GrammarSource<FileObject> forFileObject(FileObject fo, RelativeFileObjectResolver resolver) {
        return new FileObjectGrammarSource(fo, resolver);
    }

    public static GrammarSource<CharStream> forSingleCharStream(String name, CharStream stream) {
        return new StringGrammarSource(name, stream);
    }

    public static GrammarSource<CharStream> forText(String name, String grammarBody) {
        return forSingleCharStream(name, CharStreams.fromString(grammarBody));
    }

    public static GrammarSource<?> forDocument(Document doc, RelativeFileObjectResolver resolver) {
        return new DocumentGrammarSource(doc, resolver);
    }

    public FileObject toFileObject() {
        return null;
    }

    @FunctionalInterface
    public interface RelativeFileObjectResolver extends Serializable {

        Optional<FileObject> resolve(FileObject relativeTo, String name, Extraction in);
    }

}

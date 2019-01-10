package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.GrammarSourceImplementation;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.access.GSAccessor;

/**
 * Abstraction for files, documents or even text strings which can provide
 * input, and can resolve sibling documents/files/whatever.
 *
 * @author Tim Boudreau
 */
public final class GrammarSource<T> implements Serializable {

    private final GrammarSourceImplementation<T> impl;

    private GrammarSource(GrammarSourceImplementation<T> impl) {
        assert impl != null : "impl null";
        this.impl = impl;
    }

    public final String name() {
        return GSAccessor.getDefault().nameOf(impl);
    }

    public final CharStream stream() throws IOException {
        return GSAccessor.getDefault().stream(impl);
    }

    public final GrammarSource<?> resolveImport(String name) {
        return GSAccessor.getDefault().resolve(impl, name);
    }

    public final T source() throws IOException {
        return GSAccessor.getDefault().source(impl);
    }

    public final long lastModified() throws IOException {
        return GSAccessor.getDefault().lastModified(impl);
    }

    public final <R> R lookup(Class<R> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return GSAccessor.getDefault().lookup(impl, type);
    }

    public final String toString() {
        return impl.toString();
    }

    @Override
    public final int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.impl);
        return hash;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GrammarSource<?> other = (GrammarSource<?>) obj;
        if (!Objects.equals(this.impl, other.impl)) {
            return false;
        }
        return true;
    }

    public static <T> GrammarSource<T> find(T document, String mimeType) {
        return GSAccessor.getDefault().newGrammarSource(mimeType, document);
    }

//
//    public static GrammarSource<FileObject> forFileObject(FileObject fo, RelativeResolver<FileObject> resolver) {
//        return new FileObjectGrammarSource(fo, resolver);
//    }
//
//    public static GrammarSource<CharStream> forSingleCharStream(String name, CharStream stream) {
//        return new StringGrammarSource(name, stream);
//    }
//
//    public static GrammarSource<CharStream> forText(String name, String grammarBody) {
//        return forSingleCharStream(name, CharStreams.fromString(grammarBody));
//    }
//
//    public static GrammarSource<?> forDocument(Document doc, RelativeResolver<FileObject> resolver) {
//        return new DocumentGrammarSource(doc, resolver);
//    }

    static final class GSAccessorImpl extends GSAccessor {

        @Override
        public <T> GrammarSource<T> newGrammarSource(GrammarSourceImplementation<T> impl) {
            return new GrammarSource<>(impl);
        }
    }

    static {
        GSAccessor.DEFAULT = new GSAccessorImpl();
    }
}

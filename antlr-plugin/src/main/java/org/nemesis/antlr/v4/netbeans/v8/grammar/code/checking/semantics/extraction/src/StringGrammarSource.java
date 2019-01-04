package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src;

import org.antlr.v4.runtime.CharStream;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.Extraction;

/**
 *
 * @author Tim Boudreau
 */
final class StringGrammarSource extends GrammarSource<CharStream> {

    private final String name;
    // This class is for tests, don't fail the serializatoin tests because of this:
    private final transient CharStream stream;

    public StringGrammarSource(String name, CharStream stream) {
        this.name = name;
        this.stream = stream;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CharStream stream() {
        return stream;
    }

    @Override
    public GrammarSource<?> resolveImport(String name, Extraction ex) {
        return null;
    }

    @Override
    public CharStream source() {
        return stream();
    }

}

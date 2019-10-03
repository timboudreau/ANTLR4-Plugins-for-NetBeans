package org.nemesis.simple;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.nemesis.simple.language.SimpleLanguageLexer;
import org.nemesis.simple.language.SimpleLanguageParser;

/**
 *
 * @author Tim Boudreau
 */
public enum SampleFiles implements SampleFile<SimpleLanguageLexer, SimpleLanguageParser> {

    BASIC("basic.sim"),
    MINIMAL("minimal.sim"),
    ABSURDLY_MINIMAL("absurdly-minimal.sim"),
    MUCH_NESTING("much-nesting.sim"),
    MUCH_NESTING_WITH_EXTRA_NEWLINES("much-nesting-extra-newlines.sim"),
    MUCH_NESTING_UNFORMATTED("much-nesting-unformatted.sim"),
    MINIMAL_MULTILINE("minimal-with-multiline-comments.sim"),
    LONG_ITEMS("minimal_with_long_items.sim")
    ;

    private final String name;

    SampleFiles(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int length() throws IOException {
        return text().length();
    }

    private String text;

    @Override
    public String text() throws IOException {
        if (text != null) {
            return text;
        }
        CharStream cs = charStream();
        StringBuilder sb = new StringBuilder(512);
        for (;;) {
            int val = cs.LA(1);
            if (val == -1) {
                break;
            }
            char c = (char) val;
            sb.append(c);
            cs.consume();
        }
        return text = sb.toString();
    }

    @Override
    public InputStream inputStream() {
        InputStream result = SampleFiles.class.getResourceAsStream(name);
        assert result != null : name + " not found in " + SampleFiles.class.getPackage().getName().replace('.', '/');
        return result;
    }

    @Override
    public CharStream charStream() throws IOException {
        InputStream in = inputStream();
        return CharStreams.fromReader(new InputStreamReader(in,
                UTF_8), name);
    }

    @Override
    public SimpleLanguageLexer lexer() throws IOException {
        SimpleLanguageLexer lexer = new SimpleLanguageLexer(charStream());
        lexer.removeErrorListeners();
        return lexer;
    }

    @Override
    public SimpleLanguageLexer lexer(ANTLRErrorListener l) throws IOException {
        SimpleLanguageLexer lexer = new SimpleLanguageLexer(charStream());
        lexer.removeErrorListeners();
        lexer.addErrorListener(l);
        return lexer;
    }

    @Override
    public SimpleLanguageParser parser() throws IOException {
        SimpleLanguageLexer lexer = lexer();
        CommonTokenStream cts = new CommonTokenStream(lexer, 0);
        SimpleLanguageParser result = new SimpleLanguageParser(cts);
        result.removeErrorListeners();
        return result;
    }

}

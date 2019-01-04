package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.List;
import java.util.function.Supplier;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocLexer2 implements Lexer<AdhocTokenId> {

    private int cursor = -1;
    private List<Token<AdhocTokenId>> tokens;
    private final Supplier<List<Token<AdhocTokenId>>> tokenSupplier;

    AdhocLexer2(Supplier<List<Token<AdhocTokenId>>> tokens) {
        this.tokenSupplier = tokens;
    }

    private List<Token<AdhocTokenId>> tokens() {
        if (this.tokens == null) {
            this.tokens = tokenSupplier.get();
        }
        return this.tokens;
    }

    @Override
    public Token<AdhocTokenId> nextToken() {
        int pos = ++cursor;
        if (pos >= tokens().size()) {
            return null;
        }
        Token<AdhocTokenId> tok = tokens.get(pos);
        return tok;
    }

    @Override
    public Object state() {
        return null;
    }

    @Override
    public void release() {
        // do nothing
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(cursor=" + cursor + " over "
                + (tokens == null ? -1 : tokens.size()) + " tokens)";
    }
}

package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.Iterator;
import java.util.function.Supplier;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocLexer3 implements Lexer<AdhocTokenId> {

    private final Supplier<Iterator<Token<AdhocTokenId>>> tokenSupplier;
    private Iterator<Token<AdhocTokenId>> cursor;
    private final String mimeType;

    AdhocLexer3(Supplier<Iterator<Token<AdhocTokenId>>> tokens, String mimeType) {
        this.tokenSupplier = tokens;
        this.mimeType = mimeType;
        cursor();
    }

    private Iterator<Token<AdhocTokenId>> cursor() {
        if (cursor == null) {
            cursor = tokenSupplier.get();
        }
        return cursor;
    }

    @Override
    public Token<AdhocTokenId> nextToken() {
        Iterator<Token<AdhocTokenId>> cursor = cursor();
        if (cursor.hasNext()) {
            return cursor.next();
        }
        return null;
    }

    @Override
    public Object state() {
        return cursor;
    }

    @Override
    public void release() {
        // do nothing
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() 
                + " for " + AdhocMimeTypes.loggableMimeType(mimeType)
                + "(cursor=" + cursor + ")";
    }
}

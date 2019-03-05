package org.nemesis.antlr.spi.language;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;

/**
 *
 * @author Tim Boudreau
 */
final class WrapLexer<L extends org.antlr.v4.runtime.Lexer> implements IterableTokenSource {

    private final L delegate;
    private boolean eofEncountered;
    private boolean invoked;
    private final List<CommonToken> tokens = new ArrayList<>(512);

    WrapLexer(L delegate) {
        this.delegate = delegate;
    }

    @Override
    public String toString() {
        return WrapLexer.class.getName() + "{" + delegate + "}";
    }

    @Override
    public Iterator<CommonToken> iterator() {
        if (!invoked) {
            spin();
        }
        return tokens.iterator();
    }

    private void spin() {
        Token tok;
        do {
            tok = nextToken();
        } while (tok.getType() != -1);
    }

    L lexer() {
        return delegate;
    }

    @Override
    public Token nextToken() {
        invoked = true;
        Token result = delegate.nextToken();
        if (!eofEncountered) {
            eofEncountered = result != null && result.getType() == -1;
            tokens.add(result instanceof CommonToken
                    ? (CommonToken) result
                    : new CommonToken(result));
        }
        return result;
    }

    @Override
    public int getLine() {
        return delegate.getLine();
    }

    @Override
    public int getCharPositionInLine() {
        return delegate.getCharPositionInLine();
    }

    @Override
    public CharStream getInputStream() {
        return delegate.getInputStream();
    }

    @Override
    public String getSourceName() {
        return delegate.getSourceName();
    }

    @Override
    public void setTokenFactory(TokenFactory<?> tf) {
        delegate.setTokenFactory(tf);
    }

    @Override
    public TokenFactory<?> getTokenFactory() {
        return delegate.getTokenFactory();
    }

    @Override
    public void dispose() {
        tokens.clear();
    }

}

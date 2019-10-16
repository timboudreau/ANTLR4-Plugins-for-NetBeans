/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.spi.language;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;

/**
 * Wraps a lexer and implements TokenSource and allows for replaying the tokens
 * via the iterator.
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

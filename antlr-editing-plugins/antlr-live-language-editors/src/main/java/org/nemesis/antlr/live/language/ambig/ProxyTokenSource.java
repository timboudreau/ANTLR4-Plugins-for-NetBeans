/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language.ambig;

import com.mastfrog.antlr.utils.CharSequenceCharStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Pair;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;

/**
 * Ambiguity extraction is going to be problematic on lexers that have
 * programmatic predicates; since we have a way to get the set of tokens as
 * parsed in an isolating classloader by the real lexer with predicates active,
 * all we need to do is turn those back into CommonTokens as understood by our
 * own classloader, and we have a pre-chewed token stream where predicates were
 * used, that we can feed into our Parser that is dynamically created from the
 * grammar.
 *
 * @author Tim Boudreau
 */
public final class ProxyTokenSource implements TokenSource, TokenFactory<CommonToken> {

    private final AntlrProxies.ParseTreeProxy proxy;
    private int cursor = -1;

    public ProxyTokenSource(AntlrProxies.ParseTreeProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public String toString() {
        return "ProxyTokenSource(" + proxy.id() + " " + proxy.grammarName() + " "
                + proxy.grammarTokensHash() + ")";
    }

    @Override
    public Token nextToken() {
        ++cursor;
        AntlrProxies.ProxyToken tok = current();
        return toToken(tok);
    }

    private AntlrProxies.ProxyToken current() {
        if (cursor < 0) {
            return null;
        }
        if (cursor >= proxy.tokenCount()) {
            return proxy.tokens().get(proxy.tokenCount() - 1);
        }
        return proxy.tokens().get(cursor);
    }
    private Pair<TokenSource, CharStream> sourcePair;

    private Pair<TokenSource, CharStream> sourcePair() {
        if (sourcePair == null) {
            sourcePair = new Pair<>(this, getInputStream());
        }
        return sourcePair;
    }

    private Token toToken(AntlrProxies.ProxyToken tok) {
        CommonToken result = new CommonToken(sourcePair(), tok.getType(), tok.getChannel(), tok.getStartIndex(), tok.getStopIndex());
        result.setLine(tok.getLine());
        result.setCharPositionInLine(tok.getCharPositionInLine());
        result.setText(proxy.textOf(tok).toString());
        return result;
    }

    @Override
    public int getLine() {
        AntlrProxies.ProxyToken tok = current();
        return tok == null ? 0 : tok.getLine();
    }

    @Override
    public int getCharPositionInLine() {
        AntlrProxies.ProxyToken tok = current();
        return tok == null ? 0 : tok.getCharPositionInLine();
    }
    private CharStream chars;

    @Override
    public CharStream getInputStream() {
        if (chars == null) {
            chars = new CharSequenceCharStream("-", proxy.text());
        }
        return chars;
    }

    @Override
    public String getSourceName() {
        return "-";
    }

    @Override
    public void setTokenFactory(TokenFactory<?> factory) {
        // do nothing
    }

    @Override
    public TokenFactory<?> getTokenFactory() {
        return this;
    }

    @Override
    public CommonToken create(Pair<TokenSource, CharStream> source, int type, String text, int channel, int start, int stop, int line, int charPositionInLine) {
        return new CommonToken(source, type, channel, start, stop);
    }

    @Override
    public CommonToken create(int type, String text) {
        return new CommonToken(type, text);
    }
}

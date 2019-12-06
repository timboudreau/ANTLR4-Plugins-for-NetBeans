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
package com.mastfrog.tiny.ebnf.parser;

import java.util.Iterator;
import java.util.List;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Wraps a lexer and simply provides ALL of its tokens on channel 0, so we can
 * format without black magic for skipped tokens and tokens on alternate
 * channels.
 *
 * @author Tim Boudreau
 */
final class EverythingTokenStream implements TokenStream {

    private final List<CommonToken> tokens;
    int cursor = 0;
    int mark = -1;

    EverythingTokenStream(Lexer lexer, List<CommonToken> tokens) {
        this.tokens = tokens;
    }

    public Iterator<CommonToken> iterator() {
        return tokens.iterator();
    }

    void close() {
        tokens.clear();
        cursor = 0;
    }

    public void rewind() {
        cursor = 0;
    }

    public void setText(int tok, String txt) {
        CommonToken toChange = tokens.get(tok);
        int diff = txt.length() - toChange.getText().length();
        for (int i = tok + 1; i < tokens.size(); i++) {
            CommonToken t = tokens.get(i);
            t.setStartIndex(t.getStartIndex() + diff);
            t.setStopIndex(t.getStopIndex() + diff);
        }
        toChange.setText(txt);
    }

    @Override
    public CommonToken LT(int k) {
        if (k + cursor >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(cursor + k);
    }

    @Override
    public CommonToken get(int index) {
        return tokens.get(index);
    }

    @Override
    public TokenSource getTokenSource() {
        return null;
    }

    @Override
    public String getText(Interval interval) {
        StringBuilder sb = new StringBuilder();
        for (int i = interval.a; i <= interval.b; i++) {
            sb.append(tokens.get(i).getText());
        }
        return sb.toString();
    }

    @Override
    public String getText() {
        return getText(new Interval(0, tokens.size() - 1));
    }

    @Override
    public String getText(RuleContext ctx) {
        return getText(ctx.getSourceInterval());
    }

    @Override
    public String getText(Token start, Token stop) {
        int ix = tokens.indexOf(start);
        int end = tokens.indexOf(stop) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = ix; i < end; i++) {
            sb.append(tokens.get(i).getText());
        }
        return sb.toString();
    }

    @Override
    public void consume() {
        cursor++;
    }

    @Override
    public int LA(int i) {
        return LT(i).getType();
    }

    @Override
    public int mark() {
        return ++mark;
    }

    @Override
    public void release(int marker) {
        // do nothing
    }

    @Override
    public int index() {
        return cursor;
    }

    @Override
    public void seek(int index) {
        cursor = index;
    }

    @Override
    public int size() {
        return tokens.size();
    }

    @Override
    public String getSourceName() {
        return "x";
    }
}

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
package org.nemesis.antlrformatting.api;

import com.mastfrog.antlr.utils.Criterion;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Wraps a lexer and simply provides ALL of its tokens on channel 0, so we can
 * format without black magic for skipped tokens and tokens on alternate
 * channels.
 *
 * @author Tim Boudreau
 */
final class EverythingTokenStream implements EnhancedTokenStream {

    private final List<ModalToken> tokens = new ArrayList<>();
    int cursor = 0;

    EverythingTokenStream(Lexer lexer, String[] modeNames) {
        Token tok;
        for (int i = 0;; i++) {
            tok = lexer.nextToken();
            String modeName = modeNames[lexer._mode];
            ModalToken ct = new ModalToken(tok, lexer._mode, modeName);
            ct.setChannel(0);
            ct.setTokenIndex(i);
            tokens.add(ct);
            if (tok.getType() == Lexer.EOF) {
                break;
            }
        }
    }

    public Iterator<ModalToken> iterator() {
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
        ModalToken toChange = tokens.get(tok);
        int diff = txt.length() - toChange.getText().length();
        for (int i = tok + 1; i < tokens.size(); i++) {
            ModalToken t = tokens.get(i);
            t.setStartIndex(t.getStartIndex() + diff);
            t.setStopIndex(t.getStopIndex() + diff);
        }
        toChange.setText(txt);
    }

    public ModalToken findSubsequent(int after, Criterion pred) {
        return findSubsequent(after, (CommonToken t) -> {
            return pred.test(t.getType());
        });
    }

    public ModalToken findSubsequent(int after, Predicate<CommonToken> pred) {
        for (int i = after + 1; i < tokens.size(); i++) {
            if (pred.test(tokens.get(i))) {
                return tokens.get(i);
            }
        }
        return null;
    }

    @Override
    public ModalToken LT(int k) {
        if (k + cursor >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(cursor + k);
    }

    @Override
    public ModalToken get(int index) {
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
    int mark = -1;

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

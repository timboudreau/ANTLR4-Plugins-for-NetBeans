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
package org.nemesis.antlr.completion.annos;

import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 *
 * @author Tim Boudreau
 */
public interface TokenModification {

    default Interval getReplacementRange(List<Token> toks, Token caretToken) {
        Interval result = new Interval(Integer.MAX_VALUE, Integer.MIN_VALUE);
        updateReplacementRange(toks, caretToken, result);
        return result;
    }

    boolean isRange();

    Token findToken(List<Token> toks, Token caretToken);

    default void updateReplacementRange(List<Token> toks, Token caretToken, Interval into) {
        Token t = findToken(toks, caretToken);
        if (t != null) {
            into.a = Math.min(into.a, t.getStartIndex());
            into.b = Math.max(into.b, t.getStopIndex());
        }
    }

    public static Interval getReplacementRange(TokenModification first, Set<? extends TokenModification> insertActions, List<Token> toks, Token caretToken) {
        Interval result = new Interval(Integer.MAX_VALUE, Integer.MIN_VALUE);
        if (first != null) {
            first.updateReplacementRange(toks, caretToken, result);
        }
        for (TokenModification a : insertActions) {
            a.updateReplacementRange(toks, caretToken, result);
        }
        if (result.a == Integer.MAX_VALUE) {
            result.a = caretToken.getStartIndex();
            result.b = caretToken.getStartIndex();
        }
        return result;
    }

    public static Interval getReplacementRange(Set<? extends TokenModification> insertActions, List<Token> toks, Token caretToken) {
        return getReplacementRange(null, insertActions, toks, caretToken);
    }
}

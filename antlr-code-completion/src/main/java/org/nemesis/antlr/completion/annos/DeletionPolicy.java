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
import org.antlr.v4.runtime.Token;

/**
 *
 * @author Tim Boudreau
 */
public enum DeletionPolicy implements TokenModification {

    DELETE_PRECEDING_TOKEN,
    DELETE_CURRENT_TOKEN,
    DELETE_NEXT_TOKEN,
    DELETE_TOKEN_AFTER_NEXT,
    DELETE_TOKEN_BEFORE_PREVIOUS
    ;

    @Override
    public boolean isRange() {
        return true;
    }

    @Override
    public Token findToken(List<Token> toks, Token caretToken) {
        switch (this) {
            case DELETE_CURRENT_TOKEN:
                return caretToken;
            case DELETE_PRECEDING_TOKEN:
                return caretToken.getTokenIndex() > 0
                        ? toks.get(caretToken.getTokenIndex() - 1) : null;
            case DELETE_NEXT_TOKEN:
                return caretToken.getTokenIndex() < toks.size() - 1
                        ? toks.get(caretToken.getTokenIndex() + 1) : null;
            case DELETE_TOKEN_AFTER_NEXT :
                return caretToken.getTokenIndex() < toks.size() - 2
                        ? toks.get(caretToken.getTokenIndex() + 2) : null;
            case DELETE_TOKEN_BEFORE_PREVIOUS :
                return caretToken.getTokenIndex() > 1
                        ? toks.get(caretToken.getTokenIndex() - 2) : null;
            default:
                throw new AssertionError(this);
        }
    }
}

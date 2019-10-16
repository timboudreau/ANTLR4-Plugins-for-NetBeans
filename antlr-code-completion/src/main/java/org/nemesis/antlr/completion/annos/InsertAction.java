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
public enum InsertAction implements TokenModification {
    REPLACE_CURRENT_TOKEN,
    REPLACE_PRECEDING_TOKEN,
    REPLACE_NEXT_TOKEN,
    INSERT_BEFORE_CURRENT_TOKEN,
    INSERT_AFTER_CURRENT_TOKEN;

    @Override
    public boolean isRange() {
        switch (this) {
            case REPLACE_CURRENT_TOKEN:
            case REPLACE_NEXT_TOKEN:
            case REPLACE_PRECEDING_TOKEN:
                return true;
            default:
                return false;
        }
    }

    public Token findToken(List<Token> toks, Token caretToken) {
        int ix = caretToken.getTokenIndex();
        switch (this) {
            case REPLACE_CURRENT_TOKEN:
                return caretToken;
            case INSERT_BEFORE_CURRENT_TOKEN:
            case REPLACE_PRECEDING_TOKEN:
                if (ix > 0) {
                    return toks.get(ix - 1);
                }
            case INSERT_AFTER_CURRENT_TOKEN:
            case REPLACE_NEXT_TOKEN:
                if (ix < toks.size() - 1) {
                    return toks.get(ix + 1);
                }

        }
        return null;
    }



}

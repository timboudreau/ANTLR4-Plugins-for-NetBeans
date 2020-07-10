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
package com.mastfrog.antlr.code.completion.spi;

/**
 * Relationships the caret can have with a token.
 *
 * @author Tim Boudreau
 */
public enum CaretTokenRelation {
    /**
     * The caret precedes the first character of the token.
     */
    AT_TOKEN_START,
    /**
     * The caret is at a position after the start and less thane the end of the
     * token.
     */
    WITHIN_TOKEN,
    /**
     * The caret is at the position immediately subsequent to the last character
     * of the token.
     */
    AT_TOKEN_END,
    /**
     * The caret is not contained within or at the start or end of the token.
     */
    UNRELATED
}

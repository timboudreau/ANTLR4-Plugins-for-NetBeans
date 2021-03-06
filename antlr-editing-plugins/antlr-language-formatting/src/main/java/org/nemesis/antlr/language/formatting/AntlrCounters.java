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
package org.nemesis.antlr.language.formatting;

/**
 * Enum constants which our formatters tell the LexingState to collect as tokens
 * are processed, to provide positions and token count distances that are used
 * to decide indent depths and similar.
 *
 * @author Tim Boudreau
 */
public enum AntlrCounters {
    COLON_POSITION,
    LEFT_BRACE_POSITION,
    SEMICOLON_COUNT,
    LEFT_BRACES_PASSED,
    LEFT_PAREN_POS, 
    PARENS_DEPTH,
    LINE_COMMENT_INDENT,
    DISTANCE_TO_NEXT_SEMICOLON,
    DISTANCE_TO_PREV_EBNF,
    PENDING_OR_COUNT,
    DISTANCE_TO_NEXT_EBNF,
    OR_COUNT,
    DISTANCE_TO_PRECEDING_SEMICOLON,
    DISTANCE_TO_PRECEDING_COLON,
    DISTANCE_TO_RBRACE,
    DISTANCE_TO_LBRACE,
    LINE_COMMENT_COUNT,
    IN_OPTIONS, 
    OR_POSITION,
    DISTANCE_TO_PRECEDING_SHARP,
    PRECEDING_NON_WHITESPACE_TOKEN_START,
    PRECEDING_OR_POSITION,
    NEXT_OR_DISTANCE,
    NEXT_CLOSE_PAREN_DISTANCE,
    NEXT_OPEN_PAREN_DISTANCE,
    DISTANCE_TO_PRECEDING_RPAREN
}

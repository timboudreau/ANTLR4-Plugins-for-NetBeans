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
 *
 * @author Tim Boudreau
 */
public enum AntlrCounters {
    COLON_POSITION, LEFT_BRACE_POSITION, SEMICOLON_COUNT, LEFT_BRACES_PASSED,
    LEFT_PAREN_POS, PARENS_DEPTH, LINE_COMMENT_INDENT,
    DISTANCE_TO_NEXT_SEMICOLON, DISTANCE_TO_PRECEDING_SEMICOLON,
    DISTANCE_TO_PRECEDING_COLON, DISTANCE_TO_RBRACE, LINE_COMMENT_COUNT,
    IN_OPTIONS, OR_POSITION
}

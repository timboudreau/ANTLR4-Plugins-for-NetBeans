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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.completion.items;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class SortOrder {
    public final static int IDENTIFIER = 1;
    public final static int TOKEN_IDENTIFIER = 2;
    public final static int PARSER_RULE_IDENTIFIER = 3;
    public final static int KEYWORD = 4;
    public final static int PUNCTUATION = 5;
    public final static int STRING_LITERAL = 6;
    public final static int LEXER_CHAR_SET = 7;
    public final static int COMMENT = 8;
    public final static int DEFAULT = 100;
}

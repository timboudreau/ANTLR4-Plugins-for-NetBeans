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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

/**
 *
 * @author Tim Boudreau
 */
public enum RuleElementTarget {
    PARSER, LEXER, FRAGMENT;

    public RuleElementKind referenceKind() {
        switch (this) {
            case FRAGMENT:
                return RuleElementKind.FRAGMENT_RULE_REFERENCE;
            case LEXER:
                return RuleElementKind.LEXER_RULE_REFERENCE;
            case PARSER:
                return RuleElementKind.PARSER_RULE_REFERENCE;
            default:
                throw new AssertionError(this);
        }
    }

    public RuleElementKind declarationKind() {
        switch (this) {
            case FRAGMENT:
                return RuleElementKind.FRAGMENT_RULE_DECLARATION;
            case LEXER:
                return RuleElementKind.LEXER_RULE_DECLARATION;
            case PARSER:
                return RuleElementKind.PARSER_RULE_DECLARATION;
            default:
                throw new AssertionError(this);
        }
    }

}

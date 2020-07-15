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
package org.nemesis.antlr.common.extractiontypes;

import java.util.EnumSet;
import java.util.Set;
import org.nemesis.localizers.annotations.Localize;

/**
 *
 * @author Frédéric Yvon Vinet
 */
@Localize(displayName = "Grammar Type", iconPath = "org/nemesis/antlr/common/antlr-g4-file-type.png")
public enum GrammarType {
    @Localize(displayName = "Lexer Grammar")
    LEXER("lexer"),
    @Localize(displayName = "Parser Grammar")
    PARSER("parser"),
    @Localize(displayName = "Combined Grammar")
    COMBINED("combined"),
    @Localize(displayName = "Unrecognized Grammar Type")
    UNDEFINED("undefined");

    private final String value;

    GrammarType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public Set<RuleTypes> legalRuleTypes() {
        switch (this) {
            case UNDEFINED:
            case COMBINED:
                return EnumSet.allOf(RuleTypes.class);
            case LEXER:
                return EnumSet.of(RuleTypes.LEXER, RuleTypes.PARSER);
            case PARSER:
                return EnumSet.of(RuleTypes.PARSER, RuleTypes.NAMED_ALTERNATIVES);
            default:
                throw new AssertionError(this);
        }
    }

    public boolean canContain(RuleTypes type) {
        return legalRuleTypes().contains(type);
    }

    public static GrammarType toGrammarType(String grammarTypeString) {
        GrammarType grammarType;
        switch (grammarTypeString) {
            case "lexer":
                grammarType = GrammarType.LEXER;
                break;
            case "parser":
                grammarType = GrammarType.PARSER;
                break;
            case "combined":
                grammarType = GrammarType.COMBINED;
                break;
            default:
                grammarType = GrammarType.UNDEFINED;
        }
        return grammarType;
    }
};

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
import java.util.function.Supplier;
import org.nemesis.localizers.annotations.Localize;

/**
 *
 * @author Tim Boudreau
 */
@Localize(displayName = "Rule", iconPath = "org/nemesis/antlr/common/antlr-g4-file-type.png")
public enum RuleTypes implements Supplier<String> {
    @Localize(displayName = "Fragment", iconPath = "org/nemesis/antlr/common/fragment.png")
    FRAGMENT,
    @Localize(displayName = "Lexer", iconPath = "org/nemesis/antlr/common/lexer.png")
    LEXER,
    @Localize(displayName = "Parser", iconPath = "org/nemesis/antlr/common/parser.png")
    PARSER,
    @Localize(displayName = "Alternative", iconPath = "org/nemesis/antlr/common/alternative.png")
    NAMED_ALTERNATIVES;

    // only used for names, not bounds
    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public boolean isTopLevelRuleType() {
        return this != NAMED_ALTERNATIVES;
    }

    public boolean canAppearIn(GrammarType type) {
        return type.canContain(this);
    }

    public Set<GrammarType> legalIn() {
        switch (this) {
            case FRAGMENT:
            case LEXER:
                return EnumSet.of(GrammarType.COMBINED, GrammarType.LEXER, GrammarType.UNDEFINED);
            case NAMED_ALTERNATIVES:
            case PARSER:
                return EnumSet.of(GrammarType.COMBINED, GrammarType.PARSER, GrammarType.UNDEFINED);
            default:
                throw new AssertionError(this);
        }
    }

    /**
     * Used for some purpose in creating folds - should be replaced with localize hints.
     *
     * @return a string
     */
    @Override
    public String get() {
        switch (this) {
            case FRAGMENT:
                return "fragment-rule-name";
            case LEXER:
                return "lexer-rule-name";
            case PARSER:
                return "parser-rule-name";
            case NAMED_ALTERNATIVES:
                return "alternatives";
            default:
                return "default";
        }
    }
}

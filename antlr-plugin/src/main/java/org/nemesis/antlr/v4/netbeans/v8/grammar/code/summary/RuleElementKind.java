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

import java.util.EnumMap;
import java.util.Map;
import javax.swing.text.AttributeSet;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget.FRAGMENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget.LEXER;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget.PARSER;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorSettings;

/**
 *
 * @author Tim Boudreau
 */
public enum RuleElementKind {

    PARSER_RULE_DECLARATION(PARSER, true),
    PARSER_NAMED_ALTERNATIVE_SUBRULE(PARSER, false),
    LEXER_RULE_DECLARATION(LEXER, true),
    FRAGMENT_RULE_DECLARATION(FRAGMENT, true),
    PARSER_RULE_REFERENCE(PARSER, false),
    LEXER_RULE_REFERENCE(LEXER, false),
    FRAGMENT_RULE_REFERENCE(FRAGMENT, false);
    private final boolean declaration;
    private final RuleElementTarget target;

    RuleElementKind(RuleElementTarget target, boolean declaration) {
        this.target = target;
        this.declaration = declaration;
    }

    public boolean isDeclaration() {
        return declaration;
    }

    public RuleElementTarget target() {
        return target;
    }

    public boolean isAlternative() {
        return this == PARSER_NAMED_ALTERNATIVE_SUBRULE;
    }

    public String coloringName() {
        String coloringName;
        switch (this) {
            case FRAGMENT_RULE_DECLARATION:
                coloringName = "fragment_declaration";
                break;
            case LEXER_RULE_DECLARATION:
                coloringName = "lexer_declaration";
                break;
            case FRAGMENT_RULE_REFERENCE:
                coloringName = "fragment_reference";
                break;
            case LEXER_RULE_REFERENCE:
                coloringName = "token";
                break;
            case PARSER_RULE_DECLARATION:
                coloringName = "parser_declaration";
                break;
            case PARSER_RULE_REFERENCE:
                coloringName = "parserRuleIdentifier";
                break;
            case PARSER_NAMED_ALTERNATIVE_SUBRULE:
                coloringName = "named_alternative";
                break;
            default:
                throw new AssertionError(this);
        }
        return coloringName;
    }

    public static Map<RuleElementKind, AttributeSet> colorings() {
        // Do not cache - user can edit these
        MimePath mimePath = MimePath.parse(ANTLR_MIME_TYPE);
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        EnumMap<RuleElementKind, AttributeSet> result = new EnumMap<>(RuleElementKind.class);
        for (RuleElementKind kind : RuleElementKind.values()) {
            AttributeSet attrs = fcs.getTokenFontColors(kind.coloringName());
            assert attrs != null : kind.coloringName() + " missing from fonts and colors for text/x-g4";
            result.put(kind, attrs);
        }
        return result;
    }
}

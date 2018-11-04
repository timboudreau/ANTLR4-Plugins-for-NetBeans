/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

import java.util.EnumMap;
import java.util.Map;
import javax.swing.text.AttributeSet;
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
        MimePath mimePath = MimePath.parse("text/x-g4");
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

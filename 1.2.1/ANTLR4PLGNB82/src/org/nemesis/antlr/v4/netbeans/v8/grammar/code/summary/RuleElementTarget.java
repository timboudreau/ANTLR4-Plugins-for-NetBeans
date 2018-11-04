/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

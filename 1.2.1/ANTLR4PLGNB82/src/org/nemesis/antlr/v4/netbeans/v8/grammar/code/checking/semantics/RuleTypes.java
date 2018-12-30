package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

/**
 *
 * @author Tim Boudreau
 */
public enum RuleTypes {
    FRAGMENT, LEXER, PARSER, NAMED_ALTERNATIVES;

    // only used for names, not bounds
    public String toString() {
        return name().toLowerCase();
    }

}

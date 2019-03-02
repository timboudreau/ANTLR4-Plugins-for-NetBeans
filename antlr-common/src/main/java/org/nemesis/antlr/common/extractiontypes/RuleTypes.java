package org.nemesis.antlr.common.extractiontypes;

/**
 *
 * @author Tim Boudreau
 */
public enum RuleTypes {
    FRAGMENT, LEXER, PARSER, NAMED_ALTERNATIVES;

    // only used for names, not bounds
    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public boolean isTopLevelRuleType() {
        return this != NAMED_ALTERNATIVES;
    }
}

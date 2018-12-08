package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

/**
 * Visitor interface for traversing rule trees.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface RuleVisitor {

    /**
     * Enter a rule; subsequent calls to enterRule before a call to exitRule
     * means the subsequent rules are referenced by the previous one. EnterRule
     * will be called exactly once for each rule, starting from top-level and
     * orphan rules which have no antecedents. For rules referenced by
     * descendants, the order they are passed to enterRule is
     * implementation-depenedent - they will appear nested under some rule that
     * calls them, but not more than one.
     *
     * @param rule
     * @param depth
     */
    void enterRule(String rule, int depth);

    default void exitRule(String rule, int depth) {}

}
